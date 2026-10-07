package com.iron.fitness.feature.workouts.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.domain.Progression
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.CompactNumberField
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.Tag
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.workouts.data.WorkoutSetEntity
import com.iron.fitness.feature.workouts.data.isRecord

/** Действия карточки упражнения. */
interface ExerciseCardActions {
    fun toggleSet(set: SetUi, ex: ExerciseUi)
    fun updateValues(setId: Long, weight: Double?, reps: Int?, seconds: Int?)
    fun setType(setId: Long, type: SetType)
    fun deleteSet(setId: Long)
    fun addSet(ex: ExerciseUi)
    fun move(ex: ExerciseUi, delta: Int)
    fun toggleSuperset(ex: ExerciseUi)
    fun editNote(ex: ExerciseUi)
    fun editRest(ex: ExerciseUi)
    fun remove(ex: ExerciseUi)
    fun open(ex: ExerciseUi)
}

@Composable
fun SessionExerciseCard(ex: ExerciseUi, actions: ExerciseCardActions) {
    val accent = Iron.colors.accent
    val inSuperset = ex.supersetPos != SupersetPos.NONE
    IronCard(
        contentPadding = PaddingValues(0.dp),
        borderColor = if (inSuperset) Iron.colors.accent.copy(alpha = 0.6f) else Iron.colors.border,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (inSuperset) drawRect(accent, size = Size(4.dp.toPx(), size.height))
            },
    ) {
        Header(ex, actions)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ex.we.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
            ex.suggestion?.let { TargetHint(it, ex.recordType) }
            Text(
                stringResource(R.string.session_rest_value, Fmt.duration(ex.restSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = Iron.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { actions.editRest(ex) }
                    .padding(vertical = 4.dp),
            )
        }
        ColumnHeaders(ex.recordType)
        ex.sets.forEach { set -> SetRow(set, ex, actions) }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.addSet(ex) }
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IronIcon(IronIcons.Add, null, tint = Iron.colors.accentText, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.session_add_set).uppercase(), style = MaterialTheme.typography.titleSmall, color = Iron.colors.accentText)
        }
    }
}

@Composable
private fun Header(ex: ExerciseUi, actions: ExerciseCardActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (ex.supersetLetter != null) {
                Tag("${stringResource(R.string.session_superset_label)} ${ex.supersetLetter}")
                Spacer(Modifier.height(4.dp))
            }
            Text(
                ex.exercise?.name ?: stringResource(R.string.session_unknown_exercise),
                style = MaterialTheme.typography.titleMedium,
                color = Iron.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = ex.exercise != null) { actions.open(ex) },
            )
        }
        Box {
            IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
            IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                IronMenuItem(stringResource(R.string.session_menu_up), { menu = false; actions.move(ex, -1) }, IronIcons.ChevronUp, enabled = !ex.isFirst)
                IronMenuItem(stringResource(R.string.session_menu_down), { menu = false; actions.move(ex, 1) }, IronIcons.ChevronDown, enabled = !ex.isLast)
                val linked = ex.supersetPos == SupersetPos.FIRST || ex.supersetPos == SupersetPos.MIDDLE
                IronMenuItem(
                    stringResource(if (linked) R.string.session_menu_unsuperset else R.string.session_menu_superset),
                    { menu = false; actions.toggleSuperset(ex) },
                    IronIcons.Link,
                    enabled = !ex.isLast,
                )
                IronMenuItem(stringResource(R.string.session_menu_note), { menu = false; actions.editNote(ex) }, IronIcons.Edit)
                IronMenuItem(stringResource(R.string.session_menu_rest), { menu = false; actions.editRest(ex) }, IronIcons.Timer)
                if (ex.exercise != null) {
                    IronMenuItem(stringResource(R.string.session_menu_open), { menu = false; actions.open(ex) }, IronIcons.Info)
                }
                IronMenuItem(stringResource(R.string.session_menu_remove), { menu = false; actions.remove(ex) }, IronIcons.Delete, color = Iron.colors.error)
            }
        }
    }
}

@Composable
private fun TargetHint(s: Progression.Suggestion, recordType: RecordType) {
    val value = when {
        recordType == RecordType.WEIGHT_REPS && s.weightKg != null ->
            stringResource(R.string.set_weight_reps, Fmt.num(s.weightKg), s.reps)
        else -> stringResource(R.string.set_reps_only, s.reps)
    }
    val reason = when (s.reason) {
        Progression.Reason.INCREASE_WEIGHT -> stringResource(R.string.session_hint_increase)
        Progression.Reason.ADD_REPS -> stringResource(R.string.session_hint_add_reps)
        Progression.Reason.DELOAD -> stringResource(R.string.session_hint_deload)
        Progression.Reason.BODYWEIGHT_REPS -> stringResource(R.string.session_hint_bodyweight)
        Progression.Reason.NO_HISTORY -> ""
    }
    Text(
        stringResource(R.string.session_target, value) + if (reason.isNotEmpty()) " · $reason" else "",
        style = MaterialTheme.typography.bodySmall,
        color = Iron.colors.accentText,
    )
}

private val LabelWidth = 40.dp
private val WeightWidth = 72.dp
private val RepsWidth = 60.dp
private val CheckWidth = 44.dp

@Composable
private fun ColumnHeaders(recordType: RecordType) {
    val style = MaterialTheme.typography.labelSmall
    val color = Iron.colors.textSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.session_col_set), style = style, color = color, modifier = Modifier.width(LabelWidth), textAlign = TextAlign.Center, maxLines = 1)
        Text(stringResource(R.string.session_col_previous), style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (recordType) {
            RecordType.WEIGHT_REPS -> {
                Text(stringResource(R.string.session_col_kg), style = style, color = color, modifier = Modifier.width(WeightWidth), textAlign = TextAlign.Center)
                Text(stringResource(R.string.session_col_reps), style = style, color = color, modifier = Modifier.width(RepsWidth), textAlign = TextAlign.Center)
            }
            RecordType.REPS ->
                Text(stringResource(R.string.session_col_reps), style = style, color = color, modifier = Modifier.width(RepsWidth), textAlign = TextAlign.Center)
            RecordType.TIME ->
                Text(stringResource(R.string.session_col_sec), style = style, color = color, modifier = Modifier.width(WeightWidth), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(CheckWidth))
    }
}

@Composable
private fun SetRow(ui: SetUi, ex: ExerciseUi, actions: ExerciseCardActions) {
    val s = ui.set
    var weightText by remember(s.id) { mutableStateOf(Fmt.weightInput(s.weightKg)) }
    var repsText by remember(s.id) { mutableStateOf(s.reps?.toString() ?: "") }
    var secText by remember(s.id) { mutableStateOf(s.durationSec?.toString() ?: "") }
    // При отметке подхода пустые поля заполняются подсказкой в базе — подтягиваем значения.
    // Во время ввода текст не перезаписываем, чтобы не мешать набору.
    LaunchedEffect(s.completed) {
        if (Fmt.parse(weightText) != s.weightKg) weightText = Fmt.weightInput(s.weightKg)
        if (repsText.toIntOrNull() != s.reps) repsText = s.reps?.toString() ?: ""
        if (secText.toIntOrNull() != s.durationSec) secText = s.durationSec?.toString() ?: ""
    }

    fun push() = actions.updateValues(s.id, Fmt.parse(weightText), repsText.toIntOrNull(), secText.toIntOrNull())

    val done = s.completed
    val rowBg = if (done) Iron.colors.success.copy(alpha = 0.14f) else Color.Transparent
    val fieldBg = if (done) Iron.colors.success.copy(alpha = 0.10f) else Iron.colors.surfaceHigh
    Column(Modifier.background(rowBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetLabel(ui, actions)
            Text(
                previousText(ui.previous, ex.recordType),
                style = Iron.numbers.tiny,
                color = Iron.colors.textSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (ex.recordType) {
                RecordType.WEIGHT_REPS -> {
                    CompactNumberField(
                        value = weightText,
                        onValueChange = { weightText = it; push() },
                        placeholder = ui.placeholderWeight?.let { Fmt.num(it) } ?: "",
                        modifier = Modifier.width(WeightWidth),
                        background = fieldBg,
                    )
                    CompactNumberField(
                        value = repsText,
                        onValueChange = { repsText = it; push() },
                        placeholder = ui.placeholderReps?.toString() ?: "",
                        decimal = false,
                        modifier = Modifier.width(RepsWidth),
                        background = fieldBg,
                    )
                }
                RecordType.REPS -> CompactNumberField(
                    value = repsText,
                    onValueChange = { repsText = it; push() },
                    placeholder = ui.placeholderReps?.toString() ?: "",
                    decimal = false,
                    modifier = Modifier.width(RepsWidth),
                    background = fieldBg,
                )
                RecordType.TIME -> CompactNumberField(
                    value = secText,
                    onValueChange = { secText = it; push() },
                    placeholder = ui.placeholderSeconds?.toString() ?: "",
                    decimal = false,
                    modifier = Modifier.width(WeightWidth),
                    background = fieldBg,
                )
            }
            CheckBox(done) { actions.toggleSet(ui, ex) }
        }
        if (done && s.isRecord) RecordLine(s)
    }
}

@Composable
private fun SetLabel(ui: SetUi, actions: ExerciseCardActions) {
    var menu by remember { mutableStateOf(false) }
    val type = ui.set.setType
    val (text, color) = when (type) {
        SetType.WARMUP -> stringResource(R.string.session_set_type_warmup_short) to Iron.colors.warning
        SetType.DROP -> stringResource(R.string.session_set_type_drop_short) to Iron.colors.accentText
        SetType.FAILURE -> stringResource(R.string.session_set_type_failure_short) to Iron.colors.error
        SetType.NORMAL -> (ui.number?.toString() ?: "–") to Iron.colors.text
    }
    Box {
        Box(
            Modifier
                .width(LabelWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = Iron.numbers.small.copy(fontWeight = FontWeight.Bold), color = color)
        }
        IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
            SetType.entries.forEach { t ->
                IronMenuItem(
                    text = stringResource(
                        when (t) {
                            SetType.NORMAL -> R.string.session_set_normal
                            SetType.WARMUP -> R.string.session_set_warmup
                            SetType.DROP -> R.string.session_set_drop
                            SetType.FAILURE -> R.string.session_set_failure
                        },
                    ),
                    onClick = { menu = false; actions.setType(ui.set.id, t) },
                    icon = if (t == type) IronIcons.Check else null,
                )
            }
            IronDivider()
            IronMenuItem(stringResource(R.string.session_set_delete), { menu = false; actions.deleteSet(ui.set.id) }, IronIcons.Delete, color = Iron.colors.error)
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(CheckWidth, 40.dp)
            .clip(shape)
            .background(if (checked) Iron.colors.success else Iron.colors.surfaceHigh)
            .border(1.dp, if (checked) Iron.colors.success else Iron.colors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        IronIcon(
            IronIcons.Check,
            stringResource(R.string.action_done),
            tint = if (checked) Iron.colors.background else Iron.colors.textSecondary,
            size = 22.dp,
        )
    }
}

@Composable
private fun RecordLine(s: WorkoutSetEntity) {
    val kinds = buildList {
        if (s.isWeightPr) add(stringResource(R.string.record_kind_weight))
        if (s.isOneRmPr) add(stringResource(R.string.record_kind_one_rm))
        if (s.isRepsPr) add(stringResource(R.string.record_kind_reps))
        if (s.isDurationPr) add(stringResource(R.string.record_kind_duration))
    }
    Row(
        Modifier.padding(start = 54.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag(stringResource(R.string.session_record))
        Spacer(Modifier.width(8.dp))
        Text(kinds.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Iron.colors.accentText)
    }
}

@Composable
fun previousText(prev: WorkoutSetEntity?, recordType: RecordType): String {
    if (prev == null) return stringResource(R.string.dash)
    return setText(prev, recordType)
}

/** Подход текстом: «82,5 × 8», «12», «1:30». */
fun setText(s: WorkoutSetEntity, recordType: RecordType): String = when (recordType) {
    RecordType.WEIGHT_REPS -> {
        val w = s.weightKg
        val r = s.reps ?: 0
        if (w != null && w > 0) "${Fmt.num(w)}×$r" else "$r"
    }
    RecordType.REPS -> (s.reps ?: 0).toString()
    RecordType.TIME -> Fmt.duration(s.durationSec ?: 0)
}
