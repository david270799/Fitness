package com.iron.fitness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.settings.ThemeMode
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.images.ExerciseImages
import com.iron.fitness.feature.exercises.images.ImageDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val images: ExerciseImages,
    private val exercises: ExerciseRepository,
    private val bodyWeight: com.iron.fitness.core.body.BodyWeightProvider,
) : ViewModel() {

    private val _weightKg = MutableStateFlow<Double?>(null)
    /** Вес, по которому считаются калории. */
    val weightKg: StateFlow<Double?> = _weightKg.asStateFlow()

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val imageDownload: StateFlow<ImageDownloadState?> = images.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cachedBytes = MutableStateFlow(0L)
    val cachedBytes: StateFlow<Long> = _cachedBytes.asStateFlow()

    private val _totalImages = MutableStateFlow(0)
    val totalImages: StateFlow<Int> = _totalImages.asStateFlow()

    init {
        viewModelScope.launch { _weightKg.value = bodyWeight.currentKg() }
        viewModelScope.launch {
            _totalImages.value = withContext(Dispatchers.IO) {
                exercises.getAllOnce().filter { !it.isCustom }.sumOf { it.images.size }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                _cachedBytes.value = withContext(Dispatchers.IO) { images.cachedBytes() }
                delay(if (imageDownload.value?.running == true) 1_500 else 5_000)
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setTheme(id: String) = launch { repo.setTheme(id) }
    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }

    fun setDefaultRest(seconds: Int) = launch { repo.setDefaultRest(seconds) }
    fun setAutoRest(value: Boolean) = launch { repo.setAutoRest(value) }
    fun setKeepScreenOn(value: Boolean) = launch { repo.setKeepScreenOn(value) }
    fun setSound(value: Boolean) = launch { repo.setSound(value) }
    fun setVibration(value: Boolean) = launch { repo.setVibration(value) }
    fun setVoiceHints(value: Boolean) = launch { repo.setVoiceHints(value) }
    fun setReminderRepeat(minutes: Int, count: Int) = launch { repo.setReminderRepeat(minutes, count) }
    fun setBarWeight(kg: Double) = launch { repo.setBarWeight(kg) }
    fun togglePlate(kg: Double) = launch {
        val current = settings.value.plates
        val next = if (kg in current) current - kg else current + kg
        if (next.isNotEmpty()) repo.setPlates(next)
    }

    fun downloadAllImages() = images.startDownloadAll()
    fun cancelImageDownload() = images.cancel()
    fun clearImageCache() = launch {
        withContext(Dispatchers.IO) { images.clearCache() }
        _cachedBytes.value = withContext(Dispatchers.IO) { images.cachedBytes() }
    }
}
