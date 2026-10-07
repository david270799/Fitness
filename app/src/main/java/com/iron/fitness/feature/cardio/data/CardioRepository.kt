package com.iron.fitness.feature.cardio.data

import com.iron.fitness.core.body.BodyWeightProvider
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.Calories
import com.iron.fitness.core.domain.IntervalBlock
import com.iron.fitness.core.domain.Intervals
import com.iron.fitness.feature.workouts.data.WorkoutDao
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.data.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Интервальная программа с разобранными блоками. */
data class IntervalProgram(
    val id: Long = 0,
    val name: String,
    val note: String? = null,
    val blocks: List<IntervalBlock>,
    val workMet: Double = WORK_MET_HARD,
) {
    val totalSeconds: Int get() = Intervals.totalSeconds(blocks)

    companion object {
        const val WORK_MET_MODERATE = 6.0
        const val WORK_MET_HARD = 8.0
        const val WORK_MET_MAX = 10.0
    }
}

/** Запись кардио для сохранения в журнал. */
data class CardioEntry(
    val id: Long = 0,
    val type: CardioType,
    val name: String,
    val startedAt: Long,
    val durationSec: Long,
    val distanceKm: Double?,
    val intensity: Intensity,
    val note: String?,
)

@Singleton
class CardioRepository @Inject constructor(
    private val dao: IntervalDao,
    private val workoutDao: WorkoutDao,
    private val json: Json,
    private val bodyWeight: BodyWeightProvider,
) {
    private val blocksSerializer = ListSerializer(IntervalBlock.serializer())

    fun observePrograms(): Flow<List<IntervalProgram>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun getProgram(id: Long): IntervalProgram? = dao.get(id)?.toModel()

    suspend fun saveProgram(p: IntervalProgram): Long {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString(blocksSerializer, p.blocks)
        return if (p.id == 0L) {
            dao.insert(IntervalProgramEntity(name = p.name, note = p.note, blocksJson = encoded, workMet = p.workMet, createdAt = now, updatedAt = now))
        } else {
            val old = dao.get(p.id)
            dao.update(
                IntervalProgramEntity(
                    id = p.id,
                    name = p.name,
                    note = p.note,
                    blocksJson = encoded,
                    workMet = p.workMet,
                    sortOrder = old?.sortOrder ?: 0,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            p.id
        }
    }

    suspend fun copyProgram(id: Long, suffix: String): Long? {
        val p = getProgram(id) ?: return null
        return saveProgram(p.copy(id = 0, name = p.name + suffix))
    }

    suspend fun deleteProgram(id: Long) = dao.delete(id)

    private fun IntervalProgramEntity.toModel() = IntervalProgram(
        id = id,
        name = name,
        note = note,
        blocks = runCatching { json.decodeFromString(blocksSerializer, blocksJson) }.getOrDefault(emptyList()),
        workMet = workMet,
    )

    // ---------------- Журнал ----------------

    suspend fun cardioKcal(type: CardioType, intensity: Intensity, durationSec: Long): Double =
        Calories.kcal(type.met(intensity), bodyWeight.currentKg(), durationSec)

    /** Сохранить или обновить запись кардио. */
    suspend fun saveCardio(entry: CardioEntry): Long {
        val kcal = cardioKcal(entry.type, entry.intensity, entry.durationSec)
        val existing = if (entry.id != 0L) workoutDao.getWorkout(entry.id) else null
        val base = existing ?: WorkoutEntity(type = WorkoutType.CARDIO, name = entry.name, startedAt = entry.startedAt)
        val updated = base.copy(
            type = WorkoutType.CARDIO,
            name = entry.name,
            startedAt = entry.startedAt,
            endedAt = entry.startedAt + entry.durationSec * 1000,
            durationSec = entry.durationSec,
            caloriesKcal = kcal,
            cardioType = entry.type.name,
            distanceKm = entry.distanceKm?.takeIf { it > 0 },
            intensity = entry.intensity.name,
            note = entry.note?.takeIf { it.isNotBlank() },
        )
        return if (existing == null) workoutDao.insertWorkout(updated) else {
            workoutDao.updateWorkout(updated)
            existing.id
        }
    }

    /** Сохранить выполненную интервальную тренировку. */
    suspend fun saveInterval(
        name: String,
        programId: Long?,
        startedAt: Long,
        spentMsByType: Map<BlockType, Long>,
        workMet: Double,
        note: String? = null,
    ): Long {
        val durationSec = spentMsByType.values.sum() / 1000
        val kcal = Intervals.kcal(spentMsByType.mapValues { it.value / 1000 }, workMet, bodyWeight.currentKg())
        return workoutDao.insertWorkout(
            WorkoutEntity(
                type = WorkoutType.INTERVAL,
                name = name,
                startedAt = startedAt,
                endedAt = startedAt + durationSec * 1000,
                durationSec = durationSec,
                caloriesKcal = kcal,
                programId = programId,
                note = note,
            ),
        )
    }

    suspend fun getWorkout(id: Long): WorkoutEntity? = workoutDao.getWorkout(id)

    fun encodeBlocks(blocks: List<IntervalBlock>): String = json.encodeToString(blocksSerializer, blocks)
}
