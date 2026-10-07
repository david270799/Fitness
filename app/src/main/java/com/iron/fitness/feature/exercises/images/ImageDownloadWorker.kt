package com.iron.fitness.feature.exercises.images

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.iron.fitness.R
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.exercises.data.ExerciseDao
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Скачивает все картинки библиотеки в дисковый кэш Coil (для работы офлайн). */
@HiltWorker
class ImageDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: ExerciseDao,
    private val repository: ExerciseRepository,
) : CoroutineWorker(appContext, params) {

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun doWork(): Result {
        repository.ensureLibraryImported()
        val urls = dao.getLibrary().flatMap { it.images }.distinct().map { ExerciseRepository.imageUrl(it) }
        val total = urls.size
        val loader = applicationContext.imageLoader
        val diskCache = loader.diskCache
        runCatching { setForeground(foregroundInfo(0, total)) }
        var done = 0
        var failed = 0
        for (url in urls) {
            if (isStopped) break
            val cached = diskCache?.openSnapshot(url)?.use { true } ?: false
            if (!cached) {
                val request = ImageRequest.Builder(applicationContext)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = loader.execute(request)
                if (result !is SuccessResult) failed++
            }
            done++
            if (done % 10 == 0 || done == total) {
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
                runCatching { setForeground(foregroundInfo(done, total)) }
            }
        }
        return Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val ctx = applicationContext.withRussianLocale()
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_images_title))
            .setContentText(if (total > 0) ctx.getString(R.string.images_downloading, done, total) else null)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        private const val NOTIFICATION_ID = 4101
    }
}
