package com.iron.fitness.core.backup

import android.content.Context
import androidx.room.withTransaction
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полный бэкап в ZIP: база, настройки (без ключа Gemini), фото.
 * Восстановление готовится в отдельной папке и применяется при перезапуске приложения,
 * до открытия базы (см. [RestoreApplier]).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: IronDatabase,
    private val settings: SettingsRepository,
    private val photos: PhotoStorage,
    private val json: Json,
) {
    private val settingsSerializer = MapSerializer(String.serializer(), String.serializer())

    /** Записать бэкап в поток. Поток закрывается. */
    suspend fun write(out: OutputStream): BackupManifest = withContext(Dispatchers.IO) {
        val photoFiles = photos.root.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.toList().orEmpty()
        val manifest = BackupManifest(
            dbVersion = IronDatabase.VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = appVersion(),
            filesDir = context.filesDir.absolutePath,
            photos = photoFiles.size,
        )
        val snapshot = File(context.cacheDir, "backup_db").apply { deleteRecursively(); mkdirs() }
        try {
            snapshotDatabase(snapshot)
            val settingsText = json.encodeToString(settingsSerializer, settings.exportRaw())
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                zip.putText(BackupFormat.MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest))
                zip.putText(BackupFormat.SETTINGS, settingsText)
                snapshot.listFiles()?.forEach { f -> zip.putFile(BackupFormat.DB_DIR + f.name, f) }
                photoFiles.forEach { f -> zip.putFile(BackupFormat.PHOTOS_DIR + f.relativeTo(photos.root).invariantSeparatorsPath, f) }
            }
        } finally {
            snapshot.deleteRecursively()
        }
        manifest
    }

    /** Согласованная копия файлов базы: пока копируем, запись в базу заблокирована. */
    private suspend fun snapshotDatabase(dir: File) {
        val dbFile = context.getDatabasePath(IronDatabase.NAME)
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        db.withTransaction {
            dbFile.copyTo(File(dir, IronDatabase.NAME), overwrite = true)
            val wal = File(dbFile.path + "-wal")
            if (wal.exists() && wal.length() > 0) wal.copyTo(File(dir, IronDatabase.NAME + "-wal"), overwrite = true)
        }
    }

    /**
     * Проверить архив и подготовить восстановление. Данные заменятся при следующем запуске —
     * после этого вызова приложение нужно перезапустить.
     */
    suspend fun stageRestore(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, STAGING).apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES) throw BackupException(BackupProblem.BROKEN)
                    if (entry.isDirectory) continue
                    val path = BackupFormat.safeEntryPath(entry.name) ?: throw BackupException(BackupProblem.BROKEN)
                    val target = File(staging, path)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
            val manifestFile = File(staging, BackupFormat.MANIFEST)
            if (!manifestFile.exists()) throw BackupException(BackupProblem.NOT_IRON)
            val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), manifestFile.readText()) }
                .getOrElse { throw BackupException(BackupProblem.NOT_IRON) }
            BackupFormat.check(manifest, IronDatabase.VERSION)?.let { throw BackupException(it) }
            val dbFile = File(staging, BackupFormat.DB_DIR + IronDatabase.NAME)
            val header = if (dbFile.exists()) dbFile.inputStream().use { s -> ByteArray(16).also { s.read(it) } } else ByteArray(0)
            if (!BackupFormat.isSqliteHeader(header)) throw BackupException(BackupProblem.BROKEN)
            File(staging, READY).writeText(manifest.createdAt.toString())
            manifest
        } catch (e: Exception) {
            staging.deleteRecursively()
            if (e is BackupException || e is CancellationException) throw e
            throw BackupException(BackupProblem.BROKEN)
        }
    }

    /** Настройки из восстановленного бэкапа (применяются после перезапуска). */
    suspend fun applyRestoredSettings() = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, RESTORED_SETTINGS)
        if (!file.exists()) return@withContext
        runCatching { settings.importRaw(json.decodeFromString(settingsSerializer, file.readText())) }
        file.delete()
    }

    private fun appVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    companion object {
        const val STAGING = "restore_pending"
        const val READY = ".ready"
        const val RESTORED_SETTINGS = "restored_settings.json"
        private const val MAX_ENTRIES = 50_000
    }
}
