package com.iron.fitness.feature.workouts.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.workouts.data.RoutineEntity
import com.iron.fitness.feature.workouts.data.RoutineExerciseEntity
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Упражнение в редакторе шаблона. [key] — локальный ключ для списка и перетаскивания. */
data class RoutineItem(
    val key: Long,
    val exerciseId: String,
    val name: String,
    val recordType: RecordType,
    val sets: Int = 3,
    val targetReps: String = "",
    val targetWeight: String = "",
    val targetSeconds: String = "",
    val rest: String = "",
    val supersetGroup: Int? = null,
    val note: String? = null,
)

data class RoutineEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val note: String = "",
    val items: List<RoutineItem> = emptyList(),
    val error: RoutineError? = null,
)

enum class RoutineError { NAME, EXERCISES }

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val exercises: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var existing: RoutineEntity? = null
    private var nextKey = 1L

    private val _state = MutableStateFlow(RoutineEditorState(isNew = routineId == null))
    val state: StateFlow<RoutineEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = routineId?.let { repo.getRoutine(it) }
            if (r == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            existing = r.routine
            val sorted = r.exercises.sortedBy { it.position }
            val names = exercises.getAll(sorted.map { it.exerciseId }).associateBy { it.id }
            _state.value = RoutineEditorState(
                loading = false,
                isNew = false,
                name = r.routine.name,
                note = r.routine.note.orEmpty(),
                items = sorted.map { e ->
                    val ex = names[e.exerciseId]
                    RoutineItem(
                        key = nextKey++,
                        exerciseId = e.exerciseId,
                        name = ex?.name ?: e.exerciseId,
                        recordType = ex?.recordType ?: RecordType.WEIGHT_REPS,
                        sets = e.sets,
                        targetReps = e.targetReps.orEmpty(),
                        targetWeight = e.targetWeightKg?.let { com.iron.fitness.core.util.Fmt.num(it) }.orEmpty(),
                        targetSeconds = e.targetSeconds?.toString().orEmpty(),
                        rest = e.restSeconds?.toString().orEmpty(),
                        supersetGroup = e.supersetGroup,
                        note = e.note,
                    )
                },
            )
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }

    fun addExercises(ids: List<String>) {
        viewModelScope.launch {
            val found = exercises.getAll(ids).associateBy { it.id }
            val added = ids.mapNotNull { id ->
                val e = found[id] ?: return@mapNotNull null
                RoutineItem(
                    key = nextKey++,
                    exerciseId = id,
                    name = e.name,
                    recordType = e.recordType,
                    targetReps = if (e.recordType == RecordType.TIME) "" else "8-12",
                )
            }
            _state.update { it.copy(items = it.items + added, error = null) }
        }
    }

    fun update(key: Long, transform: (RoutineItem) -> RoutineItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key == key) transform(it) else it }) }

    fun remove(key: Long) = _state.update { s -> s.copy(items = normalizeGroups(s.items.filterNot { it.key == key })) }

    fun move(fromKey: Long, toKey: Long) = _state.update { s ->
        val list = s.items.toMutableList()
        val from = list.indexOfFirst { it.key == fromKey }
        val to = list.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0) return@update s
        list.add(to, list.removeAt(from))
        s.copy(items = list)
    }

    /** Связать с следующим в суперсет или разорвать связь. */
    fun toggleLink(key: Long) = _state.update { s ->
        val list = s.items.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0 || i == list.lastIndex) return@update s
        val cur = list[i]
        val next = list[i + 1]
        val linked = cur.supersetGroup != null && cur.supersetGroup == next.supersetGroup
        if (linked) {
            // Всё, что ниже разрыва, получает новую группу.
            val newGroup = (list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
            var j = i + 1
            while (j < list.size && list[j].supersetGroup == cur.supersetGroup) {
                list[j] = list[j].copy(supersetGroup = newGroup)
                j++
            }
        } else {
            val group = cur.supersetGroup ?: next.supersetGroup ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
            val oldNext = next.supersetGroup
            list[i] = cur.copy(supersetGroup = group)
            for (k in list.indices) {
                if (k == i + 1 || (oldNext != null && list[k].supersetGroup == oldNext)) list[k] = list[k].copy(supersetGroup = group)
            }
        }
        s.copy(items = normalizeGroups(list))
    }

    /** Группа из одного упражнения — не суперсет. */
    private fun normalizeGroups(list: List<RoutineItem>): List<RoutineItem> {
        val result = list.toMutableList()
        var i = 0
        while (i < result.size) {
            val g = result[i].supersetGroup
            var j = i
            while (g != null && j + 1 < result.size && result[j + 1].supersetGroup == g) j++
            if (g != null && j == i) result[i] = result[i].copy(supersetGroup = null)
            i = j + 1
        }
        return result
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        when {
            s.name.isBlank() -> { _state.update { it.copy(error = RoutineError.NAME) }; return }
            s.items.isEmpty() -> { _state.update { it.copy(error = RoutineError.EXERCISES) }; return }
        }
        viewModelScope.launch {
            val base = existing ?: RoutineEntity(name = s.name.trim())
            repo.saveRoutine(
                base.copy(name = s.name.trim(), note = s.note.trim().ifBlank { null }),
                s.items.mapIndexed { i, it ->
                    RoutineExerciseEntity(
                        routineId = base.id,
                        exerciseId = it.exerciseId,
                        position = i,
                        sets = it.sets.coerceIn(1, 20),
                        targetReps = it.targetReps.trim().ifBlank { null },
                        targetWeightKg = com.iron.fitness.core.util.Fmt.parse(it.targetWeight)?.takeIf { w -> w > 0 },
                        targetSeconds = it.targetSeconds.trim().toIntOrNull()?.takeIf { v -> v > 0 },
                        restSeconds = it.rest.trim().toIntOrNull()?.coerceIn(0, 900),
                        supersetGroup = it.supersetGroup,
                        note = it.note,
                    )
                },
            )
            onSaved()
        }
    }
}
