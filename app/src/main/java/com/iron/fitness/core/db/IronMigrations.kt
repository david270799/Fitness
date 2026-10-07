package com.iron.fitness.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции базы. SQL совпадает с тем, что генерирует Room (проверяется тестом по экспортированной схеме).
 */
object IronMigrations {
    /** Этап 4: интервальные программы и интенсивность кардио. */
    const val CREATE_INTERVAL_PROGRAMS =
        "CREATE TABLE IF NOT EXISTS `interval_programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `note` TEXT, `blocksJson` TEXT NOT NULL, `workMet` REAL NOT NULL, " +
            "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
    const val ADD_WORKOUT_INTENSITY = "ALTER TABLE `workouts` ADD COLUMN `intensity` TEXT"

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_INTERVAL_PROGRAMS)
            db.execSQL(ADD_WORKOUT_INTENSITY)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3)
}
