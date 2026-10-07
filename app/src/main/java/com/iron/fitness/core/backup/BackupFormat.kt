package com.iron.fitness.core.backup

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Описание архива бэкапа (manifest.json). */
@Serializable
data class BackupManifest(
    val app: String = BackupFormat.APP,
    val format: Int = BackupFormat.FORMAT,
    val dbVersion: Int,
    val createdAt: Long,
    val appVersion: String? = null,
    /** filesDir устройства, где делали бэкап: пути к фото в базе переписываются при восстановлении. */
    val filesDir: String? = null,
    val photos: Int = 0,
)

/** Почему архив нельзя восстановить. */
enum class BackupProblem { NOT_IRON, NEWER_FORMAT, NEWER_APP, BROKEN }

class BackupException(val problem: BackupProblem) : Exception(problem.name)

/**
 * Формат архива:
 * manifest.json, settings.json (настройки без ключей), database/iron.db (+ -wal), photos/…
 */
object BackupFormat {
    const val APP = "IRON"
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val DB_DIR = "database/"
    const val PHOTOS_DIR = "photos/"
    const val MIME = "application/zip"

    private val nameTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

    fun fileName(at: LocalDateTime): String = "iron-backup-${at.format(nameTime)}.zip"

    /** Относительный путь записи архива или null, если путь опасный (абсолютный, с «..»). */
    fun safeEntryPath(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains('\u0000')) return null
        if (normalized.length > 2 && normalized[1] == ':') return null
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** Что удалить, чтобы осталось [keep] самых новых копий. */
    fun <T> toPrune(items: List<T>, keep: Int, createdAt: (T) -> Long): List<T> =
        items.sortedByDescending(createdAt).drop(keep.coerceAtLeast(0))

    /** Проблема совместимости или null, если архив можно восстановить в этой версии приложения. */
    fun check(manifest: BackupManifest, currentDbVersion: Int): BackupProblem? = when {
        manifest.app != APP -> BackupProblem.NOT_IRON
        manifest.format > FORMAT -> BackupProblem.NEWER_FORMAT
        manifest.dbVersion > currentDbVersion -> BackupProblem.NEWER_APP
        manifest.dbVersion < 1 -> BackupProblem.BROKEN
        else -> null
    }

    /** Заголовок файла SQLite. */
    fun isSqliteHeader(bytes: ByteArray): Boolean =
        bytes.size >= 16 && String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3" && bytes[15] == 0.toByte()
}
