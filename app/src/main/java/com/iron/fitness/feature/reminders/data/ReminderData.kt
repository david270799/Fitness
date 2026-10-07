package com.iron.fitness.feature.reminders.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.iron.fitness.core.domain.DoseStatus
import kotlinx.coroutines.flow.Flow

/** Напоминание о приёме (витамины, лекарства, вода). */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String? = null,
    val note: String? = null,
    val photoPath: String? = null,
    /** Времена приёма, минуты от полуночи, через запятую: «480,1200». */
    val times: String,
    /** Дни недели битовой маской (бит 0 — понедельник). */
    val weekdays: Int = 127,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Времена приёма списком (минуты от полуночи, по возрастанию). */
val ReminderEntity.timeList: List<Int>
    get() = times.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()

/** Запись о приёме: запланированное время и что с ним стало. */
@Entity(
    tableName = "reminder_logs",
    indices = [Index(value = ["reminderId", "scheduledAt"], unique = true)],
)
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val scheduledAt: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val actedAt: Long? = null,
    /** Сколько раз уже напомнили повторно. */
    val repeats: Int = 0,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY enabled DESC, name")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Insert
    suspend fun insert(r: ReminderEntity): Long

    @Update
    suspend fun update(r: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(l: ReminderLogEntity): Long

    @Update
    suspend fun updateLog(l: ReminderLogEntity)

    @Query("SELECT * FROM reminder_logs WHERE reminderId = :id AND scheduledAt = :at")
    suspend fun getLog(id: Long, at: Long): ReminderLogEntity?

    @Query("SELECT * FROM reminder_logs WHERE reminderId = :id ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeLogs(id: Long, limit: Int): Flow<List<ReminderLogEntity>>

    @Query("SELECT * FROM reminder_logs WHERE scheduledAt >= :from ORDER BY scheduledAt")
    fun observeLogsSince(from: Long): Flow<List<ReminderLogEntity>>

    @Query("DELETE FROM reminder_logs WHERE reminderId = :id")
    suspend fun deleteLogs(id: Long)

    @Query("UPDATE reminder_logs SET status = 'MISSED' WHERE status = 'PENDING' AND scheduledAt < :before")
    suspend fun markOldPendingMissed(before: Long)
}
