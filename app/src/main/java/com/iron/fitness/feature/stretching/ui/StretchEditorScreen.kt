package com.iron.fitness.feature.stretching.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.StretchItem
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.domain.Stretching
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronSwitch
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.thumbnailModel
import com.iron.fitness.feature.exercises.ui.ExerciseThumb
import com.iron.fitness.feature.stretching.data.StretchRepository
import com.iron.fitness.feature.stretching.data.StretchRoutine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StretchEditItem(val key: Long, val item: StretchItem, val exercise: ExerciseEntity?)

data class StretchEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val phase: StretchPhase = StretchPhase.AFTER,
    val items: List<StretchEditItem> = emptyList(),
    val nameError: Boolean = false,
    val itemsError: Boolean = false,
)

@HiltViewModel
class StretchEditorViewModel @Inject constructor(
    private val repo: StretchRepository,
    private val exercises: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routineId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var nextKey = 1L
    private val _state = MutableStateFlow(StretchEditorState(isNew = routineId == null))
    val state: StateFlow<StretchEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = routineId?.let { repo.getRoutine(it) }
            if (r == null) {
                _state.update { it.copy(loading = false) }
            } else {
                val ex = exercises.getAll(r.items.map { it.exerciseId }).associateBy { it.id }
                _state.value = StretchEditorState(
                    loading = false,
                    isNew = false,
                    name = r.name,
                    phase = r.phase,
                    items = r.items.map { StretchEditItem(nextKey++, it, ex[it.exerciseId]) },
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun setPhase(p: StretchPhase) = _state.update { it.copy(phase = p) }

    fun add(ids: List<String>) {
        viewModelScope.launch {
            val ex = exercises.getAll(ids).associateBy { it.id }
            val added = ids.mapNotNull { id -> ex[id]?.let { StretchEditItem(nextKey++, StretchItem(id, 30, false), it) } }
            _state.update { it.copy(items = it.items + added, itemsError = false) }
        }
    }

    fun update(key: Long, t: (StretchItem) -> StretchItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key == key) it.copy(item = t(it.item)) else it }) }

    fun remove(key: Long) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    fun move(key: Long, delta: Int) = _state.update { s ->
        val list = s.items.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val t = i + delta
        if (i < 0 || t !in list.indices) return@update s
        list.add(t, list.removeAt(i))
        s.copy(items = list)
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        when {
            s.name.isBlank() -> { _state.update { it.copy(nameError = true) }; return }
            s.items.isEmpty() -> { _state.update { it.copy(itemsError = true) }; return }
        }
        viewModelScope.launch {
            repo.saveRoutine(StretchRoutine(id = routineId ?: 0, name = s.name.trim(), phase = s.phase, items = s.items.map { it.item }))
            onSaved()
        }
    }
}

@Composable
fun StretchEditorScreen(
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    pickedExercises: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: StretchEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(pickedExercises) {
        if (!pickedExercises.isNullOrEmpty()) {
            viewModel.add(pickedExercises)
            onPickedHandled()
        }
    }
    IronScaffold(
        title = stringResource(if (state.isNew) R.string.stretch_new else R.string.stretch_edit),
        onBack = onBack,
        actions = { GhostButton(stringResource(R.string.action_save), { viewModel.save(onBack) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "name") {
                IronTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.routine_name),
                    placeholder = stringResource(R.string.stretch_name_hint),
                    isError = state.nameError,
                    supportingText = if (state.nameError) stringResource(R.string.routine_name_required) else null,
                )
            }
            item(key = "phase") {
                Segmented(items = StretchPhase.entries, selected = state.phase, label = { stretchPhaseLabel(it) }, onSelect = viewModel::setPhase)
            }
            item(key = "total") {
                val total = Stretching.totalSeconds(state.items.map { it.item })
                SectionTitle(stringResource(R.string.stretch_positions) + " · " + Fmt.duration(total))
            }
            if (!state.loading && state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(if (state.itemsError) R.string.routine_exercises_required else R.string.stretch_empty),
                        icon = IronIcons.Stretch,
                    )
                }
            }
            items(state.items, key = { it.key }) { e ->
                val index = state.items.indexOf(e)
                StretchItemCard(
                    e = e,
                    isFirst = index == 0,
                    isLast = index == state.items.lastIndex,
                    onSeconds = { sec -> viewModel.update(e.key) { it.copy(seconds = sec.coerceIn(5, 600)) } },
                    onSides = { v -> viewModel.update(e.key) { it.copy(bothSides = v) } },
                    onMove = { d -> viewModel.move(e.key, d) },
                    onRemove = { viewModel.remove(e.key) },
                )
            }
            item(key = "add") {
                SecondaryButton(stringResource(R.string.routine_add_exercise), onAddExercises, icon = IronIcons.Add, modifier = Modifier.fillMaxWidth())
            }
            item(key = "space") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StretchItemCard(
    e: StretchEditItem,
    isFirst: Boolean,
    isLast: Boolean,
    onSeconds: (Int) -> Unit,
    onSides: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    IronCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ex = e.exercise
            if (ex != null) {
                ExerciseThumb(ex.thumbnailModel(), ex.category, size = 48.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                ex?.name ?: e.item.exerciseId,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
                IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                    IronMenuItem(stringResource(R.string.session_menu_up), { menu = false; onMove(-1) }, IronIcons.ChevronUp, enabled = !isFirst)
                    IronMenuItem(stringResource(R.string.session_menu_down), { menu = false; onMove(1) }, IronIcons.ChevronDown, enabled = !isLast)
                    IronMenuItem(stringResource(R.string.action_delete), { menu = false; onRemove() }, IronIcons.Delete, color = Iron.colors.error)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stretch_hold).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            IronIconButton(IronIcons.Remove, null, { onSeconds(e.item.seconds - 5) })
            Text(Fmt.duration(e.item.seconds), style = Iron.numbers.small, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
            IronIconButton(IronIcons.Add, null, { onSeconds(e.item.seconds + 5) })
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.stretch_both_sides), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            IronSwitch(checked = e.item.bothSides, onCheckedChange = onSides)
            Spacer(Modifier.width(8.dp))
        }
    }
}
