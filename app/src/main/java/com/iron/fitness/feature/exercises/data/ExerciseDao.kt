package com.iron.fitness.feature.exercises.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT * FROM exercises")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE isCustom = 0")
    suspend fun getLibrary(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    suspend fun libraryCount(): Int

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    fun observeLibraryCount(): Flow<Int>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercises SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE exercises SET recordType = :recordType, restSeconds = :restSeconds, note = :note WHERE id = :id")
    suspend fun updateUserSettings(id: String, recordType: RecordType, restSeconds: Int?, note: String?)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)
}
