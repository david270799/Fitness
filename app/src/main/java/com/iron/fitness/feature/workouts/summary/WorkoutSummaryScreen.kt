package com.iron.fitness.feature.workouts.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.stretching.data.StretchLauncher
import com.iron.fitness.feature.workouts.data.RecordItem
import com.iron.fitness.feature.workouts.data.RecordKind
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val stretch: StretchLauncher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val workoutId: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch { _summary.value = repo.summary(workoutId) }
    }

    /** Заминка, подобранная под мышцы этой тренировки. */
    fun startStretch(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(stretch.startAfterWorkout(workoutId)) }
    }

    fun saveAsRoutine(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.saveWorkoutAsRoutine(workoutId)
            onDone()
        }
    }
}

@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    onStretchStarted: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.summary_routine_saved)
    val busyText = stringResource(R.string.run_busy_text)
    IronScaffold(title = stringResource(R.string.summary_title), onBack = onDone, snackbarHostState = snackbar) { padding ->
        val s = summary ?: return@IronScaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.summary_done).uppercase(), style = MaterialTheme.typography.labelLarge, color = Iron.colors.accentText)
            Text(s.name, style = MaterialTheme.typography.displaySmall)
            SummaryGrid(s)
            SectionTitle(stringResource(R.string.summary_records))
            if (s.records.isEmpty()) {
                Text(stringResource(R.string.summary_no_records), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            } else {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.records.forEach { RecordLine(it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                stringResource(R.string.summary_stretch_after),
                {
                    viewModel.startStretch { ok ->
                        if (ok) onStretchStarted() else scope.launch { snackbar.showSnackbar(busyText) }
                    }
                },
                icon = IronIcons.Stretch,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(R.string.summary_save_routine),
                { viewModel.saveAsRoutine { scope.launch { snackbar.showSnackbar(savedText) } } },
                icon = IronIcons.Save,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(stringResource(R.string.action_done), onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Плитки итогов: длительность, объём, подходы, упражнения, калории, отдых. */
@Composable
fun SummaryGrid(s: WorkoutSummary) {
    val tiles = buildList<Triple<String, String, String?>> {
        add(Triple(stringResource(R.string.summary_duration), Fmt.duration(s.durationSec), null))
        add(Triple(stringResource(R.string.summary_volume), Fmt.grouped(s.volumeKg), stringResource(R.string.unit_kg)))
        add(Triple(stringResource(R.string.summary_sets), s.setCount.toString(), null))
        add(Triple(stringResource(R.string.summary_exercises), s.exerciseCount.toString(), null))
        add(Triple(stringResource(R.string.summary_calories), "≈ " + s.caloriesKcal.toInt(), stringResource(R.string.unit_kcal)))
        add(Triple(stringResource(R.string.summary_avg_rest), s.averageRestSec?.let { Fmt.duration(it) } ?: "—", null))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, unit) ->
                    IronCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, style = Iron.numbers.medium, maxLines = 1)
                            if (unit != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(unit, style = MaterialTheme.typography.labelMedium, color = Iron.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.summary_calories_note), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
    }
}

@Composable
private fun RecordLine(r: RecordItem) {
    val kind = stringResource(
        when (r.kind) {
            RecordKind.WEIGHT -> R.string.record_kind_weight
            RecordKind.REPS -> R.string.record_kind_reps
            RecordKind.ONE_RM -> R.string.record_kind_one_rm
            RecordKind.DURATION -> R.string.record_kind_duration
        },
    )
    val value = when {
        r.kind == RecordKind.DURATION -> Fmt.duration(r.durationSec ?: 0)
        r.weightKg != null && r.weightKg > 0 -> stringResource(R.string.set_weight_reps, Fmt.num(r.weightKg), r.reps ?: 0)
        else -> stringResource(R.string.set_reps_only, r.reps ?: 0)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IronIcon(IronIcons.Trophy, null, tint = Iron.colors.accentText, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.exerciseName, style = MaterialTheme.typography.bodyLarge)
            Text(kind, style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        }
        Text(value, style = Iron.numbers.small)
    }
}
