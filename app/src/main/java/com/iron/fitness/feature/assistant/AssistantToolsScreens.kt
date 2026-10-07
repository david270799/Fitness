package com.iron.fitness.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.iron.fitness.core.domain.Progression
import com.iron.fitness.core.domain.StretchItem
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.stretching.data.StretchLauncher
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
private fun ToolColumn(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ======================= Обзор недели =======================

@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(private val service: AssistantService) : ViewModel() {
    private val _context = MutableStateFlow<String?>(null)
    val context: StateFlow<String?> = _context.asStateFlow()
    private val _state = MutableStateFlow<AiState<String>>(AiState.Idle)
    val state: StateFlow<AiState<String>> = _state.asStateFlow()

    init {
        viewModelScope.launch { _context.value = service.weeklyContext() }
    }

    fun request() {
        val ctx = _context.value ?: return
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { AiState.Done(service.weeklyReview(ctx)) }.getOrElse { it.toAiError() }
        }
    }
}

@Composable
fun WeeklyReviewScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: WeeklyReviewViewModel = hiltViewModel()) {
    val ctx by viewModel.context.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    IronScaffold(title = stringResource(R.string.ai_f_review), onBack = onBack) { padding ->
        ToolColumn(padding) {
            Text(stringResource(R.string.ai_review_what_sent), style = MaterialTheme.typography.titleSmall)
            IronCard(modifier = Modifier.fillMaxWidth()) {
                Text(ctx ?: stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
            Text(stringResource(R.string.ai_review_body_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            PrimaryButton(
                stringResource(R.string.ai_review_get),
                viewModel::request,
                icon = IronIcons.Sparkles,
                enabled = ctx != null && state !is AiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading()
                is AiState.Error -> AiError(s.kind, viewModel::request) { navigate(Routes.SETTINGS) }
                is AiState.Done -> IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(s.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ======================= Замена упражнения =======================

@HiltViewModel
class SubstituteViewModel @Inject constructor(
    private val service: AssistantService,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val _exercise = MutableStateFlow<ExerciseEntity?>(null)
    val exercise: StateFlow<ExerciseEntity?> = _exercise.asStateFlow()
    private val _state = MutableStateFlow<AiState<List<Matched<AiAlt>>>>(AiState.Idle)
    val state: StateFlow<AiState<List<Matched<AiAlt>>>> = _state.asStateFlow()
    private var lastReason = ""

    fun pick(id: String) = viewModelScope.launch {
        _exercise.value = exercises.get(id)
        _state.value = AiState.Idle
    }

    fun request(reason: String) {
        val e = _exercise.value ?: return
        lastReason = reason
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { AiState.Done(service.alternatives(e, reason)) }.getOrElse { it.toAiError() }
        }
    }

    fun retry() = request(lastReason)
}

private val REASONS = listOf(R.string.ai_reason_equipment, R.string.ai_reason_pain, R.string.ai_reason_variety, R.string.ai_reason_home)

@Composable
fun SubstituteScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    picked: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: SubstituteViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reason by rememberSaveable { mutableStateOf(REASONS.first()) }
    val reasonText = stringResource(reason)
    LaunchedEffect(picked) {
        picked?.firstOrNull()?.let { viewModel.pick(it) }
        if (picked != null) onPickedHandled()
    }
    IronScaffold(title = stringResource(R.string.ai_f_substitute), onBack = onBack) { padding ->
        ToolColumn(padding) {
            IronCard(onClick = { navigate(Routes.library(mode = "pick")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_sub_exercise).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                Text(exercise?.name ?: stringResource(R.string.ai_sub_pick), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.ai_sub_reason).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONS.forEach { r -> IronChip(stringResource(r), selected = r == reason, onClick = { reason = r }) }
            }
            PrimaryButton(
                stringResource(R.string.ai_sub_find),
                { viewModel.request(reasonText) },
                icon = IronIcons.Sparkles,
                enabled = exercise != null && state !is AiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading()
                is AiState.Error -> AiError(s.kind, viewModel::retry) { navigate(Routes.SETTINGS) }
                is AiState.Done -> IronCard(modifier = Modifier.fillMaxWidth()) {
                    s.value.forEach { m -> MatchedLine(m.ai.nameRu, m.ai.reason, m.exercise) { navigate(Routes.exercise(it)) } }
                }
            }
        }
    }
}

// ======================= Растяжка =======================

@HiltViewModel
class StretchSuggestViewModel @Inject constructor(
    private val service: AssistantService,
    private val launcher: StretchLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow<AiState<List<Matched<AiStretchItem>>>>(AiState.Idle)
    val state: StateFlow<AiState<List<Matched<AiStretchItem>>>> = _state.asStateFlow()
    private var lastPhase = StretchPhase.AFTER
    private var lastPhaseText = ""

    fun request(phase: StretchPhase, phaseText: String) {
        lastPhase = phase
        lastPhaseText = phaseText
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val muscles = if (phase == StretchPhase.AFTER) service.musclesOfLastStrength() else emptyList()
                AiState.Done(service.stretchFor(muscles, phaseText))
            }.getOrElse { it.toAiError() }
        }
    }

    fun retry() = request(lastPhase, lastPhaseText)

    /** Запустить предложенную растяжку — это действие пользователя (кнопка «Начать»). */
    fun start(name: String, onResult: (Boolean) -> Unit) {
        val done = _state.value as? AiState.Done ?: return
        val items = done.value.mapNotNull { m -> m.exercise?.let { StretchItem(it.id, m.ai.seconds.coerceIn(10, 180), m.ai.bothSides) } }
        viewModelScope.launch { onResult(launcher.start(name, lastPhase, items)) }
    }
}

@Composable
fun StretchSuggestScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: StretchSuggestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phase by rememberSaveable { mutableStateOf(StretchPhase.AFTER) }
    val phaseText = stringResource(
        when (phase) {
            StretchPhase.BEFORE -> R.string.ai_stretch_before
            StretchPhase.AFTER -> R.string.ai_stretch_after
            StretchPhase.ANY -> R.string.ai_stretch_any
        },
    )
    val name = stringResource(R.string.ai_stretch_name)
    IronScaffold(title = stringResource(R.string.ai_f_stretch), onBack = onBack) { padding ->
        ToolColumn(padding) {
            Segmented(
                items = StretchPhase.entries,
                selected = phase,
                label = { com.iron.fitness.feature.stretching.ui.stretchPhaseLabel(it) },
                onSelect = { phase = it },
            )
            Text(stringResource(R.string.ai_stretch_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            PrimaryButton(
                stringResource(R.string.ai_stretch_get),
                { viewModel.request(phase, phaseText) },
                icon = IronIcons.Sparkles,
                enabled = state !is AiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading()
                is AiState.Error -> AiError(s.kind, viewModel::retry) { navigate(Routes.SETTINGS) }
                is AiState.Done -> {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        s.value.forEach { m ->
                            MatchedLine(
                                m.ai.nameRu,
                                Fmt.duration(m.ai.seconds) + if (m.ai.bothSides) " · " + stringResource(R.string.stretch_both_sides) else "",
                                m.exercise,
                            ) { navigate(Routes.exercise(it)) }
                        }
                    }
                    PrimaryButton(
                        stringResource(R.string.action_start),
                        { viewModel.start(name) { navigate(Routes.INTERVAL_RUN) } },
                        icon = IronIcons.Play,
                        enabled = s.value.any { it.exercise != null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ======================= Прогрессия =======================

@HiltViewModel
class ProgressionViewModel @Inject constructor(private val service: AssistantService) : ViewModel() {
    private val _local = MutableStateFlow<ProgressionExplanation?>(null)
    val local: StateFlow<ProgressionExplanation?> = _local.asStateFlow()
    private val _ai = MutableStateFlow<AiState<String>>(AiState.Idle)
    val ai: StateFlow<AiState<String>> = _ai.asStateFlow()

    fun pick(id: String) = viewModelScope.launch {
        _local.value = service.explainLocally(id)
        _ai.value = AiState.Idle
    }

    fun askAi() {
        val ex = _local.value ?: return
        _ai.value = AiState.Loading
        viewModelScope.launch { _ai.value = runCatching { AiState.Done(service.explainWithAi(ex)) }.getOrElse { it.toAiError() } }
    }
}

@Composable
fun ProgressionScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    picked: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: ProgressionViewModel = hiltViewModel(),
) {
    val local by viewModel.local.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        picked?.firstOrNull()?.let { viewModel.pick(it) }
        if (picked != null) onPickedHandled()
    }
    IronScaffold(title = stringResource(R.string.ai_f_progression), onBack = onBack) { padding ->
        ToolColumn(padding) {
            Text(stringResource(R.string.ai_prog_rules), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            SecondaryButton(stringResource(R.string.ai_sub_pick), { navigate(Routes.library(mode = "pick", category = "STRENGTH")) }, icon = IronIcons.Search, modifier = Modifier.fillMaxWidth())
            local?.let { ex ->
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ai_prog_range, ex.range.first, ex.range.last, Fmt.num(ex.increment)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Iron.colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ex.lastSession?.let { stringResource(R.string.ai_prog_last, it) } ?: stringResource(R.string.ai_prog_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ex.suggestion?.let { sug ->
                        val target = if (sug.weightKg != null) stringResource(R.string.set_weight_reps, Fmt.num(sug.weightKg), sug.reps) else stringResource(R.string.set_reps_only, sug.reps)
                        Text(stringResource(R.string.session_target, target), style = Iron.numbers.small, color = Iron.colors.accentText, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            stringResource(
                                when (sug.reason) {
                                    Progression.Reason.INCREASE_WEIGHT -> R.string.ai_prog_why_increase
                                    Progression.Reason.ADD_REPS -> R.string.ai_prog_why_reps
                                    Progression.Reason.DELOAD -> R.string.ai_prog_why_deload
                                    Progression.Reason.BODYWEIGHT_REPS -> R.string.ai_prog_why_bodyweight
                                    Progression.Reason.NO_HISTORY -> R.string.ai_prog_no_history
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                SecondaryButton(
                    stringResource(R.string.ai_prog_ask),
                    viewModel::askAi,
                    icon = IronIcons.Sparkles,
                    enabled = ai !is AiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val s = ai) {
                    AiState.Idle -> Unit
                    AiState.Loading -> AiLoading()
                    is AiState.Error -> AiError(s.kind, viewModel::askAi) { navigate(Routes.SETTINGS) }
                    is AiState.Done -> IronCard(modifier = Modifier.fillMaxWidth()) { Text(s.value, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
