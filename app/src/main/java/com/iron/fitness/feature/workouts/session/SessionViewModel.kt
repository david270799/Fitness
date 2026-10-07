package com.iron.fitness.feature.workouts.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.domain.Progression
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.timer.RestState
import com.iron.fitness.feature.timer.RestTimer
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutExerciseEntity
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSetEntity
import com.iron.fitness.feature.workouts.data.WorkoutWithExercises
import com.iron.fitness.feature.workouts.data.toPerformance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Подход в таблице вместе с подсказками. */
data class SetUi(
    val set: WorkoutSetEntity,
    /** Номер среди рабочих подходов (1, 2, 3…), null — разминка. */
    val number: Int?,
    val previous: WorkoutSetEntity?,
    val placeholderWeight: Double?,
    val placeholderReps: Int?,
    val placeholderSeconds: Int?,
)

enum class SupersetPos { NONE, FIRST, MIDDLE, LAST }

data class ExerciseUi(
    val we: WorkoutExerciseEntity,
    val exercise: ExerciseEntity?,
    val recordType: RecordType,
    val sets: List<SetUi>,
    val suggestion: Progression.Suggestion?,
    val supersetPos: SupersetPos,
    val supersetLetter: Char?,
    val isFirst: Boolean,
    val isLast: Boolean,
    /** Отдых после подхода с учётом настроек упражнения и общих. */
    val restSeconds: Int,
)

data class SessionState(
    val loading: Boolean = true,
    val workout: WorkoutEntity? = null,
    val exercises: List<ExerciseUi> = emptyList(),
    val completedCount: Int = 0,
    val incompleteCount: Int = 0,
    val settings: AppSettings = AppSettings(),
)

sealed interface SessionEvent {
    data class Record(val exerciseName: String) : SessionEvent
    data object MissingValues : SessionEvent
    data object Saved : SessionEvent
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val restTimer: RestTimer,
    settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workoutId: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    val editMode: Boolean = savedStateHandle.get<Boolean>("edit") ?: false

    /** Подходы прошлой тренировки по каждому упражнению. */
    private val previousCache = MutableStateFlow<Map<String, List<WorkoutSetEntity>>>(emptyMap())
    private val exerciseCache = MutableStateFlow<Map<String, ExerciseEntity>>(emptyMap())
    private val loadingIds = mutableSetOf<String>()

    /** Все изменения подходов идут строго по очереди, чтобы порядок ввода сохранялся. */
    private val writeLock = Mutex()

    private val events = Channel<SessionEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SessionEvent> = events.receiveAsFlow()

    val rest: StateFlow<RestState?> = restTimer.state

    private val full = repo.observeFull(workoutId)

    val state: StateFlow<SessionState> = combine(
        full,
        previousCache,
        exerciseCache,
        settingsRepo.settings,
    ) { f, prev, ex, settings ->
        if (f != null) ensureLoaded(f)
        build(f, prev, ex, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState())

    private fun ensureLoaded(f: WorkoutWithExercises) {
        val ids = f.exercises.map { it.exercise.exerciseId }.distinct()
        val missing = ids.filter { it !in loadingIds }
        if (missing.isEmpty()) return
        loadingIds += missing
        viewModelScope.launch {
            val entities = exerciseRepo.getAll(missing).associateBy { it.id }
            exerciseCache.update { it + entities }
            val prev = missing.associateWith { repo.previousSets(it, workoutId) }
            previousCache.update { it + prev }
        }
    }

    private fun build(
        f: WorkoutWithExercises?,
        prevMap: Map<String, List<WorkoutSetEntity>>,
        exMap: Map<String, ExerciseEntity>,
        settings: AppSettings,
    ): SessionState {
        if (f == null) return SessionState(loading = false, settings = settings)
        val ordered = f.exercises.sortedBy { it.exercise.position }
        // Суперсеты: подряд идущие упражнения с одной группой (минимум два).
        val positions = MutableList(ordered.size) { SupersetPos.NONE }
        val letters = MutableList<Char?>(ordered.size) { null }
        var letter = 'A'
        var i = 0
        while (i < ordered.size) {
            val g = ordered[i].exercise.supersetGroup
            var j = i
            while (g != null && j + 1 < ordered.size && ordered[j + 1].exercise.supersetGroup == g) j++
            if (j > i) {
                for (k in i..j) {
                    positions[k] = when (k) {
                        i -> SupersetPos.FIRST
                        j -> SupersetPos.LAST
                        else -> SupersetPos.MIDDLE
                    }
                    letters[k] = letter
                }
                letter++
            }
            i = j + 1
        }

        var completed = 0
        var incomplete = 0
        val items = ordered.mapIndexed { index, item ->
            val we = item.exercise
            val exercise = exMap[we.exerciseId]
            val recordType = exercise?.recordType ?: RecordType.WEIGHT_REPS
            val prev = prevMap[we.exerciseId].orEmpty()
            val prevWarm = prev.filter { it.setType == SetType.WARMUP }
            val prevWork = prev.filter { it.setType != SetType.WARMUP }
            val suggestion = if (recordType == RecordType.TIME || prevWork.isEmpty()) {
                null
            } else {
                Progression.suggest(
                    prevWork.map { it.toPerformance() },
                    Progression.parseRange(we.targetReps),
                    Progression.incrementFor(exercise?.equipment),
                ).takeIf { it.reason != Progression.Reason.NO_HISTORY }
            }
            var warmIdx = 0
            var workIdx = 0
            val sets = item.sets.sortedBy { it.position }.map { s ->
                if (s.completed) completed++ else incomplete++
                val isWarm = s.setType == SetType.WARMUP
                val previous = if (isWarm) prevWarm.getOrNull(warmIdx++) else prevWork.getOrNull(workIdx++)
                val number = if (isWarm) null else workIdx
                val target = if (isWarm) null else suggestion
                SetUi(
                    set = s,
                    number = number,
                    previous = previous,
                    placeholderWeight = target?.weightKg ?: previous?.weightKg,
                    placeholderReps = target?.reps ?: previous?.reps,
                    placeholderSeconds = previous?.durationSec,
                )
            }
            ExerciseUi(
                we = we,
                exercise = exercise,
                recordType = recordType,
                sets = sets,
                suggestion = suggestion,
                supersetPos = positions[index],
                supersetLetter = letters[index],
                isFirst = index == 0,
                isLast = index == ordered.lastIndex,
                restSeconds = we.restSeconds ?: exercise?.restSeconds ?: settings.defaultRestSeconds,
            )
        }
        return SessionState(
            loading = false,
            workout = f.workout,
            exercises = items,
            completedCount = completed,
            incompleteCount = incomplete,
            settings = settings,
        )
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writeLock.withLock { block() } }
    }

    // ---------- Подходы ----------

    fun updateValues(setId: Long, weight: Double?, reps: Int?, seconds: Int?) = write {
        repo.updateSetValues(setId, weight, reps, seconds)
    }

    fun toggleSet(ui: SetUi, ex: ExerciseUi) = write {
        // Берём свежие значения: только что введённые цифры уже записаны (запись идёт по очереди).
        val s = repo.getSet(ui.set.id) ?: return@write
        if (!s.completed) {
            val reps = s.reps ?: ui.placeholderReps
            val secs = s.durationSec ?: ui.placeholderSeconds
            val valid = when (ex.recordType) {
                RecordType.WEIGHT_REPS, RecordType.REPS -> (reps ?: 0) > 0
                RecordType.TIME -> (secs ?: 0) > 0
            }
            if (!valid) {
                events.send(SessionEvent.MissingValues)
                return@write
            }
        }
        val result = repo.toggleComplete(
            s.id,
            fallbackWeight = if (ex.recordType == RecordType.WEIGHT_REPS) ui.placeholderWeight else null,
            fallbackReps = if (ex.recordType == RecordType.TIME) null else ui.placeholderReps,
            fallbackDuration = if (ex.recordType == RecordType.TIME) ui.placeholderSeconds else null,
        ) ?: return@write
        if (result.completed) {
            if (result.isRecord) events.send(SessionEvent.Record(result.exerciseName))
            val restSec = result.restSeconds
            if (!editMode && restSec != null && state.value.settings.autoStartRestTimer) {
                restTimer.start(restSec, result.exerciseName)
            }
        }
    }

    fun setType(setId: Long, type: SetType) = write { repo.setSetType(setId, type) }
    fun deleteSet(setId: Long) = write { repo.deleteSet(setId) }
    fun addSet(workoutExerciseId: Long) = write { repo.addSetTo(workoutId, workoutExerciseId) }

    // ---------- Упражнения ----------

    fun addExercises(ids: List<String>) = write { repo.addExercises(workoutId, ids) }
    fun removeExercise(workoutExerciseId: Long) = write { repo.removeExercise(workoutExerciseId) }
    fun move(workoutExerciseId: Long, delta: Int) = write { repo.moveExercise(workoutId, workoutExerciseId, delta) }
    fun toggleSuperset(workoutExerciseId: Long) = write { repo.toggleSupersetWithNext(workoutId, workoutExerciseId) }
    fun setExerciseNote(workoutExerciseId: Long, note: String) = write { repo.updateExerciseNote(workoutExerciseId, note) }
    fun setExerciseRest(workoutExerciseId: Long, seconds: Int?) = write { repo.updateExerciseRest(workoutExerciseId, seconds) }

    // ---------- Тренировка ----------

    fun rename(name: String) = write { repo.rename(workoutId, name) }
    fun setNote(note: String) = write { repo.setWorkoutNote(workoutId, note) }

    fun startRest(seconds: Int) = restTimer.start(seconds)
    fun addRest(seconds: Int) = restTimer.add(seconds)
    fun skipRest() = restTimer.stop()

    /** Завершить: возвращает id для экрана итогов или null, если сохранять нечего. */
    fun finish(onDone: (Long?) -> Unit) = write {
        restTimer.stop()
        if (state.value.completedCount == 0) {
            repo.discard(workoutId)
            onDone(null)
        } else {
            repo.finish(workoutId)
            onDone(workoutId)
        }
    }

    fun discard(onDone: () -> Unit) = write {
        restTimer.stop()
        repo.discard(workoutId)
        onDone()
    }

    /** Сохранить правку завершённой тренировки. */
    fun saveEdit(startedAt: Long?, durationMinutes: Int?, onDone: () -> Unit) = write {
        repo.saveEdited(workoutId, startedAt, durationMinutes?.let { it * 60L })
        events.send(SessionEvent.Saved)
        onDone()
    }
}
