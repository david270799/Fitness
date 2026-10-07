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

    /** Этап 5: свои комплексы растяжки. */
    const val CREATE_STRETCH_ROUTINES =
        "CREATE TABLE IF NOT EXISTS `stretch_routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `phase` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_STRETCH_ROUTINES)
        }
    }

    /** Этап 6: дневные челленджи, история целей и записи прогресса. */
    const val CREATE_CHALLENGES =
        "CREATE TABLE IF NOT EXISTS `challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `unit` TEXT NOT NULL, `goal` INTEGER NOT NULL, `step1` INTEGER NOT NULL, " +
            "`step2` INTEGER NOT NULL, `exerciseId` TEXT, `reminderEnabled` INTEGER NOT NULL, " +
            "`reminderMinutes` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL)"
    const val CREATE_CHALLENGE_GOALS =
        "CREATE TABLE IF NOT EXISTS `challenge_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`challengeId` INTEGER NOT NULL, `fromDay` INTEGER NOT NULL, `goal` INTEGER NOT NULL)"
    const val INDEX_CHALLENGE_GOALS =
        "CREATE INDEX IF NOT EXISTS `index_challenge_goals_challengeId` ON `challenge_goals` (`challengeId`)"
    const val CREATE_CHALLENGE_LOGS =
        "CREATE TABLE IF NOT EXISTS `challenge_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`challengeId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
    const val INDEX_CHALLENGE_LOGS =
        "CREATE INDEX IF NOT EXISTS `index_challenge_logs_challengeId_day` ON `challenge_logs` (`challengeId`, `day`)"

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_CHALLENGES)
            db.execSQL(CREATE_CHALLENGE_GOALS)
            db.execSQL(INDEX_CHALLENGE_GOALS)
            db.execSQL(CREATE_CHALLENGE_LOGS)
            db.execSQL(INDEX_CHALLENGE_LOGS)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
