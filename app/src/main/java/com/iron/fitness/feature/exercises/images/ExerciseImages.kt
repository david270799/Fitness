package com.iron.fitness.feature.exercises.images

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImageDownloadState(
    val running: Boolean,
    val done: Int,
    val total: Int,
    val failed: Int,
    val finished: Boolean,
)

@Singleton
class ExerciseImages @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun startDownloadAll() {
        val request = OneTimeWorkRequestBuilder<ImageDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun observeState(): Flow<ImageDownloadState?> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            val data = if (info.state.isFinished) info.outputData else info.progress
            ImageDownloadState(
                running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                done = data.getInt(ImageDownloadWorker.KEY_DONE, 0),
                total = data.getInt(ImageDownloadWorker.KEY_TOTAL, 0),
                failed = data.getInt(ImageDownloadWorker.KEY_FAILED, 0),
                finished = info.state.isFinished,
            )
        }

    @OptIn(ExperimentalCoilApi::class)
    fun cachedBytes(): Long = context.imageLoader.diskCache?.size ?: 0L

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }

    companion object {
        const val WORK_NAME = "download_exercise_images"
        const val MAX_CACHE_BYTES = 600L * 1024 * 1024
        /** Средний размер картинки Free Exercise DB (замер ~55 КБ). */
        const val AVG_IMAGE_BYTES = 55L * 1024

        fun cacheDir(context: Context): File = File(context.filesDir, "exercise_images")
    }
}
