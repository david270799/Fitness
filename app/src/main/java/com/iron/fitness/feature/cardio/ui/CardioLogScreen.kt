package com.iron.fitness.feature.cardio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.body.BodyWeightProvider
import com.iron.fitness.core.domain.Calories
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.data.CardioEntry
import com.iron.fitness.feature.cardio.data.CardioRepository
import com.iron.fitness.feature.cardio.data.CardioType
import com.iron.fitness.feature.cardio.data.Intensity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class CardioLogState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: CardioType = CardioType.RUN,
    val startedAt: Long = System.currentTimeMillis(),
    val minutes: String = "30",
    val distance: String = "",
    val intensity: Intensity = Intensity.MODERATE,
    val note: String = "",
    val weightKg: Double? = null,
) {
    val durationSec: Long get() = ((minutes.replace(',', '.').toDoubleOrNull() ?: 0.0) * 60).toLong()
    val distanceKm: Double? get() = Fmt.parse(distance)?.takeIf { it > 0 }
}

@HiltViewModel
class CardioLogViewModel @Inject constructor(
    private val repo: CardioRepository,
    private val bodyWeight: BodyWeightProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val workoutId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(CardioLogState(isNew = workoutId == null))
    val state: StateFlow<CardioLogState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val weight = bodyWeight.currentKg()
            val w = workoutId?.let { repo.getWorkout(it) }
            _state.value = if (w == null) {
                // По умолчанию — тренировка, закончившаяся сейчас.
                CardioLogState(loading = false, startedAt = System.currentTimeMillis() - 30 * 60_000L, weightKg = weight)
            } else {
                CardioLogState(
                    loading = false,
                    isNew = false,
                    type = CardioType.of(w.cardioType),
                    startedAt = w.startedAt,
                    minutes = Fmt.num(w.durationSec / 60.0, 1),
                    distance = w.distanceKm?.let { Fmt.num(it, 2) }.orEmpty(),
                    intensity = w.intensity?.let { runCatching { Intensity.valueOf(it) }.getOrNull() } ?: Intensity.MODERATE,
                    note = w.note.orEmpty(),
                    weightKg = weight,
                )
            }
        }
    }

    fun update(transform: (CardioLogState) -> CardioLogState) = _state.update(transform)

    fun save(defaultName: String, onDone: () -> Unit) {
        val s = _state.value
        if (s.durationSec <= 0) return
        viewModelScope.launch {
            repo.saveCardio(
                CardioEntry(
                    id = workoutId ?: 0,
                    type = s.type,
                    name = defaultName,
                    startedAt = s.startedAt,
                    durationSec = s.durationSec,
                    distanceKm = s.distanceKm,
                    intensity = s.intensity,
                    note = s.note,
                ),
            )
            onDone()
        }
    }
}

@Composable
fun CardioLogScreen(onBack: () -> Unit, viewModel: CardioLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val typeName = stringResource(state.type.label)
    IronScaffold(
        title = stringResource(if (state.isNew) R.string.cardio_log else R.string.cardio_edit),
        onBack = onBack,
    ) { padding ->
        if (state.loading) return@IronScaffold
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
            Text(stringResource(R.string.cardio_type).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioType.entries.forEach { t ->
                    IronChip(stringResource(t.label), selected = t == state.type, icon = t.icon, onClick = { viewModel.update { it.copy(type = t) } })
                }
            }
            IronCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_edit_date).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        Text(Fmt.dateTime(state.startedAt), style = MaterialTheme.typography.bodyLarge)
                    }
                    GhostButton(stringResource(R.string.cardio_date), { pickDate = true })
                    GhostButton(stringResource(R.string.cardio_time), { pickTime = true })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IronTextField(
                    value = state.minutes,
                    onValueChange = { v -> viewModel.update { it.copy(minutes = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)) } },
                    label = stringResource(R.string.cardio_minutes),
                    keyboardType = KeyboardType.Decimal,
                    textStyle = Iron.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
                if (state.type.hasDistance) {
                    IronTextField(
                        value = state.distance,
                        onValueChange = { v -> viewModel.update { it.copy(distance = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) } },
                        label = stringResource(R.string.cardio_distance),
                        keyboardType = KeyboardType.Decimal,
                        textStyle = Iron.numbers.medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(stringResource(R.string.cardio_intensity).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Segmented(
                items = Intensity.entries,
                selected = state.intensity,
                label = { stringResource(it.label) },
                onSelect = { i -> viewModel.update { it.copy(intensity = i) } },
            )
            IronCard(modifier = Modifier.fillMaxWidth()) {
                val kcal = Calories.kcal(state.type.met(state.intensity), state.weightKg, state.durationSec)
                Text(stringResource(R.string.approx_kcal, kcal.toInt()), style = Iron.numbers.medium)
                paceText(state.type, state.durationSec, state.distanceKm)?.let {
                    Text(it, style = Iron.numbers.small, color = Iron.colors.textSecondary)
                }
                Text(stringResource(R.string.cardio_kcal_note), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
            IronTextField(
                value = state.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = stringResource(R.string.routine_note),
                singleLine = false,
                minLines = 2,
            )
            PrimaryButton(
                stringResource(R.string.action_save),
                { viewModel.save(typeName, onBack) },
                enabled = state.durationSec > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val dt = Fmt.toLocalDateTime(state.startedAt)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                GhostButton(stringResource(R.string.action_ok), {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val millis = LocalDateTime.of(date, dt.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        viewModel.update { it.copy(startedAt = millis) }
                    }
                    pickDate = false
                })
            },
            dismissButton = { GhostButton(stringResource(R.string.action_cancel), { pickDate = false }, color = Iron.colors.textSecondary) },
            colors = DatePickerDefaults.colors(containerColor = Iron.colors.surface),
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = Iron.colors.surface))
        }
    }
    if (pickTime) {
        val dt = Fmt.toLocalDateTime(state.startedAt)
        TextInputDialog(
            title = stringResource(R.string.cardio_time),
            initial = Fmt.time(state.startedAt),
            supportingText = stringResource(R.string.cardio_time_hint),
            validate = { parseTime(it) != null },
            onConfirm = { text ->
                parseTime(text)?.let { t ->
                    val millis = LocalDateTime.of(dt.toLocalDate(), t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    viewModel.update { it.copy(startedAt = millis) }
                }
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

private fun parseTime(text: String): LocalTime? {
    val parts = text.trim().split(':', '.')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return LocalTime.of(h, m)
}

/** Темп для бега и ходьбы (мин/км) или скорость для остального (км/ч). */
@Composable
fun paceText(type: CardioType, durationSec: Long, distanceKm: Double?): String? {
    if (distanceKm == null || distanceKm <= 0 || durationSec <= 0) return null
    return if (type == CardioType.RUN || type == CardioType.WALK) {
        val secPerKm = (durationSec / distanceKm).toLong()
        stringResource(R.string.cardio_pace, Fmt.duration(secPerKm))
    } else {
        stringResource(R.string.cardio_speed, Fmt.num(distanceKm / (durationSec / 3600.0), 1))
    }
}
