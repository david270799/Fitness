package com.iron.fitness.feature.body.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.ChartPoint
import com.iron.fitness.core.ui.charts.LineChart
import com.iron.fitness.core.ui.components.ChipRow
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyMetric
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.MeasurementEntity
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BodyMetricViewModel @Inject constructor(
    repo: BodyRepository,
    settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val initial: BodyMetric = savedStateHandle.get<String>("metric")?.let { runCatching { BodyMetric.valueOf(it) }.getOrNull() } ?: BodyMetric.WEIGHT
    val measurements: StateFlow<List<MeasurementEntity>> = repo.observeMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goal: StateFlow<Double?> = settings.settings.map { it.weightGoalKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BodyMetricScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: BodyMetricViewModel = hiltViewModel()) {
    val list by viewModel.measurements.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(viewModel.initial) }
    val withValue = list.filter { metric.valueOf(it) != null }
    IronScaffold(
        title = stringResource(R.string.body_history),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Add, stringResource(R.string.body_add_measurement), { navigate(Routes.measurementEdit()) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChipRow(
                items = BodyMetric.entries,
                selected = metric,
                label = { metricLabel(it) },
                onSelect = { metric = it },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "chart") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text((metricLabel(metric) + ", " + metricUnit(metric)).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        val points = withValue.groupBy { it.day }
                            .map { (day, ms) -> ChartPoint(day.toDouble(), metric.valueOf(ms.first())!!, Fmt.dateCompact(LocalDate.ofEpochDay(day))) }
                        if (points.size >= 2) {
                            LineChart(points, formatY = { Fmt.num(it, 1) }, goal = if (metric == BodyMetric.WEIGHT) goal else null, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text(stringResource(R.string.body_chart_need_more), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                items(withValue, key = { it.id }) { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { navigate(Routes.measurementEdit(m.id)) },
                    ) {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.dateFull(LocalDate.ofEpochDay(m.day)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Fmt.num(metric.valueOf(m) ?: 0.0, 1) + " " + metricUnit(metric), style = Iron.numbers.small)
                        }
                        IronDivider()
                    }
                }
            }
        }
    }
}
