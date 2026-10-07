package com.iron.fitness

import com.iron.fitness.core.alarms.AlarmRescheduler
import com.iron.fitness.core.backup.BackupManager
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
    private val alarms: AlarmRescheduler,
    private val backup: BackupManager,
) {
    fun start() {
        scope.launch {
            // Сначала настройки из восстановленного бэкапа (если были), потом библиотека.
            runCatching { backup.applyRestoredSettings() }
            runCatching { exercises.ensureLibraryImported() }
        }
        // Будильники могли пропасть (принудительная остановка, обновление) — ставим заново.
        scope.launch { alarms.rescheduleAll() }
    }
}
