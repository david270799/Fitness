package com.iron.fitness.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.domain.Streaks
import com.iron.fitness.feature.stretching.data.StretchLauncher
import com.iron.fitness.feature.stretching.data.StretchRepository
import com.iron.fitness.feature.stretching.data.StretchTemplates
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.workouts.data.RoutineEntity
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TodayState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val active: WorkoutEntity? = null,
    /** Следующий шаблон по кругу после последнего выполненного. */
    val nextRoutine: RoutineEntity? = null,
    val routineCount: Int = 0,
    val weekWorkouts: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val weekStreak: Int = 0,
    val last: WorkoutEntity? = null,
    val heatmap: Map<LocalDate, Int> = emptyMap(),
    /** Растяжка за сегодня. */
    val stretchToday: List<WorkoutEntity> = emptyList(),
    /** Последняя силовая сегодня — для подбора заминки. */
    val strengthTodayId: Long? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val stretchRepo: StretchRepository,
    private val stretch: StretchLauncher,
) : ViewModel() {

    private val since: Long = LocalDate.now().minusWeeks(HEATMAP_WEEKS.toLong() + 1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val state: StateFlow<TodayState> = combine(
        repo.observeActiveStrength(),
        repo.observeRoutines(),
        repo.observeRoutineLastUse(),
        repo.observeFinishedSince(since),
        stretchRepo.observeToday(),
    ) { active, routines, lastUse, finished, stretchToday ->
        val today = LocalDate.now()
        val weekStart = Streaks.weekStart(today)
        val dates = finished.map { Fmt.toLocalDate(it.startedAt) }
        val thisWeek = finished.filter { !Fmt.toLocalDate(it.startedAt).isBefore(weekStart) }
        val heat = dates.groupingBy { it }.eachCount()

        // Следующий по плану: шаблон после последнего выполненного (по порядку в списке).
        val ordered = routines.map { it.routine }
        val lastRoutineId = lastUse.maxByOrNull { it.lastAt }?.routineId
        val next = if (ordered.isEmpty()) {
            null
        } else {
            val idx = ordered.indexOfFirst { it.id == lastRoutineId }
            if (idx < 0) ordered.first() else ordered[(idx + 1) % ordered.size]
        }
        TodayState(
            loading = false,
            today = today,
            active = active,
            nextRoutine = next,
            routineCount = ordered.size,
            weekWorkouts = thisWeek.size,
            weekVolumeKg = thisWeek.sumOf { it.volumeKg },
            weekStreak = Streaks.weeksInRow(dates, today),
            last = finished.maxByOrNull { it.startedAt },
            heatmap = heat,
            stretchToday = stretchToday,
            strengthTodayId = finished
                .filter { it.type == WorkoutType.STRENGTH && Fmt.toLocalDate(it.startedAt) == today }
                .maxByOrNull { it.startedAt }?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun startEmpty(name: String, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startEmpty(name)) }
    }

    fun startRoutine(id: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startFromRoutine(id)) }
    }

    /** Запустить растяжку: перед — разминка, после — заминка под сегодняшнюю силовую. */
    fun startStretch(phase: StretchPhase, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = when (phase) {
                StretchPhase.BEFORE -> stretch.startTemplate(StretchTemplates.before)
                StretchPhase.AFTER -> state.value.strengthTodayId?.let { stretch.startAfterWorkout(it) }
                    ?: stretch.startTemplate(StretchTemplates.suggestAfter(emptyList()))
                StretchPhase.ANY -> stretch.startTemplate(StretchTemplates.all.first { it.phase == StretchPhase.ANY })
            }
            onResult(ok)
        }
    }

    fun markStretch(name: String, phase: StretchPhase, minutes: Int) {
        viewModelScope.launch { stretchRepo.markDone(name, phase, minutes) }
    }

    companion object {
        const val HEATMAP_WEEKS = 16
    }
}
