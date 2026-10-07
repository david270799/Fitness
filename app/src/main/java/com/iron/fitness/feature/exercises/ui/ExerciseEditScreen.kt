package com.iron.fitness.feature.exercises.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.MultiChipFlow
import com.iron.fitness.core.ui.components.PhotoPickerBlock
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.feature.exercises.data.ExerciseCategory
import com.iron.fitness.feature.exercises.data.RecordType
import com.iron.fitness.feature.exercises.model.Equipment
import com.iron.fitness.feature.exercises.model.Muscle
import com.iron.fitness.feature.exercises.model.label
import java.io.File

@Composable
fun ExerciseEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    IronScaffold(
        title = stringResource(if (viewModel.isNew) R.string.exercise_edit_new else R.string.exercise_edit_title),
        onBack = onBack,
        actions = {
            IronIconButton(IronIcons.Check, stringResource(R.string.action_save), { viewModel.save(onSaved) })
        },
    ) { padding ->
        if (!form.loaded) return@IronScaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IronTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = stringResource(R.string.exercise_edit_name),
                isError = form.nameError,
                supportingText = if (form.nameError) stringResource(R.string.exercise_edit_name_required) else null,
            )
            FieldLabel(stringResource(R.string.exercise_edit_category))
            Segmented(
                items = ExerciseCategory.entries,
                selected = form.category,
                label = { stringResource(it.label) },
                onSelect = viewModel::setCategory,
            )
            FieldLabel(stringResource(R.string.exercise_record_type))
            Segmented(
                items = RecordType.entries,
                selected = form.recordType,
                label = { stringResource(it.label) },
                onSelect = viewModel::setRecordType,
            )
            FieldLabel(stringResource(R.string.exercise_edit_muscles))
            MultiChipFlow(
                items = Muscle.entries,
                selected = form.muscles,
                label = { stringResource(it.label) },
                onToggle = viewModel::toggleMuscle,
            )
            FieldLabel(stringResource(R.string.exercise_edit_equipment))
            MultiChipFlow(
                items = Equipment.entries,
                selected = setOfNotNull(form.equipment),
                label = { stringResource(it.label) },
                onToggle = viewModel::setEquipment,
            )
            IronTextField(
                value = form.rest,
                onValueChange = viewModel::setRest,
                label = stringResource(R.string.exercise_rest),
                placeholder = stringResource(R.string.exercise_edit_rest_hint),
                keyboardType = KeyboardType.Number,
            )
            IronTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = stringResource(R.string.exercise_note),
                placeholder = stringResource(R.string.exercise_note_hint),
                singleLine = false,
                minLines = 3,
            )
            FieldLabel(stringResource(R.string.exercise_edit_photo))
            PhotoPickerBlock(
                photo = form.photoPath?.let { File(it) },
                onPickGallery = viewModel::onGalleryPicked,
                requestCameraUri = viewModel::newCameraUri,
                onCameraResult = viewModel::onCameraResult,
                onRemove = viewModel::removePhoto,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                stringResource(R.string.action_save),
                onClick = { viewModel.save(onSaved) },
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = com.iron.fitness.core.ui.theme.Iron.colors.textSecondary)
}
