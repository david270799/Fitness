package com.iron.fitness.feature.reminders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.domain.ReminderSchedule
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.reminders.data.ReminderAlarms
import com.iron.fitness.feature.reminders.data.ReminderEntity
import com.iron.fitness.feature.reminders.data.ReminderLogEntity
import com.iron.fitness.feature.reminders.data.ReminderRepository
import com.iron.fitness.feature.reminders.data.timeList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val alarms: ReminderAlarms,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    val reminder: StateFlow<ReminderEntity?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs: StateFlow<List<ReminderLogEntity>> = repo.observeLogs(id, 90).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun take(log: ReminderLogEntity) = viewModelScope.launch { alarms.onTaken(id, log.scheduledAt) }
    fun setStatus(log: ReminderLogEntity, status: DoseStatus) = viewModelScope.launch { repo.setStatus(id, log.scheduledAt, status) }
}

@Composable
fun ReminderDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: ReminderDetailViewModel = hiltViewModel()) {
    val r by viewModel.reminder.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    IronScaffold(
        title = r?.name ?: stringResource(R.string.more_reminders),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), { onEdit(viewModel.id) }) },
    ) { padding ->
        val reminder = r ?: return@IronScaffold
        val now = LocalDateTime.now()
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recent = logs.filter { it.scheduledAt >= monthAgo }
        val adherence = ReminderSchedule.adherencePercent(recent.map { Fmt.toLocalDateTime(it.scheduledAt) to it.status }, now)
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReminderPhoto(reminder.photoPath, size = 88.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            reminder.dose?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            Text(reminder.timeList.joinToString(" · ") { Fmt.minutesOfDay(it) }, style = Iron.numbers.small)
                            Text(daysLabel(reminder.weekdays), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                            if (!reminder.enabled) {
                                Text(stringResource(R.string.reminder_disabled), style = MaterialTheme.typography.labelSmall, color = Iron.colors.warning)
                            }
                        }
                    }
                    reminder.note?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            item(key = "adherence") {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reminder_adherence).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    Text(adherence?.let { "$it %" } ?: "—", style = Iron.numbers.large, color = when {
                        adherence == null -> Iron.colors.textSecondary
                        adherence >= 90 -> Iron.colors.success
                        adherence >= 70 -> Iron.colors.warning
                        else -> Iron.colors.error
                    })
                    Text(
                        stringResource(
                            R.string.reminder_adherence_detail,
                            recent.count { it.status == DoseStatus.TAKEN },
                            recent.count { it.status == DoseStatus.MISSED },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Iron.colors.textSecondary,
                    )
                }
            }
            item(key = "history") { SectionTitle(stringResource(R.string.reminder_history)) }
            items(logs, key = { it.id }) { log ->
                val (color, label) = when (log.status) {
                    DoseStatus.TAKEN -> Iron.colors.success to stringResource(R.string.reminder_status_taken)
                    DoseStatus.MISSED -> Iron.colors.error to stringResource(R.string.reminder_status_missed)
                    DoseStatus.SKIPPED -> Iron.colors.textSecondary to stringResource(R.string.reminder_status_skipped)
                    DoseStatus.PENDING -> Iron.colors.warning to stringResource(R.string.reminder_status_due)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.dateTime(log.scheduledAt), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            label + (log.actedAt?.takeIf { log.status == DoseStatus.TAKEN }?.let { " · " + Fmt.time(it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                        )
                    }
                    when (log.status) {
                        DoseStatus.TAKEN -> GhostButton(stringResource(R.string.reminder_mark_missed), { viewModel.setStatus(log, DoseStatus.MISSED) }, color = Iron.colors.textSecondary)
                        else -> GhostButton(stringResource(R.string.reminder_taken), { viewModel.take(log) })
                    }
                }
            }
            if (logs.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.reminder_history_empty), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                }
            }
        }
    }
}
