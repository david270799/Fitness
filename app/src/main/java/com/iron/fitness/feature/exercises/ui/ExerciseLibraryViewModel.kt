package com.iron.fitness.feature.exercises.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.feature.exercises.data.ExerciseCategory
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.model.Equipment
import com.iron.fitness.feature.exercises.model.Muscle
import com.iron.fitness.feature.exercises.model.normalizeForSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Вкладка-фильтр в верхнем ряду. */
enum class LibraryTab { ALL, STRENGTH, CARDIO, STRETCHING, CUSTOM, FAVORITES }

data class LibraryFilters(
    val query: String = "",
    val tab: LibraryTab = LibraryTab.ALL,
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
)

data class LibraryItem(
    val exercise: ExerciseEntity,
    val searchKey: String,
)

data class LibraryState(
    val loading: Boolean = true,
    val items: List<ExerciseEntity> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val selected: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val pickMode: String = savedStateHandle.get<String>("mode") ?: MODE_BROWSE
    private val presetCategory: String? = savedStateHandle.get<String>("category")

    private val filters = MutableStateFlow(
        LibraryFilters(
            tab = when (presetCategory) {
                ExerciseCategory.STRENGTH.name -> LibraryTab.STRENGTH
                ExerciseCategory.CARDIO.name -> LibraryTab.CARDIO
                ExerciseCategory.STRETCHING.name -> LibraryTab.STRETCHING
                else -> LibraryTab.ALL
            },
        ),
    )
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    private val indexed = repository.observeAll()
        .map { list -> list.map { LibraryItem(it, normalizeForSearch(it.name + " " + it.nameEn)) } }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<LibraryState> = combine(
        indexed,
        filters.debounce { if (it.query.isEmpty()) 0L else 150L },
        selected,
        repository.libraryReady,
    ) { items, f, sel, ready ->
        val words = normalizeForSearch(f.query).split(' ').filter { it.isNotBlank() }
        val filtered = items.asSequence()
            .filter { item ->
                val e = item.exercise
                when (f.tab) {
                    LibraryTab.ALL -> true
                    LibraryTab.STRENGTH -> e.category == ExerciseCategory.STRENGTH
                    LibraryTab.CARDIO -> e.category == ExerciseCategory.CARDIO
                    LibraryTab.STRETCHING -> e.category == ExerciseCategory.STRETCHING
                    LibraryTab.CUSTOM -> e.isCustom
                    LibraryTab.FAVORITES -> e.isFavorite
                }
            }
            .filter { f.muscle == null || f.muscle.key in it.exercise.primaryMuscles || f.muscle.key in it.exercise.secondaryMuscles }
            .filter { f.equipment == null || it.exercise.equipment == f.equipment.key }
            .filter { item -> words.all { item.searchKey.contains(it) } }
            .map { it.exercise }
            .sortedWith(
                compareByDescending<ExerciseEntity> { it.isFavorite }
                    .thenByDescending { f.muscle != null && f.muscle.key in it.primaryMuscles }
                    .thenBy { it.name.lowercase() },
            )
            .toList()
        LibraryState(
            loading = !ready && items.isEmpty(),
            items = filtered,
            filters = f,
            selected = sel,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setTab(tab: LibraryTab) = filters.update { it.copy(tab = tab) }
    fun setMuscle(m: Muscle?) = filters.update { it.copy(muscle = m) }
    fun setEquipment(e: Equipment?) = filters.update { it.copy(equipment = e) }
    fun resetFilters() = filters.update { LibraryFilters(tab = it.tab) }

    fun toggleSelected(id: String) = selected.update { if (id in it) it - id else it + id }

    fun toggleFavorite(e: ExerciseEntity) {
        viewModelScope.launch { repository.setFavorite(e.id, !e.isFavorite) }
    }

    companion object {
        const val MODE_BROWSE = "browse"
        const val MODE_PICK_ONE = "pick"
        const val MODE_PICK_MANY = "pickMany"
    }
}
