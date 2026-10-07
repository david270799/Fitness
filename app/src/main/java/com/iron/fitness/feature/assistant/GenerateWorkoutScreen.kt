package com.iron.fitness.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenerateWorkoutViewModel @Inject constructor(private val service: AssistantService) : ViewModel() {
    private val _state = MutableStateFlow<AiState<Pair<AiRoutine, List<Matched<AiRoutineExercise>>>>>(AiState.Idle)
    val state: StateFlow<AiState<Pair<AiRoutine, List<Matched<AiRoutineExercise>>>>> = _state.asStateFlow()
    private var lastRequest: (() -> Unit)? = null

    fun generate(goal: String, minutes: Int, equipment: List<String>, level: String) {
        lastRequest = { generate(goal, minutes, equipment, level) }
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { AiState.Done(service.generateRoutine(goal, minutes, equipment, level)) }
                .getOrElse { it.toAiError() }
        }
    }

    fun retry() = lastRequest?.invoke()

    fun save(defaultName: String, onSaved: () -> Unit) {
        val done = _state.value as? AiState.Done ?: return
        viewModelScope.launch {
            service.saveRoutine(done.value.first, done.value.second, defaultName)
            onSaved()
        }
    }
}

private val EQUIPMENT = listOf(
    R.string.equipment_barbell, R.string.equipment_dumbbell, R.string.equipment_kettlebells,
    R.string.equipment_cable, R.string.equipment_machine, R.string.equipment_body_only, R.string.equipment_bands,
)
private val LEVELS = listOf(R.string.level_beginner, R.string.level_intermediate, R.string.level_expert)
private val MINUTES = listOf(30, 45, 60, 75)

@Composable
fun GenerateWorkoutScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: GenerateWorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf(60) }
    var level by rememberSaveable { mutableStateOf(R.string.level_intermediate) }
    var equipment by rememberSaveable { mutableStateOf(listOf(R.string.equipment_barbell, R.string.equipment_dumbbell)) }
    val equipmentNames = equipment.map { stringResource(it) }
    val levelName = stringResource(level)
    val defaultName = stringResource(R.string.ai_routine_default)
    val defaultGoal = stringResource(R.string.ai_goal_default)

    IronScaffold(title = stringResource(R.string.ai_f_workout), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IronTextField(
                value = goal,
                onValueChange = { goal = it },
                label = stringResource(R.string.ai_goal),
                placeholder = stringResource(R.string.ai_goal_hint),
                singleLine = false,
                minLines = 2,
            )
            Text(stringResource(R.string.ai_minutes).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Segmented(items = MINUTES, selected = minutes, label = { stringResource(R.string.minutes_short, it) }, onSelect = { minutes = it })
            Text(stringResource(R.string.ai_level).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Segmented(items = LEVELS, selected = level, label = { stringResource(it) }, onSelect = { level = it })
            Text(stringResource(R.string.ai_equipment).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EQUIPMENT.forEach { e ->
                    IronChip(stringResource(e), selected = e in equipment, onClick = { equipment = if (e in equipment) equipment - e else equipment + e })
                }
            }
            PrimaryButton(
                stringResource(R.string.ai_generate),
                { viewModel.generate(goal.ifBlank { defaultGoal }, minutes, equipmentNames, levelName) },
                icon = IronIcons.Sparkles,
                enabled = state !is AiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
            SentDataNote(stringResource(R.string.ai_sent_workout))
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading()
                is AiState.Error -> AiError(s.kind, viewModel::retry) { navigate(Routes.SETTINGS) }
                is AiState.Done -> {
                    val (routine, matched) = s.value
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(routine.name, style = MaterialTheme.typography.titleLarge)
                        routine.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary) }
                        Spacer(Modifier.height(6.dp))
                        matched.forEach { m ->
                            MatchedLine(
                                title = m.ai.nameRu,
                                detail = listOfNotNull(
                                    "${m.ai.sets} × ${m.ai.reps ?: "—"}",
                                    m.ai.restSec?.let { stringResource(R.string.ai_rest_sec, it) },
                                    m.ai.note,
                                ).joinToString(" · "),
                                exercise = m.exercise,
                                onOpen = { navigate(Routes.exercise(it)) },
                            )
                        }
                    }
                    if (matched.any { it.exercise == null }) {
                        Text(stringResource(R.string.ai_unmatched_skipped), style = MaterialTheme.typography.bodySmall, color = Iron.colors.warning)
                    }
                    PrimaryButton(
                        stringResource(R.string.summary_save_routine),
                        { viewModel.save(defaultName) { onBack() } },
                        icon = IronIcons.Save,
                        enabled = matched.any { it.exercise != null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
