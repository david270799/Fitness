package com.iron.fitness.feature.cardio.run

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.body.BodyWeightProvider
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.Intervals
import com.iron.fitness.core.domain.Phase
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.FlatProgressBar
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.data.CardioEntry
import com.iron.fitness.feature.cardio.data.CardioRepository
import com.iron.fitness.feature.cardio.data.CardioType
import com.iron.fitness.feature.cardio.data.Intensity
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.exercises.data.thumbnailModel
import com.iron.fitness.feature.exercises.ui.ExerciseThumb
import com.iron.fitness.feature.timer.phaseName
import com.iron.fitness.feature.timer.phaseTypeName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntervalRunViewModel @Inject constructor(
    private val runner: ProgramRunner,
    private val cardio: CardioRepository,
    private val exercises: ExerciseRepository,
    bodyWeight: BodyWeightProvider,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<RunState?> = runner.state
    val settings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _exercises = MutableStateFlow<Map<String, ExerciseEntity>>(emptyMap())
    val exerciseMap: StateFlow<Map<String, ExerciseEntity>> = _exercises.asStateFlow()

    private val _weight = MutableStateFlow<Double?>(null)
    val weight: StateFlow<Double?> = _weight.asStateFlow()

    init {
        viewModelScope.launch {
            _weight.value = bodyWeight.currentKg()
            val ids = runner.state.value?.phases?.mapNotNull { it.exerciseId }?.distinct().orEmpty()
            if (ids.isNotEmpty()) _exercises.value = exercises.getAll(ids).associateBy { it.id }
        }
    }

    fun togglePause() = runner.togglePause()
    fun skip() = runner.advance()
    fun stop(save: Boolean) = runner.stop(save)
    fun dismiss() = runner.dismiss()

    /** Дополнить сохранённое кардио дистанцией и интенсивностью. */
    fun saveCardioDetails(distanceKm: Double?, intensity: Intensity, onDone: () -> Unit) {
        val s = runner.state.value ?: return onDone()
        val id = s.savedWorkoutId ?: return onDone()
        viewModelScope.launch {
            val w = cardio.getWorkout(id)
            if (w != null) {
                cardio.saveCardio(
                    CardioEntry(
                        id = id,
                        type = s.cardioType ?: CardioType.OTHER,
                        name = w.name,
                        startedAt = w.startedAt,
                        durationSec = w.durationSec,
                        distanceKm = distanceKm,
                        intensity = intensity,
                        note = w.note,
                    ),
                )
            }
            onDone()
        }
    }
}

@Composable
fun phaseColor(type: BlockType): Color = when (type) {
    BlockType.WORK -> Iron.colors.accent
    BlockType.REST -> Iron.colors.success
    BlockType.WARMUP, BlockType.COOLDOWN -> Iron.colors.warning
    BlockType.REPEAT -> Iron.colors.textSecondary
}

@Composable
fun IntervalRunScreen(onBack: () -> Unit, viewModel: IntervalRunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exercises by viewModel.exerciseMap.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }

    val s = state
    LaunchedEffect(s == null) {
        if (s == null && !left) {
            left = true
            onBack()
        }
    }
    if (s == null) return

    val view = LocalView.current
    val keepOn = settings.keepScreenOn && !s.finished
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Iron.colors.background),
    ) {
        if (s.finished) {
            FinishedPanel(
                s = s,
                kcal = Intervals.kcal(s.spentMs.mapValues { it.value / 1000 }, s.workMet, weight),
                onSaveCardio = { d, i -> viewModel.saveCardioDetails(d, i) { left = true; viewModel.dismiss(); onBack() } },
                onDone = { left = true; viewModel.dismiss(); onBack() },
            )
        } else {
            RunningPanel(
                s = s,
                now = now,
                exercises = exercises,
                kcal = Intervals.kcal(s.spentWithCurrent(now).mapValues { it.value / 1000 }, s.workMet, weight),
                onBack = onBack,
                onPause = viewModel::togglePause,
                onSkip = { viewModel.skip() },
                onStop = { confirmStop = true },
            )
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = Iron.colors.surface,
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.run_stop_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.run_stop_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                GhostButton(stringResource(R.string.run_stop_save), { confirmStop = false; viewModel.stop(save = true) })
            },
            dismissButton = {
                Row {
                    GhostButton(stringResource(R.string.run_stop_discard), { confirmStop = false; viewModel.stop(save = false) }, color = Iron.colors.error)
                    GhostButton(stringResource(R.string.action_cancel), { confirmStop = false }, color = Iron.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun RunningPanel(
    s: RunState,
    now: Long,
    exercises: Map<String, ExerciseEntity>,
    kcal: Double,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val res = LocalContext.current.resources
    val phase = s.current ?: return
    val color = phaseColor(phase.type)
    val remaining = s.remainingMs(now)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIconButton(IronIcons.ChevronDown, stringResource(R.string.action_back), onBack)
            Text(
                s.title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (phase.rounds > 0) {
                Text(stringResource(R.string.run_round, phase.round, phase.rounds).uppercase(), style = Iron.numbers.tiny, color = Iron.colors.textSecondary)
            }
        }
        if (s.kind == RunKind.INTERVAL) {
            val planned = s.plannedMs.coerceAtLeast(1)
            val spent = s.totalSpentMs(now)
            FlatProgressBar((spent.toFloat() / planned).coerceIn(0f, 1f), height = 6.dp)
            Row {
                Text(stringResource(R.string.run_elapsed, Fmt.duration(spent / 1000)), style = Iron.numbers.tiny, color = Iron.colors.textSecondary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.run_left, Fmt.duration(s.totalRemainingMs(now) / 1000)), style = Iron.numbers.tiny, color = Iron.colors.textSecondary)
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .background(color, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text(
                (if (s.paused) stringResource(R.string.run_paused) else phaseTypeName(res, phase.type)).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = if (phase.type == BlockType.WORK) Iron.colors.onAccent else Iron.colors.background,
            )
        }
        val big = if (remaining != null) Fmt.duration(remaining / 1000 + if (remaining % 1000 > 0) 1 else 0) else Fmt.duration(s.phaseRunMs(now) / 1000)
        val size = when {
            big.length <= 4 -> 112.sp
            big.length == 5 -> 96.sp
            else -> 68.sp
        }
        Text(
            big,
            style = Iron.numbers.huge.copy(fontSize = size, lineHeight = size * 1.05f),
            color = if (s.paused) Iron.colors.textSecondary else Iron.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            phaseName(res, phase).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        phase.exerciseId?.let { exercises[it] }?.let { ex ->
            ExerciseThumb(
                model = ex.thumbnailModel(),
                category = ex.category,
                size = 160.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        val next = s.next
        if (next != null) {
            NextCard(next)
        }
        Text(
            stringResource(R.string.approx_kcal, kcal.toInt()),
            style = Iron.numbers.tiny,
            color = Iron.colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(IronIcons.Stop, stringResource(R.string.run_stop), onStop, Modifier.weight(1f), primary = false)
            BigButton(if (s.paused) IronIcons.Play else IronIcons.Pause, stringResource(if (s.paused) R.string.run_resume else R.string.run_pause), onPause, Modifier.weight(1f), primary = true)
            if (s.kind == RunKind.INTERVAL) {
                BigButton(IronIcons.SkipNext, stringResource(R.string.run_skip), onSkip, Modifier.weight(1f), primary = false)
            } else {
                BigButton(IronIcons.Flag, stringResource(R.string.run_finish), { onStop() }, Modifier.weight(1f), primary = false)
            }
        }
    }
}

@Composable
private fun NextCard(next: Phase) {
    val res = LocalContext.current.resources
    IronCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.run_next_label).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.padding(end = 8.dp).background(phaseColor(next.type), RoundedCornerShape(2.dp)).padding(4.dp)) {}
            Text(
                phaseName(res, next) + if (next.seconds > 0) " · " + Fmt.duration(next.seconds) else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BigButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .height(68.dp)
            .clip(shape)
            .background(if (primary) Iron.colors.accent else Iron.colors.surface)
            .border(1.dp, if (primary) Iron.colors.accent else Iron.colors.border, shape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        IronIcon(icon, label, tint = if (primary) Iron.colors.onAccent else Iron.colors.text, size = 30.dp)
    }
}

@Composable
private fun FinishedPanel(
    s: RunState,
    kcal: Double,
    onSaveCardio: (Double?, Intensity) -> Unit,
    onDone: () -> Unit,
) {
    var distance by rememberSaveable { mutableStateOf("") }
    var intensity by rememberSaveable { mutableStateOf(Intensity.MODERATE) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IronIcon(IronIcons.CircleCheck, null, tint = Iron.colors.success, size = 48.dp)
        Text(stringResource(R.string.run_done_title).uppercase(), style = MaterialTheme.typography.labelLarge, color = Iron.colors.accentText)
        Text(s.title, style = MaterialTheme.typography.displaySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IronCard(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.summary_duration).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                Text(Fmt.duration(s.spentMs.values.sum() / 1000), style = Iron.numbers.medium)
            }
            if (s.kind == RunKind.INTERVAL) {
                IronCard(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.summary_calories).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    Text("≈ " + kcal.toInt(), style = Iron.numbers.medium)
                }
            }
        }
        if (s.kind == RunKind.CARDIO) {
            if (s.cardioType?.hasDistance != false) {
                IronTextField(
                    value = distance,
                    onValueChange = { v -> distance = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = stringResource(R.string.cardio_distance),
                    keyboardType = KeyboardType.Decimal,
                )
            }
            Text(stringResource(R.string.cardio_intensity).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Segmented(
                items = Intensity.entries,
                selected = intensity,
                label = { stringResource(it.label) },
                onSelect = { intensity = it },
            )
            PrimaryButton(
                stringResource(R.string.action_save),
                { onSaveCardio(Fmt.parse(distance)?.takeIf { it > 0 }, intensity) },
                modifier = Modifier.fillMaxWidth(),
                enabled = s.savedWorkoutId != null,
            )
        } else {
            Text(stringResource(R.string.run_saved_to_journal), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            PrimaryButton(stringResource(R.string.action_done), onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
