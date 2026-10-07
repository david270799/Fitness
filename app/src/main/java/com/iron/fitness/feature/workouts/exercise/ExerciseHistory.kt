package com.iron.fitness.feature.workouts.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.OneRepMax
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.ChartPoint
import com.iron.fitness.core.ui.charts.LineChart
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.workouts.data.SetWithDate
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSetEntity
import com.iron.fitness.feature.workouts.data.isRecord
import com.iron.fitness.feature.workouts.session.setText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** Одна тренировка с упражнением. */
data class ExerciseSession(
    val workoutId: Long,
    val date: LocalDate,
    val startedAt: Long,
    val workoutName: String,
    val sets: List<WorkoutSetEntity>,
    /** Значение для графика: лучший 1ПМ, повторы или время. */
    val metric: Double,
    val hasRecord: Boolean,
)

data class ExerciseHistoryState(
    val loading: Boolean = true,
    val recordType: RecordType = RecordType.WEIGHT_REPS,
    val bestWeight: WorkoutSetEntity? = null,
    val bestOneRm: Pair<WorkoutSetEntity, Double>? = null,
    val bestReps: WorkoutSetEntity? = null,
    val bestDuration: WorkoutSetEntity? = null,
    val sessions: List<ExerciseSession> = emptyList(),
)

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    repo: WorkoutRepository,
    exercises: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedStateHandle.get<String>("id"))

    val state: StateFlow<ExerciseHistoryState> = combine(
        repo.observeCompletedSetsForExercise(exerciseId),
        exercises.observe(exerciseId),
    ) { rows, exercise ->
        build(rows, exercise?.recordType ?: RecordType.WEIGHT_REPS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryState())

    private fun build(rows: List<SetWithDate>, type: RecordType): ExerciseHistoryState {
        val working = rows.filter { it.set.setType != SetType.WARMUP }
        val sessions = rows.groupBy { it.set.workoutId }.map { (id, list) ->
            val sets = list.map { it.set }
            val work = sets.filter { it.setType != SetType.WARMUP }
            val metric = when (type) {
                RecordType.WEIGHT_REPS -> work.maxOfOrNull { OneRepMax.epley(it.weightKg ?: 0.0, it.reps ?: 0) } ?: 0.0
                RecordType.REPS -> (work.maxOfOrNull { it.reps ?: 0 } ?: 0).toDouble()
                RecordType.TIME -> (work.maxOfOrNull { it.durationSec ?: 0 } ?: 0).toDouble()
            }
            ExerciseSession(
                workoutId = id,
                date = Fmt.toLocalDate(list.first().startedAt),
                startedAt = list.first().startedAt,
                workoutName = list.first().workoutName,
                sets = sets,
                metric = metric,
                hasRecord = sets.any { it.isRecord },
            )
        }.sortedByDescending { it.startedAt }
        val bestOneRm = working.map { it.set }
            .filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            .map { it to OneRepMax.epley(it.weightKg!!, it.reps!!) }
            .maxByOrNull { it.second }
        return ExerciseHistoryState(
            loading = false,
            recordType = type,
            bestWeight = working.map { it.set }.filter { (it.weightKg ?: 0.0) > 0 }.maxWithOrNull(
                compareBy<WorkoutSetEntity> { it.weightKg ?: 0.0 }.thenBy { it.reps ?: 0 },
            ),
            bestOneRm = bestOneRm,
            bestReps = working.map { it.set }.maxByOrNull { it.reps ?: 0 }?.takeIf { (it.reps ?: 0) > 0 },
            bestDuration = working.map { it.set }.maxByOrNull { it.durationSec ?: 0 }?.takeIf { (it.durationSec ?: 0) > 0 },
            sessions = sessions,
        )
    }
}

/** История и рекорды в карточке упражнения. */
@Composable
fun ExerciseHistoryContent(
    onOpenWorkout: (Long) -> Unit,
    viewModel: ExerciseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) return
    if (state.sessions.isEmpty()) {
        EmptyState(stringResource(R.string.exercise_no_history), icon = IronIcons.History)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BestsRow(state)
        val points = state.sessions.filter { it.metric > 0 }.reversed().map {
            ChartPoint(it.date.toEpochDay().toDouble(), it.metric, Fmt.dateCompact(it.date))
        }
        val chartTitle = stringResource(
            when (state.recordType) {
                RecordType.WEIGHT_REPS -> R.string.exhist_chart_1rm
                RecordType.REPS -> R.string.exhist_chart_reps
                RecordType.TIME -> R.string.exhist_chart_duration
            },
        )
        IronCard(modifier = Modifier.fillMaxWidth()) {
            Text(chartTitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            if (points.size < 2) {
                Text(stringResource(R.string.exhist_chart_need_more), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            } else {
                LineChart(
                    points = points,
                    formatY = { if (state.recordType == RecordType.TIME) Fmt.duration(it.toLong()) else Fmt.num(it, 1) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        SectionTitle(stringResource(R.string.exhist_recent))
        state.sessions.take(10).forEach { session ->
            IronCard(
                onClick = { onOpenWorkout(session.workoutId) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Fmt.date(session.date) + " · " + session.workoutName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Iron.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            session.sets.joinToString(", ") { setText(it, state.recordType) },
                            style = Iron.numbers.tiny,
                        )
                    }
                    if (session.hasRecord) {
                        Spacer(Modifier.width(8.dp))
                        IronIcon(IronIcons.Trophy, null, tint = Iron.colors.accentText, size = 18.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BestsRow(state: ExerciseHistoryState) {
    val tiles = buildList<Pair<String, String>> {
        when (state.recordType) {
            RecordType.WEIGHT_REPS -> {
                state.bestWeight?.let { add(stringResource(R.string.exhist_best_weight) to "${Fmt.num(it.weightKg ?: 0.0)}×${it.reps ?: 0}") }
                state.bestOneRm?.let { add(stringResource(R.string.exhist_best_1rm) to Fmt.num(it.second, 1)) }
            }
            RecordType.REPS -> state.bestReps?.let { add(stringResource(R.string.exhist_best_reps) to (it.reps ?: 0).toString()) }
            RecordType.TIME -> state.bestDuration?.let { add(stringResource(R.string.exhist_best_duration) to Fmt.duration(it.durationSec ?: 0)) }
        }
        add(stringResource(R.string.exhist_sessions) to state.sessions.size.toString())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        tiles.forEach { (label, value) ->
            IronCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = Iron.numbers.medium, maxLines = 1)
            }
        }
    }
}
