package com.iron.fitness.feature.stretching.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Свой комплекс растяжки (позиции хранятся в JSON). */
@Entity(tableName = "stretch_routines")
data class StretchRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** BEFORE / AFTER / ANY. */
    val phase: String,
    val itemsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface StretchDao {
    @Query("SELECT * FROM stretch_routines ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<StretchRoutineEntity>>

    @Query("SELECT * FROM stretch_routines WHERE id = :id")
    suspend fun get(id: Long): StretchRoutineEntity?

    @Insert
    suspend fun insert(item: StretchRoutineEntity): Long

    @Update
    suspend fun update(item: StretchRoutineEntity)

    @Query("DELETE FROM stretch_routines WHERE id = :id")
    suspend fun delete(id: Long)
}
