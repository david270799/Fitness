package com.iron.fitness.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.domain.Insight
import com.iron.fitness.core.domain.InsightKey
import com.iron.fitness.core.domain.InsightKind
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.ChartPoint
import com.iron.fitness.core.ui.charts.LineChart
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyMetric
import com.iron.fitness.feature.body.data.latestAndMonthAgo
import com.iron.fitness.feature.body.ui.BodyViewModel
import com.iron.fitness.feature.body.ui.metricLabel
import com.iron.fitness.navigation.Routes
import java.io.File
import java.time.LocalDate

private val GRID = listOf(BodyMetric.WAIST, BodyMetric.CHEST, BodyMetric.HIPS, BodyMetric.ARM, BodyMetric.THIGH, BodyMetric.BODY_FAT)

@Composable
fun BodyScreen(navigate: (String) -> Unit, viewModel: BodyViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var goalDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    IronScaffold(
        title = stringResource(R.string.body_title),
        actions = { IronIconButton(IronIcons.Add, stringResource(R.string.body_add_measurement), { navigate(Routes.measurementEdit()) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!s.loading && s.measurements.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(R.string.body_empty),
                        icon = IronIcons.Scale,
                        action = { PrimaryButton(stringResource(R.string.body_add_measurement), { navigate(Routes.measurementEdit()) }, icon = IronIcons.Add) },
                    )
                }
            }
            item(key = "weight") {
                val (now, before) = latestAndMonthAgo(s.measurements, BodyMetric.WEIGHT, today)
                val points = s.measurements.filter { it.weightKg != null && it.day >= today - 180 }
                    .groupBy { it.day }.map { (day, list) -> ChartPoint(day.toDouble(), list.first().weightKg!!, Fmt.dateCompact(LocalDate.ofEpochDay(day))) }
                IronCard(onClick = { navigate(Routes.bodyMetric(BodyMetric.WEIGHT.name)) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.body_weight).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(now?.let { Fmt.num(it, 1) } ?: "—", style = Iron.numbers.large)
                                Text(" " + stringResource(R.string.unit_kg), style = MaterialTheme.typography.labelLarge, color = Iron.colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            if (now != null && before != null) {
                                Text(stringResource(R.string.body_delta_month, Fmt.signed(now - before)), style = Iron.numbers.tiny, color = Iron.colors.textSecondary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.body_goal).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                            Text(s.weightGoal?.let { Fmt.num(it, 1) + " " + stringResource(R.string.unit_kg) } ?: stringResource(R.string.not_set), style = Iron.numbers.small)
                            if (now != null && s.weightGoal != null) {
                                Text(stringResource(R.string.body_to_goal, Fmt.signed(s.weightGoal!! - now)), style = Iron.numbers.tiny, color = Iron.colors.accentText)
                            }
                            GhostButton(stringResource(R.string.action_edit), { goalDialog = true })
                        }
                    }
                    if (points.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        LineChart(points, formatY = { Fmt.num(it, 1) }, goal = s.weightGoal, height = 150.dp)
                    }
                }
            }
            item(key = "grid") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GRID.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m ->
                                val (now, before) = latestAndMonthAgo(s.measurements, m, today)
                                IronCard(
                                    onClick = { navigate(Routes.bodyMetric(m.name)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(12.dp),
                                ) {
                                    Text(metricLabel(m).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1)
                                    Text(now?.let { Fmt.num(it, 1) } ?: "—", style = Iron.numbers.medium)
                                    Text(
                                        if (now != null && before != null) stringResource(R.string.body_delta_month, Fmt.signed(now - before)) else " ",
                                        style = Iron.numbers.tiny,
                                        color = Iron.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "photos_title") {
                SectionTitle(stringResource(R.string.body_photos)) {
                    GhostButton(stringResource(R.string.body_photos_all), { navigate(Routes.PROGRESS_PHOTOS) })
                }
            }
            item(key = "photos") {
                if (s.photos.isEmpty()) {
                    IronCard(onClick = { navigate(Routes.PROGRESS_PHOTOS) }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IronIcon(IronIcons.Camera, null, tint = Iron.colors.textSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.body_photos_empty), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.photos.take(10), key = { it.id }) { p ->
                            Column {
                                AsyncImage(
                                    model = File(p.path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(4.dp)),
                                )
                                Text(Fmt.dateCompact(LocalDate.ofEpochDay(p.day)), style = Iron.numbers.tiny, color = Iron.colors.textSecondary)
                            }
                        }
                    }
                }
            }
            item(key = "inbody") { InBodyCard(navigate) }
            if (s.insights.isNotEmpty()) {
                item(key = "insights") { InsightsCard(s.insights) }
            }
            item(key = "history") {
                GhostButton(stringResource(R.string.body_history), { navigate(Routes.bodyMetric(BodyMetric.WEIGHT.name)) })
            }
            item(key = "disclaimer") {
                Text(stringResource(R.string.body_disclaimer), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
        }
    }
    if (goalDialog) {
        TextInputDialog(
            title = stringResource(R.string.body_goal_title),
            initial = s.weightGoal?.let { Fmt.num(it, 1) }.orEmpty(),
            label = stringResource(R.string.body_goal_label),
            keyboardType = KeyboardType.Decimal,
            supportingText = stringResource(R.string.body_goal_hint),
            validate = { it.isBlank() || (Fmt.parse(it) ?: 0.0) > 20 },
            onConfirm = { viewModel.setGoal(Fmt.parse(it)); goalDialog = false },
            onDismiss = { goalDialog = false },
        )
    }
}

/** Вход в раздел InBody. */
@Composable
private fun InBodyCard(navigate: (String) -> Unit) {
    IronCard(onClick = { navigate(Routes.INBODY) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIcon(IronIcons.Scan, null, tint = Iron.colors.accentText)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_f_inbody), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ai_f_inbody_sub), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
            IronIcon(IronIcons.ChevronRight, null, tint = Iron.colors.textSecondary)
        }
    }
}

@Composable
private fun InsightsCard(insights: List<Insight>) {
    IronCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.body_insights).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        val strengths = insights.filter { it.kind == InsightKind.STRENGTH }
        val weak = insights.filter { it.kind == InsightKind.WEAKNESS }
        if (strengths.isNotEmpty()) {
            Text(stringResource(R.string.body_strengths), style = MaterialTheme.typography.titleSmall, color = Iron.colors.success, modifier = Modifier.padding(top = 8.dp))
            strengths.forEach { InsightLine(it) }
        }
        if (weak.isNotEmpty()) {
            Text(stringResource(R.string.body_weaknesses), style = MaterialTheme.typography.titleSmall, color = Iron.colors.warning, modifier = Modifier.padding(top = 8.dp))
            weak.forEach { InsightLine(it) }
        }
        Text(stringResource(R.string.body_insights_note), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun InsightLine(i: Insight) {
    val v = i.value
    val text = when (i.key) {
        InsightKey.FREQ_GOOD -> stringResource(R.string.ins_freq_good, Fmt.num(v ?: 0.0, 1))
        InsightKey.FREQ_LOW -> stringResource(R.string.ins_freq_low, Fmt.num(v ?: 0.0, 1))
        InsightKey.PULL_LAGGING -> stringResource(R.string.ins_pull_lagging, Fmt.num(v ?: 0.0, 1))
        InsightKey.PUSH_PULL_BALANCED -> stringResource(R.string.ins_push_pull_ok)
        InsightKey.LOWER_LAGGING -> stringResource(R.string.ins_lower_lagging, Fmt.num(v ?: 0.0, 0))
        InsightKey.UPPER_LOWER_BALANCED -> stringResource(R.string.ins_upper_lower_ok)
        InsightKey.PR_PROGRESS -> stringResource(R.string.ins_pr, (v ?: 0.0).toInt())
        InsightKey.NO_PR -> stringResource(R.string.ins_no_pr)
        InsightKey.CARDIO_GOOD -> stringResource(R.string.ins_cardio_good, (v ?: 0.0).toInt())
        InsightKey.CARDIO_NONE -> stringResource(R.string.ins_cardio_none)
        InsightKey.STRETCH_GOOD -> stringResource(R.string.ins_stretch_good, (v ?: 0.0).toInt())
        InsightKey.STRETCH_NONE -> stringResource(R.string.ins_stretch_none)
        InsightKey.WEIGHT_TOWARDS_GOAL -> stringResource(R.string.ins_weight_towards, Fmt.signed(v ?: 0.0))
        InsightKey.WEIGHT_AWAY_FROM_GOAL -> stringResource(R.string.ins_weight_away, Fmt.signed(v ?: 0.0))
        InsightKey.WAIST_DOWN -> stringResource(R.string.ins_waist_down, Fmt.signed(v ?: 0.0))
        InsightKey.WAIST_UP -> stringResource(R.string.ins_waist_up, Fmt.signed(v ?: 0.0))
    }
    Row(Modifier.padding(top = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
