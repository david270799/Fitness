package com.iron.fitness.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iron.fitness.feature.cardio.data.IntervalDao
import com.iron.fitness.feature.cardio.data.IntervalProgramEntity
import com.iron.fitness.feature.daily.data.ChallengeDao
import com.iron.fitness.feature.daily.data.ChallengeEntity
import com.iron.fitness.feature.daily.data.ChallengeGoalEntity
import com.iron.fitness.feature.daily.data.ChallengeLogEntity
import com.iron.fitness.feature.body.data.BodyDao
import com.iron.fitness.feature.body.data.MeasurementEntity
import com.iron.fitness.feature.body.data.ProgressPhotoEntity
import com.iron.fitness.feature.exercises.data.ExerciseDao
import com.iron.fitness.feature.reminders.data.ReminderDao
import com.iron.fitness.feature.reminders.data.ReminderEntity
import com.iron.fitness.feature.reminders.data.ReminderLogEntity
import com.iron.fitness.feature.stretching.data.StretchDao
import com.iron.fitness.feature.stretching.data.StretchRoutineEntity
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.workouts.data.RoutineEntity
import com.iron.fitness.feature.workouts.data.RoutineExerciseEntity
import com.iron.fitness.feature.workouts.data.WorkoutDao
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutExerciseEntity
import com.iron.fitness.feature.workouts.data.WorkoutSetEntity

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        IntervalProgramEntity::class,
        StretchRoutineEntity::class,
        ChallengeEntity::class,
        ChallengeGoalEntity::class,
        ChallengeLogEntity::class,
        ReminderEntity::class,
        ReminderLogEntity::class,
        MeasurementEntity::class,
        ProgressPhotoEntity::class,
    ],
    version = IronDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IronDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun intervalDao(): IntervalDao
    abstract fun stretchDao(): StretchDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun bodyDao(): BodyDao

    companion object {
        const val VERSION = 7
        const val NAME = "iron.db"
    }
}
