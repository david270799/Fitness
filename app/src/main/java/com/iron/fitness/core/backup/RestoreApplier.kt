package com.iron.fitness.core.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.media.PhotoStorage
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Применяет подготовленное восстановление. Вызывается в самом начале запуска приложения,
 * пока база ещё не открыта.
 */
object RestoreApplier {
    private const val TAG = "IronRestore"
    private val json = Json { ignoreUnknownKeys = true }

    fun applyPending(context: Context) {
        val staging = File(context.filesDir, BackupManager.STAGING)
        if (!staging.exists()) return
        if (File(staging, BackupManager.READY).exists()) {
            runCatching { apply(context, staging) }.onFailure { Log.e(TAG, "Не удалось восстановить бэкап", it) }
        }
        staging.deleteRecursively()
    }

    private fun apply(context: Context, staging: File) {
        val manifest = json.decodeFromString(BackupManifest.serializer(), File(staging, BackupFormat.MANIFEST).readText())
        val dbDir = File(staging, BackupFormat.DB_DIR)
        val target = context.getDatabasePath(IronDatabase.NAME)
        target.parentFile?.mkdirs()

        // Сначала кладём новую базу рядом, затем заменяем — старая не удаляется, пока новая не скопирована.
        val incoming = File(target.path + ".restore")
        File(dbDir, IronDatabase.NAME).copyTo(incoming, overwrite = true)
        val incomingWal = File(dbDir, IronDatabase.NAME + "-wal").takeIf { it.exists() }
            ?.copyTo(File(target.path + ".restore-wal"), overwrite = true)
        listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        check(incoming.renameTo(target)) { "rename db" }
        incomingWal?.renameTo(File(target.path + "-wal"))

        val oldFiles = manifest.filesDir
        val newFiles = context.filesDir.absolutePath
        if (oldFiles != null && oldFiles != newFiles) rewritePaths(target, oldFiles, newFiles)

        val photosRoot = File(context.filesDir, PhotoStorage.ROOT_DIR)
        photosRoot.deleteRecursively()
        File(staging, BackupFormat.PHOTOS_DIR).takeIf { it.isDirectory }?.let { src ->
            if (!src.renameTo(photosRoot)) src.copyRecursively(photosRoot, overwrite = true)
        }

        File(staging, BackupFormat.SETTINGS).takeIf { it.exists() }
            ?.copyTo(File(context.filesDir, BackupManager.RESTORED_SETTINGS), overwrite = true)
    }

    /** Фото хранятся по абсолютным путям: переносим их на папку этого устройства. */
    private fun rewritePaths(dbFile: File, old: String, new: String) {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('android_metadata', 'room_master_table')",
                null,
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            for (table in tables) {
                val columns = db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
                    buildList { while (c.moveToNext()) if (c.getString(2).equals("TEXT", ignoreCase = true)) add(c.getString(1)) }
                }
                for (col in columns) {
                    db.execSQL(
                        "UPDATE `$table` SET `$col` = REPLACE(`$col`, ?, ?) WHERE instr(`$col`, ?) > 0",
                        arrayOf<Any?>(old, new, old),
                    )
                }
            }
        }
    }
}

/** Перезапуск приложения (после восстановления данных). */
object AppRestart {
    fun restart(context: Context) {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
