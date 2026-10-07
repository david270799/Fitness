package com.iron.fitness.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupFormatTest {
    @Test
    fun safePathsAreNormalized() {
        assertEquals("photos/progress/a.jpg", BackupFormat.safeEntryPath("photos/progress/a.jpg"))
        assertEquals("photos/a.jpg", BackupFormat.safeEntryPath("./photos//a.jpg"))
        assertEquals("database/iron.db", BackupFormat.safeEntryPath("database\\iron.db"))
    }

    @Test
    fun dangerousPathsAreRejected() {
        assertNull(BackupFormat.safeEntryPath("../shared_prefs/x.xml"))
        assertNull(BackupFormat.safeEntryPath("photos/../../x"))
        assertNull(BackupFormat.safeEntryPath("/data/data/x"))
        assertNull(BackupFormat.safeEntryPath("C:/x"))
        assertNull(BackupFormat.safeEntryPath(""))
    }

    @Test
    fun pruneKeepsNewest() {
        val items = listOf(5L, 1L, 4L, 2L, 3L)
        assertEquals(listOf(2L, 1L), BackupFormat.toPrune(items, 3) { it })
        assertTrue(BackupFormat.toPrune(items, 10) { it }.isEmpty())
    }

    @Test
    fun compatibilityCheck() {
        val ok = BackupManifest(dbVersion = 7, createdAt = 0)
        assertNull(BackupFormat.check(ok, 8))
        assertNull(BackupFormat.check(ok.copy(dbVersion = 8), 8))
        assertEquals(BackupProblem.NEWER_APP, BackupFormat.check(ok.copy(dbVersion = 9), 8))
        assertEquals(BackupProblem.NOT_IRON, BackupFormat.check(ok.copy(app = "OTHER"), 8))
        assertEquals(BackupProblem.NEWER_FORMAT, BackupFormat.check(ok.copy(format = 2), 8))
    }

    @Test
    fun sqliteHeader() {
        val header = "SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + ByteArray(84)
        assertTrue(BackupFormat.isSqliteHeader(header))
        assertFalse(BackupFormat.isSqliteHeader("PK".toByteArray()))
    }

    @Test
    fun fileNameHasDateAndTime() {
        assertEquals("iron-backup-2026-03-05_07-09.zip", BackupFormat.fileName(LocalDateTime.of(2026, 3, 5, 7, 9)))
    }
}
