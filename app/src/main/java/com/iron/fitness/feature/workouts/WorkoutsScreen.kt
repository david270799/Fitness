package com.iron.fitness.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.ui.CardioTab
import com.iron.fitness.feature.stretching.ui.StretchingTab
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.feature.workouts.history.WorkoutRow
import com.iron.fitness.navigation.Routes
import kotlinx.coroutines.delay

enum class WorkoutsTab { STRENGTH, CARDIO, STRETCHING }

@Composable
fun WorkoutsScreen(
    navigate: (String) -> Unit,
    viewModel: WorkoutsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(WorkoutsTab.STRENGTH) }
    IronScaffold(
        title = stringResource(R.string.workouts_title),
        actions = {
            IronIconButton(IronIcons.History, stringResource(R.string.history_title), { navigate(Routes.HISTORY) })
            IronIconButton(IronIcons.Calculator, stringResource(R.string.workouts_tools), { navigate(Routes.TOOLS) })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Segmented(
                items = WorkoutsTab.entries,
                selected = tab,
                label = {
                    stringResource(
                        when (it) {
                            WorkoutsTab.STRENGTH -> R.string.workouts_tab_strength
                            WorkoutsTab.CARDIO -> R.string.workouts_tab_cardio
                            WorkoutsTab.STRETCHING -> R.string.workouts_tab_stretching
                        },
                    )
                },
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when (tab) {
                WorkoutsTab.STRENGTH -> StrengthTab(state, viewModel, navigate)
                WorkoutsTab.CARDIO -> CardioTab(navigate)
                WorkoutsTab.STRETCHING -> StretchingTab(navigate)
            }
        }
    }
}

@Composable
private fun StrengthTab(state: WorkoutsState, viewModel: WorkoutsViewModel, navigate: (String) -> Unit) {
    var blockedBy by remember { mutableStateOf<WorkoutEntity?>(null) }
    var deleteRoutine by remember { mutableStateOf<RoutineCard?>(null) }
    val defaultName = stringResource(R.string.workouts_default_name)
    val copySuffix = stringResource(R.string.workouts_copy_suffix)

    fun guardStart(start: () -> Unit) {
        val active = state.active
        if (active != null) blockedBy = active else start()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.active?.let { active ->
            item(key = "active") {
                ActiveWorkoutBanner(active) { navigate(Routes.workoutSession(active.id)) }
            }
        }
        item(key = "start") {
            PrimaryButton(
                text = stringResource(R.string.workouts_start_empty),
                onClick = { guardStart { viewModel.startEmpty(defaultName) { navigate(Routes.workoutSession(it)) } } },
                icon = IronIcons.Play,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "routines_title") {
            SectionTitle(stringResource(R.string.workouts_routines)) {
                GhostButton(stringResource(R.string.workouts_new_routine), { navigate(Routes.routineEdit()) })
            }
        }
        if (!state.loading && state.routines.isEmpty()) {
            item(key = "no_routines") {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workouts_no_routines), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        stringResource(R.string.workouts_new_routine),
                        { navigate(Routes.routineEdit()) },
                        icon = IronIcons.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        items(state.routines, key = { "r" + it.routine.id }) { card ->
            RoutineCardView(
                card = card,
                onOpen = { navigate(Routes.routineEdit(card.routine.id)) },
                onStart = { guardStart { viewModel.startRoutine(card.routine.id) { navigate(Routes.workoutSession(it)) } } },
                onCopy = { viewModel.copyRoutine(card.routine.id, copySuffix) },
                onDelete = { deleteRoutine = card },
            )
        }
        item(key = "recent_title") {
            SectionTitle(stringResource(R.string.workouts_recent)) {
                GhostButton(stringResource(R.string.workouts_history_all), { navigate(Routes.HISTORY) })
            }
        }
        if (!state.loading && state.recent.isEmpty()) {
            item(key = "no_recent") {
                Text(stringResource(R.string.workouts_no_history), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            }
        }
        items(state.recent, key = { "w" + it.workout.id }) { row ->
            WorkoutRow(row, onClick = { navigate(Routes.workoutDetail(row.workout.id)) })
        }
    }

    blockedBy?.let { active ->
        ConfirmDialog(
            title = stringResource(R.string.workouts_already_active_title),
            text = stringResource(R.string.workouts_already_active_text, active.name),
            confirmText = stringResource(R.string.action_open),
            onConfirm = { blockedBy = null; navigate(Routes.workoutSession(active.id)) },
            onDismiss = { blockedBy = null },
        )
    }
    deleteRoutine?.let { card ->
        ConfirmDialog(
            title = stringResource(R.string.workouts_routine_delete_title),
            text = stringResource(R.string.workouts_routine_delete_text, card.routine.name),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteRoutine(card.routine.id); deleteRoutine = null },
            onDismiss = { deleteRoutine = null },
        )
    }
}

@Composable
fun ActiveWorkoutBanner(active: WorkoutEntity, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    IronCard(onClick = onOpen, borderColor = Iron.colors.accent, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workouts_active_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.accentText)
                Text(active.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.workouts_active_since, Fmt.time(active.startedAt), Fmt.duration((now - active.startedAt) / 1000)),
                    style = Iron.numbers.tiny,
                    color = Iron.colors.textSecondary,
                )
            }
            PrimaryButton(stringResource(R.string.action_continue), onOpen, modifier = Modifier.height(44.dp))
        }
    }
}

@Composable
private fun RoutineCardView(
    card: RoutineCard,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    IronCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.exerciseNames.isNotEmpty()) {
                    Text(
                        card.exerciseNames.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Iron.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.exercises, card.exerciseNames.size, card.exerciseNames.size) + " · " +
                        pluralStringResource(R.plurals.sets, card.setCount, card.setCount),
                    style = Iron.numbers.tiny,
                    color = Iron.colors.textSecondary,
                )
                Text(
                    card.lastAt?.let { stringResource(R.string.workouts_last_done, Fmt.date(Fmt.toLocalDate(it))) }
                        ?: stringResource(R.string.workouts_never_done),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
                    IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                        IronMenuItem(stringResource(R.string.action_edit), { menu = false; onOpen() }, IronIcons.Edit)
                        IronMenuItem(stringResource(R.string.action_copy), { menu = false; onCopy() }, IronIcons.Copy)
                        IronMenuItem(stringResource(R.string.action_delete), { menu = false; onDelete() }, IronIcons.Delete, color = Iron.colors.error)
                    }
                }
                PrimaryButton(
                    stringResource(R.string.action_start),
                    onStart,
                    modifier = Modifier.padding(end = 10.dp).height(40.dp),
                )
            }
        }
    }
}
