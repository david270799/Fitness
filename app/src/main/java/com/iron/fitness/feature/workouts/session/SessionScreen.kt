package com.iron.fitness.feature.workouts.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.FlatProgressBar
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDropdownMenu
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronMenuItem
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.timer.RestState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private sealed interface SessionDialog {
    data object Rename : SessionDialog
    data object Note : SessionDialog
    data object Discard : SessionDialog
    data object Finish : SessionDialog
    data class ExerciseNote(val ex: ExerciseUi) : SessionDialog
    data class ExerciseRest(val ex: ExerciseUi) : SessionDialog
    data class RemoveExercise(val ex: ExerciseUi) : SessionDialog
}

/** Разрешение на уведомления спрашиваем один раз за запуск приложения. */
private var notificationPermissionAsked = false

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onFinished: (summaryWorkoutId: Long?) -> Unit,
    onAddExercises: () -> Unit,
    onOpenExercise: (String) -> Unit,
    pickedExercises: List<String>?,
    onPickedHandled: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val editMode = viewModel.editMode
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SessionDialog?>(null) }

    LaunchedEffect(pickedExercises) {
        if (!pickedExercises.isNullOrEmpty()) {
            viewModel.addExercises(pickedExercises)
            onPickedHandled()
        }
    }

    val resources = LocalContext.current.resources
    val missingText = stringResource(R.string.session_missing_values)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { e ->
            when (e) {
                is SessionEvent.Record -> scope.launch {
                    snackbar.showSnackbar(resources.getString(R.string.session_new_record, e.exerciseName))
                }
                SessionEvent.MissingValues -> scope.launch { snackbar.showSnackbar(missingText) }
                SessionEvent.Saved -> Unit
            }
        }
    }

    // Экран не гаснет во время тренировки (если включено в настройках).
    val view = LocalView.current
    val keepOn = !editMode && state.settings.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    NotificationPermissionRequest(enabled = !editMode)

    // Уходим с экрана один раз, даже если сработали и кнопка, и удаление тренировки.
    var leaving by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (!leaving) { leaving = true; onBack() } }
    val finished: (Long?) -> Unit = { id -> if (!leaving) { leaving = true; onFinished(id) } }

    // Тренировку удалили (например, отменили на другом экране).
    LaunchedEffect(state.loading, state.workout) {
        if (!state.loading && state.workout == null) leave()
    }

    val actions = remember(viewModel) {
        object : ExerciseCardActions {
            override fun toggleSet(set: SetUi, ex: ExerciseUi) = viewModel.toggleSet(set, ex)
            override fun updateValues(setId: Long, weight: Double?, reps: Int?, seconds: Int?) =
                viewModel.updateValues(setId, weight, reps, seconds)
            override fun setType(setId: Long, type: SetType) = viewModel.setType(setId, type)
            override fun deleteSet(setId: Long) = viewModel.deleteSet(setId)
            override fun addSet(ex: ExerciseUi) = viewModel.addSet(ex.we.id)
            override fun move(ex: ExerciseUi, delta: Int) = viewModel.move(ex.we.id, delta)
            override fun toggleSuperset(ex: ExerciseUi) = viewModel.toggleSuperset(ex.we.id)
            override fun editNote(ex: ExerciseUi) { dialog = SessionDialog.ExerciseNote(ex) }
            override fun editRest(ex: ExerciseUi) { dialog = SessionDialog.ExerciseRest(ex) }
            override fun remove(ex: ExerciseUi) {
                if (ex.sets.any { it.set.completed }) dialog = SessionDialog.RemoveExercise(ex) else viewModel.removeExercise(ex.we.id)
            }
            override fun open(ex: ExerciseUi) = onOpenExercise(ex.we.exerciseId)
        }
    }

    // Состояние правки даты/длительности (только для завершённой тренировки).
    val workout = state.workout
    var editDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var editDuration by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Iron.colors.background,
        contentColor = Iron.colors.text,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SessionTopBar(
                title = workout?.name.orEmpty(),
                startedAt = workout?.startedAt,
                editMode = editMode,
                onBack = leave,
                onRename = { dialog = SessionDialog.Rename },
                onNote = { dialog = SessionDialog.Note },
                onDiscard = { dialog = SessionDialog.Discard },
                onStartRest = { viewModel.startRest(state.settings.defaultRestSeconds) },
                onFinish = {
                    if (editMode) {
                        val start = editDateMillis
                        viewModel.saveEdit(start, editDuration?.toIntOrNull()?.takeIf { it > 0 }, leave)
                    } else if (state.incompleteCount == 0 && state.completedCount > 0) {
                        viewModel.finish(finished)
                    } else {
                        dialog = SessionDialog.Finish
                    }
                },
            )
        },
        bottomBar = {
            val r = rest
            if (!editMode && r != null) {
                RestBar(r, onAdd = viewModel::addRest, onSkip = viewModel::skipRest)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editMode && workout != null) {
                item(key = "edit_header") {
                    EditHeader(
                        startedAt = editDateMillis ?: workout.startedAt,
                        durationText = editDuration ?: ((workout.durationSec + 30) / 60).toString(),
                        onDateChange = { editDateMillis = it },
                        onDurationChange = { editDuration = it },
                    )
                }
            }
            workout?.note?.let { note ->
                item(key = "note") {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                }
            }
            if (!state.loading && state.exercises.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.session_empty), icon = IronIcons.Workouts) }
            }
            items(state.exercises, key = { it.we.id }) { ex ->
                SessionExerciseCard(ex, actions)
            }
            item(key = "add") {
                SecondaryButton(
                    text = stringResource(R.string.session_add_exercise),
                    onClick = onAddExercises,
                    icon = IronIcons.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    when (val d = dialog) {
        SessionDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.session_rename),
            initial = workout?.name.orEmpty(),
            onConfirm = { viewModel.rename(it); dialog = null },
            onDismiss = { dialog = null },
            validate = { it.isNotBlank() },
        )
        SessionDialog.Note -> TextInputDialog(
            title = stringResource(R.string.session_note),
            initial = workout?.note.orEmpty(),
            singleLine = false,
            onConfirm = { viewModel.setNote(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SessionDialog.Discard -> ConfirmDialog(
            title = stringResource(R.string.session_discard_title),
            text = stringResource(R.string.session_discard_text),
            confirmText = stringResource(R.string.session_discard),
            destructive = true,
            onConfirm = { dialog = null; viewModel.discard(leave) },
            onDismiss = { dialog = null },
        )
        SessionDialog.Finish -> ConfirmDialog(
            title = stringResource(R.string.session_finish_title),
            text = if (state.completedCount == 0) {
                stringResource(R.string.session_finish_empty)
            } else {
                stringResource(R.string.session_finish_text, state.incompleteCount)
            },
            confirmText = stringResource(R.string.session_finish),
            onConfirm = { dialog = null; viewModel.finish(finished) },
            onDismiss = { dialog = null },
        )
        is SessionDialog.ExerciseNote -> TextInputDialog(
            title = stringResource(R.string.session_menu_note),
            initial = d.ex.we.note.orEmpty(),
            singleLine = false,
            onConfirm = { viewModel.setExerciseNote(d.ex.we.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is SessionDialog.ExerciseRest -> TextInputDialog(
            title = stringResource(R.string.session_menu_rest),
            initial = d.ex.restSeconds.toString(),
            label = stringResource(R.string.routine_rest),
            keyboardType = KeyboardType.Number,
            onConfirm = {
                viewModel.setExerciseRest(d.ex.we.id, it.trim().toIntOrNull()?.coerceIn(0, 900))
                dialog = null
            },
            onDismiss = { dialog = null },
            validate = { it.isBlank() || it.trim().toIntOrNull() != null },
        )
        is SessionDialog.RemoveExercise -> ConfirmDialog(
            title = stringResource(R.string.session_menu_remove),
            text = d.ex.exercise?.name,
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.removeExercise(d.ex.we.id); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SessionTopBar(
    title: String,
    startedAt: Long?,
    editMode: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onNote: () -> Unit,
    onDiscard: () -> Unit,
    onStartRest: () -> Unit,
    onFinish: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (!editMode) {
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    Surface(color = Iron.colors.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IronIconButton(IronIcons.Back, stringResource(R.string.action_back), onBack)
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onRename)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    (if (editMode) stringResource(R.string.session_edit_title) else title).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (editMode) {
                    Text(title, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (startedAt != null) {
                    Text(
                        Fmt.duration((now - startedAt) / 1000),
                        style = Iron.numbers.small,
                        color = Iron.colors.accentText,
                    )
                }
            }
            if (!editMode) {
                IronIconButton(IronIcons.Timer, stringResource(R.string.timer_start_rest), onStartRest)
            }
            PrimaryButton(
                text = stringResource(if (editMode) R.string.action_save else R.string.session_finish),
                onClick = onFinish,
                modifier = Modifier.height(40.dp),
            )
            Box {
                IronIconButton(IronIcons.MoreVert, stringResource(R.string.action_more), { menu = true })
                IronDropdownMenu(expanded = menu, onDismiss = { menu = false }) {
                    IronMenuItem(stringResource(R.string.action_rename), { menu = false; onRename() }, IronIcons.Edit)
                    IronMenuItem(stringResource(R.string.session_note), { menu = false; onNote() }, IronIcons.FileText)
                    if (!editMode) {
                        IronMenuItem(stringResource(R.string.session_discard), { menu = false; onDiscard() }, IronIcons.Delete, color = Iron.colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestBar(rest: RestState, onAdd: (Int) -> Unit, onSkip: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(rest) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    Surface(color = Iron.colors.surface, modifier = Modifier.border(1.dp, Iron.colors.border)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.timer_rest_label), style = MaterialTheme.typography.titleSmall, color = Iron.colors.textSecondary)
                    rest.label?.let {
                        Text(stringResource(R.string.timer_rest_after, it), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(Fmt.duration(rest.remainingSec(now)), style = Iron.numbers.large, color = Iron.colors.text)
            }
            FlatProgressBar(rest.progress(now), height = 6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.timer_minus_15), { onAdd(-15) }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.timer_plus_15), { onAdd(15) }, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.timer_skip), onSkip, modifier = Modifier.weight(1.2f).height(48.dp))
            }
        }
    }
}

@Composable
private fun EditHeader(
    startedAt: Long,
    durationText: String,
    onDateChange: (Long) -> Unit,
    onDurationChange: (String) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    val dateTime = Fmt.toLocalDateTime(startedAt)
    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.session_edit_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.session_edit_date).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    Text(Fmt.dateTime(startedAt), style = MaterialTheme.typography.bodyLarge)
                }
                GhostButton(stringResource(R.string.action_edit), { pickDate = true })
            }
            IronTextField(
                value = durationText,
                onValueChange = { v -> onDurationChange(v.filter { it.isDigit() }.take(4)) },
                label = stringResource(R.string.session_edit_duration),
                keyboardType = KeyboardType.Number,
            )
        }
    }
    if (pickDate) {
        val initialUtc = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                GhostButton(stringResource(R.string.action_ok), {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = java.time.Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val newStart = LocalDateTime.of(date, dateTime.toLocalTime())
                        onDateChange(newStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
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
}

@Composable
private fun NotificationPermissionRequest(enabled: Boolean) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !notificationPermissionAsked) {
            notificationPermissionAsked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
