package com.iron.fitness

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iron.fitness.core.backup.RestoreApplier
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.util.RU
import com.iron.fitness.feature.exercises.images.ExerciseImages
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class IronApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var initializer: AppInitializer
    @Inject lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Восстановление из бэкапа применяется до того, как кто-то откроет базу.
        RestoreApplier.applyPending(this)
        super.onCreate()
        Locale.setDefault(RU)
        NotificationChannels.createAll(this)
        initializer.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(ExerciseImages.cacheDir(this))
                .maxSizeBytes(ExerciseImages.MAX_CACHE_BYTES)
                .build()
        }
        // Картинки упражнений не меняются: храним их бессрочно, без перепроверки.
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
