package com.iron.fitness.feature.daily.data

import androidx.room.withTransaction
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.domain.ChallengeStats
import com.iron.fitness.core.domain.Challenges
import com.iron.fitness.core.domain.GoalChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Челлендж со статистикой. */
data class ChallengeUi(
    val challenge: ChallengeEntity,
    val stats: ChallengeStats,
    /** epochDay → сумма за день. */
    val totals: Map<Long, Int>,
    val goals: List<GoalChange>,
) {
    val progress: Float get() = if (stats.todayGoal <= 0) 0f else (stats.todayTotal.toFloat() / stats.todayGoal).coerceIn(0f, 1f)
    val doneToday: Boolean get() = stats.todayGoal > 0 && stats.todayTotal >= stats.todayGoal
}

@Singleton
class ChallengeRepository @Inject constructor(
    private val db: IronDatabase,
    private val dao: ChallengeDao,
) {
    private fun build(c: ChallengeEntity, allGoals: List<ChallengeGoalEntity>, allTotals: List<DayTotal>): ChallengeUi {
        val goals = allGoals.filter { it.challengeId == c.id }.map { GoalChange(it.fromDay, it.goal) }
            .ifEmpty { listOf(GoalChange(Long.MIN_VALUE / 2, c.goal)) }
        val totals = allTotals.filter { it.challengeId == c.id }.associate { it.day to it.total }
        return ChallengeUi(c, Challenges.stats(totals, goals, LocalDate.now()), totals, goals)
    }

    fun observeActive(): Flow<List<ChallengeUi>> =
        combine(dao.observeActive(), dao.observeGoals(), dao.observeTotals()) { list, goals, totals ->
            list.map { build(it, goals, totals) }
        }

    fun observeAll(): Flow<List<ChallengeUi>> =
        combine(dao.observeAll(), dao.observeGoals(), dao.observeTotals()) { list, goals, totals ->
            list.map { build(it, goals, totals) }
        }

    fun observe(id: Long): Flow<ChallengeUi?> =
        combine(dao.observe(id), dao.observeGoals(), dao.observeTotals()) { c, goals, totals ->
            c?.let { build(it, goals, totals) }
        }

    fun observeLogs(id: Long, limit: Int = 50): Flow<List<ChallengeLogEntity>> = dao.observeLogs(id, limit)

    suspend fun get(id: Long): ChallengeEntity? = dao.get(id)
    suspend fun getActive(): List<ChallengeEntity> = dao.getActive()
    suspend fun totalToday(id: Long): Int = dao.totalOn(id, LocalDate.now().toEpochDay())

    /** Добавить к сегодняшнему прогрессу (отрицательное значение — исправить ошибку). */
    suspend fun add(id: Long, amount: Int) {
        if (amount == 0) return
        dao.insertLog(ChallengeLogEntity(challengeId = id, day = LocalDate.now().toEpochDay(), amount = amount))
    }

    suspend fun deleteLog(logId: Long) = dao.deleteLog(logId)

    suspend fun create(c: ChallengeEntity): Long = db.withTransaction {
        val id = dao.insert(c.copy(id = 0, createdAt = System.currentTimeMillis()))
        dao.insertGoal(ChallengeGoalEntity(challengeId = id, fromDay = LocalDate.now().toEpochDay(), goal = c.goal))
        id
    }

    /** Сохранить изменения. Новая цель действует с сегодняшнего дня, прошлые дни оцениваются по старой. */
    suspend fun update(c: ChallengeEntity) = db.withTransaction {
        val old = dao.get(c.id) ?: return@withTransaction
        dao.update(c)
        if (old.goal != c.goal) {
            val today = LocalDate.now().toEpochDay()
            dao.deleteGoalOn(c.id, today)
            dao.insertGoal(ChallengeGoalEntity(challengeId = c.id, fromDay = today, goal = c.goal))
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val c = dao.get(id) ?: return
        dao.update(c.copy(archived = archived))
    }

    suspend fun delete(id: Long) = db.withTransaction {
        dao.deleteLogs(id)
        dao.deleteGoals(id)
        dao.delete(id)
    }
}
