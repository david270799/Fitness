package com.iron.fitness.feature.workouts.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.CompactNumberField
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.feature.exercises.data.RecordType
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    pickedExercises: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pickedExercises) {
        if (!pickedExercises.isNullOrEmpty()) {
            viewModel.addExercises(pickedExercises)
            onPickedHandled()
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.key as? Long ?: return@rememberReorderableLazyListState
        val t = to.key as? Long ?: return@rememberReorderableLazyListState
        viewModel.move(f, t)
    }

    IronScaffold(
        title = stringResource(if (state.isNew) R.string.routine_new_title else R.string.routine_edit_title),
        onBack = onBack,
        actions = { GhostButton(stringResource(R.string.action_save), { viewModel.save(onBack) }) },
    ) { padding ->
        LazyColumn(
            state = listState,
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
                    placeholder = stringResource(R.string.routine_name_hint),
                    isError = state.error == RoutineError.NAME,
                    supportingText = if (state.error == RoutineError.NAME) stringResource(R.string.routine_name_required) else null,
                )
            }
            item(key = "note") {
                IronTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = stringResource(R.string.routine_note),
                    singleLine = false,
                    minLines = 2,
                )
            }
            item(key = "title") { SectionTitle(stringResource(R.string.routine_exercises)) }
            if (!state.loading && state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(if (state.error == RoutineError.EXERCISES) R.string.routine_exercises_required else R.string.routine_empty),
                        icon = IronIcons.Workouts,
                    )
                }
            }
            val list = state.items
            items(list, key = { it.key }) { item ->
                val index = list.indexOfFirst { it.key == item.key }
                val next = list.getOrNull(index + 1)
                val prev = list.getOrNull(index - 1)
                val linkedNext = item.supersetGroup != null && next?.supersetGroup == item.supersetGroup
                val linkedPrev = item.supersetGroup != null && prev?.supersetGroup == item.supersetGroup
                ReorderableItem(reorderState, key = item.key) { dragging ->
                    Column {
                        RoutineItemCard(
                            item = item,
                            inSuperset = linkedNext || linkedPrev,
                            dragging = dragging,
                            onChange = { t -> viewModel.update(item.key, t) },
                            onRemove = { viewModel.remove(item.key) },
                        )
                        if (next != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                IronChip(
                                    text = stringResource(if (linkedNext) R.string.routine_superset_unlink else R.string.routine_superset_link),
                                    selected = linkedNext,
                                    onClick = { viewModel.toggleLink(item.key) },
                                    icon = IronIcons.Link,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "add") {
                SecondaryButton(
                    stringResource(R.string.routine_add_exercise),
                    onAddExercises,
                    icon = IronIcons.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "space") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.RoutineItemCard(
    item: RoutineItem,
    inSuperset: Boolean,
    dragging: Boolean,
    onChange: ((RoutineItem) -> RoutineItem) -> Unit,
    onRemove: () -> Unit,
) {
    val accent = Iron.colors.accent
    IronCard(
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        borderColor = if (dragging) Iron.colors.accent else Iron.colors.border,
        color = if (dragging) Iron.colors.surfaceHigh else Iron.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { if (inSuperset) drawRect(accent, size = Size(4.dp.toPx(), size.height)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIconButton(
                IronIcons.DragHandle,
                stringResource(R.string.routine_drag),
                onClick = {},
                modifier = Modifier.draggableHandle(),
                tint = Iron.colors.textSecondary,
            )
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IronIconButton(IronIcons.Close, stringResource(R.string.action_delete), onRemove, tint = Iron.colors.textSecondary)
        }
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Field(stringResource(R.string.routine_sets)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper(IronIcons.Remove) { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }
                    Text(
                        item.sets.toString(),
                        style = Iron.numbers.small,
                        modifier = Modifier.width(28.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Stepper(IronIcons.Add) { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(20)) } }
                }
            }
            if (item.recordType == RecordType.TIME) {
                Field(stringResource(R.string.routine_target_seconds), Modifier.weight(1f)) {
                    CompactNumberField(
                        value = item.targetSeconds,
                        onValueChange = { v -> onChange { it.copy(targetSeconds = v) } },
                        decimal = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Field(stringResource(R.string.routine_target_reps), Modifier.weight(1f)) {
                    CompactNumberField(
                        value = item.targetReps,
                        onValueChange = { v -> onChange { it.copy(targetReps = v) } },
                        placeholder = stringResource(R.string.routine_target_reps_hint),
                        allowedChars = { c -> c.isDigit() || c == '-' },
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (item.recordType == RecordType.WEIGHT_REPS) {
                Field(stringResource(R.string.routine_target_weight), Modifier.weight(1f)) {
                    CompactNumberField(
                        value = item.targetWeight,
                        onValueChange = { v -> onChange { it.copy(targetWeight = v) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Field(stringResource(R.string.routine_rest), Modifier.weight(1f)) {
                CompactNumberField(
                    value = item.rest,
                    onValueChange = { v -> onChange { it.copy(rest = v) } },
                    decimal = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun Stepper(icon: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .size(36.dp, 40.dp)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IronIcon(icon, null, size = 18.dp)
    }
}
