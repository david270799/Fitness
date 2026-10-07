package com.iron.fitness.feature.cardio.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IntervalDao {
    @Query("SELECT * FROM interval_programs ORDER BY sortOrder, updatedAt DESC")
    fun observeAll(): Flow<List<IntervalProgramEntity>>

    @Query("SELECT * FROM interval_programs WHERE id = :id")
    suspend fun get(id: Long): IntervalProgramEntity?

    @Insert
    suspend fun insert(program: IntervalProgramEntity): Long

    @Update
    suspend fun update(program: IntervalProgramEntity)

    @Query("DELETE FROM interval_programs WHERE id = :id")
    suspend fun delete(id: Long)
}
