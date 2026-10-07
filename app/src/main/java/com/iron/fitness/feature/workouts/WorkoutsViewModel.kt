package com.iron.fitness.feature.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.workouts.data.RoutineEntity
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSummaryRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineCard(
    val routine: RoutineEntity,
    val exerciseNames: List<String>,
    val setCount: Int,
    val lastAt: Long?,
)

data class WorkoutsState(
    val loading: Boolean = true,
    val active: WorkoutEntity? = null,
    val routines: List<RoutineCard> = emptyList(),
    val recent: List<WorkoutSummaryRow> = emptyList(),
)

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val names = exercises.observeAll().map { list -> list.associate { it.id to it.name } }

    val state: StateFlow<WorkoutsState> = combine(
        repo.observeActiveStrength(),
        repo.observeRoutines(),
        repo.observeRoutineLastUse(),
        repo.observeHistory(),
        names,
    ) { active, routines, lastUse, history, nameMap ->
        val last = lastUse.associate { it.routineId to it.lastAt }
        WorkoutsState(
            loading = false,
            active = active,
            routines = routines.map { r ->
                val items = r.exercises.sortedBy { it.position }
                RoutineCard(
                    routine = r.routine,
                    exerciseNames = items.mapNotNull { nameMap[it.exerciseId] },
                    setCount = items.sumOf { it.sets },
                    lastAt = last[r.routine.id],
                )
            },
            recent = history.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutsState())

    fun startEmpty(name: String, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startEmpty(name)) }
    }

    fun startRoutine(id: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startFromRoutine(id)) }
    }

    fun copyRoutine(id: Long, suffix: String) {
        viewModelScope.launch { repo.copyRoutine(id, suffix) }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { repo.deleteRoutine(id) }
    }
}
