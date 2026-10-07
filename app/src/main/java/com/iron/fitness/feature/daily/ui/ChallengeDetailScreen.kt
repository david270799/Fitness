package com.iron.fitness.feature.daily.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.Challenges
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.daily.data.ChallengeLogEntity
import com.iron.fitness.feature.daily.data.ChallengeReminders
import com.iron.fitness.feature.daily.data.ChallengeRepository
import com.iron.fitness.feature.daily.data.ChallengeUi
import com.iron.fitness.feature.daily.data.ChallengeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    private val repo: ChallengeRepository,
    private val reminders: ChallengeReminders,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    val ui: StateFlow<ChallengeUi?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs: StateFlow<List<ChallengeLogEntity>> = repo.observeLogs(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(amount: Int) = viewModelScope.launch { repo.add(id, amount) }
    fun deleteLog(logId: Long) = viewModelScope.launch { repo.deleteLog(logId) }
    fun setArchived(archived: Boolean) = viewModelScope.launch {
        repo.setArchived(id, archived)
        repo.get(id)?.let { reminders.schedule(it) }
    }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        reminders.cancel(id)
        repo.delete(id)
        onDone()
    }
}

@Composable
fun ChallengeDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: ChallengeDetailViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    var leaving by remember { mutableStateOf(false) }

    IronScaffold(
        title = ui?.challenge?.name ?: stringResource(R.string.daily_title),
        onBack = onBack,
        actions = {
            IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), { onEdit(viewModel.id) })
            Box {
                IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
                IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                    val archived = ui?.challenge?.archived == true
                    IronMenuItem(
                        stringResource(if (archived) R.string.challenge_unarchive else R.string.challenge_archive),
                        { menu = false; viewModel.setArchived(!archived) },
                        IronIcons.Folder,
                    )
                    IronMenuItem(stringResource(R.string.action_delete), { menu = false; confirmDelete = true }, IronIcons.Delete, color = Iron.colors.error)
                }
            }
        },
    ) { padding ->
        val current = ui ?: return@IronScaffold
        val c = current.challenge
        val s = current.stats
        val today = LocalDate.now()
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "card") { ChallengeCard(current, onAdd = viewModel::add, onOpen = {}) }
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(stringResource(R.string.challenge_streak), pluralStringResource(R.plurals.days, s.streak, s.streak), Modifier.weight(1f))
                    StatBox(stringResource(R.string.challenge_best), pluralStringResource(R.plurals.days, s.best, s.best), Modifier.weight(1f))
                }
            }
            item(key = "stats2") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(stringResource(R.string.challenge_best_day), challengeValue(s.bestDay, c.unit), Modifier.weight(1f))
                    StatBox(
                        stringResource(R.string.challenge_total),
                        if (c.unit == ChallengeUnit.SECONDS) Fmt.durationWords(s.totalAllTime) else Fmt.grouped(s.totalAllTime.toDouble()),
                        Modifier.weight(1f),
                    )
                }
            }
            item(key = "week") {
                val week = Challenges.lastWeek(current.totals, today)
                val doneCount = week.count { (d, v) -> Challenges.goalOn(d.toEpochDay(), current.goals).let { g -> g in 1..v } }
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.challenge_week).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.challenge_week_done, doneCount, 7), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    }
                    WeekBars(week, current.goals)
                }
            }
            item(key = "calendar") {
                val month = YearMonth.from(today).minusMonths(monthOffset)
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    MonthCalendar(
                        month = month,
                        totals = current.totals,
                        goals = current.goals,
                        onPrev = { monthOffset += 1 },
                        onNext = { if (monthOffset > 0) monthOffset -= 1 },
                        today = today,
                    )
                }
            }
            if (current.goals.size > 1) {
                item(key = "goals") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.challenge_goal_history).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        current.goals.sortedByDescending { it.fromEpochDay }.forEach { g ->
                            Text(
                                stringResource(R.string.challenge_goal_since, challengeValue(g.goal, c.unit), Fmt.date(LocalDate.ofEpochDay(g.fromEpochDay))),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            item(key = "log_title") { SectionTitle(stringResource(R.string.challenge_log)) }
            items(logs, key = { it.id }) { log ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Fmt.date(LocalDate.ofEpochDay(log.day)) + ", " + Fmt.time(log.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Iron.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (log.amount > 0) "+" else "") + challengeValue(log.amount, c.unit),
                        style = Iron.numbers.small,
                    )
                    IronIconButton(IronIcons.Close, stringResource(R.string.action_delete), { viewModel.deleteLog(log.id) }, tint = Iron.colors.textSecondary)
                }
            }
        }
    }
    LaunchedEffect(ui == null) {
        // Удалили на другом экране.
        if (ui == null && leaving) onBack()
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.challenge_delete_title),
            text = stringResource(R.string.challenge_delete_text),
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

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    IronCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        Text(value, style = Iron.numbers.small)
    }
}
