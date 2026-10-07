package com.iron.fitness.feature.cardio.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.Intervals
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
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.data.CardioRepository
import com.iron.fitness.feature.cardio.data.CardioType
import com.iron.fitness.feature.cardio.data.IntervalProgram
import com.iron.fitness.feature.cardio.data.IntervalTemplates
import com.iron.fitness.feature.cardio.run.ProgramRunner
import com.iron.fitness.feature.cardio.run.RunState
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

data class CardioTabState(
    val loading: Boolean = true,
    val programs: List<IntervalProgram> = emptyList(),
    val recent: List<WorkoutSummaryRow> = emptyList(),
    val run: RunState? = null,
)

@HiltViewModel
class CardioTabViewModel @Inject constructor(
    private val repo: CardioRepository,
    private val runner: ProgramRunner,
    workouts: WorkoutRepository,
) : ViewModel() {
    val state: StateFlow<CardioTabState> = combine(
        repo.observePrograms(),
        workouts.observeHistory(),
        runner.state,
    ) { programs, history, run ->
        CardioTabState(
            loading = false,
            programs = programs,
            recent = history.filter { it.workout.type == WorkoutType.CARDIO || it.workout.type == WorkoutType.INTERVAL }.take(5),
            run = run,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardioTabState())

    fun start(program: IntervalProgram) = runner.startInterval(program)
    fun startCardio(type: CardioType, title: String) = runner.startCardio(type, title)
    fun copy(id: Long, suffix: String) = viewModelScope.launch { repo.copyProgram(id, suffix) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteProgram(id) }
    fun saveTemplate(program: IntervalProgram, onSaved: (Long) -> Unit) = viewModelScope.launch {
        onSaved(repo.saveProgram(program))
    }
}

/** Вкладка «Кардио»: запись кардио, секундомер, интервальные программы и шаблоны. */
@Composable
fun CardioTab(navigate: (String) -> Unit, viewModel: CardioTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val res = LocalContext.current.resources
    var pickStopwatch by remember { mutableStateOf(false) }
    var deleteProgram by remember { mutableStateOf<IntervalProgram?>(null) }
    var busy by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.workouts_copy_suffix)
    val run = state.run
    val running = run != null && !run.finished

    fun startGuarded(block: () -> Unit) {
        if (running) busy = true else {
            block()
            navigate(Routes.INTERVAL_RUN)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (run != null) {
            item(key = "run") { RunBanner(run) { navigate(Routes.INTERVAL_RUN) } }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    stringResource(R.string.cardio_stopwatch),
                    { pickStopwatch = true },
                    icon = IronIcons.Timer,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    stringResource(R.string.cardio_log),
                    { navigate(Routes.cardioLog()) },
                    icon = IronIcons.Edit,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
        }
        item(key = "programs_title") {
            SectionTitle(stringResource(R.string.intervals_my)) {
                GhostButton(stringResource(R.string.intervals_new), { navigate(Routes.intervalEdit()) })
            }
        }
        if (!state.loading && state.programs.isEmpty()) {
            item(key = "programs_empty") {
                Text(stringResource(R.string.intervals_empty), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            }
        }
        items(state.programs, key = { "p" + it.id }) { p ->
            ProgramCard(
                title = p.name,
                subtitle = programSubtitle(p),
                onOpen = { navigate(Routes.intervalEdit(p.id)) },
                onStart = { startGuarded { viewModel.start(p) } },
                menu = { close ->
                    IronMenuItem(stringResource(R.string.action_edit), { close(); navigate(Routes.intervalEdit(p.id)) }, IronIcons.Edit)
                    IronMenuItem(stringResource(R.string.action_copy), { close(); viewModel.copy(p.id, copySuffix) }, IronIcons.Copy)
                    IronMenuItem(stringResource(R.string.action_delete), { close(); deleteProgram = p }, IronIcons.Delete, color = Iron.colors.error)
                },
            )
        }
        item(key = "templates_title") { SectionTitle(stringResource(R.string.intervals_templates)) }
        items(IntervalTemplates.all, key = { "t" + it.key }) { t ->
            val program = remember(t.key) { t.toProgram(res) }
            ProgramCard(
                title = stringResource(t.name),
                subtitle = stringResource(t.description) + " · " + Fmt.durationWords(program.totalSeconds.toLong()),
                onOpen = null,
                onStart = { startGuarded { viewModel.start(program) } },
                menu = { close ->
                    IronMenuItem(stringResource(R.string.intervals_to_mine), {
                        close()
                        viewModel.saveTemplate(program) { id -> navigate(Routes.intervalEdit(id)) }
                    }, IronIcons.Copy)
                },
            )
        }
        item(key = "recent_title") {
            SectionTitle(stringResource(R.string.cardio_recent)) {
                GhostButton(stringResource(R.string.workouts_history_all), { navigate(Routes.HISTORY) })
            }
        }
        if (!state.loading && state.recent.isEmpty()) {
            item(key = "recent_empty") {
                Text(stringResource(R.string.workouts_no_history), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            }
        }
        items(state.recent, key = { "w" + it.workout.id }) { row ->
            WorkoutRow(row, onClick = { navigate(Routes.workoutDetail(row.workout.id)) })
        }
    }

    if (pickStopwatch) {
        IronBottomSheet(title = stringResource(R.string.cardio_pick_type), onDismiss = { pickStopwatch = false }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioType.entries.forEach { type ->
                    val label = stringResource(type.label)
                    IronChip(label, selected = false, icon = type.icon, onClick = {
                        pickStopwatch = false
                        startGuarded { viewModel.startCardio(type, label) }
                    })
                }
            }
        }
    }
    deleteProgram?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.intervals_delete_title),
            text = p.name,
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.delete(p.id); deleteProgram = null },
            onDismiss = { deleteProgram = null },
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

@Composable
private fun programSubtitle(p: IntervalProgram): String {
    val phases = Intervals.expand(p.blocks).size
    return Fmt.durationWords(p.totalSeconds.toLong()) + " · " + pluralStringResource(R.plurals.blocks, phases, phases)
}

@Composable
private fun RunBanner(run: RunState, onOpen: () -> Unit) {
    IronCard(onClick = onOpen, borderColor = Iron.colors.accent, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (run.finished) R.string.run_finished else if (run.paused) R.string.run_paused else R.string.run_active).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.accentText,
                )
                Text(run.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PrimaryButton(stringResource(R.string.action_open), onOpen, modifier = Modifier.height(44.dp))
        }
    }
}

@Composable
private fun ProgramCard(
    title: String,
    subtitle: String,
    onOpen: (() -> Unit)?,
    onStart: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IronCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
