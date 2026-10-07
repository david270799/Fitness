package com.iron.fitness.feature.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.alarms.AlarmScheduler
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.domain.ReminderSchedule
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronSwitch
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.reminders.data.ReminderAlarms
import com.iron.fitness.feature.reminders.data.ReminderEntity
import com.iron.fitness.feature.reminders.data.ReminderLogEntity
import com.iron.fitness.feature.reminders.data.ReminderRepository
import com.iron.fitness.feature.reminders.data.timeList
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Приём сегодня: время, статус. */
data class TodayDose(val reminder: ReminderEntity, val at: Long, val status: DoseStatus)

data class RemindersState(
    val loading: Boolean = true,
    val items: List<ReminderEntity> = emptyList(),
    val today: List<TodayDose> = emptyList(),
)

/** Приёмы на сегодня по расписанию с учётом журнала. */
fun todayDoses(reminders: List<ReminderEntity>, logs: List<ReminderLogEntity>, date: LocalDate = LocalDate.now()): List<TodayDose> {
    val zone = ZoneId.systemDefault()
    val byKey = logs.associateBy { it.reminderId to it.scheduledAt }
    return reminders.filter { it.enabled }.flatMap { r ->
        ReminderSchedule.dosesOn(r.timeList, r.weekdays, date).map { dt ->
            val at = dt.atZone(zone).toInstant().toEpochMilli()
            TodayDose(r, at, byKey[r.id to at]?.status ?: DoseStatus.PENDING)
        }
    }.sortedBy { it.at }
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val alarms: ReminderAlarms,
    private val scheduler: AlarmScheduler,
) : ViewModel() {
    private val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val state: StateFlow<RemindersState> = combine(repo.observeAll(), repo.observeLogsSince(dayStart)) { list, logs ->
        RemindersState(loading = false, items = list, today = todayDoses(list, logs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersState())

    val exactAllowed: Boolean get() = scheduler.canScheduleExact()

    fun setEnabled(r: ReminderEntity, enabled: Boolean) = viewModelScope.launch {
        repo.setEnabled(r.id, enabled)
        val updated = repo.get(r.id) ?: return@launch
        if (enabled) alarms.scheduleNext(updated) else alarms.cancel(r.id)
    }

    fun take(d: TodayDose) = viewModelScope.launch { alarms.onTaken(d.reminder.id, d.at) }
}

@Composable
fun daysLabel(mask: Int): String = when {
    ReminderSchedule.isEveryDay(mask) -> stringResource(R.string.reminder_every_day)
    ReminderSchedule.isWeekdays(mask) -> stringResource(R.string.reminder_weekdays)
    else -> DayOfWeek.entries.filter { ReminderSchedule.isDayEnabled(mask, it) }.joinToString(", ") { Fmt.dayOfWeekShort(it) }
}

@Composable
fun ReminderPhoto(path: String?, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(size).clip(shape).background(Iron.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            IronIcon(IronIcons.Pill, null, tint = Iron.colors.textSecondary)
        }
    }
}

@Composable
fun DoseStatusChip(d: TodayDose, onTake: () -> Unit) {
    val now = System.currentTimeMillis()
    val (color, text) = when (d.status) {
        DoseStatus.TAKEN -> Iron.colors.success to stringResource(R.string.reminder_status_taken)
        DoseStatus.MISSED -> Iron.colors.error to stringResource(R.string.reminder_status_missed)
        DoseStatus.SKIPPED -> Iron.colors.textSecondary to stringResource(R.string.reminder_status_skipped)
        DoseStatus.PENDING -> (if (d.at <= now) Iron.colors.warning else Iron.colors.textSecondary) to
            stringResource(if (d.at <= now) R.string.reminder_status_due else R.string.reminder_status_planned)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(Fmt.time(d.at), style = Iron.numbers.small, modifier = Modifier.width(56.dp))
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(d.reminder.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(d.reminder.dose?.takeIf { it.isNotBlank() }, text).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
        if (d.status == DoseStatus.PENDING || d.status == DoseStatus.MISSED) {
            GhostButton(stringResource(R.string.reminder_taken), onTake)
        } else if (d.status == DoseStatus.TAKEN) {
            IronIcon(IronIcons.CircleCheck, null, tint = Iron.colors.success)
        }
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IronScaffold(
        title = stringResource(R.string.more_reminders),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Add, stringResource(R.string.reminder_new), { navigate(Routes.reminderEdit()) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!viewModel.exactAllowed) {
                item(key = "exact") {
                    IronCard(borderColor = Iron.colors.warning, modifier = Modifier.fillMaxWidth(), onClick = { navigate(Routes.PERMISSIONS) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IronIcon(IronIcons.Warning, null, tint = Iron.colors.warning)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.reminder_exact_warning), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (state.today.isNotEmpty()) {
                item(key = "today") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reminder_today).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        Spacer(Modifier.padding(top = 6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.today.forEach { d -> DoseStatusChip(d) { viewModel.take(d) } }
                        }
                    }
                }
            }
            if (!state.loading && state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(R.string.reminder_empty),
                        icon = IronIcons.Pill,
                        action = { PrimaryButton(stringResource(R.string.reminder_new), { navigate(Routes.reminderEdit()) }, icon = IronIcons.Add) },
                    )
                }
            }
            items(state.items, key = { it.id }) { r ->
                IronCard(onClick = { navigate(Routes.reminderDetail(r.id)) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReminderPhoto(r.photoPath)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            r.dose?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                r.timeList.forEach { m -> Text(Fmt.minutesOfDay(m), style = Iron.numbers.tiny) }
                            }
                            Text(daysLabel(r.weekdays), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        }
                        IronSwitch(checked = r.enabled, onCheckedChange = { viewModel.setEnabled(r, it) })
                    }
                }
            }
        }
    }
}
