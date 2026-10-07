package com.iron.fitness.feature.daily.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.components.SwitchRow
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.daily.data.ChallengeEntity
import com.iron.fitness.feature.daily.data.ChallengeReminders
import com.iron.fitness.feature.daily.data.ChallengeRepository
import com.iron.fitness.feature.daily.data.ChallengeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengeForm(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val unit: ChallengeUnit = ChallengeUnit.REPS,
    val goal: String = "50",
    val step1: String = "5",
    val step2: String = "10",
    val reminder: Boolean = false,
    val reminderMinutes: Int = 19 * 60,
    val nameError: Boolean = false,
)

@HiltViewModel
class ChallengeEditViewModel @Inject constructor(
    private val repo: ChallengeRepository,
    private val reminders: ChallengeReminders,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var existing: ChallengeEntity? = null
    private val _form = MutableStateFlow(ChallengeForm(isNew = id == null))
    val form: StateFlow<ChallengeForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val c = id?.let { repo.get(it) }
            existing = c
            _form.value = if (c == null) {
                ChallengeForm(loading = false)
            } else {
                ChallengeForm(
                    loading = false,
                    isNew = false,
                    name = c.name,
                    unit = c.unit,
                    goal = c.goal.toString(),
                    step1 = c.step1.toString(),
                    step2 = c.step2.toString(),
                    reminder = c.reminderEnabled,
                    reminderMinutes = c.reminderMinutes,
                )
            }
        }
    }

    fun update(t: (ChallengeForm) -> ChallengeForm) = _form.update(t)

    fun setUnit(unit: ChallengeUnit) = _form.update { f ->
        if (f.unit == unit) f else if (unit == ChallengeUnit.SECONDS) {
            f.copy(unit = unit, goal = "180", step1 = "15", step2 = "30")
        } else {
            f.copy(unit = unit, goal = "50", step1 = "5", step2 = "10")
        }
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = true) }
            return
        }
        val goal = f.goal.toIntOrNull()?.coerceIn(1, 100_000) ?: return
        val step1 = f.step1.toIntOrNull()?.coerceIn(1, 10_000) ?: 5
        val step2 = f.step2.toIntOrNull()?.coerceIn(1, 10_000) ?: 10
        viewModelScope.launch {
            val base = existing ?: ChallengeEntity(name = f.name.trim(), goal = goal)
            val entity = base.copy(
                name = f.name.trim(),
                unit = f.unit,
                goal = goal,
                step1 = step1,
                step2 = step2,
                reminderEnabled = f.reminder,
                reminderMinutes = f.reminderMinutes,
            )
            val savedId = if (existing == null) repo.create(entity) else {
                repo.update(entity)
                entity.id
            }
            repo.get(savedId)?.let { reminders.schedule(it) }
            onDone()
        }
    }
}

@Composable
fun ChallengeEditScreen(onBack: () -> Unit, viewModel: ChallengeEditViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    IronScaffold(
        title = stringResource(if (f.isNew) R.string.challenge_new else R.string.challenge_edit),
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
                label = stringResource(R.string.challenge_name),
                placeholder = stringResource(R.string.challenge_name_hint),
                isError = f.nameError,
                supportingText = if (f.nameError) stringResource(R.string.routine_name_required) else null,
            )
            Text(stringResource(R.string.challenge_unit).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Segmented(
                items = ChallengeUnit.entries,
                selected = f.unit,
                label = { stringResource(if (it == ChallengeUnit.REPS) R.string.challenge_unit_reps else R.string.challenge_unit_seconds) },
                onSelect = viewModel::setUnit,
            )
            IronTextField(
                value = f.goal,
                onValueChange = { v -> viewModel.update { it.copy(goal = v.filter(Char::isDigit).take(6)) } },
                label = stringResource(if (f.unit == ChallengeUnit.SECONDS) R.string.challenge_goal_seconds else R.string.challenge_goal),
                keyboardType = KeyboardType.Number,
                textStyle = Iron.numbers.medium,
                supportingText = if (f.isNew) null else stringResource(R.string.challenge_goal_change_hint),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IronTextField(
                    value = f.step1,
                    onValueChange = { v -> viewModel.update { it.copy(step1 = v.filter(Char::isDigit).take(5)) } },
                    label = stringResource(R.string.challenge_step1),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                IronTextField(
                    value = f.step2,
                    onValueChange = { v -> viewModel.update { it.copy(step2 = v.filter(Char::isDigit).take(5)) } },
                    label = stringResource(R.string.challenge_step2),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            IronCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                SwitchRow(
                    title = stringResource(R.string.challenge_reminder),
                    subtitle = stringResource(R.string.challenge_reminder_sub),
                    checked = f.reminder,
                    onCheckedChange = { on ->
                        viewModel.update { it.copy(reminder = on) }
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                if (f.reminder) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.challenge_reminder_time, Fmt.minutesOfDay(f.reminderMinutes)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(stringResource(R.string.action_edit), { pickTime = true })
                    }
                }
            }
            PrimaryButton(stringResource(R.string.action_save), { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickTime) {
        TextInputDialog(
            title = stringResource(R.string.challenge_reminder),
            initial = Fmt.minutesOfDay(f.reminderMinutes),
            supportingText = stringResource(R.string.cardio_time_hint),
            validate = { Fmt.parseTime(it) != null },
            onConfirm = { text ->
                Fmt.parseTime(text)?.let { t -> viewModel.update { it.copy(reminderMinutes = t.hour * 60 + t.minute) } }
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}
