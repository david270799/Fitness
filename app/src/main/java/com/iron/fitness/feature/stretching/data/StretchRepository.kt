package com.iron.fitness.feature.stretching.data

import androidx.annotation.StringRes
import com.iron.fitness.R
import com.iron.fitness.core.body.BodyWeightProvider
import com.iron.fitness.core.domain.Calories
import com.iron.fitness.core.domain.StretchItem
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.domain.Stretching
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.workouts.data.WorkoutDao
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class StretchRoutine(
    val id: Long = 0,
    val name: String,
    val phase: StretchPhase,
    val items: List<StretchItem>,
) {
    val totalSeconds: Int get() = Stretching.totalSeconds(items)
}

/** Встроенный комплекс растяжки. */
data class StretchTemplate(
    val key: String,
    @StringRes val name: Int,
    val phase: StretchPhase,
    /** Основные группы мышц комплекса — для подбора после тренировки. */
    val muscles: Set<String>,
    val items: List<StretchItem>,
)

object StretchTemplates {
    private fun i(id: String, sec: Int = 30, both: Boolean = false) = StretchItem(id, sec, both)

    val all: List<StretchTemplate> = listOf(
        StretchTemplate(
            "before_strength", R.string.stpl_before, StretchPhase.BEFORE,
            emptySet(),
            listOf(
                i("Arm_Circles", 30), i("Shoulder_Circles", 30), i("Elbow_Circles", 20),
                i("Standing_Hip_Circles", 30), i("Knee_Circles", 20), i("Ankle_Circles", 20),
                i("Front_Leg_Raises", 20, true), i("Side_Leg_Raises", 20, true),
                i("Inchworm", 40), i("Worlds_Greatest_Stretch", 30, true),
            ),
        ),
        StretchTemplate(
            "after_upper", R.string.stpl_after_upper, StretchPhase.AFTER,
            setOf("chest", "shoulders", "triceps", "biceps", "lats", "middle back", "traps", "forearms", "neck"),
            listOf(
                i("Shoulder_Stretch", 30, true), i("Triceps_Stretch", 30, true), i("Standing_Biceps_Stretch", 30),
                i("One_Arm_Against_Wall", 30, true), i("Upper_Back_Stretch", 30), i("Kneeling_Forearm_Stretch", 30),
                i("Childs_Pose", 45), i("Side_Neck_Stretch", 20, true),
            ),
        ),
        StretchTemplate(
            "after_lower", R.string.stpl_after_lower, StretchPhase.AFTER,
            setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors", "lower back"),
            listOf(
                i("Standing_Hip_Flexors", 30, true), i("On_Your_Side_Quad_Stretch", 30, true),
                i("Seated_Floor_Hamstring_Stretch", 45), i("Ankle_On_The_Knee", 30, true),
                i("Side_Lying_Groin_Stretch", 30), i("Calf_Stretch_Hands_Against_Wall", 30, true), i("Childs_Pose", 45),
            ),
        ),
        StretchTemplate(
            "morning", R.string.stpl_morning, StretchPhase.ANY,
            emptySet(),
            listOf(
                i("Upward_Stretch", 30), i("Standing_Lateral_Stretch", 20, true), i("Cat_Stretch", 40),
                i("Childs_Pose", 40), i("Hug_Knees_To_Chest", 30), i("Knee_Across_The_Body", 30, true), i("Chin_To_Chest_Stretch", 20),
            ),
        ),
        StretchTemplate(
            "runner", R.string.stpl_runner, StretchPhase.AFTER,
            setOf("quadriceps", "hamstrings", "calves", "glutes"),
            listOf(
                i("Runners_Stretch", 30, true), i("Standing_Gastrocnemius_Calf_Stretch", 30, true),
                i("Standing_Soleus_And_Achilles_Stretch", 30, true), i("Kneeling_Hip_Flexor", 30, true),
                i("Hamstring_Stretch", 30, true), i("IT_Band_and_Glute_Stretch", 30, true),
            ),
        ),
    )

    /** Подобрать заминку под мышцы тренировки: максимум пересечений, иначе «низ тела». */
    fun suggestAfter(muscles: Collection<String>): StretchTemplate {
        val after = all.filter { it.phase == StretchPhase.AFTER && it.key != "runner" }
        return after.maxByOrNull { t -> muscles.count { it in t.muscles } } ?: after.first()
    }

    val before: StretchTemplate get() = all.first { it.key == "before_strength" }
}

@Singleton
class StretchRepository @Inject constructor(
    private val dao: StretchDao,
    private val workoutDao: WorkoutDao,
    private val exercises: ExerciseRepository,
    private val json: Json,
    private val bodyWeight: BodyWeightProvider,
) {
    private val serializer = ListSerializer(StretchItem.serializer())

    fun observeRoutines(): Flow<List<StretchRoutine>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun getRoutine(id: Long): StretchRoutine? = dao.get(id)?.toModel()

    suspend fun saveRoutine(r: StretchRoutine): Long {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString(serializer, r.items)
        return if (r.id == 0L) {
            dao.insert(StretchRoutineEntity(name = r.name, phase = r.phase.name, itemsJson = encoded, createdAt = now, updatedAt = now))
        } else {
            val old = dao.get(r.id)
            dao.update(StretchRoutineEntity(r.id, r.name, r.phase.name, encoded, old?.createdAt ?: now, now))
            r.id
        }
    }

    suspend fun copyRoutine(id: Long, suffix: String): Long? {
        val r = getRoutine(id) ?: return null
        return saveRoutine(r.copy(id = 0, name = r.name + suffix))
    }

    suspend fun deleteRoutine(id: Long) = dao.delete(id)

    private fun StretchRoutineEntity.toModel() = StretchRoutine(
        id = id,
        name = name,
        phase = runCatching { StretchPhase.valueOf(phase) }.getOrDefault(StretchPhase.ANY),
        items = runCatching { json.decodeFromString(serializer, itemsJson) }.getOrDefault(emptyList()),
    )

    /** Названия упражнений для подписей таймера. */
    suspend fun names(ids: Collection<String>): Map<String, String> =
        exercises.getAll(ids.distinct()).associate { it.id to it.name }

    /** С какой силовой тренировкой связать растяжку: «перед» — с идущей, «после» — с последней сегодня. */
    suspend fun linkTarget(phase: StretchPhase): Long? = when (phase) {
        StretchPhase.BEFORE -> workoutDao.getActiveStrength()?.id
        StretchPhase.AFTER -> lastStrengthToday()?.id
        StretchPhase.ANY -> null
    }

    private suspend fun lastStrengthToday(): WorkoutEntity? {
        val (from, to) = todayRange()
        return workoutDao.finishedBetween(from, to).filter { it.type == WorkoutType.STRENGTH }.maxByOrNull { it.startedAt }
    }

    private fun todayRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        return today.atStartOfDay(zone).toInstant().toEpochMilli() to today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Сохранить выполненную растяжку в журнал. */
    suspend fun saveStretch(
        name: String,
        phase: StretchPhase,
        startedAt: Long,
        durationSec: Long,
        linkedWorkoutId: Long?,
        programId: Long? = null,
    ): Long = workoutDao.insertWorkout(
        WorkoutEntity(
            type = WorkoutType.STRETCHING,
            name = name,
            startedAt = startedAt,
            endedAt = startedAt + durationSec * 1000,
            durationSec = durationSec,
            caloriesKcal = Calories.kcal(Stretching.MET, bodyWeight.currentKg(), durationSec),
            stretchPhase = phase.name,
            linkedWorkoutId = linkedWorkoutId,
            programId = programId,
        ),
    )

    /** «Растяжка выполнена» без таймера. */
    suspend fun markDone(name: String, phase: StretchPhase, minutes: Int): Long {
        val duration = minutes.coerceIn(1, 180) * 60L
        return saveStretch(
            name = name,
            phase = phase,
            startedAt = System.currentTimeMillis() - duration * 1000,
            durationSec = duration,
            linkedWorkoutId = linkTarget(phase),
        )
    }

    /** Растяжка за сегодня (для экрана «Сегодня»). */
    fun observeToday(): Flow<List<WorkoutEntity>> {
        val (from, _) = todayRange()
        return workoutDao.observeFinishedSince(from).map { list -> list.filter { it.type == WorkoutType.STRETCHING } }
    }

    /** Основные мышцы силовой тренировки — для подбора заминки. */
    suspend fun musclesOfWorkout(workoutId: Long): List<String> {
        val ids = workoutDao.exerciseIdsInWorkout(workoutId)
        return exercises.getAll(ids).flatMap { it.primaryMuscles }
    }
}
