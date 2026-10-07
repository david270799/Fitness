package com.iron.fitness

import com.iron.fitness.di.ApplicationScope
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Фоновые задачи при запуске приложения. */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val exercises: ExerciseRepository,
) {
    fun start() {
        scope.launch { runCatching { exercises.ensureLibraryImported() } }
    }
}
