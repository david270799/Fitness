package com.iron.fitness.di

import android.content.Context
import androidx.room.Room
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.db.IronMigrations
import com.iron.fitness.feature.cardio.data.IntervalDao
import com.iron.fitness.feature.exercises.data.ExerciseDao
import com.iron.fitness.feature.workouts.data.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IronDatabase =
        Room.databaseBuilder(context, IronDatabase::class.java, IronDatabase.NAME)
            // Начиная с версии 2 схема меняется только миграциями — записи тренировок сохраняются.
            .addMigrations(*IronMigrations.ALL)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    @Provides
    fun provideExerciseDao(db: IronDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutDao(db: IronDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideIntervalDao(db: IronDatabase): IntervalDao = db.intervalDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
