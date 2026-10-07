package com.iron.fitness.feature.inbody

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Результат InBody после проверки пользователем. */
@Entity(tableName = "inbody_results", indices = [Index("day")])
data class InBodyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val photoPath: String? = null,
    val weightKg: Double? = null,
    val skeletalMuscleKg: Double? = null,
    val bodyFatKg: Double? = null,
    val bodyFatPct: Double? = null,
    val bmi: Double? = null,
    val visceralFatLevel: Double? = null,
    val bmrKcal: Double? = null,
    val totalBodyWaterL: Double? = null,
    val leanMassKg: Double? = null,
    val inbodyScore: Double? = null,
    val ecwRatio: Double? = null,
    val armLeftLeanKg: Double? = null,
    val armRightLeanKg: Double? = null,
    val trunkLeanKg: Double? = null,
    val legLeftLeanKg: Double? = null,
    val legRightLeanKg: Double? = null,
    /** Разбор ассистента (если запрашивали). */
    val analysis: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface InBodyDao {
    @Query("SELECT * FROM inbody_results ORDER BY day DESC, createdAt DESC")
    fun observeAll(): Flow<List<InBodyEntity>>

    @Query("SELECT * FROM inbody_results WHERE id = :id")
    fun observe(id: Long): Flow<InBodyEntity?>

    @Query("SELECT * FROM inbody_results WHERE id = :id")
    suspend fun get(id: Long): InBodyEntity?

    @Query("SELECT * FROM inbody_results ORDER BY day DESC, createdAt DESC")
    suspend fun getAll(): List<InBodyEntity>

    @Insert
    suspend fun insert(e: InBodyEntity): Long

    @Update
    suspend fun update(e: InBodyEntity)

    @Query("DELETE FROM inbody_results WHERE id = :id")
    suspend fun delete(id: Long)
}
