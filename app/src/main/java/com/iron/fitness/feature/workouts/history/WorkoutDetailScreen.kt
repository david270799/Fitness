package com.iron.fitness.feature.workouts.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.Tag
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSummary
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.feature.workouts.data.WorkoutWithExercises
import com.iron.fitness.feature.workouts.data.isRecord
import com.iron.fitness.feature.workouts.session.setText
import com.iron.fitness.feature.workouts.summary.SummaryGrid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutDetailState(
    val loading: Boolean = true,
    val full: WorkoutWithExercises? = null,
    val summary: WorkoutSummary? = null,
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val workoutId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val state: StateFlow<WorkoutDetailState> = repo.observeFull(workoutId).mapLatest { full ->
        if (full == null) return@mapLatest WorkoutDetailState(loading = false)
        val ids = full.exercises.map { it.exercise.exerciseId }.distinct()
        WorkoutDetailState(
            loading = false,
            full = full,
            summary = repo.summary(workoutId),
            exercises = exerciseRepo.getAll(ids).associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(workoutId)
            onDone()
        }
    }

    fun saveAsRoutine(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.saveWorkoutAsRoutine(workoutId)
            onDone()
        }
    }
}

@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditCardio: (Long) -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.summary_routine_saved)
    val full = state.full
    LaunchedEffect(state.loading, full == null) {
        if (!state.loading && full == null && !leaving) {
            leaving = true
            onBack()
        }
    }
    IronScaffold(
        title = full?.workout?.name ?: stringResource(R.string.workout_detail_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (full != null && full.workout.type == WorkoutType.STRENGTH) {
                IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), { onEdit(full.workout.id) })
            }
            if (full != null && full.workout.type == WorkoutType.CARDIO) {
                IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), { onEditCardio(full.workout.id) })
            }
            Box {
                IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
                IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                    if (full?.workout?.type == WorkoutType.STRENGTH) {
                        IronMenuItem(stringResource(R.string.summary_save_routine), {
                            menu = false
                            viewModel.saveAsRoutine { scope.launch { snackbar.showSnackbar(savedText) } }
                        }, IronIcons.Save)
                    }
                    IronMenuItem(stringResource(R.string.action_delete), { menu = false; confirmDelete = true }, IronIcons.Delete, color = Iron.colors.error)
                }
            }
        },
    ) { padding ->
        if (full == null) return@IronScaffold
        val w = full.workout
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "date") {
                Column {
                    Text(
                        Fmt.dayOfWeek(Fmt.toLocalDate(w.startedAt)) + ", " + Fmt.dateTime(w.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Iron.colors.textSecondary,
                    )
                    w.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (w.type == WorkoutType.STRENGTH) {
                state.summary?.let { s -> item(key = "grid") { SummaryGrid(s) } }
            } else {
                item(key = "cardio_grid") { CardioGrid(w) }
            }
            items(full.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { item ->
                val ex = state.exercises[item.exercise.exerciseId]
                val recordType = ex?.recordType ?: RecordType.WEIGHT_REPS
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        ex?.name ?: stringResource(R.string.session_unknown_exercise),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(enabled = ex != null) { onOpenExercise(item.exercise.exerciseId) },
                    )
                    item.exercise.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary) }
                    Spacer(Modifier.height(8.dp))
                    var number = 0
                    item.sets.sortedBy { it.position }.forEach { s ->
                        if (s.setType != SetType.WARMUP) number++
                        val label = when (s.setType) {
                            SetType.WARMUP -> stringResource(R.string.session_set_type_warmup_short)
                            SetType.DROP -> stringResource(R.string.session_set_type_drop_short)
                            SetType.FAILURE -> stringResource(R.string.session_set_type_failure_short)
                            SetType.NORMAL -> number.toString()
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = Iron.numbers.small, color = Iron.colors.textSecondary, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            Text(setText(s, recordType), style = Iron.numbers.small, modifier = Modifier.weight(1f))
                            if (s.isRecord) Tag(stringResource(R.string.session_record))
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.workout_delete_title),
            text = stringResource(R.string.workout_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                leaving = true
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Плитки для кардио и интервалов: время, дистанция, темп, калории. */
@Composable
private fun CardioGrid(w: com.iron.fitness.feature.workouts.data.WorkoutEntity) {
    val type = com.iron.fitness.feature.cardio.data.CardioType.of(w.cardioType)
    val tiles = buildList<Pair<String, String>> {
        add(stringResource(R.string.summary_duration) to Fmt.duration(w.durationSec))
        w.distanceKm?.let { add(stringResource(R.string.cardio_distance) to Fmt.num(it, 2)) }
        com.iron.fitness.feature.cardio.ui.paceText(type, w.durationSec, w.distanceKm)?.let { add(stringResource(R.string.cardio_pace_title) to it) }
        w.caloriesKcal?.let { add(stringResource(R.string.summary_calories) to "≈ " + it.toInt()) }
        w.intensity?.let { name ->
            runCatching { com.iron.fitness.feature.cardio.data.Intensity.valueOf(name) }.getOrNull()?.let {
                add(stringResource(R.string.cardio_intensity) to stringResource(it.label))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    IronCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        Text(value, style = Iron.numbers.medium, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
