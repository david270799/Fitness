package com.iron.fitness.feature.stretching.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.domain.Stretching
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronBottomSheet
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.components.Tag
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.run.ProgramRunner
import com.iron.fitness.feature.cardio.run.RunState
import com.iron.fitness.feature.stretching.data.StretchLauncher
import com.iron.fitness.feature.stretching.data.StretchRepository
import com.iron.fitness.feature.stretching.data.StretchRoutine
import com.iron.fitness.feature.stretching.data.StretchTemplate
import com.iron.fitness.feature.stretching.data.StretchTemplates
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSummaryRow
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.feature.workouts.history.WorkoutRow
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StretchTabState(
    val loading: Boolean = true,
    val routines: List<StretchRoutine> = emptyList(),
    val recent: List<WorkoutSummaryRow> = emptyList(),
    val run: RunState? = null,
)

@HiltViewModel
class StretchTabViewModel @Inject constructor(
    private val repo: StretchRepository,
    private val launcher: StretchLauncher,
    runner: ProgramRunner,
    workouts: WorkoutRepository,
) : ViewModel() {
    val state: StateFlow<StretchTabState> = combine(
        repo.observeRoutines(),
        workouts.observeHistory(),
        runner.state,
    ) { routines, history, run ->
        StretchTabState(
            loading = false,
            routines = routines,
            recent = history.filter { it.workout.type == WorkoutType.STRETCHING }.take(5),
            run = run,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StretchTabState())

    fun startTemplate(t: StretchTemplate, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(launcher.startTemplate(t)) }
    fun startRoutine(r: StretchRoutine, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(launcher.startRoutine(r)) }
    fun copyRoutine(id: Long, suffix: String) = viewModelScope.launch { repo.copyRoutine(id, suffix) }
    fun deleteRoutine(id: Long) = viewModelScope.launch { repo.deleteRoutine(id) }
    fun templateToMine(t: StretchTemplate, name: String, onSaved: (Long) -> Unit) = viewModelScope.launch {
        onSaved(repo.saveRoutine(StretchRoutine(name = name, phase = t.phase, items = t.items)))
    }
    fun markDone(name: String, phase: StretchPhase, minutes: Int) = viewModelScope.launch { repo.markDone(name, phase, minutes) }
}

@Composable
fun stretchPhaseLabel(phase: StretchPhase): String = stringResource(
    when (phase) {
        StretchPhase.BEFORE -> R.string.stretch_phase_before
        StretchPhase.AFTER -> R.string.stretch_phase_after
        StretchPhase.ANY -> R.string.stretch_phase_any
    },
)

/** Вкладка «Растяжка»: шаблоны и свои комплексы с таймером удержания, отметка «выполнена». */
@Composable
fun StretchingTab(navigate: (String) -> Unit, viewModel: StretchTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val res = LocalContext.current.resources
    var markSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var deleteRoutine by remember { mutableStateOf<StretchRoutine?>(null) }
    val copySuffix = stringResource(R.string.workouts_copy_suffix)
    val onStarted: (Boolean) -> Unit = { ok -> if (ok) navigate(Routes.INTERVAL_RUN) else busy = true }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.run?.let { run ->
            item(key = "run") {
                IronCard(onClick = { navigate(Routes.INTERVAL_RUN) }, borderColor = Iron.colors.accent, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_active).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.accentText)
                    Text(run.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(key = "mark") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    stringResource(R.string.stretch_mark_done),
                    { markSheet = true },
                    icon = IronIcons.Check,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    stringResource(R.string.stretch_new),
                    { navigate(Routes.stretchEdit()) },
                    icon = IronIcons.Add,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
        }
        item(key = "templates_title") { SectionTitle(stringResource(R.string.intervals_templates)) }
        items(StretchTemplates.all, key = { "t" + it.key }) { t ->
            val name = stringResource(t.name)
            StretchCard(
                title = name,
                phase = t.phase,
                seconds = Stretching.totalSeconds(t.items),
                count = t.items.size,
                onOpen = null,
                onStart = { viewModel.startTemplate(t, onStarted) },
                menu = { close ->
                    IronMenuItem(stringResource(R.string.intervals_to_mine), {
                        close()
                        viewModel.templateToMine(t, name) { id -> navigate(Routes.stretchEdit(id)) }
                    }, IronIcons.Copy)
                },
            )
        }
        item(key = "mine_title") {
            SectionTitle(stringResource(R.string.stretch_mine)) {
                GhostButton(stringResource(R.string.stretch_new), { navigate(Routes.stretchEdit()) })
            }
        }
        if (!state.loading && state.routines.isEmpty()) {
            item(key = "mine_empty") {
                Text(stringResource(R.string.stretch_mine_empty), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            }
        }
        items(state.routines, key = { "r" + it.id }) { r ->
            StretchCard(
                title = r.name,
                phase = r.phase,
                seconds = r.totalSeconds,
                count = r.items.size,
                onOpen = { navigate(Routes.stretchEdit(r.id)) },
                onStart = { viewModel.startRoutine(r, onStarted) },
                menu = { close ->
                    IronMenuItem(stringResource(R.string.action_edit), { close(); navigate(Routes.stretchEdit(r.id)) }, IronIcons.Edit)
                    IronMenuItem(stringResource(R.string.action_copy), { close(); viewModel.copyRoutine(r.id, copySuffix) }, IronIcons.Copy)
                    IronMenuItem(stringResource(R.string.action_delete), { close(); deleteRoutine = r }, IronIcons.Delete, color = Iron.colors.error)
                },
            )
        }
        item(key = "recent_title") { SectionTitle(stringResource(R.string.stretch_recent)) }
        if (!state.loading && state.recent.isEmpty()) {
            item(key = "recent_empty") {
                Text(stringResource(R.string.workouts_no_history), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            }
        }
        items(state.recent, key = { "w" + it.workout.id }) { row ->
            WorkoutRow(row, onClick = { navigate(Routes.workoutDetail(row.workout.id)) })
        }
    }

    if (markSheet) {
        MarkStretchSheet(
            onSave = { phase, minutes ->
                viewModel.markDone(res.getString(R.string.stretch_default_name), phase, minutes)
                markSheet = false
            },
            onDismiss = { markSheet = false },
        )
    }
    deleteRoutine?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.stretch_delete_title),
            text = r.name,
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteRoutine(r.id); deleteRoutine = null },
            onDismiss = { deleteRoutine = null },
        )
    }
    if (busy) {
        ConfirmDialog(
            title = stringResource(R.string.run_busy_title),
            text = stringResource(R.string.run_busy_text),
            confirmText = stringResource(R.string.action_open),
            onConfirm = { busy = false; navigate(Routes.INTERVAL_RUN) },
            onDismiss = { busy = false },
        )
    }
}

/** Лист «Растяжка выполнена»: когда и сколько минут. */
@Composable
fun MarkStretchSheet(onSave: (StretchPhase, Int) -> Unit, onDismiss: () -> Unit, initialPhase: StretchPhase = StretchPhase.AFTER) {
    var phase by remember { mutableStateOf(initialPhase) }
    var minutes by remember { mutableIntStateOf(10) }
    IronBottomSheet(title = stringResource(R.string.stretch_mark_done), onDismiss = onDismiss) {
        Segmented(
            items = StretchPhase.entries,
            selected = phase,
            label = { stretchPhaseLabel(it) },
            onSelect = { phase = it },
        )
        Text(stringResource(R.string.cardio_minutes).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20, 30).forEach { m ->
                IronChip(stringResource(R.string.minutes_short, m), selected = minutes == m, onClick = { minutes = m })
            }
        }
        Text(stringResource(R.string.stretch_mark_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
        PrimaryButton(stringResource(R.string.action_save), { onSave(phase, minutes) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StretchCard(
    title: String,
    phase: StretchPhase,
    seconds: Int,
    count: Int,
    onOpen: (() -> Unit)?,
    onStart: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IronCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tag(stretchPhaseLabel(phase), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(
                    Fmt.durationWords(seconds.toLong()) + " · " + stringResource(R.string.stretch_count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = Iron.colors.textSecondary,
                )
            }
            PrimaryButton(stringResource(R.string.action_start), onStart, modifier = Modifier.padding(start = 8.dp).height(40.dp))
            Box {
                IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { expanded = true })
                IronDropdownMenu(expanded = expanded, onDismiss = { expanded = false }) {
                    menu { expanded = false }
                }
            }
        }
    }
}
