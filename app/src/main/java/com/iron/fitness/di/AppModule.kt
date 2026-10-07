package com.iron.fitness.di

import android.content.Context
import androidx.room.Room
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.feature.exercises.data.ExerciseDao
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
            // До первого релиза схема меняется от этапа к этапу; после релиза — только миграции.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideExerciseDao(db: IronDatabase): ExerciseDao = db.exerciseDao()

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
