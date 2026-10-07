package com.iron.fitness.feature.cardio.run

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.Intervals
import com.iron.fitness.core.domain.Phase
import com.iron.fitness.di.ApplicationScope
import com.iron.fitness.feature.cardio.data.CardioEntry
import com.iron.fitness.feature.cardio.data.CardioRepository
import com.iron.fitness.feature.cardio.data.CardioType
import com.iron.fitness.feature.cardio.data.Intensity
import com.iron.fitness.feature.cardio.data.IntervalProgram
import com.iron.fitness.feature.timer.TimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class RunKind { INTERVAL, CARDIO }

/**
 * Состояние запущенной программы. Время — по elapsedRealtime: не зависит от сна экрана и смены часов.
 */
data class RunState(
    val kind: RunKind,
    val title: String,
    val programId: Long?,
    val cardioType: CardioType?,
    val workMet: Double,
    val phases: List<Phase>,
    val index: Int = 0,
    /** Сколько уже прошло в текущем отрезке до последней паузы. */
    val phaseAccumMs: Long = 0,
    /** Когда отрезок запущен (после паузы), null — пауза. */
    val runningSince: Long?,
    val startedAtWall: Long,
    /** Время завершённых отрезков по типам. */
    val spentMs: Map<BlockType, Long> = emptyMap(),
    val finished: Boolean = false,
    val savedWorkoutId: Long? = null,
) {
    val paused: Boolean get() = runningSince == null
    val current: Phase? get() = phases.getOrNull(index)
    val next: Phase? get() = phases.getOrNull(index + 1)

    fun phaseRunMs(now: Long = SystemClock.elapsedRealtime()): Long =
        phaseAccumMs + (runningSince?.let { now - it } ?: 0L)

    /** Остаток текущего отрезка; null — отрезок без ограничения времени. */
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long? {
        val p = current ?: return null
        if (p.seconds <= 0) return null
        return (p.seconds * 1000L - phaseRunMs(now)).coerceAtLeast(0)
    }

    fun totalSpentMs(now: Long = SystemClock.elapsedRealtime()): Long =
        spentMs.values.sum() + if (finished) 0 else phaseRunMs(now)

    /** Остаток всей программы (без открытых отрезков). */
    fun totalRemainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (remainingMs(now) ?: 0L) + phases.drop(index + 1).sumOf { it.seconds * 1000L }

    val plannedMs: Long get() = phases.sumOf { it.seconds * 1000L }

    /** Время по типам с учётом текущего отрезка (для калорий). */
    fun spentWithCurrent(now: Long = SystemClock.elapsedRealtime()): Map<BlockType, Long> {
        val p = current ?: return spentMs
        if (finished) return spentMs
        return spentMs + (p.type to ((spentMs[p.type] ?: 0L) + phaseRunMs(now)))
    }
}

/** Событие перехода, на которое сервис отвечает сигналом. */
enum class RunnerEvent { NEXT_PHASE, FINISHED }

/**
 * Исполнитель интервальных программ и кардио-секундомера. Сам ничего не отсчитывает —
 * отсчёт ведёт [TimerService] (через [ProgramRunnerHost]), чтобы работать при выключенном экране.
 */
@Singleton
class ProgramRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CardioRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RunState?>(null)
    val state: StateFlow<RunState?> = _state.asStateFlow()
    private val saveLock = Mutex()

    val isRunning: Boolean get() = _state.value?.let { !it.finished } == true

    fun startInterval(program: IntervalProgram) {
        val phases = Intervals.expand(program.blocks)
        if (phases.isEmpty()) return
        _state.value = RunState(
            kind = RunKind.INTERVAL,
            title = program.name,
            programId = program.id.takeIf { it > 0 },
            cardioType = null,
            workMet = program.workMet,
            phases = phases,
            runningSince = SystemClock.elapsedRealtime(),
            startedAtWall = System.currentTimeMillis(),
        )
        startService()
    }

    /** Кардио-секундомер: один открытый отрезок, идёт до «Завершить». */
    fun startCardio(type: CardioType, title: String) {
        _state.value = RunState(
            kind = RunKind.CARDIO,
            title = title,
            programId = null,
            cardioType = type,
            workMet = type.met(Intensity.MODERATE),
            phases = listOf(Phase(BlockType.WORK, 0, title, null)),
            runningSince = SystemClock.elapsedRealtime(),
            startedAtWall = System.currentTimeMillis(),
        )
        startService()
    }

    fun togglePause() {
        val now = SystemClock.elapsedRealtime()
        _state.update { s ->
            if (s == null || s.finished) return@update s
            if (s.paused) s.copy(runningSince = now) else s.copy(phaseAccumMs = s.phaseRunMs(now), runningSince = null)
        }
        refreshService()
    }

    /** Перейти к следующему отрезку. Возвращает событие (следующий отрезок или конец программы). */
    fun advance(): RunnerEvent? {
        val now = SystemClock.elapsedRealtime()
        var event: RunnerEvent? = null
        _state.update { s ->
            if (s == null || s.finished) return@update s
            val p = s.current ?: return@update s
            val spent = s.spentMs + (p.type to ((s.spentMs[p.type] ?: 0L) + s.phaseRunMs(now)))
            if (s.index + 1 >= s.phases.size) {
                event = RunnerEvent.FINISHED
                s.copy(spentMs = spent, finished = true, runningSince = null, phaseAccumMs = 0)
            } else {
                event = RunnerEvent.NEXT_PHASE
                s.copy(
                    index = s.index + 1,
                    spentMs = spent,
                    phaseAccumMs = 0,
                    runningSince = if (s.paused) null else now,
                )
            }
        }
        if (event == RunnerEvent.FINISHED) saveFinished()
        refreshService()
        return event
    }

    /** Завершить досрочно: с сохранением в журнал или без. */
    fun stop(save: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val s = _state.value ?: return
        if (s.finished) {
            refreshService()
            return
        }
        val p = s.current
        val spent = if (p != null) s.spentMs + (p.type to ((s.spentMs[p.type] ?: 0L) + s.phaseRunMs(now))) else s.spentMs
        val total = spent.values.sum()
        if (!save || total < MIN_SAVE_MS) {
            _state.value = null
        } else {
            _state.value = s.copy(spentMs = spent, finished = true, runningSince = null, phaseAccumMs = 0)
            saveFinished()
        }
        refreshService()
    }

    /** Убрать экран итогов. */
    fun dismiss() {
        if (_state.value?.finished == true) _state.value = null
        refreshService()
    }

    private fun saveFinished() {
        val s = _state.value ?: return
        if (!s.finished || s.savedWorkoutId != null) return
        scope.launch {
            saveLock.withLock {
                val current = _state.value ?: return@withLock
                if (current.savedWorkoutId != null) return@withLock
                val id = when (current.kind) {
                    RunKind.INTERVAL -> repository.saveInterval(
                        name = current.title,
                        programId = current.programId,
                        startedAt = current.startedAtWall,
                        spentMsByType = current.spentMs,
                        workMet = current.workMet,
                    )
                    RunKind.CARDIO -> repository.saveCardio(
                        CardioEntry(
                            type = current.cardioType ?: CardioType.OTHER,
                            name = current.title,
                            startedAt = current.startedAtWall,
                            durationSec = current.spentMs.values.sum() / 1000,
                            distanceKm = null,
                            intensity = Intensity.MODERATE,
                            note = null,
                        ),
                    )
                }
                _state.update { it?.copy(savedWorkoutId = id) }
            }
        }
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

    private fun refreshService() {
        runCatching {
            context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_REFRESH))
        }
    }

    companion object {
        /** Короче 10 секунд в журнал не сохраняем. */
        const val MIN_SAVE_MS = 10_000L
    }
}
