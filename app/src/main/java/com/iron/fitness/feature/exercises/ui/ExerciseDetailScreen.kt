package com.iron.fitness.feature.exercises.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.IronBottomSheet
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.Tag
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.exercises.data.imageModels
import com.iron.fitness.feature.exercises.model.Equipment
import com.iron.fitness.feature.exercises.model.Level
import com.iron.fitness.feature.exercises.model.Muscle
import com.iron.fitness.feature.exercises.model.forceLabel
import com.iron.fitness.feature.exercises.model.label
import com.iron.fitness.feature.exercises.model.mechanicLabel

/**
 * Карточка упражнения. [historyContent] — слот для истории и рекордов (появляется на этапе тренировок).
 */
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    historyContent: @Composable (exerciseId: String) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val defaultRest by viewModel.defaultRest.collectAsStateWithLifecycle()
    var settingsSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    IronScaffold(
        title = stringResource(R.string.exercise_title),
        onBack = onBack,
        actions = {
            val e = exercise
            if (e != null) {
                IronIconButton(
                    IronIcons.Star,
                    stringResource(if (e.isFavorite) R.string.exercise_favorite_remove else R.string.exercise_favorite_add),
                    viewModel::toggleFavorite,
                    tint = if (e.isFavorite) Iron.colors.warning else Iron.colors.text,
                )
                if (e.isCustom) {
                    IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), { onEdit(e.id) })
                    IronIconButton(IronIcons.Delete, stringResource(R.string.action_delete), { confirmDelete = true })
                }
            }
        },
    ) { padding ->
        val e = exercise ?: return@IronScaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ImagesPager(e) }
            item { Header(e) }
            item { MusclesBlock(e) }
            if (e.instructions.isNotEmpty()) {
                item { TechniqueBlock(e.instructions) }
            }
            item {
                MySettingsBlock(e, defaultRest) { settingsSheet = true }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle(stringResource(R.string.exercise_history))
                    historyContent(e.id)
                }
            }
        }
    }

    val e = exercise
    if (settingsSheet && e != null) {
        SettingsSheet(
            exercise = e,
            onDismiss = { settingsSheet = false },
            onSave = { type, rest, note ->
                viewModel.saveSettings(type, rest, note)
                settingsSheet = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.exercise_delete_title),
            text = stringResource(R.string.exercise_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagesPager(e: ExerciseEntity) {
    val models = remember(e.customImagePath, e.images) { e.imageModels() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Iron.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (models.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IronIcon(IronIcons.Image, null, tint = Iron.colors.textSecondary, size = 36.dp)
                Text(stringResource(R.string.exercise_no_images), color = Iron.colors.textSecondary)
            }
        } else {
            val pager = rememberPagerState { models.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                SubcomposeAsyncImage(
                    model = models[page],
                    contentDescription = e.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                    loading = {
                        Box(Modifier.fillMaxSize().background(Iron.colors.surfaceHigh), contentAlignment = Alignment.Center) {
                            IronIcon(IronIcons.Image, null, tint = Iron.colors.textSecondary, size = 32.dp)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize().background(Iron.colors.surfaceHigh), contentAlignment = Alignment.Center) {
                            IronIcon(IronIcons.Cloud, null, tint = Iron.colors.textSecondary, size = 32.dp)
                        }
                    },
                )
            }
            if (models.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(models.size) { i ->
                        Box(
                            Modifier
                                .size(width = if (i == pager.currentPage) 18.dp else 8.dp, height = 4.dp)
                                .background(if (i == pager.currentPage) Iron.colors.accent else Iron.colors.border),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(e: ExerciseEntity) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(e.name, style = MaterialTheme.typography.headlineMedium)
        if (!e.isCustom) {
            Text(
                stringResource(R.string.exercise_original_name, e.nameEn),
                style = MaterialTheme.typography.bodySmall,
                color = Iron.colors.textSecondary,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(stringResource(e.category.label))
            if (e.isCustom) Tag(stringResource(R.string.exercise_custom_badge), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text)
            Equipment.of(e.equipment)?.let { Tag(stringResource(it.label), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text) }
            Level.of(e.level)?.let { Tag(stringResource(it.label), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text) }
            mechanicLabel(e.mechanic)?.let { Tag(stringResource(it), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text) }
            forceLabel(e.force)?.let { Tag(stringResource(it), color = Iron.colors.surfaceHigh, contentColor = Iron.colors.text) }
        }
    }
}

@Composable
private fun MusclesBlock(e: ExerciseEntity) {
    val primary = e.primaryMuscles.mapNotNull { Muscle.of(it) }
    val secondary = e.secondaryMuscles.mapNotNull { Muscle.of(it) }
    if (primary.isEmpty() && secondary.isEmpty()) return
    IronCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        if (primary.isNotEmpty()) {
            Text(stringResource(R.string.exercise_primary_muscles).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Text(primary.map { stringResource(it.label) }.joinToString(", "), style = MaterialTheme.typography.titleMedium, color = Iron.colors.accentText)
        }
        if (secondary.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.exercise_secondary_muscles).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Text(secondary.map { stringResource(it.label) }.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TechniqueBlock(steps: List<String>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.exercise_technique))
        steps.forEachIndexed { index, step ->
            Row {
                Text(
                    "%02d".format(index + 1),
                    style = Iron.numbers.small,
                    color = Iron.colors.accentText,
                    modifier = Modifier.width(36.dp),
                )
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MySettingsBlock(e: ExerciseEntity, defaultRest: Int, onEdit: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.exercise_my_settings)) {
            IronIconButton(IronIcons.Edit, stringResource(R.string.action_edit), onEdit, tint = Iron.colors.textSecondary)
        }
        IronCard(Modifier.fillMaxWidth()) {
            SettingLine(stringResource(R.string.exercise_record_type), stringResource(e.recordType.label))
            SettingLine(
                stringResource(R.string.exercise_rest),
                e.restSeconds?.let { Fmt.duration(it) } ?: stringResource(R.string.exercise_rest_default, Fmt.duration(defaultRest)),
            )
            if (!e.note.isNullOrBlank()) {
                SettingLine(stringResource(R.string.exercise_note), e.note)
            }
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsSheet(
    exercise: ExerciseEntity,
    onDismiss: () -> Unit,
    onSave: (RecordType, Int?, String) -> Unit,
) {
    var type by remember { mutableStateOf(exercise.recordType) }
    var rest by remember { mutableStateOf(exercise.restSeconds?.toString() ?: "") }
    var note by remember { mutableStateOf(exercise.note ?: "") }
    IronBottomSheet(title = stringResource(R.string.exercise_my_settings), onDismiss = onDismiss) {
        Text(stringResource(R.string.exercise_record_type), style = MaterialTheme.typography.labelLarge)
        Segmented(
            items = RecordType.entries,
            selected = type,
            label = { stringResource(it.label) },
            onSelect = { type = it },
        )
        IronTextField(
            value = rest,
            onValueChange = { v -> rest = v.filter { it.isDigit() }.take(4) },
            label = stringResource(R.string.exercise_rest),
            placeholder = stringResource(R.string.exercise_edit_rest_hint),
            keyboardType = KeyboardType.Number,
        )
        IronTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(R.string.exercise_note),
            placeholder = stringResource(R.string.exercise_note_hint),
            singleLine = false,
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.action_cancel), onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.action_save),
                onClick = { onSave(type, rest.toIntOrNull()?.takeIf { it > 0 }, note) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
