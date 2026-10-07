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

    /** Этап 7: напоминания о приёме и журнал приёмов. */
    const val CREATE_REMINDERS =
        "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `dose` TEXT, `note` TEXT, `photoPath` TEXT, `times` TEXT NOT NULL, " +
            "`weekdays` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
    const val CREATE_REMINDER_LOGS =
        "CREATE TABLE IF NOT EXISTS `reminder_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`reminderId` INTEGER NOT NULL, `scheduledAt` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
            "`actedAt` INTEGER, `repeats` INTEGER NOT NULL)"
    const val INDEX_REMINDER_LOGS =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_logs_reminderId_scheduledAt` ON `reminder_logs` (`reminderId`, `scheduledAt`)"

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_REMINDERS)
            db.execSQL(CREATE_REMINDER_LOGS)
            db.execSQL(INDEX_REMINDER_LOGS)
        }
    }

    /** Этап 9: замеры тела и фото прогресса. */
    const val CREATE_BODY_MEASUREMENTS =
        "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`day` INTEGER NOT NULL, `weightKg` REAL, `bodyFatPct` REAL, `waistCm` REAL, `chestCm` REAL, " +
            "`hipsCm` REAL, `armCm` REAL, `thighCm` REAL, `calfCm` REAL, `neckCm` REAL, `note` TEXT, " +
            "`createdAt` INTEGER NOT NULL)"
    const val INDEX_BODY_MEASUREMENTS =
        "CREATE INDEX IF NOT EXISTS `index_body_measurements_day` ON `body_measurements` (`day`)"
    const val CREATE_PROGRESS_PHOTOS =
        "CREATE TABLE IF NOT EXISTS `progress_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`day` INTEGER NOT NULL, `path` TEXT NOT NULL, `pose` TEXT NOT NULL, `weightKg` REAL, `note` TEXT, " +
            "`createdAt` INTEGER NOT NULL)"
    const val INDEX_PROGRESS_PHOTOS =
        "CREATE INDEX IF NOT EXISTS `index_progress_photos_day` ON `progress_photos` (`day`)"

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_BODY_MEASUREMENTS)
            db.execSQL(INDEX_BODY_MEASUREMENTS)
            db.execSQL(CREATE_PROGRESS_PHOTOS)
            db.execSQL(INDEX_PROGRESS_PHOTOS)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
