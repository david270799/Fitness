package com.iron.fitness.feature.daily.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Единица челленджа: повторы или секунды. */
enum class ChallengeUnit { REPS, SECONDS }

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: ChallengeUnit = ChallengeUnit.REPS,
    /** Текущая дневная цель (история — в challenge_goals). */
    val goal: Int,
    val step1: Int = 5,
    val step2: Int = 10,
    val exerciseId: String? = null,
    val reminderEnabled: Boolean = false,
    /** Минуты от полуночи. */
    val reminderMinutes: Int = 19 * 60,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** История целей: с даты [fromDay] (epochDay) цель — [goal]. */
@Entity(tableName = "challenge_goals", indices = [Index("challengeId")])
data class ChallengeGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: Long,
    val fromDay: Long,
    val goal: Int,
)

/** Одна запись прогресса (нажатие «+10», таймер и т. п.). */
@Entity(tableName = "challenge_logs", indices = [Index(value = ["challengeId", "day"])])
data class ChallengeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: Long,
    /** epochDay. */
    val day: Long,
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Сумма за день. */
data class DayTotal(val challengeId: Long, val day: Long, val total: Int)

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE archived = 0 ORDER BY sortOrder, createdAt")
    fun observeActive(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges ORDER BY archived, sortOrder, createdAt")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    fun observe(id: Long): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun get(id: Long): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE archived = 0")
    suspend fun getActive(): List<ChallengeEntity>

    @Insert
    suspend fun insert(c: ChallengeEntity): Long

    @Update
    suspend fun update(c: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM challenge_goals WHERE challengeId = :id ORDER BY fromDay")
    suspend fun goals(id: Long): List<ChallengeGoalEntity>

    @Query("SELECT * FROM challenge_goals ORDER BY fromDay")
    fun observeGoals(): Flow<List<ChallengeGoalEntity>>

    @Insert
    suspend fun insertGoal(g: ChallengeGoalEntity): Long

    @Query("DELETE FROM challenge_goals WHERE challengeId = :id AND fromDay = :day")
    suspend fun deleteGoalOn(id: Long, day: Long)

    @Query("DELETE FROM challenge_goals WHERE challengeId = :id")
    suspend fun deleteGoals(id: Long)

    @Insert
    suspend fun insertLog(l: ChallengeLogEntity): Long

    @Query("DELETE FROM challenge_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM challenge_logs WHERE challengeId = :id")
    suspend fun deleteLogs(id: Long)

    @Query("SELECT challengeId, day, SUM(amount) AS total FROM challenge_logs GROUP BY challengeId, day")
    fun observeTotals(): Flow<List<DayTotal>>

    @Query("SELECT challengeId, day, SUM(amount) AS total FROM challenge_logs WHERE challengeId = :id GROUP BY day")
    suspend fun totals(id: Long): List<DayTotal>

    @Query("SELECT * FROM challenge_logs WHERE challengeId = :id ORDER BY createdAt DESC LIMIT :limit")
    fun observeLogs(id: Long, limit: Int): Flow<List<ChallengeLogEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM challenge_logs WHERE challengeId = :id AND day = :day")
    suspend fun totalOn(id: Long, day: Long): Int
}
