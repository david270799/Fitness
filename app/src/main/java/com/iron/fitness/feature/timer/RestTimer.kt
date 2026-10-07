package com.iron.fitness.feature.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Состояние таймера отдыха. Время считается по elapsedRealtime, поэтому не зависит от сна экрана. */
data class RestState(
    val endElapsed: Long,
    val totalSec: Int,
    val label: String?,
) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = (endElapsed - now).coerceAtLeast(0)
    fun remainingSec(now: Long = SystemClock.elapsedRealtime()): Int = ((remainingMs(now) + 999) / 1000).toInt()
    fun progress(now: Long = SystemClock.elapsedRealtime()): Float =
        if (totalSec <= 0) 0f else (remainingMs(now) / 1000f / totalSec).coerceIn(0f, 1f)
}

@Singleton
class RestTimer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<RestState?>(null)
    val state: StateFlow<RestState?> = _state.asStateFlow()

    fun start(seconds: Int, label: String? = null) {
        if (seconds <= 0) return
        _state.value = RestState(SystemClock.elapsedRealtime() + seconds * 1000L, seconds, label)
        startService()
    }

    fun add(seconds: Int) {
        val s = _state.value ?: return
        val newEnd = s.endElapsed + seconds * 1000L
        val now = SystemClock.elapsedRealtime()
        if (newEnd <= now) {
            stop()
            return
        }
        _state.value = s.copy(endElapsed = newEnd, totalSec = (s.totalSec + seconds).coerceAtLeast(1))
        startService()
    }

    fun stop() {
        if (_state.value == null) return
        _state.value = null
        // Сервис уже запущен (идёт отсчёт), поэтому обычный startService допустим.
        runCatching {
            context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_REFRESH))
        }
    }

    /** Вызывается сервисом при окончании отдыха. */
    internal fun onFinished() {
        _state.value = null
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java)
                    .setAction(TimerService.ACTION_REFRESH)
                    .putExtra(TimerService.EXTRA_FOREGROUND, true),
            )
        }
    }
}
