package com.iron.fitness.feature.body.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Замер тела. Любое поле может быть пустым — записывается то, что измерили. */
@Entity(tableName = "body_measurements", indices = [Index("day")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** epochDay. */
    val day: Long,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val hipsCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val neckCm: Double? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ракурс фото прогресса. */
enum class PhotoPose { FRONT, SIDE, BACK }

@Entity(tableName = "progress_photos", indices = [Index("day")])
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val path: String,
    val pose: PhotoPose = PhotoPose.FRONT,
    val weightKg: Double? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_measurements ORDER BY day DESC, createdAt DESC")
    fun observeMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE id = :id")
    suspend fun getMeasurement(id: Long): MeasurementEntity?

    @Query("SELECT weightKg FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY day DESC, createdAt DESC LIMIT 1")
    suspend fun latestWeight(): Double?

    @Query("SELECT weightKg FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY day DESC, createdAt DESC LIMIT 1")
    fun observeLatestWeight(): Flow<Double?>

    @Insert
    suspend fun insertMeasurement(m: MeasurementEntity): Long

    @Update
    suspend fun updateMeasurement(m: MeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("SELECT * FROM progress_photos ORDER BY day DESC, createdAt DESC")
    fun observePhotos(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    suspend fun getPhoto(id: Long): ProgressPhotoEntity?

    @Insert
    suspend fun insertPhoto(p: ProgressPhotoEntity): Long

    @Update
    suspend fun updatePhoto(p: ProgressPhotoEntity)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)
}
