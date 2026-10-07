package com.iron.fitness.feature.exercises.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.media.PhotoKind
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.feature.exercises.data.ExerciseCategory
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.exercises.model.Equipment
import com.iron.fitness.feature.exercises.model.Muscle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExerciseForm(
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val recordType: RecordType = RecordType.WEIGHT_REPS,
    val muscles: Set<Muscle> = emptySet(),
    val equipment: Equipment? = null,
    val rest: String = "",
    val note: String = "",
    val photoPath: String? = null,
    val nameError: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    private val photos: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editId: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }
    val isNew: Boolean = editId == null

    private var original: ExerciseEntity? = null
    private val tempPhotos = mutableSetOf<String>()

    private val _form = MutableStateFlow(ExerciseForm(loaded = editId == null))
    val form: StateFlow<ExerciseForm> = _form.asStateFlow()

    init {
        val presetCategory = savedStateHandle.get<String>("category")
        if (editId == null && presetCategory != null) {
            runCatching { ExerciseCategory.valueOf(presetCategory) }.getOrNull()?.let { cat ->
                _form.update { it.copy(category = cat, recordType = defaultTypeFor(cat)) }
            }
        }
        if (editId != null) {
            viewModelScope.launch {
                val e = repository.get(editId)
                original = e
                if (e != null) {
                    _form.value = ExerciseForm(
                        name = e.name,
                        category = e.category,
                        recordType = e.recordType,
                        muscles = e.primaryMuscles.mapNotNull { Muscle.of(it) }.toSet(),
                        equipment = Equipment.of(e.equipment),
                        rest = e.restSeconds?.toString() ?: "",
                        note = e.note ?: "",
                        photoPath = e.customImagePath,
                        loaded = true,
                    )
                }
            }
        }
    }

    private fun defaultTypeFor(cat: ExerciseCategory) = when (cat) {
        ExerciseCategory.STRENGTH -> RecordType.WEIGHT_REPS
        ExerciseCategory.CARDIO -> RecordType.TIME
        ExerciseCategory.STRETCHING -> RecordType.TIME
    }

    fun setName(v: String) = _form.update { it.copy(name = v, nameError = false) }
    fun setCategory(v: ExerciseCategory) = _form.update {
        val type = if (it.recordType == defaultTypeFor(it.category)) defaultTypeFor(v) else it.recordType
        it.copy(category = v, recordType = type)
    }
    fun setRecordType(v: RecordType) = _form.update { it.copy(recordType = v) }
    fun toggleMuscle(m: Muscle) = _form.update { it.copy(muscles = if (m in it.muscles) it.muscles - m else it.muscles + m) }
    fun setEquipment(e: Equipment?) = _form.update { it.copy(equipment = if (it.equipment == e) null else e) }
    fun setRest(v: String) = _form.update { it.copy(rest = v.filter { c -> c.isDigit() }.take(4)) }
    fun setNote(v: String) = _form.update { it.copy(note = v) }

    fun newCameraUri(): Uri {
        val (uri, file) = photos.newCameraTarget()
        savedStateHandle["pending_camera"] = file.absolutePath
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val path = savedStateHandle.get<String>("pending_camera") ?: return
        savedStateHandle.remove<String>("pending_camera")
        val file = File(path)
        if (!success) {
            file.delete()
            return
        }
        viewModelScope.launch { photos.importFile(file, PhotoKind.EXERCISE)?.let(::setPhoto) }
    }

    fun onGalleryPicked(uri: Uri) {
        viewModelScope.launch { photos.import(uri, PhotoKind.EXERCISE)?.let(::setPhoto) }
    }

    private fun setPhoto(path: String) {
        tempPhotos += path
        val previous = _form.value.photoPath
        if (previous != null && previous in tempPhotos) {
            photos.delete(previous)
            tempPhotos -= previous
        }
        _form.update { it.copy(photoPath = path) }
    }

    fun removePhoto() {
        val previous = _form.value.photoPath
        if (previous != null && previous in tempPhotos) {
            photos.delete(previous)
            tempPhotos -= previous
        }
        _form.update { it.copy(photoPath = null) }
    }

    fun save(onSaved: (String) -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = true) }
            return
        }
        if (f.saving) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val base = original
            val entity = ExerciseEntity(
                id = base?.id ?: "",
                name = f.name.trim(),
                nameEn = base?.nameEn ?: "",
                category = f.category,
                sourceCategory = null,
                force = null,
                level = null,
                mechanic = null,
                equipment = f.equipment?.key,
                primaryMuscles = f.muscles.map { it.key },
                secondaryMuscles = emptyList(),
                instructions = base?.instructions ?: emptyList(),
                images = emptyList(),
                isCustom = true,
                recordType = f.recordType,
                note = f.note.trim().ifBlank { null },
                customImagePath = f.photoPath,
                restSeconds = f.rest.toIntOrNull()?.takeIf { it > 0 },
                met = base?.met,
                isFavorite = base?.isFavorite ?: false,
                createdAt = base?.createdAt ?: 0L,
            )
            val id = repository.saveCustom(entity)
            val oldPhoto = base?.customImagePath
            if (oldPhoto != null && oldPhoto != f.photoPath) photos.delete(oldPhoto)
            tempPhotos.clear()
            onSaved(id)
        }
    }

    override fun onCleared() {
        // Фото, выбранные, но не сохранённые, удаляем.
        tempPhotos.filter { it != original?.customImagePath }.forEach { photos.delete(it) }
        super.onCleared()
    }
}
