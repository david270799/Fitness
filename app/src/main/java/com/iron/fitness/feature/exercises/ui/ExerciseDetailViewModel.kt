package com.iron.fitness.feature.exercises.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    private val photos: PhotoStorage,
    settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val exerciseId: String = checkNotNull(savedStateHandle.get<String>("id"))

    val exercise: StateFlow<ExerciseEntity?> = repository.observe(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val defaultRest: StateFlow<Int> = settings.settings.map { it.defaultRestSeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 90)

    fun toggleFavorite() {
        val e = exercise.value ?: return
        viewModelScope.launch { repository.setFavorite(e.id, !e.isFavorite) }
    }

    fun saveSettings(recordType: RecordType, restSeconds: Int?, note: String) {
        viewModelScope.launch { repository.updateUserSettings(exerciseId, recordType, restSeconds, note) }
    }

    fun delete(onDone: () -> Unit) {
        val e = exercise.value ?: return
        viewModelScope.launch {
            repository.deleteCustom(e.id)
            photos.delete(e.customImagePath)
            onDone()
        }
    }
}
