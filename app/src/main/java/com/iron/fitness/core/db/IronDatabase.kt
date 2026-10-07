package com.iron.fitness.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iron.fitness.feature.exercises.data.ExerciseDao
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
    ],
    version = IronDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IronDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val VERSION = 2
        const val NAME = "iron.db"
    }
}
