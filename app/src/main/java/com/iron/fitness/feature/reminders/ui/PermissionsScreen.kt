package com.iron.fitness.feature.reminders.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.alarms.AlarmRescheduler
import com.iron.fitness.core.alarms.AlarmScheduler
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.theme.Iron
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val rescheduler: AlarmRescheduler,
) : ViewModel() {
    init {
        // Экран показывается автоматически только один раз; дальше — из настроек.
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    fun canExact() = scheduler.canScheduleExact()

    /** После выдачи разрешений будильники лучше переустановить точными. */
    fun reschedule() = viewModelScope.launch { rescheduler.rescheduleAll() }
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

@SuppressLint("BatteryLife")
private fun batteryIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

private fun exactAlarmIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        null
    }

@Composable
fun PermissionsScreen(onDone: () -> Unit, viewModel: PermissionsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        viewModel.reschedule()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Статусы пересчитываются после возврата из системных настроек (refresh меняется в ON_RESUME).
    val notifOk = remember(refresh) { notificationsGranted(context) }
    val exactOk = remember(refresh) { viewModel.canExact() }
    val batteryOk = remember(refresh) { batteryUnrestricted(context) }

    IronScaffold(title = stringResource(R.string.perm_title), onBack = onDone) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.perm_intro), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            PermissionCard(
                icon = IronIcons.Bell,
                title = stringResource(R.string.perm_notifications),
                text = stringResource(R.string.perm_notifications_text),
                granted = notifOk,
                button = stringResource(R.string.perm_allow),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            PermissionCard(
                icon = IronIcons.Alarm,
                title = stringResource(R.string.perm_exact),
                text = stringResource(R.string.perm_exact_text),
                granted = exactOk,
                button = stringResource(R.string.perm_open_settings),
                onClick = { exactAlarmIntent(context)?.let { runCatching { context.startActivity(it) } } },
            )
            PermissionCard(
                icon = IronIcons.Battery,
                title = stringResource(R.string.perm_battery),
                text = stringResource(R.string.perm_battery_text),
                granted = batteryOk,
                button = stringResource(R.string.perm_open_settings),
                onClick = {
                    runCatching { context.startActivity(batteryIntent(context)) }.onFailure {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                },
            )
            Text(stringResource(R.string.perm_vendor_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            PrimaryButton(stringResource(R.string.action_done), onDone, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    granted: Boolean,
    button: String,
    onClick: () -> Unit,
) {
    IronCard(modifier = Modifier.fillMaxWidth(), borderColor = if (granted) Iron.colors.success else Iron.colors.border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIcon(icon, null, tint = if (granted) Iron.colors.success else Iron.colors.text)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) IronIcon(IronIcons.CircleCheck, null, tint = Iron.colors.success)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(button, onClick, modifier = Modifier.fillMaxWidth())
        } else {
            Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.labelMedium, color = Iron.colors.success, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
