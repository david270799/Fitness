package com.iron.fitness.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.feature.daily.data.ChallengeEntity
import com.iron.fitness.feature.daily.data.ChallengeRepository
import com.iron.fitness.feature.daily.data.ChallengeUi
import com.iron.fitness.feature.daily.data.ChallengeUnit
import com.iron.fitness.feature.daily.ui.ChallengeCard
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyState(val loading: Boolean = true, val items: List<ChallengeUi> = emptyList())

/** Быстрый старт: готовые челленджи. */
private data class QuickChallenge(val nameRes: Int, val unit: ChallengeUnit, val goal: Int, val step1: Int, val step2: Int)

private val QUICK = listOf(
    QuickChallenge(R.string.qc_pushups, ChallengeUnit.REPS, 50, 5, 10),
    QuickChallenge(R.string.qc_squats, ChallengeUnit.REPS, 100, 10, 20),
    QuickChallenge(R.string.qc_plank, ChallengeUnit.SECONDS, 180, 15, 30),
    QuickChallenge(R.string.qc_abs, ChallengeUnit.REPS, 50, 5, 10),
    QuickChallenge(R.string.qc_pullups, ChallengeUnit.REPS, 20, 1, 5),
)

@HiltViewModel
class DailyViewModel @Inject constructor(private val repo: ChallengeRepository) : ViewModel() {
    val state: StateFlow<DailyState> = repo.observeActive()
        .map { DailyState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyState())

    fun add(id: Long, amount: Int) = viewModelScope.launch { repo.add(id, amount) }

    fun createQuick(name: String, unit: ChallengeUnit, goal: Int, step1: Int, step2: Int) = viewModelScope.launch {
        repo.create(ChallengeEntity(name = name, unit = unit, goal = goal, step1 = step1, step2 = step2))
    }
}

@Composable
fun DailyScreen(navigate: (String) -> Unit, viewModel: DailyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IronScaffold(
        title = stringResource(R.string.daily_title),
        actions = { IronIconButton(IronIcons.Add, stringResource(R.string.challenge_new), { navigate(Routes.challengeEdit()) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.challenge.id }) { ui ->
                ChallengeCard(
                    ui = ui,
                    onAdd = { viewModel.add(ui.challenge.id, it) },
                    onOpen = { navigate(Routes.challenge(ui.challenge.id)) },
                )
            }
            if (!state.loading) {
                item(key = "quick") {
                    QuickStart(
                        showIntro = state.items.isEmpty(),
                        existing = state.items.map { it.challenge.name },
                        onCreate = viewModel::createQuick,
                        onCustom = { navigate(Routes.challengeEdit()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStart(
    showIntro: Boolean,
    existing: List<String>,
    onCreate: (String, ChallengeUnit, Int, Int, Int) -> Unit,
    onCustom: () -> Unit,
) {
    IronCard(modifier = Modifier.fillMaxWidth()) {
        if (showIntro) {
            Text(stringResource(R.string.challenge_intro), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
        }
        Text(stringResource(R.string.challenge_quick).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK.forEach { q ->
                val name = stringResource(q.nameRes)
                if (name !in existing) {
                    IronChip(
                        text = name + " · " + if (q.unit == ChallengeUnit.SECONDS) com.iron.fitness.core.util.Fmt.duration(q.goal) else q.goal.toString(),
                        selected = false,
                        icon = IronIcons.Add,
                        onClick = { onCreate(name, q.unit, q.goal, q.step1, q.step2) },
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
        SecondaryButton(stringResource(R.string.challenge_new), onCustom, icon = IronIcons.Edit, modifier = Modifier.fillMaxWidth())
    }
}
