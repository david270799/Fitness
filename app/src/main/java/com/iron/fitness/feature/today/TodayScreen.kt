package com.iron.fitness.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.ActivityHeatmap
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.feature.daily.ui.ChallengeCard
import com.iron.fitness.feature.reminders.ui.DoseStatusChip
import com.iron.fitness.feature.stretching.ui.MarkStretchSheet
import com.iron.fitness.feature.stretching.ui.stretchPhaseLabel
import com.iron.fitness.feature.workouts.ActiveWorkoutBanner
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.iron.fitness.navigation.Routes

@Composable
fun TodayScreen(
    navigate: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.workouts_default_name)
    val stretchName = stringResource(R.string.stretch_default_name)
    var markSheet by remember { mutableStateOf(false) }
    if (markSheet) {
        MarkStretchSheet(
            onSave = { phase, minutes -> viewModel.markStretch(stretchName, phase, minutes); markSheet = false },
            onDismiss = { markSheet = false },
            initialPhase = if (state.strengthTodayId != null) StretchPhase.AFTER else StretchPhase.ANY,
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(top = 16.dp)) {
            Text(
                Fmt.dayOfWeek(state.today).uppercase() + " · " + Fmt.date(state.today),
                style = MaterialTheme.typography.labelLarge,
                color = Iron.colors.textSecondary,
            )
            Text(stringResource(R.string.today_title).uppercase(), style = MaterialTheme.typography.displaySmall)
        }

        val active = state.active
        if (active != null) {
            ActiveWorkoutBanner(active) { navigate(Routes.workoutSession(active.id)) }
        } else {
            PlanCard(state, navigate, onStartRoutine = { id ->
                viewModel.startRoutine(id) { navigate(Routes.workoutSession(it)) }
            }, onStartEmpty = {
                viewModel.startEmpty(defaultName) { navigate(Routes.workoutSession(it)) }
            })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.today_week),
                value = state.weekWorkouts.toString(),
                sub = pluralStringResource(R.plurals.workouts, state.weekWorkouts, state.weekWorkouts).substringAfter(' '),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.today_week_streak),
                value = state.weekStreak.toString(),
                sub = pluralStringResource(R.plurals.weeks_in_row, state.weekStreak, state.weekStreak).substringAfter(' '),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.today_volume_week),
                value = Fmt.grouped(state.weekVolumeKg),
                sub = stringResource(R.string.unit_kg),
                modifier = Modifier.weight(1f),
            )
        }

        val doses by viewModel.doses.collectAsStateWithLifecycle()
        if (doses.isNotEmpty()) {
            SectionTitle(stringResource(R.string.reminder_today)) {
                GhostButton(stringResource(R.string.action_open), { navigate(Routes.REMINDERS) })
            }
            IronCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    doses.forEach { d -> DoseStatusChip(d) { viewModel.takeDose(d) } }
                }
            }
        }

        val challengeList by viewModel.challengeList.collectAsStateWithLifecycle()
        if (challengeList.isNotEmpty()) {
            SectionTitle(stringResource(R.string.today_daily)) {
                GhostButton(stringResource(R.string.action_open), { navigate(Routes.DAILY) })
            }
            challengeList.forEach { ui ->
                ChallengeCard(
                    ui = ui,
                    onAdd = { viewModel.addToChallenge(ui.challenge.id, it) },
                    onOpen = { navigate(Routes.challenge(ui.challenge.id)) },
                    compact = true,
                )
            }
        }

        StretchTodayCard(
            done = state.stretchToday,
            onStart = { phase ->
                // Если уже идёт другая программа — просто открываем её таймер.
                viewModel.startStretch(phase) { navigate(Routes.INTERVAL_RUN) }
            },
            onMark = { markSheet = true },
        )

        SectionTitle(stringResource(R.string.today_activity))
        IronCard(modifier = Modifier.fillMaxWidth()) {
            ActivityHeatmap(levels = state.heatmap, weeks = TodayViewModel.HEATMAP_WEEKS, today = state.today)
        }

        state.last?.let { last ->
            SectionTitle(stringResource(R.string.today_last_workout))
            IronCard(onClick = { navigate(Routes.workoutDetail(last.id)) }, modifier = Modifier.fillMaxWidth()) {
                Text(Fmt.dateTime(last.startedAt), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                Text(last.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Fmt.durationWords(last.durationSec) +
                        (if (last.volumeKg > 0) " · " + stringResource(R.string.value_kg, Fmt.grouped(last.volumeKg)) else "") +
                        (last.distanceKm?.let { " · " + Fmt.num(it, 2) + " " + stringResource(R.string.unit_km) } ?: "") +
                        (last.caloriesKcal?.let { " · " + stringResource(R.string.approx_kcal, it.toInt()) } ?: ""),
                    style = Iron.numbers.tiny,
                    color = Iron.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun PlanCard(
    state: TodayState,
    navigate: (String) -> Unit,
    onStartRoutine: (Long) -> Unit,
    onStartEmpty: () -> Unit,
) {
    IronCard(modifier = Modifier.fillMaxWidth(), borderColor = Iron.colors.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val next = state.nextRoutine
            if (next != null) {
                Text(stringResource(R.string.today_next_planned).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.accentText)
                Text(next.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PrimaryButton(
                    stringResource(R.string.today_start_workout),
                    { onStartRoutine(next.id) },
                    icon = IronIcons.Play,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(stringResource(R.string.workouts_start_empty), onStartEmpty, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.today_no_plan), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                PrimaryButton(stringResource(R.string.workouts_start_empty), onStartEmpty, icon = IronIcons.Play, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.workouts_new_routine), { navigate(Routes.routineEdit()) }, icon = IronIcons.Add, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StretchTodayCard(done: List<WorkoutEntity>, onStart: (StretchPhase) -> Unit, onMark: () -> Unit) {
    IronCard(modifier = Modifier.fillMaxWidth(), borderColor = if (done.isNotEmpty()) Iron.colors.success else Iron.colors.border) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IronIcon(
                    if (done.isNotEmpty()) IronIcons.CircleCheck else IronIcons.Stretch,
                    null,
                    tint = if (done.isNotEmpty()) Iron.colors.success else Iron.colors.textSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (done.isNotEmpty()) R.string.stretch_done_today else R.string.stretch_title).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            done.forEach { w ->
                val phase = w.stretchPhase?.let { runCatching { StretchPhase.valueOf(it) }.getOrNull() } ?: StretchPhase.ANY
                Text(
                    stretchPhaseLabel(phase) + " · " + w.name + " · " + Fmt.durationWords(w.durationSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = Iron.colors.textSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IronChip(stringResource(R.string.stretch_phase_before), selected = false, icon = IronIcons.Play, onClick = { onStart(StretchPhase.BEFORE) })
                IronChip(stringResource(R.string.stretch_phase_after), selected = false, icon = IronIcons.Play, onClick = { onStart(StretchPhase.AFTER) })
                IronChip(stringResource(R.string.stretch_mark_short), selected = false, icon = IronIcons.Check, onClick = onMark)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    IronCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Iron.numbers.medium, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
