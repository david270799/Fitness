package com.iron.fitness.feature.reminders.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.ReminderSchedule
import com.iron.fitness.core.media.PhotoKind
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PhotoPickerBlock
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.reminders.data.ReminderAlarms
import com.iron.fitness.feature.reminders.data.ReminderEntity
import com.iron.fitness.feature.reminders.data.ReminderRepository
import com.iron.fitness.feature.reminders.data.timeList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject

data class ReminderForm(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val dose: String = "",
    val note: String = "",
    val photoPath: String? = null,
    val times: List<Int> = listOf(9 * 60),
    val weekdays: Int = ReminderSchedule.EVERY_DAY,
    val enabled: Boolean = true,
    val nameError: Boolean = false,
    val timesError: Boolean = false,
)

@HiltViewModel
class ReminderEditViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val alarms: ReminderAlarms,
    private val photos: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var existing: ReminderEntity? = null
    /** Фото, загруженные в этой сессии (удаляются, если не сохранены). */
    private val tempPhotos = mutableSetOf<String>()

    private val _form = MutableStateFlow(ReminderForm(isNew = id == null))
    val form: StateFlow<ReminderForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val r = id?.let { repo.get(it) }
            existing = r
            _form.value = if (r == null) ReminderForm(loading = false) else ReminderForm(
                loading = false,
                isNew = false,
                name = r.name,
                dose = r.dose.orEmpty(),
                note = r.note.orEmpty(),
                photoPath = r.photoPath,
                times = r.timeList,
                weekdays = r.weekdays,
                enabled = r.enabled,
            )
        }
    }

    fun update(t: (ReminderForm) -> ReminderForm) = _form.update(t)
    fun addTime(minutes: Int) = _form.update { it.copy(times = (it.times + minutes).distinct().sorted(), timesError = false) }
    fun removeTime(minutes: Int) = _form.update { it.copy(times = it.times - minutes) }
    fun toggleDay(day: DayOfWeek) = _form.update { it.copy(weekdays = ReminderSchedule.toggleDay(it.weekdays, day)) }

    fun newCameraUri(): Uri {
        val (uri, file) = photos.newCameraTarget()
        savedStateHandle["pending_camera"] = file.absolutePath
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val path = savedStateHandle.get<String>("pending_camera") ?: return
        savedStateHandle.remove<String>("pending_camera")
        val file = File(path)
        if (!success) {
            file.delete()
            return
        }
        viewModelScope.launch { photos.importFile(file, PhotoKind.REMINDER)?.let(::setPhoto) }
    }

    fun onGalleryPicked(uri: Uri) {
        viewModelScope.launch { photos.import(uri, PhotoKind.REMINDER)?.let(::setPhoto) }
    }

    private fun setPhoto(path: String) {
        val previous = _form.value.photoPath
        if (previous != null && previous in tempPhotos) {
            photos.delete(previous)
            tempPhotos -= previous
        }
        tempPhotos += path
        _form.update { it.copy(photoPath = path) }
    }

    fun removePhoto() {
        val previous = _form.value.photoPath
        if (previous != null && previous in tempPhotos) {
            photos.delete(previous)
            tempPhotos -= previous
        }
        _form.update { it.copy(photoPath = null) }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        when {
            f.name.isBlank() -> { _form.update { it.copy(nameError = true) }; return }
            f.times.isEmpty() || f.weekdays and ReminderSchedule.EVERY_DAY == 0 -> { _form.update { it.copy(timesError = true) }; return }
        }
        viewModelScope.launch {
            val base = existing ?: ReminderEntity(name = f.name.trim(), times = "")
            val entity = base.copy(
                name = f.name.trim(),
                dose = f.dose.trim().ifBlank { null },
                note = f.note.trim().ifBlank { null },
                photoPath = f.photoPath,
                times = f.times.joinToString(","),
                weekdays = f.weekdays,
                enabled = f.enabled,
            )
            val savedId = repo.save(entity)
            val oldPhoto = existing?.photoPath
            if (oldPhoto != null && oldPhoto != f.photoPath) photos.delete(oldPhoto)
            f.photoPath?.let { tempPhotos.remove(it) }
            repo.get(savedId)?.let { alarms.scheduleNext(it) }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val r = existing ?: return onDone()
        viewModelScope.launch {
            alarms.cancel(r.id)
            repo.delete(r.id)
            onDone()
        }
    }

    override fun onCleared() {
        // Несохранённые фото не оставляем.
        tempPhotos.forEach { photos.delete(it) }
    }
}

@Composable
fun ReminderEditScreen(onBack: () -> Unit, onDeleted: () -> Unit, viewModel: ReminderEditViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var addTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    IronScaffold(
        title = stringResource(if (f.isNew) R.string.reminder_new else R.string.reminder_edit),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IronTextField(
                value = f.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v, nameError = false) } },
                label = stringResource(R.string.reminder_name),
                placeholder = stringResource(R.string.reminder_name_hint),
                isError = f.nameError,
                supportingText = if (f.nameError) stringResource(R.string.routine_name_required) else null,
            )
            IronTextField(
                value = f.dose,
                onValueChange = { v -> viewModel.update { it.copy(dose = v) } },
                label = stringResource(R.string.reminder_dose),
                placeholder = stringResource(R.string.reminder_dose_hint),
            )
            SectionTitle(stringResource(R.string.reminder_times))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                f.times.forEach { m ->
                    IronChip(Fmt.minutesOfDay(m), selected = true, icon = IronIcons.Close, onClick = { viewModel.removeTime(m) })
                }
                IronChip(stringResource(R.string.reminder_add_time), selected = false, icon = IronIcons.Add, onClick = { addTime = true })
            }
            SectionTitle(stringResource(R.string.reminder_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { d ->
                    IronChip(Fmt.dayOfWeekShort(d), selected = ReminderSchedule.isDayEnabled(f.weekdays, d), onClick = { viewModel.toggleDay(d) })
                }
            }
            if (f.timesError) {
                Text(stringResource(R.string.reminder_times_error), style = MaterialTheme.typography.bodySmall, color = Iron.colors.error)
            }
            SectionTitle(stringResource(R.string.reminder_photo))
            PhotoPickerBlock(
                photo = f.photoPath?.let { File(it) },
                onPickGallery = viewModel::onGalleryPicked,
                requestCameraUri = viewModel::newCameraUri,
                onCameraResult = viewModel::onCameraResult,
                onRemove = viewModel::removePhoto,
                previewHeight = 160.dp,
            )
            IronTextField(
                value = f.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = stringResource(R.string.reminder_note),
                placeholder = stringResource(R.string.reminder_note_hint),
                singleLine = false,
                minLines = 2,
            )
            PrimaryButton(stringResource(R.string.action_save), { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth())
            if (!f.isNew) {
                GhostButton(stringResource(R.string.action_delete), { confirmDelete = true }, color = Iron.colors.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (addTime) {
        TextInputDialog(
            title = stringResource(R.string.reminder_add_time),
            initial = "",
            placeholder = "08:00",
            supportingText = stringResource(R.string.cardio_time_hint),
            validate = { Fmt.parseTime(it) != null },
            onConfirm = { text ->
                Fmt.parseTime(text)?.let { t -> viewModel.addTime(t.hour * 60 + t.minute) }
                addTime = false
            },
            onDismiss = { addTime = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.reminder_delete_title),
            text = stringResource(R.string.reminder_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete(onDeleted) },
            onDismiss = { confirmDelete = false },
        )
    }
}
