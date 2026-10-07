package com.iron.fitness.feature.stats

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.iron.fitness.core.domain.Granularity
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.domain.StatsMath
import com.iron.fitness.core.domain.StatsPeriod
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.BarChart
import com.iron.fitness.core.ui.charts.BarPoint
import com.iron.fitness.core.ui.components.FlatProgressBar
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.model.Muscle
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.feature.workouts.data.isRecord
import com.iron.fitness.feature.workouts.history.workoutTypeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Сводка за период. */
data class StatsState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val workouts: Int = 0,
    val durationSec: Long = 0,
    val kcal: Double = 0.0,
    val volumeKg: Double = 0.0,
    val distanceKm: Double = 0.0,
    val records: Int = 0,
    val countBars: List<BarPoint> = emptyList(),
    val volumeBars: List<BarPoint> = emptyList(),
    val kcalBars: List<BarPoint> = emptyList(),
    val byType: List<Pair<WorkoutType, Int>> = emptyList(),
    /** Упражнение → (подходов, объём). */
    val topExercises: List<Triple<String, Int, Double>> = emptyList(),
    /** Мышца → рабочих подходов. */
    val muscles: List<Pair<Muscle, Int>> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
    private val exporter: CsvExporter,
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.MONTH)
    private val since = LocalDate.now().minusYears(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val shareEvents = Channel<Intent>(Channel.BUFFERED)
    val share: Flow<Intent> = shareEvents.receiveAsFlow()

    val state: StateFlow<StatsState> = combine(
        period,
        workouts.observeFinishedSince(since),
        workouts.observeCompletedSetsSince(since),
        exercises.observeAll(),
    ) { p, ws, sets, exList ->
        val today = LocalDate.now()
        val start = StatsMath.periodStart(p, today)
        val inPeriod = ws.filter { !Fmt.toLocalDate(it.startedAt).isBefore(start) }
        val setsIn = sets.filter { !Fmt.toLocalDate(it.startedAt).isBefore(start) }
        val exMap = exList.associateBy { it.id }
        fun label(d: LocalDate): String = when (p.granularity) {
            Granularity.DAY -> Fmt.dayOfWeekShort(d.dayOfWeek)
            Granularity.WEEK -> "${d.dayOfMonth}.${"%02d".format(d.monthValue)}"
            Granularity.MONTH -> d.month.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, com.iron.fitness.core.util.RU).take(3)
        }
        fun bars(valueOf: (com.iron.fitness.feature.workouts.data.WorkoutEntity) -> Double) =
            StatsMath.aggregate(inPeriod, { Fmt.toLocalDate(it.startedAt) }, valueOf, p, today).map { (d, v) -> BarPoint(label(d), v) }

        val working = setsIn.filter { it.set.setType != SetType.WARMUP }
        val top = working.groupBy { it.set.exerciseId }
            .map { (id, list) -> Triple(exMap[id]?.name ?: id, list.size, list.sumOf { (it.set.weightKg ?: 0.0) * (it.set.reps ?: 0) }) }
            .sortedByDescending { it.second }
            .take(6)
        val muscleSets = working.flatMap { row -> exMap[row.set.exerciseId]?.primaryMuscles.orEmpty() }
            .mapNotNull { Muscle.of(it) }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }

        StatsState(
            loading = false,
            period = p,
            workouts = inPeriod.size,
            durationSec = inPeriod.sumOf { it.durationSec },
            kcal = inPeriod.sumOf { it.caloriesKcal ?: 0.0 },
            volumeKg = inPeriod.sumOf { it.volumeKg },
            distanceKm = inPeriod.sumOf { it.distanceKm ?: 0.0 },
            records = setsIn.count { it.set.isRecord },
            countBars = bars { 1.0 },
            volumeBars = bars { it.volumeKg },
            kcalBars = bars { it.caloriesKcal ?: 0.0 },
            byType = inPeriod.groupingBy { it.type }.eachCount().toList().sortedByDescending { it.second },
            topExercises = top,
            muscles = muscleSets,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setPeriod(p: StatsPeriod) { period.value = p }

    fun export() = viewModelScope.launch {
        val uris = exporter.export()
        shareEvents.send(exporter.shareIntent(uris))
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.share.collect { intent -> runCatching { context.startActivity(intent) } }
    }
    IronScaffold(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Share, stringResource(R.string.csv_share), { viewModel.export() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "period") {
                Segmented(
                    items = StatsPeriod.entries,
                    selected = s.period,
                    label = {
                        stringResource(
                            when (it) {
                                StatsPeriod.WEEK -> R.string.stats_week
                                StatsPeriod.MONTH -> R.string.stats_month
                                StatsPeriod.QUARTER -> R.string.stats_quarter
                                StatsPeriod.YEAR -> R.string.stats_year
                            },
                        )
                    },
                    onSelect = viewModel::setPeriod,
                )
            }
            item(key = "tiles") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(stringResource(R.string.stats_workouts), s.workouts.toString(), Modifier.weight(1f))
                        Tile(stringResource(R.string.summary_duration), Fmt.durationWords(s.durationSec), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(stringResource(R.string.summary_calories), "≈ " + Fmt.grouped(s.kcal), Modifier.weight(1f))
                        Tile(stringResource(R.string.summary_volume), Fmt.grouped(s.volumeKg) + " " + stringResource(R.string.unit_kg), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(stringResource(R.string.stats_distance), Fmt.num(s.distanceKm, 1) + " " + stringResource(R.string.unit_km), Modifier.weight(1f))
                        Tile(stringResource(R.string.summary_records), s.records.toString(), Modifier.weight(1f))
                    }
                }
            }
            item(key = "count") { ChartCard(stringResource(R.string.stats_chart_workouts), s.countBars) }
            item(key = "volume") { ChartCard(stringResource(R.string.stats_chart_volume), s.volumeBars) { Fmt.grouped(it) } }
            item(key = "kcal") { ChartCard(stringResource(R.string.stats_chart_kcal), s.kcalBars) { Fmt.grouped(it) } }
            if (s.byType.isNotEmpty()) {
                item(key = "types") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.stats_by_type).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        val total = s.byType.sumOf { it.second }.coerceAtLeast(1)
                        s.byType.forEach { (type, n) ->
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(workoutTypeLabel(type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                                FlatProgressBar(n.toFloat() / total, modifier = Modifier.weight(1f), height = 8.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(n.toString(), style = Iron.numbers.tiny)
                            }
                        }
                    }
                }
            }
            if (s.muscles.isNotEmpty()) {
                item(key = "muscles") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.stats_muscles).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        val max = s.muscles.maxOf { it.second }.coerceAtLeast(1)
                        s.muscles.forEach { (m, n) ->
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(m.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                FlatProgressBar(n.toFloat() / max, modifier = Modifier.weight(1f), height = 8.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(n.toString(), style = Iron.numbers.tiny)
                            }
                        }
                        Text(stringResource(R.string.stats_muscles_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (s.topExercises.isNotEmpty()) {
                item(key = "top_title") { SectionTitle(stringResource(R.string.stats_top_exercises)) }
                item(key = "top") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        s.topExercises.forEach { (name, sets, volume) ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.stats_sets_volume, sets, Fmt.grouped(volume)),
                                    style = Iron.numbers.tiny,
                                    color = Iron.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "csv") {
                Text(stringResource(R.string.csv_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    IronCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Iron.numbers.small, maxLines = 1)
    }
}

@Composable
private fun ChartCard(title: String, bars: List<BarPoint>, format: (Double) -> String = { it.toInt().toString() }) {
    IronCard(modifier = Modifier.fillMaxWidth()) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        BarChart(bars, formatValue = format)
    }
}
