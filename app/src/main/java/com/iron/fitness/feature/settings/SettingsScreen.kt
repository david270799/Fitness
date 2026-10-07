package com.iron.fitness.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.ThemeMode
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.FlatProgressBar
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronChip
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.navigation.Routes
import com.iron.fitness.core.ui.components.SwitchRow
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.feature.assistant.aiErrorText
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.exercises.images.ExerciseImages
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.ListRow
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.ui.theme.IronPalette
import com.iron.fitness.core.ui.theme.IronPalettes

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    IronScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceSection(settings, viewModel)
            WorkoutSection(settings, viewModel)
            ReminderSection(settings, viewModel, navigate)
            AssistantSection(settings, viewModel)
            CaloriesSection(viewModel)
            ImagesSection(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearanceSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_appearance))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IronPalettes.all.forEach { palette ->
            ThemeTile(
                palette = palette,
                selected = palette.id == settings.themeId,
                onClick = { viewModel.setTheme(palette.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    IronCard(contentPadding = PaddingValues(0.dp)) {
        Text(
            stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        ThemeModeRow(
            title = stringResource(R.string.settings_theme_mode_fixed),
            subtitle = stringResource(R.string.settings_theme_mode_fixed_sub),
            selected = settings.themeMode == ThemeMode.FIXED,
            onClick = { viewModel.setThemeMode(ThemeMode.FIXED) },
        )
        ThemeModeRow(
            title = stringResource(R.string.settings_theme_mode_system),
            subtitle = stringResource(R.string.settings_theme_mode_system_sub),
            selected = settings.themeMode == ThemeMode.SYSTEM,
            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
        )
    }
}

@Composable
private fun ThemeModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ListRow(title = title, subtitle = subtitle, onClick = onClick) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Iron.colors.accent, unselectedColor = Iron.colors.textSecondary),
        )
    }
}

@Composable
private fun ThemeTile(
    palette: IronPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(palette.background)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Iron.colors.accent else Iron.colors.border), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 22.dp, height = 22.dp).background(palette.surface).border(1.dp, palette.border))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = 22.dp, height = 22.dp).background(palette.accent))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(palette.text))
        Text(
            stringResource(palette.nameRes).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = palette.text,
            maxLines = 1,
        )
    }
}

/** Все блины, которые можно отметить как доступные. */
private val ALL_PLATES = listOf(50.0, 25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 2.0, 1.25, 1.0, 0.5, 0.25)
private val BAR_WEIGHTS = listOf(20.0, 15.0, 10.0, 7.0)

@Composable
private fun WorkoutSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_workouts))
    IronCard(contentPadding = PaddingValues(0.dp)) {
        ListRow(
            title = stringResource(R.string.settings_default_rest),
            subtitle = stringResource(R.string.settings_default_rest_sub),
            icon = IronIcons.Timer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IronIconButton(IronIcons.Remove, stringResource(R.string.timer_minus_15), { viewModel.setDefaultRest(settings.defaultRestSeconds - 15) })
                Text(Fmt.duration(settings.defaultRestSeconds), style = Iron.numbers.small)
                IronIconButton(IronIcons.Add, stringResource(R.string.timer_plus_15), { viewModel.setDefaultRest(settings.defaultRestSeconds + 15) })
            }
        }
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_auto_rest),
            subtitle = stringResource(R.string.settings_auto_rest_sub),
            checked = settings.autoStartRestTimer,
            onCheckedChange = viewModel::setAutoRest,
            icon = IronIcons.Alarm,
        )
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen),
            subtitle = stringResource(R.string.settings_keep_screen_sub),
            checked = settings.keepScreenOn,
            onCheckedChange = viewModel::setKeepScreenOn,
            icon = IronIcons.Sun,
        )
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_sound),
            checked = settings.soundEnabled,
            onCheckedChange = viewModel::setSound,
            icon = IronIcons.Volume,
        )
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_vibration),
            checked = settings.vibrationEnabled,
            onCheckedChange = viewModel::setVibration,
            icon = IronIcons.Vibrate,
        )
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_voice),
            subtitle = stringResource(R.string.settings_voice_sub),
            checked = settings.voiceHintsEnabled,
            onCheckedChange = viewModel::setVoiceHints,
            icon = IronIcons.Mic,
        )
    }
    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_bar_weight), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BAR_WEIGHTS.forEach { w ->
                    IronChip(
                        text = stringResource(R.string.value_kg, Fmt.num(w)),
                        selected = settings.barWeightKg == w,
                        onClick = { viewModel.setBarWeight(w) },
                    )
                }
            }
            Text(stringResource(R.string.settings_plates), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_PLATES.forEach { p ->
                    IronChip(
                        text = Fmt.num(p),
                        selected = p in settings.plates,
                        onClick = { viewModel.togglePlate(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderSection(settings: AppSettings, viewModel: SettingsViewModel, navigate: (String) -> Unit) {
    SectionTitle(stringResource(R.string.settings_section_reminders))
    IronCard(contentPadding = PaddingValues(0.dp)) {
        ListRow(
            title = stringResource(R.string.settings_repeat_interval),
            subtitle = stringResource(R.string.settings_repeat_interval_sub),
            icon = IronIcons.Repeat,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IronIconButton(IronIcons.Remove, null, { viewModel.setReminderRepeat(settings.reminderRepeatMinutes - 5, settings.reminderRepeatCount) })
                Text(stringResource(R.string.minutes_short, settings.reminderRepeatMinutes), style = Iron.numbers.small)
                IronIconButton(IronIcons.Add, null, { viewModel.setReminderRepeat(settings.reminderRepeatMinutes + 5, settings.reminderRepeatCount) })
            }
        }
        IronDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_repeat_count), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { n ->
                    IronChip(
                        text = if (n == 0) stringResource(R.string.settings_repeat_none) else n.toString(),
                        selected = settings.reminderRepeatCount == n,
                        onClick = { viewModel.setReminderRepeat(settings.reminderRepeatMinutes, n) },
                    )
                }
            }
        }
        IronDivider()
        ListRow(
            title = stringResource(R.string.perm_title),
            subtitle = stringResource(R.string.settings_permissions_sub),
            icon = IronIcons.Shield,
            onClick = { navigate(Routes.PERMISSIONS) },
            trailing = { IronIcon(IronIcons.ChevronRight, null, tint = Iron.colors.textSecondary) },
        )
    }
}

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@Composable
private fun AssistantSection(settings: AppSettings, viewModel: SettingsViewModel) {
    val hasKey by viewModel.hasGeminiKey.collectAsStateWithLifecycle()
    val check by viewModel.keyCheck.collectAsStateWithLifecycle()
    var keyDialog by rememberSaveable { mutableStateOf(false) }
    var modelDialog by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    SectionTitle(stringResource(R.string.settings_section_assistant))
    IronCard(contentPadding = PaddingValues(0.dp)) {
        ListRow(
            title = stringResource(R.string.settings_ai_key),
            subtitle = if (hasKey) {
                stringResource(R.string.settings_ai_key_saved, viewModel.keyTail().orEmpty())
            } else {
                stringResource(R.string.settings_ai_key_none)
            },
            icon = IronIcons.Shield,
            onClick = { keyDialog = true },
            trailing = { IronIcon(IronIcons.Edit, null, tint = Iron.colors.textSecondary) },
        )
        IronDivider()
        ListRow(
            title = stringResource(R.string.settings_ai_model),
            subtitle = settings.geminiModel,
            icon = IronIcons.Sparkles,
            onClick = { modelDialog = true },
            trailing = { IronIcon(IronIcons.Edit, null, tint = Iron.colors.textSecondary) },
        )
        IronDivider()
        SwitchRow(
            title = stringResource(R.string.settings_ai_body),
            subtitle = stringResource(R.string.settings_ai_body_sub),
            checked = settings.sendBodyDataToAssistant,
            onCheckedChange = viewModel::setSendBodyData,
            icon = IronIcons.Body,
        )
    }
    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_ai_how), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            Text(stringResource(R.string.settings_ai_free_tier), style = MaterialTheme.typography.bodySmall, color = Iron.colors.warning)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    stringResource(R.string.settings_ai_get_key),
                    { runCatching { uriHandler.openUri(AI_STUDIO_URL) } },
                    icon = IronIcons.Link,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    stringResource(R.string.settings_ai_check),
                    viewModel::checkKey,
                    icon = IronIcons.CircleCheck,
                    enabled = hasKey && check !is KeyCheck.Running,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val c = check) {
                KeyCheck.Idle -> Unit
                KeyCheck.Running -> Text(stringResource(R.string.settings_ai_checking), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                is KeyCheck.Ok -> Text(stringResource(R.string.settings_ai_ok, c.model), style = MaterialTheme.typography.bodySmall, color = Iron.colors.success)
                is KeyCheck.Failed -> Text(aiErrorText(c.kind), style = MaterialTheme.typography.bodySmall, color = Iron.colors.error)
            }
            if (hasKey) {
                GhostButton(stringResource(R.string.settings_ai_key_delete), { viewModel.setGeminiKey(null) }, color = Iron.colors.textSecondary)
            }
        }
    }
    if (keyDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_ai_key),
            initial = "",
            label = stringResource(R.string.settings_ai_key_label),
            supportingText = stringResource(R.string.settings_ai_key_hint),
            validate = { it.isNotBlank() },
            onConfirm = { viewModel.setGeminiKey(it); keyDialog = false },
            onDismiss = { keyDialog = false },
        )
    }
    if (modelDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_ai_model),
            initial = settings.geminiModel,
            label = stringResource(R.string.settings_ai_model_label),
            supportingText = stringResource(R.string.settings_ai_model_hint, AppSettings.DEFAULT_GEMINI_MODEL),
            onConfirm = { viewModel.setGeminiModel(it); modelDialog = false },
            onDismiss = { modelDialog = false },
        )
    }
}

@Composable
private fun CaloriesSection(viewModel: SettingsViewModel) {
    val weight by viewModel.weightKg.collectAsStateWithLifecycle()
    SectionTitle(stringResource(R.string.settings_section_calories))
    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.calories_formula), style = Iron.numbers.small)
            Text(stringResource(R.string.calories_explain), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            Text(
                stringResource(R.string.calories_weight_used, weight?.let { Fmt.num(it, 1) } ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.calories_met_table), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
        }
    }
}

@Composable
private fun ImagesSection(viewModel: SettingsViewModel) {
    val download by viewModel.imageDownload.collectAsStateWithLifecycle()
    val cached by viewModel.cachedBytes.collectAsStateWithLifecycle()
    val total by viewModel.totalImages.collectAsStateWithLifecycle()
    SectionTitle(stringResource(R.string.settings_section_images))
    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.images_info), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            if (total > 0) {
                val estimateMb = total * ExerciseImages.AVG_IMAGE_BYTES / (1024.0 * 1024.0)
                Text(stringResource(R.string.images_total, total, Fmt.num(estimateMb, 0)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.images_cached, Fmt.num(cached / (1024.0 * 1024.0), 1)), style = Iron.numbers.small)
            val state = download
            if (state != null && state.running) {
                val progress = if (state.total > 0) state.done.toFloat() / state.total else 0f
                FlatProgressBar(progress)
                Text(stringResource(R.string.images_downloading, state.done, state.total), style = Iron.numbers.tiny)
                SecondaryButton(stringResource(R.string.images_cancel), viewModel::cancelImageDownload, icon = IronIcons.Stop, modifier = Modifier.fillMaxWidth())
            } else {
                if (state != null && state.finished && state.total > 0) {
                    Text(
                        if (state.failed > 0) stringResource(R.string.images_failed, state.failed) else stringResource(R.string.images_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.failed > 0) Iron.colors.warning else Iron.colors.success,
                    )
                }
                PrimaryButton(stringResource(R.string.images_download_all), viewModel::downloadAllImages, icon = IronIcons.Download, modifier = Modifier.fillMaxWidth())
                GhostButton(stringResource(R.string.images_clear), viewModel::clearImageCache, color = Iron.colors.textSecondary)
            }
        }
    }
}
