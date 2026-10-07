package com.iron.fitness.feature.body.data

import com.iron.fitness.core.media.PhotoStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Показатель замера (для графиков и списков). */
enum class BodyMetric(val unitCm: Boolean) {
    WEIGHT(false), BODY_FAT(false), WAIST(true), CHEST(true), HIPS(true), ARM(true), THIGH(true), CALF(true), NECK(true);

    fun valueOf(m: MeasurementEntity): Double? = when (this) {
        WEIGHT -> m.weightKg
        BODY_FAT -> m.bodyFatPct
        WAIST -> m.waistCm
        CHEST -> m.chestCm
        HIPS -> m.hipsCm
        ARM -> m.armCm
        THIGH -> m.thighCm
        CALF -> m.calfCm
        NECK -> m.neckCm
    }
}

@Singleton
class BodyRepository @Inject constructor(
    private val dao: BodyDao,
    private val photos: PhotoStorage,
) {
    fun observeMeasurements(): Flow<List<MeasurementEntity>> = dao.observeMeasurements()
    fun observePhotos(): Flow<List<ProgressPhotoEntity>> = dao.observePhotos()
    fun observeLatestWeight(): Flow<Double?> = dao.observeLatestWeight()
    suspend fun latestWeight(): Double? = dao.latestWeight()
    suspend fun getMeasurement(id: Long): MeasurementEntity? = dao.getMeasurement(id)

    suspend fun saveMeasurement(m: MeasurementEntity): Long = if (m.id == 0L) dao.insertMeasurement(m) else {
        dao.updateMeasurement(m)
        m.id
    }

    suspend fun deleteMeasurement(id: Long) = dao.deleteMeasurement(id)

    suspend fun addPhoto(p: ProgressPhotoEntity): Long = dao.insertPhoto(p)
    suspend fun updatePhoto(p: ProgressPhotoEntity) = dao.updatePhoto(p)

    suspend fun deletePhoto(id: Long) {
        val p = dao.getPhoto(id) ?: return
        dao.deletePhoto(id)
        photos.delete(p.path)
    }
}

/** Последнее значение показателя и значение примерно месяц назад (для динамики). */
fun latestAndMonthAgo(list: List<MeasurementEntity>, metric: BodyMetric, todayEpochDay: Long): Pair<Double?, Double?> {
    val withValue = list.filter { metric.valueOf(it) != null }.sortedByDescending { it.day }
    val latest = withValue.firstOrNull() ?: return null to null
    val monthAgo = withValue.firstOrNull { it.day <= todayEpochDay - 28 }
        ?: withValue.lastOrNull()?.takeIf { it.id != latest.id }
    return metric.valueOf(latest) to monthAgo?.let { metric.valueOf(it) }
}
