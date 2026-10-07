package com.iron.fitness.feature.reminders.data

import androidx.room.withTransaction
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.media.PhotoStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val db: IronDatabase,
    private val dao: ReminderDao,
    private val photos: PhotoStorage,
) {
    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<ReminderEntity?> = dao.observe(id)
    suspend fun get(id: Long): ReminderEntity? = dao.get(id)
    suspend fun getAll(): List<ReminderEntity> = dao.getAll()
    fun observeLogs(id: Long, limit: Int = 60): Flow<List<ReminderLogEntity>> = dao.observeLogs(id, limit)
    fun observeLogsSince(from: Long): Flow<List<ReminderLogEntity>> = dao.observeLogsSince(from)

    suspend fun save(r: ReminderEntity): Long = if (r.id == 0L) dao.insert(r) else {
        dao.update(r)
        r.id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val r = dao.get(id) ?: return
        dao.update(r.copy(enabled = enabled))
    }

    suspend fun delete(id: Long) {
        val r = dao.get(id)
        db.withTransaction {
            dao.deleteLogs(id)
            dao.delete(id)
        }
        photos.delete(r?.photoPath)
    }

    /** Запись о приёме (создаётся, если ещё нет). */
    suspend fun ensureLog(id: Long, at: Long): ReminderLogEntity {
        dao.getLog(id, at)?.let { return it }
        dao.insertLog(ReminderLogEntity(reminderId = id, scheduledAt = at))
        return dao.getLog(id, at) ?: ReminderLogEntity(reminderId = id, scheduledAt = at)
    }

    suspend fun setStatus(id: Long, at: Long, status: DoseStatus) {
        val log = ensureLog(id, at)
        dao.updateLog(log.copy(status = status, actedAt = if (status == DoseStatus.PENDING) null else System.currentTimeMillis()))
    }

    suspend fun setRepeats(id: Long, at: Long, repeats: Int) {
        val log = ensureLog(id, at)
        dao.updateLog(log.copy(repeats = repeats))
    }

    suspend fun getLog(id: Long, at: Long): ReminderLogEntity? = dao.getLog(id, at)

    /** Неотмеченные приёмы старше [before] — пропущены. */
    suspend fun markOldPendingMissed(before: Long) = dao.markOldPendingMissed(before)
}
