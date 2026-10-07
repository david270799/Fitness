package com.iron.fitness.feature.workouts.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ChipRow
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SearchField
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutSummaryRow
import com.iron.fitness.feature.workouts.data.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

enum class HistoryRange(val days: Long?) { ALL(null), DAYS_30(30), DAYS_90(90), YEAR(365) }

data class HistoryState(
    val loading: Boolean = true,
    val query: String = "",
    val type: WorkoutType? = null,
    val range: HistoryRange = HistoryRange.ALL,
    /** Месяц → тренировки, от новых к старым. */
    val groups: List<Pair<YearMonth, List<WorkoutSummaryRow>>> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: WorkoutRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val type = MutableStateFlow<WorkoutType?>(null)
    private val range = MutableStateFlow(HistoryRange.ALL)
    val queryValue: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<HistoryState> = combine(repo.observeHistory(), query, type, range) { rows, q, t, rg ->
        val needle = q.trim().lowercase()
        val from = rg.days?.let { System.currentTimeMillis() - it * 24 * 60 * 60 * 1000 } ?: Long.MIN_VALUE
        val filtered = rows.filter { r ->
            r.workout.startedAt >= from &&
                (t == null || r.workout.type == t) &&
                (needle.isEmpty() || r.workout.name.lowercase().contains(needle) || r.workout.note?.lowercase()?.contains(needle) == true)
        }
        HistoryState(
            loading = false,
            query = q,
            type = t,
            range = rg,
            groups = filtered.groupBy { YearMonth.from(Fmt.toLocalDate(it.workout.startedAt)) }.toList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setQuery(q: String) { query.value = q }
    fun setType(t: WorkoutType?) { type.value = t }
    fun setRange(r: HistoryRange) { range.value = r }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryValue.collectAsStateWithLifecycle()
    IronScaffold(title = stringResource(R.string.history_title), onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.history_search),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val types: List<WorkoutType?> = listOf(null) + WorkoutType.entries
            ChipRow(
                items = types,
                selected = state.type,
                label = { workoutTypeLabel(it) },
                onSelect = viewModel::setType,
                modifier = Modifier.padding(top = 8.dp),
            )
            ChipRow(
                items = HistoryRange.entries,
                selected = state.range,
                label = {
                    stringResource(
                        when (it) {
                            HistoryRange.ALL -> R.string.history_range_all
                            HistoryRange.DAYS_30 -> R.string.history_range_30
                            HistoryRange.DAYS_90 -> R.string.history_range_90
                            HistoryRange.YEAR -> R.string.history_range_year
                        },
                    )
                },
                onSelect = viewModel::setRange,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (!state.loading && state.groups.isEmpty()) {
                EmptyState(
                    stringResource(if (query.isBlank() && state.type == null) R.string.workouts_no_history else R.string.history_empty),
                    icon = IronIcons.History,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { (month, rows) ->
                    item(key = "m$month") {
                        SectionTitle(Fmt.monthYear(month.atDay(1)) + " · " + rows.size)
                    }
                    items(rows, key = { it.workout.id }) { row ->
                        WorkoutRow(row, onClick = { onOpen(row.workout.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun workoutTypeLabel(type: WorkoutType?): String = stringResource(
    when (type) {
        null -> R.string.library_filter_all
        WorkoutType.STRENGTH -> R.string.workout_type_strength
        WorkoutType.CARDIO -> R.string.workout_type_cardio
        WorkoutType.INTERVAL -> R.string.workout_type_interval
        WorkoutType.STRETCHING -> R.string.workout_type_stretching
    },
)
