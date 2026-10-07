package com.iron.fitness.feature.exercises.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ChipRow
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SearchField
import com.iron.fitness.core.ui.components.SelectorButton
import com.iron.fitness.core.ui.components.SingleChoiceSheet
import com.iron.fitness.core.ui.components.Tag
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.thumbnailModel
import com.iron.fitness.feature.exercises.model.Equipment
import com.iron.fitness.feature.exercises.model.Muscle

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onPicked: (List<String>) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode = viewModel.pickMode
    val picking = mode != ExerciseLibraryViewModel.MODE_BROWSE
    var muscleSheet by remember { mutableStateOf(false) }
    var equipmentSheet by remember { mutableStateOf(false) }

    IronScaffold(
        title = stringResource(if (picking) R.string.library_pick_title else R.string.library_title),
        onBack = onBack,
        actions = {
            IronIconButton(IronIcons.Add, stringResource(R.string.library_add_custom), onCreateCustom)
        },
        bottomBar = {
            if (mode == ExerciseLibraryViewModel.MODE_PICK_MANY && state.selected.isNotEmpty()) {
                Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.library_add_selected, state.selected.size),
                        onClick = { onPicked(state.selected.toList()) },
                        icon = IronIcons.Check,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.library_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            ChipRow(
                items = LibraryTab.entries,
                selected = state.filters.tab,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setTab,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectorButton(
                    label = stringResource(R.string.library_filter_muscle),
                    value = state.filters.muscle?.let { stringResource(it.label) } ?: stringResource(R.string.library_filter_any),
                    onClick = { muscleSheet = true },
                    active = state.filters.muscle != null,
                    modifier = Modifier.weight(1f),
                )
                SelectorButton(
                    label = stringResource(R.string.library_filter_equipment),
                    value = state.filters.equipment?.let { stringResource(it.label) } ?: stringResource(R.string.library_filter_any),
                    onClick = { equipmentSheet = true },
                    active = state.filters.equipment != null,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.exercises, state.items.size, state.items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Iron.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (state.filters.muscle != null || state.filters.equipment != null || state.filters.query.isNotEmpty()) {
                    GhostButton(stringResource(R.string.library_reset_filters), onClick = {
                        viewModel.resetFilters()
                        viewModel.setQuery("")
                    })
                }
            }
            IronDivider()
            when {
                state.loading -> Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Iron.colors.accent)
                    Text(stringResource(R.string.library_loading), color = Iron.colors.textSecondary)
                }
                state.items.isEmpty() -> EmptyState(stringResource(R.string.library_empty), icon = IronIcons.Search)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.items, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            selectable = mode == ExerciseLibraryViewModel.MODE_PICK_MANY,
                            selected = exercise.id in state.selected,
                            onClick = {
                                when (mode) {
                                    ExerciseLibraryViewModel.MODE_PICK_ONE -> onPicked(listOf(exercise.id))
                                    ExerciseLibraryViewModel.MODE_PICK_MANY -> viewModel.toggleSelected(exercise.id)
                                    else -> onOpenExercise(exercise.id)
                                }
                            },
                            onInfo = if (picking) ({ onOpenExercise(exercise.id) }) else null,
                        )
                        IronDivider(Modifier.padding(start = 84.dp))
                    }
                }
            }
        }
    }

    if (muscleSheet) {
        SingleChoiceSheet(
            title = stringResource(R.string.library_filter_muscle),
            items = Muscle.entries,
            selected = state.filters.muscle,
            anyLabel = stringResource(R.string.library_filter_all),
            label = { stringResource(it.label) },
            onSelect = viewModel::setMuscle,
            onDismiss = { muscleSheet = false },
        )
    }
    if (equipmentSheet) {
        SingleChoiceSheet(
            title = stringResource(R.string.library_filter_equipment),
            items = Equipment.entries,
            selected = state.filters.equipment,
            anyLabel = stringResource(R.string.library_filter_all),
            label = { stringResource(it.label) },
            onSelect = viewModel::setEquipment,
            onDismiss = { equipmentSheet = false },
        )
    }
}

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.ALL -> R.string.library_filter_all
    LibraryTab.STRENGTH -> R.string.category_strength
    LibraryTab.CARDIO -> R.string.category_cardio
    LibraryTab.STRETCHING -> R.string.category_stretching
    LibraryTab.CUSTOM -> R.string.library_filter_custom
    LibraryTab.FAVORITES -> R.string.library_filter_favorites
}

@Composable
fun ExerciseRow(
    exercise: ExerciseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumb(exercise.thumbnailModel(), exercise.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                exerciseSubtitle(exercise),
                style = MaterialTheme.typography.bodySmall,
                color = Iron.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (exercise.isCustom || exercise.isFavorite) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (exercise.isCustom) Tag(stringResource(R.string.exercise_custom_badge), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text)
                    if (exercise.isFavorite) IronIcon(IronIcons.Star, null, tint = Iron.colors.warning, size = 16.dp)
                }
            }
        }
        if (onInfo != null) {
            IronIconButton(IronIcons.Info, null, onInfo, tint = Iron.colors.textSecondary)
        }
        if (selectable) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Iron.colors.accent,
                    checkmarkColor = Iron.colors.onAccent,
                    uncheckedColor = Iron.colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
fun exerciseSubtitle(exercise: ExerciseEntity): String {
    val muscles = exercise.primaryMuscles.mapNotNull { Muscle.of(it) }.map { stringResource(it.label) }
    val equipment = Equipment.of(exercise.equipment)?.let { stringResource(it.label) }
    return (muscles + listOfNotNull(equipment)).joinToString(" · ")
}
