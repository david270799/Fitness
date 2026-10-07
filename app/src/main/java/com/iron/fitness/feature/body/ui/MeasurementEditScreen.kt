package com.iron.fitness.feature.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyMetric
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.MeasurementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@Composable
fun metricLabel(m: BodyMetric): String = stringResource(
    when (m) {
        BodyMetric.WEIGHT -> R.string.body_weight
        BodyMetric.BODY_FAT -> R.string.body_fat
        BodyMetric.WAIST -> R.string.body_waist
        BodyMetric.CHEST -> R.string.body_chest
        BodyMetric.HIPS -> R.string.body_hips
        BodyMetric.ARM -> R.string.body_arm
        BodyMetric.THIGH -> R.string.body_thigh
        BodyMetric.CALF -> R.string.body_calf
        BodyMetric.NECK -> R.string.body_neck
    },
)

@Composable
fun metricUnit(m: BodyMetric): String = when {
    m == BodyMetric.WEIGHT -> stringResource(R.string.unit_kg)
    m == BodyMetric.BODY_FAT -> stringResource(R.string.unit_percent)
    else -> stringResource(R.string.unit_cm)
}

data class MeasurementForm(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val day: Long = LocalDate.now().toEpochDay(),
    val values: Map<BodyMetric, String> = emptyMap(),
    val note: String = "",
)

@HiltViewModel
class MeasurementEditViewModel @Inject constructor(
    private val repo: BodyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var existing: MeasurementEntity? = null
    private val _form = MutableStateFlow(MeasurementForm(isNew = id == null))
    val form: StateFlow<MeasurementForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val m = id?.let { repo.getMeasurement(it) }
            existing = m
            _form.value = if (m == null) MeasurementForm(loading = false) else MeasurementForm(
                loading = false,
                isNew = false,
                day = m.day,
                values = BodyMetric.entries.mapNotNull { metric -> metric.valueOf(m)?.let { metric to Fmt.num(it, 2) } }.toMap(),
                note = m.note.orEmpty(),
            )
        }
    }

    fun setValue(m: BodyMetric, v: String) = _form.update { it.copy(values = it.values + (m to v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6))) }
    fun setDay(day: Long) = _form.update { it.copy(day = day) }
    fun setNote(v: String) = _form.update { it.copy(note = v) }

    val canSave: Boolean get() = _form.value.values.values.any { (Fmt.parse(it) ?: 0.0) > 0 }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        fun v(m: BodyMetric) = f.values[m]?.let { Fmt.parse(it) }?.takeIf { it > 0 }
        if (BodyMetric.entries.none { v(it) != null }) return
        viewModelScope.launch {
            val base = existing ?: MeasurementEntity(day = f.day)
            repo.saveMeasurement(
                base.copy(
                    day = f.day,
                    weightKg = v(BodyMetric.WEIGHT),
                    bodyFatPct = v(BodyMetric.BODY_FAT),
                    waistCm = v(BodyMetric.WAIST),
                    chestCm = v(BodyMetric.CHEST),
                    hipsCm = v(BodyMetric.HIPS),
                    armCm = v(BodyMetric.ARM),
                    thighCm = v(BodyMetric.THIGH),
                    calfCm = v(BodyMetric.CALF),
                    neckCm = v(BodyMetric.NECK),
                    note = f.note.trim().ifBlank { null },
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val m = existing ?: return onDone()
        viewModelScope.launch {
            repo.deleteMeasurement(m.id)
            onDone()
        }
    }
}

@Composable
fun MeasurementEditScreen(onBack: () -> Unit, viewModel: MeasurementEditViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    IronScaffold(
        title = stringResource(if (f.isNew) R.string.body_add_measurement else R.string.body_measurement),
        onBack = onBack,
        actions = { GhostButton(stringResource(R.string.action_save), { viewModel.save(onBack) }) },
    ) { padding ->
        if (f.loading) return@IronScaffold
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IronCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_edit_date).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        Text(Fmt.dateFull(LocalDate.ofEpochDay(f.day)), style = MaterialTheme.typography.bodyLarge)
                    }
                    GhostButton(stringResource(R.string.action_edit), { pickDate = true })
                }
            }
            Text(stringResource(R.string.body_fill_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            BodyMetric.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        IronTextField(
                            value = f.values[m].orEmpty(),
                            onValueChange = { viewModel.setValue(m, it) },
                            label = metricLabel(m) + ", " + metricUnit(m),
                            keyboardType = KeyboardType.Decimal,
                            textStyle = Iron.numbers.small,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            IronTextField(
                value = f.note,
                onValueChange = viewModel::setNote,
                label = stringResource(R.string.routine_note),
                singleLine = false,
                minLines = 2,
            )
            Text(stringResource(R.string.body_how_to_measure), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            PrimaryButton(stringResource(R.string.action_save), { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth())
            if (!f.isNew) GhostButton(stringResource(R.string.action_delete), { confirmDelete = true }, color = Iron.colors.error)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(f.day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                GhostButton(stringResource(R.string.action_ok), {
                    state.selectedDateMillis?.let { viewModel.setDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    pickDate = false
                })
            },
            dismissButton = { GhostButton(stringResource(R.string.action_cancel), { pickDate = false }, color = Iron.colors.textSecondary) },
            colors = DatePickerDefaults.colors(containerColor = Iron.colors.surface),
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Iron.colors.surface))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.confirm_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
