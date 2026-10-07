package com.iron.fitness.feature.workouts.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.OneRepMax
import com.iron.fitness.core.domain.PlateCalculator
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.components.ChipRow
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}

private enum class ToolTab { PLATES, ONE_RM }

@Composable
fun ToolsScreen(onBack: () -> Unit, viewModel: ToolsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ToolTab.PLATES) }
    IronScaffold(title = stringResource(R.string.tools_title), onBack = onBack) { padding ->
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
            Segmented(
                items = ToolTab.entries,
                selected = tab,
                label = { stringResource(if (it == ToolTab.PLATES) R.string.tools_plates else R.string.tools_1rm) },
                onSelect = { tab = it },
            )
            when (tab) {
                ToolTab.PLATES -> PlatesCalculator(settings)
                ToolTab.ONE_RM -> OneRmCalculator()
            }
        }
    }
}

@Composable
private fun PlatesCalculator(settings: AppSettings) {
    var target by rememberSaveable { mutableStateOf("100") }
    val bars = listOf(20.0, 15.0, 10.0, 7.0).let { if (settings.barWeightKg in it) it else listOf(settings.barWeightKg) + it }
    var bar by rememberSaveable { mutableStateOf(settings.barWeightKg) }
    IronTextField(
        value = target,
        onValueChange = { v -> target = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
        label = stringResource(R.string.tools_target_weight),
        keyboardType = KeyboardType.Decimal,
        textStyle = Iron.numbers.medium,
    )
    Text(stringResource(R.string.tools_bar).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
    ChipRow(
        items = bars,
        selected = bar,
        label = { stringResource(R.string.value_kg, Fmt.num(it)) },
        onSelect = { bar = it },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    )
    val targetKg = Fmt.parse(target) ?: 0.0
    val result = PlateCalculator.calculate(targetKg, bar, settings.plates)
    IronCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tools_per_side).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            if (result.perSide.isEmpty()) {
                Text(stringResource(R.string.tools_only_bar), style = MaterialTheme.typography.titleMedium)
            } else {
                BarbellPicture(result.perSide)
                Text(
                    result.perSide.joinToString(" + ") { Fmt.num(it) },
                    style = Iron.numbers.medium,
                )
            }
            Text(stringResource(R.string.tools_achieved, Fmt.num(result.achievedKg)), style = MaterialTheme.typography.bodyLarge)
            if (result.remainderKg > 0.001) {
                Text(
                    stringResource(R.string.tools_remainder, Fmt.num(result.remainderKg)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Iron.colors.warning,
                )
            }
        }
    }
    Text(stringResource(R.string.tools_plates_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
}

/** Схематичный гриф: блины одной стороны, высота — по весу диска. */
@Composable
private fun BarbellPicture(plates: List<Double>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 96.dp)) {
        Box(Modifier.width(36.dp).height(10.dp).background(Iron.colors.textSecondary))
        Box(Modifier.width(8.dp).height(28.dp).background(Iron.colors.textSecondary))
        plates.forEach { p ->
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .width(plateWidth(p))
                    .height(plateHeight(p))
                    .clip(RoundedCornerShape(2.dp))
                    .background(Iron.colors.accent)
                    .border(1.dp, Iron.colors.border, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {}
        }
        Box(Modifier.width(24.dp).height(10.dp).background(Iron.colors.textSecondary))
    }
}

private fun plateHeight(kg: Double): Dp = when {
    kg >= 20 -> 96.dp
    kg >= 15 -> 84.dp
    kg >= 10 -> 72.dp
    kg >= 5 -> 56.dp
    kg >= 2.5 -> 44.dp
    else -> 34.dp
}

private fun plateWidth(kg: Double): Dp = when {
    kg >= 20 -> 16.dp
    kg >= 10 -> 13.dp
    kg >= 5 -> 10.dp
    else -> 8.dp
}

@Composable
private fun OneRmCalculator() {
    var weight by rememberSaveable { mutableStateOf("100") }
    var reps by rememberSaveable { mutableStateOf("5") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IronTextField(
            value = weight,
            onValueChange = { v -> weight = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
            label = stringResource(R.string.tools_weight),
            keyboardType = KeyboardType.Decimal,
            textStyle = Iron.numbers.medium,
            modifier = Modifier.weight(1f),
        )
        IronTextField(
            value = reps,
            onValueChange = { v -> reps = v.filter { it.isDigit() }.take(2) },
            label = stringResource(R.string.tools_reps),
            keyboardType = KeyboardType.Number,
            textStyle = Iron.numbers.medium,
            modifier = Modifier.weight(1f),
        )
    }
    val oneRm = OneRepMax.epley(Fmt.parse(weight) ?: 0.0, reps.toIntOrNull() ?: 0)
    IronCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tools_1rm_result).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(oneRm, 1), style = Iron.numbers.large, color = Iron.colors.accentText)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.labelLarge, color = Iron.colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(stringResource(R.string.tools_1rm_formula), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
    }
    if (oneRm > 0) {
        SectionTitle(stringResource(R.string.tools_percent_table))
        IronCard(modifier = Modifier.fillMaxWidth()) {
            TableRow(stringResource(R.string.tools_col_percent), stringResource(R.string.tools_col_weight), stringResource(R.string.tools_col_reps), header = true)
            OneRepMax.table(oneRm).forEach { row ->
                IronDivider()
                TableRow("${row.percent}", Fmt.num(roundToPlate(row.weightKg), 2), row.reps.toString())
            }
        }
    }
}

/** Округление до 2,5 кг (минимальный шаг пары блинов 1,25). */
private fun roundToPlate(kg: Double): Double = Math.round(kg / 2.5) * 2.5

@Composable
private fun TableRow(a: String, b: String, c: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else Iron.numbers.small
    val color = if (header) Iron.colors.textSecondary else Iron.colors.text
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(a, style = style, color = color, modifier = Modifier.weight(1f))
        Text(b, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(c, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
