package com.iron.fitness.feature.cardio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.Intervals
import com.iron.fitness.core.domain.Phase
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronSwitch
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.cardio.data.IntervalProgram
import com.iron.fitness.feature.cardio.run.phaseColor
import com.iron.fitness.feature.timer.phaseName
import com.iron.fitness.feature.timer.phaseTypeName

private val STEP_TYPES = listOf(BlockType.WARMUP, BlockType.WORK, BlockType.REST, BlockType.COOLDOWN)
private val WORK_METS = listOf(IntervalProgram.WORK_MET_MODERATE, IntervalProgram.WORK_MET_HARD, IntervalProgram.WORK_MET_MAX)

/** Разбор «1:30» или «90» в секунды. */
fun parseDuration(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return if (':' in t) {
        val parts = t.split(':')
        if (parts.size != 2) return null
        val m = parts[0].toIntOrNull() ?: return null
        val s = parts[1].toIntOrNull() ?: return null
        if (s !in 0..59) null else m * 60 + s
    } else {
        t.toIntOrNull()
    }
}

@Composable
fun IntervalEditorScreen(
    onBack: () -> Unit,
    onPickExercise: () -> Unit,
    pickedExercises: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: IntervalEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var durationFor by remember { mutableStateOf<EditorBlock?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(pickedExercises) {
        if (!pickedExercises.isNullOrEmpty()) {
            viewModel.setExercise(pickedExercises)
            onPickedHandled()
        }
    }

    val blocks = state.toBlocks()
    val phases = Intervals.expand(blocks)
    val actions = remember(viewModel) {
        BlockActions(
            onDuration = { durationFor = it },
            onSeconds = { key, sec -> viewModel.update(key) { it.copy(seconds = sec.coerceIn(1, 3 * 3600)) } },
            onType = { key, type -> viewModel.update(key) { it.copy(type = type) } },
            onLabel = { key, text -> viewModel.update(key) { it.copy(label = text, exerciseId = null) } },
            onPick = { key -> viewModel.pickTargetKey = key; onPickExercise() },
            onRounds = { key, n -> viewModel.update(key) { it.copy(rounds = n.coerceIn(1, Intervals.MAX_ROUNDS)) } },
            onSkipLast = { key, v -> viewModel.update(key) { it.copy(skipLastRest = v) } },
            onMove = viewModel::move,
            onRemove = viewModel::remove,
            onAddChild = viewModel::addChild,
        )
    }

    IronScaffold(
        title = stringResource(if (state.isNew) R.string.intervals_new else R.string.intervals_edit),
        onBack = onBack,
        actions = { GhostButton(stringResource(R.string.action_save), { viewModel.save(onBack) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "name") {
                IronTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.routine_name),
                    placeholder = stringResource(R.string.intervals_name_hint),
                    isError = state.nameError,
                    supportingText = if (state.nameError) stringResource(R.string.routine_name_required) else null,
                )
            }
            item(key = "intensity") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.intervals_work_intensity).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    Segmented(
                        items = WORK_METS,
                        selected = WORK_METS.minByOrNull { kotlin.math.abs(it - state.workMet) } ?: IntervalProgram.WORK_MET_HARD,
                        label = {
                            stringResource(
                                when (it) {
                                    IntervalProgram.WORK_MET_MODERATE -> R.string.intensity_moderate
                                    IntervalProgram.WORK_MET_HARD -> R.string.intensity_vigorous
                                    else -> R.string.intensity_max
                                },
                            )
                        },
                        onSelect = viewModel::setWorkMet,
                    )
                }
            }
            item(key = "preview") { PreviewCard(phases, expanded = showPreview, onToggle = { showPreview = !showPreview }) }
            item(key = "blocks_title") { SectionTitle(stringResource(R.string.intervals_blocks)) }
            if (state.emptyError) {
                item(key = "empty_error") {
                    Text(stringResource(R.string.intervals_empty_error), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.error)
                }
            }
            items(state.blocks, key = { it.key }) { b ->
                val index = state.blocks.indexOf(b)
                if (b.type == BlockType.REPEAT) {
                    RepeatCard(b, isFirst = index == 0, isLast = index == state.blocks.lastIndex, actions = actions)
                } else {
                    StepCard(b, isFirst = index == 0, isLast = index == state.blocks.lastIndex, actions = actions)
                }
            }
            item(key = "add") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.intervals_add_block).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (STEP_TYPES + BlockType.REPEAT).forEach { t ->
                            IronChip(
                                text = typeLabel(t),
                                selected = false,
                                icon = if (t == BlockType.REPEAT) IronIcons.Repeat else IronIcons.Add,
                                onClick = { viewModel.addTop(t) },
                            )
                        }
                    }
                }
            }
            item(key = "note") {
                IronTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = stringResource(R.string.routine_note),
                    singleLine = false,
                    minLines = 2,
                )
            }
            item(key = "space") { Spacer(Modifier.height(32.dp)) }
        }
    }

    durationFor?.let { b ->
        TextInputDialog(
            title = stringResource(R.string.intervals_duration),
            initial = Fmt.duration(b.seconds),
            supportingText = stringResource(R.string.intervals_duration_hint),
            keyboardType = KeyboardType.Text,
            validate = { (parseDuration(it) ?: 0) > 0 },
            onConfirm = { text ->
                parseDuration(text)?.let { sec -> actions.onSeconds(b.key, sec) }
                durationFor = null
            },
            onDismiss = { durationFor = null },
        )
    }
}

/** Колбэки карточек блоков. */
class BlockActions(
    val onDuration: (EditorBlock) -> Unit,
    val onSeconds: (Long, Int) -> Unit,
    val onType: (Long, BlockType) -> Unit,
    val onLabel: (Long, String) -> Unit,
    val onPick: (Long) -> Unit,
    val onRounds: (Long, Int) -> Unit,
    val onSkipLast: (Long, Boolean) -> Unit,
    val onMove: (Long, Int) -> Unit,
    val onRemove: (Long) -> Unit,
    val onAddChild: (Long, BlockType) -> Unit,
)

@Composable
private fun typeLabel(type: BlockType): String = phaseTypeName(LocalContext.current.resources, type)

@Composable
private fun StepCard(b: EditorBlock, isFirst: Boolean, isLast: Boolean, actions: BlockActions, nested: Boolean = false) {
    val color = phaseColor(b.type)
    var typeMenu by remember { mutableStateOf(false) }
    IronCard(
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
        color = if (nested) Iron.colors.surfaceHigh else Iron.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(color, size = Size(4.dp.toPx(), size.height)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { typeMenu = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(typeLabel(b.type).uppercase(), style = MaterialTheme.typography.titleSmall, color = Iron.colors.text)
                    IronIcon(IronIcons.ChevronDown, null, size = 16.dp, tint = Iron.colors.textSecondary)
                }
                IronDropdownMenu(expanded = typeMenu, onDismiss = { typeMenu = false }) {
                    STEP_TYPES.forEach { t ->
                        IronMenuItem(typeLabel(t), { typeMenu = false; actions.onType(b.key, t) }, if (t == b.type) IronIcons.Check else null)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            DurationStepper(b, actions)
            BlockMenu(b, isFirst, isLast, actions)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            IronTextField(
                value = b.label,
                onValueChange = { actions.onLabel(b.key, it) },
                placeholder = stringResource(R.string.intervals_label_hint),
                modifier = Modifier.weight(1f),
            )
            if (b.type == BlockType.WORK) {
                IronIconButton(IronIcons.Search, stringResource(R.string.intervals_pick_exercise), { actions.onPick(b.key) })
            }
        }
        if (b.exerciseId != null) {
            Text(
                stringResource(R.string.intervals_linked_exercise),
                style = MaterialTheme.typography.labelSmall,
                color = Iron.colors.accentText,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DurationStepper(b: EditorBlock, actions: BlockActions) {
    val step = if (b.seconds <= 60) 5 else 15
    Row(verticalAlignment = Alignment.CenterVertically) {
        IronIconButton(IronIcons.Remove, null, { actions.onSeconds(b.key, (b.seconds - step).coerceAtLeast(5)) })
        Text(
            Fmt.duration(b.seconds),
            style = Iron.numbers.small,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { actions.onDuration(b) }
                .padding(vertical = 6.dp),
        )
        IronIconButton(IronIcons.Add, null, { actions.onSeconds(b.key, b.seconds + step) })
    }
}

@Composable
private fun BlockMenu(b: EditorBlock, isFirst: Boolean, isLast: Boolean, actions: BlockActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
        IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
            IronMenuItem(stringResource(R.string.session_menu_up), { menu = false; actions.onMove(b.key, -1) }, IronIcons.ChevronUp, enabled = !isFirst)
            IronMenuItem(stringResource(R.string.session_menu_down), { menu = false; actions.onMove(b.key, 1) }, IronIcons.ChevronDown, enabled = !isLast)
            IronMenuItem(stringResource(R.string.action_delete), { menu = false; actions.onRemove(b.key) }, IronIcons.Delete, color = Iron.colors.error)
        }
    }
}

@Composable
private fun RepeatCard(b: EditorBlock, isFirst: Boolean, isLast: Boolean, actions: BlockActions) {
    IronCard(
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
        borderColor = Iron.colors.accent.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIcon(IronIcons.Repeat, null, tint = Iron.colors.accentText, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.intervals_repeat).uppercase(), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            IronIconButton(IronIcons.Remove, null, { actions.onRounds(b.key, b.rounds - 1) })
            Text("× ${b.rounds}", style = Iron.numbers.small, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
            IronIconButton(IronIcons.Add, null, { actions.onRounds(b.key, b.rounds + 1) })
            BlockMenu(b, isFirst, isLast, actions)
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.children.forEachIndexed { i, c ->
                StepCard(c, isFirst = i == 0, isLast = i == b.children.lastIndex, actions = actions, nested = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IronChip(typeLabel(BlockType.WORK), selected = false, icon = IronIcons.Add, onClick = { actions.onAddChild(b.key, BlockType.WORK) })
                IronChip(typeLabel(BlockType.REST), selected = false, icon = IronIcons.Add, onClick = { actions.onAddChild(b.key, BlockType.REST) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.intervals_skip_last_rest),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IronSwitch(checked = b.skipLastRest, onCheckedChange = { actions.onSkipLast(b.key, it) })
            }
        }
    }
}

@Composable
private fun PreviewCard(phases: List<Phase>, expanded: Boolean, onToggle: () -> Unit) {
    val res = LocalContext.current.resources
    val total = phases.sumOf { it.seconds }
    IronCard(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.intervals_total).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                Text(Fmt.duration(total), style = Iron.numbers.medium)
            }
            Text(pluralStringResource(R.plurals.blocks, phases.size, phases.size), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            IronIcon(if (expanded) IronIcons.ChevronUp else IronIcons.ChevronDown, null, tint = Iron.colors.textSecondary)
        }
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            // Лента программы: ширина отрезка пропорциональна длительности.
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(2.dp))) {
                phases.filter { it.seconds > 0 }.forEach { p ->
                    Box(
                        Modifier
                            .weight(p.seconds.toFloat())
                            .height(14.dp)
                            .background(phaseColor(p.type)),
                    )
                    Box(Modifier.width(1.dp).height(14.dp).background(Iron.colors.surface))
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            var elapsed = 0
            phases.forEach { p ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.duration(elapsed), style = Iron.numbers.tiny, color = Iron.colors.textSecondary, modifier = Modifier.width(52.dp))
                    Box(Modifier.width(8.dp).height(8.dp).background(phaseColor(p.type)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        phaseName(res, p) + if (p.rounds > 0) " · ${p.round}/${p.rounds}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (p.seconds > 0) Fmt.duration(p.seconds) else "∞", style = Iron.numbers.tiny)
                }
                elapsed += p.seconds
            }
        }
    }
}
