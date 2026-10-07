package com.iron.fitness.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iron.fitness.feature.exercises.data.ExerciseDao
import com.iron.fitness.feature.exercises.data.ExerciseEntity

@Database(
    entities = [
        ExerciseEntity::class,
    ],
    version = IronDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IronDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val VERSION = 1
        const val NAME = "iron.db"
    }
}
