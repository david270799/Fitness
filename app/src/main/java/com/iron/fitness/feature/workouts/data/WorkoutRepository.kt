package com.iron.fitness.feature.workouts.data

import androidx.room.withTransaction
import com.iron.fitness.core.body.BodyWeightProvider
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.domain.Bests
import com.iron.fitness.core.domain.Calories
import com.iron.fitness.core.domain.Records
import com.iron.fitness.core.domain.SetPerformance
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Итоги завершённой тренировки. */
data class WorkoutSummary(
    val workoutId: Long,
    val name: String,
    val durationSec: Long,
    val volumeKg: Double,
    val setCount: Int,
    val exerciseCount: Int,
    val caloriesKcal: Double,
    val averageRestSec: Long?,
    val records: List<RecordItem>,
)

data class RecordItem(
    val exerciseName: String,
    val kind: RecordKind,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
)

enum class RecordKind { WEIGHT, REPS, ONE_RM, DURATION }

/** Результат отметки подхода. */
data class SetCompletion(
    val completed: Boolean,
    val isRecord: Boolean,
    /** Сколько отдыхать после подхода (null — не запускать таймер, например внутри суперсета). */
    val restSeconds: Int?,
    val exerciseName: String,
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: IronDatabase,
    private val dao: WorkoutDao,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val bodyWeight: BodyWeightProvider,
) {
    fun observeActiveStrength(): Flow<WorkoutEntity?> = dao.observeActiveStrength()
    fun observeFull(id: Long): Flow<WorkoutWithExercises?> = dao.observeFull(id)
    suspend fun getFull(id: Long): WorkoutWithExercises? = dao.getFull(id)
    fun observeHistory(): Flow<List<WorkoutSummaryRow>> = dao.observeHistory()
    fun observeRoutines(): Flow<List<RoutineWithExercises>> = dao.observeRoutines()
    fun observeRoutineLastUse(): Flow<List<RoutineLastUse>> = dao.observeRoutineLastUse()
    fun observeFinishedSince(from: Long): Flow<List<WorkoutEntity>> = dao.observeFinishedSince(from)
    suspend fun getActiveStrength(): WorkoutEntity? = dao.getActiveStrength()
    fun observeCompletedSetsSince(from: Long): Flow<List<SetWithDate>> = dao.observeCompletedSetsSince(from)
    suspend fun allFinished(): List<WorkoutEntity> = dao.allFinished()
    suspend fun allCompletedSets(): List<SetWithDate> = dao.allCompletedSets()
    suspend fun getRoutine(id: Long): RoutineWithExercises? = dao.getRoutine(id)
    fun observeCompletedSetsForExercise(exerciseId: String): Flow<List<SetWithDate>> =
        dao.observeCompletedSetsForExercise(exerciseId)

    // ---------------- Начало тренировки ----------------

    suspend fun startEmpty(name: String): Long {
        dao.getActiveStrength()?.let { return it.id }
        return dao.insertWorkout(WorkoutEntity(name = name, startedAt = System.currentTimeMillis()))
    }

    suspend fun startFromRoutine(routineId: Long): Long {
        dao.getActiveStrength()?.let { return it.id }
        val routine = dao.getRoutine(routineId) ?: error("Шаблон не найден")
        return db.withTransaction {
            val workoutId = dao.insertWorkout(
                WorkoutEntity(name = routine.routine.name, routineId = routineId, startedAt = System.currentTimeMillis()),
            )
            routine.exercises.sortedBy { it.position }.forEachIndexed { index, re ->
                val weId = dao.insertWorkoutExercise(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = re.exerciseId,
                        position = index,
                        supersetGroup = re.supersetGroup,
                        note = re.note,
                        restSeconds = re.restSeconds,
                        targetReps = re.targetReps,
                    ),
                )
                // Значения прошлого раза и цель прогрессии показываются подсказкой в пустых полях.
                val sets = (0 until re.sets.coerceAtLeast(1)).map { i ->
                    WorkoutSetEntity(
                        workoutExerciseId = weId,
                        workoutId = workoutId,
                        exerciseId = re.exerciseId,
                        position = i,
                        weightKg = re.targetWeightKg,
                        reps = null,
                        durationSec = re.targetSeconds,
                    )
                }
                dao.insertSets(sets)
            }
            workoutId
        }
    }

    suspend fun addExercises(workoutId: Long, exerciseIds: List<String>) {
        db.withTransaction {
            val existing = dao.getWorkoutExercises(workoutId)
            var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
            for (exId in exerciseIds) {
                val weId = dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exId, position = position++))
                val previous = dao.previousSets(exId, workoutId).filter { it.setType != SetType.WARMUP }
                val count = previous.size.coerceIn(1, 5).takeIf { previous.isNotEmpty() } ?: 3
                val sets = (0 until count).map { i ->
                    WorkoutSetEntity(
                        workoutExerciseId = weId,
                        workoutId = workoutId,
                        exerciseId = exId,
                        position = i,
                    )
                }
                dao.insertSets(sets)
            }
        }
    }

    // ---------------- Подходы ----------------

    suspend fun addSet(workoutExerciseId: Long) {
        val sets = dao.getSets(workoutExerciseId)
        val last = sets.lastOrNull()
        val we = dao.getWorkoutExercises(last?.workoutId ?: return addFirstSet(workoutExerciseId))
            .firstOrNull { it.id == workoutExerciseId } ?: return
        dao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                workoutId = we.workoutId,
                exerciseId = we.exerciseId,
                position = last.position + 1,
                setType = if (last.setType == SetType.WARMUP) SetType.NORMAL else last.setType,
                weightKg = last.weightKg,
                reps = last.reps,
                durationSec = last.durationSec,
            ),
        )
    }

    private suspend fun addFirstSet(workoutExerciseId: Long) {
        // У упражнения нет подходов: находим его через список упражнений тренировки.
        val all = db.workoutDao()
        val active = all.getActiveStrength()
        val candidates = buildList {
            if (active != null) addAll(all.getWorkoutExercises(active.id))
        }
        val we = candidates.firstOrNull { it.id == workoutExerciseId } ?: return
        dao.insertSet(WorkoutSetEntity(workoutExerciseId = we.id, workoutId = we.workoutId, exerciseId = we.exerciseId, position = 0))
    }

    suspend fun addSetTo(workoutId: Long, workoutExerciseId: Long) {
        val sets = dao.getSets(workoutExerciseId)
        if (sets.isNotEmpty()) {
            addSet(workoutExerciseId)
            return
        }
        val we = dao.getWorkoutExercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.insertSet(WorkoutSetEntity(workoutExerciseId = we.id, workoutId = workoutId, exerciseId = we.exerciseId, position = 0))
    }

    suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?) {
        val set = dao.getSet(setId) ?: return
        val updated = set.copy(weightKg = weightKg, reps = reps, durationSec = durationSec)
        if (updated != set) {
            dao.updateSet(updated)
            if (set.completed) refreshRecordsForWorkoutExercise(set.workoutId, set.exerciseId)
        }
    }

    suspend fun setSetType(setId: Long, type: SetType) {
        val set = dao.getSet(setId) ?: return
        dao.updateSet(set.copy(setType = type))
        if (set.completed) refreshRecordsForWorkoutExercise(set.workoutId, set.exerciseId)
    }

    suspend fun deleteSet(setId: Long) {
        val set = dao.getSet(setId) ?: return
        db.withTransaction {
            dao.deleteSet(set)
            val rest = dao.getSets(set.workoutExerciseId).mapIndexed { i, s -> s.copy(position = i) }
            dao.updateSets(rest)
        }
        if (set.completed) refreshRecordsForWorkoutExercise(set.workoutId, set.exerciseId)
    }

    /**
     * Отметка подхода «выполнено». Пустые поля берутся из подсказки «прошлый раз», если она есть.
     */
    suspend fun toggleComplete(setId: Long, fallbackWeight: Double?, fallbackReps: Int?, fallbackDuration: Int?): SetCompletion? {
        val set = dao.getSet(setId) ?: return null
        val exercise = exercises.get(set.exerciseId)
        val name = exercise?.name ?: ""
        if (set.completed) {
            dao.updateSet(set.copy(completed = false, completedAt = null, isWeightPr = false, isRepsPr = false, isOneRmPr = false, isDurationPr = false))
            refreshRecordsForWorkoutExercise(set.workoutId, set.exerciseId)
            return SetCompletion(completed = false, isRecord = false, restSeconds = null, exerciseName = name)
        }
        val filled = set.copy(
            weightKg = set.weightKg ?: fallbackWeight,
            reps = set.reps ?: fallbackReps,
            durationSec = set.durationSec ?: fallbackDuration,
            completed = true,
            completedAt = System.currentTimeMillis(),
        )
        dao.updateSet(filled)
        val flags = refreshRecordsForWorkoutExercise(set.workoutId, set.exerciseId)[setId]
        val rest = restAfter(set, exercise)
        return SetCompletion(completed = true, isRecord = flags == true, restSeconds = rest, exerciseName = name)
    }

    private suspend fun restAfter(set: WorkoutSetEntity, exercise: ExerciseEntity?): Int? {
        val list = dao.getWorkoutExercises(set.workoutId)
        val we = list.firstOrNull { it.id == set.workoutExerciseId } ?: return null
        val group = we.supersetGroup
        if (group != null) {
            val next = list.filter { it.position > we.position }.minByOrNull { it.position }
            if (next != null && next.supersetGroup == group) return null
        }
        return we.restSeconds ?: exercise?.restSeconds ?: settings.current().defaultRestSeconds
    }

    /**
     * Пересчитывает рекорды выполненных подходов упражнения в текущей тренировке относительно
     * истории (завершённые тренировки). Возвращает setId → «есть рекорд».
     */
    private suspend fun refreshRecordsForWorkoutExercise(workoutId: Long, exerciseId: String): Map<Long, Boolean> {
        val workout = dao.getWorkout(workoutId) ?: return emptyMap()
        if (workout.endedAt != null) {
            recomputeRecords(listOf(exerciseId))
            return emptyMap()
        }
        val weighted = isWeighted(exerciseId)
        val history = dao.completedSetsForExercise(exerciseId).filter { it.set.workoutId != workoutId }
        var bests: Bests = Records.bestsOf(history.map { it.set.toPerformance() }, weighted)
        val current = dao.getWorkoutExercises(workoutId)
            .filter { it.exerciseId == exerciseId }
            .flatMap { dao.getSets(it.id) }
            .filter { it.completed }
            .sortedBy { it.completedAt ?: 0L }
        val result = mutableMapOf<Long, Boolean>()
        val updates = mutableListOf<WorkoutSetEntity>()
        val hasHistory = history.isNotEmpty()
        for (s in current) {
            val (flags, next) = Records.evaluate(s.toPerformance(), bests, weighted)
            bests = next
            // Первая тренировка с упражнением не даёт «рекордов».
            val f = if (hasHistory) flags else com.iron.fitness.core.domain.RecordFlags()
            val updated = s.copy(isWeightPr = f.weight, isRepsPr = f.reps, isOneRmPr = f.oneRm, isDurationPr = f.duration)
            if (updated != s) updates += updated
            result[s.id] = f.any
        }
        if (updates.isNotEmpty()) dao.updateSets(updates)
        return result
    }

    /** Полный пересчёт отметок рекордов по истории (после завершения, правки или удаления тренировки). */
    suspend fun recomputeRecords(exerciseIds: Collection<String>) {
        for (exId in exerciseIds.distinct()) {
            val weighted = isWeighted(exId)
            val sets = dao.completedSetsForExercise(exId)
            if (sets.isEmpty()) continue
            val firstWorkout = sets.first().set.workoutId
            var bests = Bests()
            val updates = mutableListOf<WorkoutSetEntity>()
            for (row in sets) {
                val s = row.set
                val (flags, next) = Records.evaluate(s.toPerformance(), bests, weighted)
                bests = next
                val f = if (s.workoutId == firstWorkout) com.iron.fitness.core.domain.RecordFlags() else flags
                val updated = s.copy(isWeightPr = f.weight, isRepsPr = f.reps, isOneRmPr = f.oneRm, isDurationPr = f.duration)
                if (updated != s) updates += updated
            }
            if (updates.isNotEmpty()) dao.updateSets(updates)
        }
    }

    private suspend fun isWeighted(exerciseId: String): Boolean =
        exercises.get(exerciseId)?.recordType == RecordType.WEIGHT_REPS

    suspend fun getSet(id: Long): WorkoutSetEntity? = dao.getSet(id)

    suspend fun previousSets(exerciseId: String, workoutId: Long): List<WorkoutSetEntity> =
        dao.previousSets(exerciseId, workoutId)

    // ---------------- Упражнения в тренировке ----------------

    suspend fun removeExercise(workoutExerciseId: Long) {
        val workoutId = findWorkoutIdOfExercise(workoutExerciseId) ?: return
        db.withTransaction {
            dao.deleteWorkoutExercise(workoutExerciseId)
            normalizePositions(workoutId)
        }
    }

    suspend fun moveExercise(workoutId: Long, workoutExerciseId: Long, delta: Int) {
        db.withTransaction {
            val list = dao.getWorkoutExercises(workoutId).toMutableList()
            val index = list.indexOfFirst { it.id == workoutExerciseId }
            val target = index + delta
            if (index < 0 || target !in list.indices) return@withTransaction
            val item = list.removeAt(index)
            list.add(target, item)
            dao.updateWorkoutExercises(list.mapIndexed { i, e -> e.copy(position = i) })
        }
    }

    suspend fun reorderExercises(workoutId: Long, orderedIds: List<Long>) {
        val list = dao.getWorkoutExercises(workoutId).associateBy { it.id }
        dao.updateWorkoutExercises(orderedIds.mapIndexedNotNull { i, id -> list[id]?.copy(position = i) })
    }

    /** Объединить упражнение в суперсет со следующим (или разъединить). */
    suspend fun toggleSupersetWithNext(workoutId: Long, workoutExerciseId: Long) {
        db.withTransaction {
            val list = dao.getWorkoutExercises(workoutId)
            val index = list.indexOfFirst { it.id == workoutExerciseId }
            if (index < 0 || index == list.lastIndex) return@withTransaction
            val current = list[index]
            val next = list[index + 1]
            if (current.supersetGroup != null && current.supersetGroup == next.supersetGroup) {
                // Разрываем связь: всё после текущего — в новую группу или без группы.
                val tail = list.drop(index + 1).takeWhile { it.supersetGroup == current.supersetGroup }
                val newGroup = if (tail.size > 1) (list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1 else null
                val updates = mutableListOf<WorkoutExerciseEntity>()
                tail.forEach { updates += it.copy(supersetGroup = newGroup) }
                val head = list.take(index + 1).reversed().takeWhile { it.supersetGroup == current.supersetGroup }
                if (head.size == 1) updates += current.copy(supersetGroup = null)
                dao.updateWorkoutExercises(updates)
            } else {
                val group = current.supersetGroup ?: next.supersetGroup ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
                val nextGroup = next.supersetGroup
                val updates = mutableListOf(current.copy(supersetGroup = group), next.copy(supersetGroup = group))
                if (nextGroup != null && nextGroup != group) {
                    list.filter { it.supersetGroup == nextGroup && it.id != next.id }.forEach { updates += it.copy(supersetGroup = group) }
                }
                dao.updateWorkoutExercises(updates)
            }
        }
    }

    suspend fun updateExerciseNote(workoutExerciseId: Long, note: String?) {
        val workoutId = findWorkoutIdOfExercise(workoutExerciseId) ?: return
        val we = dao.getWorkoutExercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.updateWorkoutExercise(we.copy(note = note?.takeIf { it.isNotBlank() }))
    }

    suspend fun updateExerciseRest(workoutExerciseId: Long, restSeconds: Int?) {
        val workoutId = findWorkoutIdOfExercise(workoutExerciseId) ?: return
        val we = dao.getWorkoutExercises(workoutId).firstOrNull { it.id == workoutExerciseId } ?: return
        dao.updateWorkoutExercise(we.copy(restSeconds = restSeconds))
    }

    private suspend fun findWorkoutIdOfExercise(workoutExerciseId: Long): Long? =
        db.query("SELECT workoutId FROM workout_exercises WHERE id = ?", arrayOf(workoutExerciseId)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    private suspend fun normalizePositions(workoutId: Long) {
        val list = dao.getWorkoutExercises(workoutId)
        dao.updateWorkoutExercises(list.mapIndexed { i, e -> e.copy(position = i) })
    }

    suspend fun rename(workoutId: Long, name: String) {
        val w = dao.getWorkout(workoutId) ?: return
        dao.updateWorkout(w.copy(name = name.trim().ifBlank { w.name }))
    }

    suspend fun setWorkoutNote(workoutId: Long, note: String?) {
        val w = dao.getWorkout(workoutId) ?: return
        dao.updateWorkout(w.copy(note = note?.takeIf { it.isNotBlank() }))
    }

    // ---------------- Завершение ----------------

    suspend fun finish(workoutId: Long): WorkoutSummary? {
        val workout = dao.getWorkout(workoutId) ?: return null
        val endedAt = System.currentTimeMillis()
        db.withTransaction {
            dao.deleteIncompleteSets(workoutId)
            dao.deleteEmptyExercises(workoutId)
            normalizePositions(workoutId)
            dao.updateWorkout(workout.copy(endedAt = endedAt))
        }
        val ids = dao.exerciseIdsInWorkout(workoutId)
        recomputeRecords(ids)
        return recalculateTotals(workoutId)
    }

    /** Пересчитать длительность, объём и калории (после завершения или правки). */
    suspend fun recalculateTotals(workoutId: Long): WorkoutSummary? {
        val full = dao.getFull(workoutId) ?: return null
        val w = full.workout
        val end = w.endedAt ?: System.currentTimeMillis()
        val duration = ((end - w.startedAt) / 1000).coerceAtLeast(0)
        val completed = full.exercises.flatMap { it.sets }.filter { it.completed }
        val working = completed.filter { it.setType != SetType.WARMUP }
        val volume = working.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        val met = Calories.strengthMet(working.size, duration)
        val kcal = Calories.kcal(met, bodyWeight.currentKg(), duration)
        if (w.endedAt != null) {
            dao.updateWorkout(w.copy(durationSec = duration, volumeKg = volume, caloriesKcal = kcal))
        }
        return buildSummary(full.copy(workout = w.copy(durationSec = duration, volumeKg = volume, caloriesKcal = kcal)))
    }

    suspend fun summary(workoutId: Long): WorkoutSummary? = dao.getFull(workoutId)?.let { buildSummary(it) }

    private suspend fun buildSummary(full: WorkoutWithExercises): WorkoutSummary {
        val w = full.workout
        val names = exercises.getAll(full.exercises.map { it.exercise.exerciseId }.distinct()).associateBy({ it.id }, { it.name })
        val completed = full.exercises.flatMap { it.sets }.filter { it.completed }
        val rests = completed.mapNotNull { it.completedAt }.sorted().zipWithNext { a, b -> (b - a) / 1000 }
            .filter { it in 1..1800 }
        val records = mutableListOf<RecordItem>()
        for (we in full.exercises) {
            val name = names[we.exercise.exerciseId] ?: continue
            for (s in we.sets.filter { it.completed }) {
                if (s.isWeightPr) records += RecordItem(name, RecordKind.WEIGHT, s.weightKg, s.reps, null)
                if (s.isOneRmPr) records += RecordItem(name, RecordKind.ONE_RM, s.weightKg, s.reps, null)
                if (s.isRepsPr) records += RecordItem(name, RecordKind.REPS, s.weightKg, s.reps, null)
                if (s.isDurationPr) records += RecordItem(name, RecordKind.DURATION, null, null, s.durationSec)
            }
        }
        return WorkoutSummary(
            workoutId = w.id,
            name = w.name,
            durationSec = w.durationSec,
            volumeKg = w.volumeKg,
            setCount = completed.size,
            exerciseCount = full.exercises.count { e -> e.sets.any { it.completed } },
            caloriesKcal = w.caloriesKcal ?: 0.0,
            averageRestSec = if (rests.isEmpty()) null else rests.average().toLong(),
            records = records,
        )
    }

    suspend fun discard(workoutId: Long) = dao.deleteWorkout(workoutId)

    suspend fun delete(workoutId: Long) {
        val ids = dao.exerciseIdsInWorkout(workoutId)
        dao.deleteWorkout(workoutId)
        recomputeRecords(ids)
    }

    /** Сохранить правку завершённой тренировки. */
    suspend fun saveEdited(workoutId: Long, startedAt: Long? = null, durationSec: Long? = null) {
        val w = dao.getWorkout(workoutId) ?: return
        val start = startedAt ?: w.startedAt
        val oldDuration = ((w.endedAt ?: start) - w.startedAt).coerceAtLeast(0)
        val end = start + (durationSec?.times(1000) ?: oldDuration)
        db.withTransaction {
            dao.updateWorkout(w.copy(startedAt = start, endedAt = end))
            dao.deleteIncompleteSets(workoutId)
            dao.deleteEmptyExercises(workoutId)
            normalizePositions(workoutId)
        }
        recomputeRecords(dao.exerciseIdsInWorkout(workoutId))
        recalculateTotals(workoutId)
    }

    /** Подход для записи готовой тренировки (например, со слов ассистента). */
    data class LoggedSet(val weightKg: Double?, val reps: Int?, val durationSec: Int?)

    /** Сохранить уже выполненную тренировку целиком (после подтверждения пользователем). */
    suspend fun saveLogged(
        name: String,
        startedAt: Long,
        durationSec: Long,
        exercises: List<Pair<String, List<LoggedSet>>>,
    ): Long {
        val workoutId = db.withTransaction {
            val id = dao.insertWorkout(
                WorkoutEntity(name = name, startedAt = startedAt, endedAt = startedAt + durationSec * 1000, durationSec = durationSec),
            )
            exercises.forEachIndexed { index, (exerciseId, sets) ->
                val weId = dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutId = id, exerciseId = exerciseId, position = index))
                dao.insertSets(
                    sets.mapIndexed { i, s ->
                        WorkoutSetEntity(
                            workoutExerciseId = weId,
                            workoutId = id,
                            exerciseId = exerciseId,
                            position = i,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            durationSec = s.durationSec,
                            completed = true,
                            completedAt = startedAt + (i + 1) * 60_000L,
                        )
                    },
                )
            }
            id
        }
        recomputeRecords(exercises.map { it.first })
        recalculateTotals(workoutId)
        return workoutId
    }

    // ---------------- Шаблоны ----------------

    suspend fun saveRoutine(routine: RoutineEntity, items: List<RoutineExerciseEntity>): Long = db.withTransaction {
        val id = if (routine.id == 0L) {
            dao.insertRoutine(routine.copy(createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        } else {
            dao.updateRoutine(routine.copy(updatedAt = System.currentTimeMillis()))
            routine.id
        }
        dao.deleteRoutineExercises(id)
        dao.insertRoutineExercises(items.mapIndexed { i, e -> e.copy(id = 0, routineId = id, position = i) })
        id
    }

    suspend fun copyRoutine(id: Long, copySuffix: String): Long? {
        val r = dao.getRoutine(id) ?: return null
        return saveRoutine(r.routine.copy(id = 0, name = r.routine.name + copySuffix), r.exercises.sortedBy { it.position })
    }

    suspend fun deleteRoutine(id: Long) = dao.deleteRoutine(id)

    /** Сохранить завершённую тренировку как шаблон. */
    suspend fun saveWorkoutAsRoutine(workoutId: Long): Long? {
        val full = dao.getFull(workoutId) ?: return null
        val items = full.exercises.sortedBy { it.exercise.position }.map { we ->
            val working = we.sets.filter { it.setType != SetType.WARMUP }
            RoutineExerciseEntity(
                routineId = 0,
                exerciseId = we.exercise.exerciseId,
                position = we.exercise.position,
                sets = working.size.coerceAtLeast(1),
                targetReps = working.mapNotNull { it.reps }.takeIf { it.isNotEmpty() }?.let { reps ->
                    if (reps.min() == reps.max()) "${reps.min()}" else "${reps.min()}-${reps.max()}"
                },
                targetWeightKg = working.mapNotNull { it.weightKg }.maxOrNull(),
                restSeconds = we.exercise.restSeconds,
                supersetGroup = we.exercise.supersetGroup,
            )
        }
        return saveRoutine(RoutineEntity(name = full.workout.name), items)
    }
}

fun WorkoutSetEntity.toPerformance() = SetPerformance(weightKg, reps, durationSec, setType)
