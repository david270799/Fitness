package com.iron.fitness.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iron.fitness.feature.cardio.data.IntervalDao
import com.iron.fitness.feature.cardio.data.IntervalProgramEntity
import com.iron.fitness.feature.exercises.data.ExerciseDao
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

    companion object {
        const val VERSION = 4
        const val NAME = "iron.db"
    }
}
