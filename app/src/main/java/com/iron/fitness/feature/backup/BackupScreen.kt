package com.iron.fitness.feature.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.backup.AppRestart
import com.iron.fitness.core.backup.BackupException
import com.iron.fitness.core.backup.BackupFormat
import com.iron.fitness.core.backup.BackupManager
import com.iron.fitness.core.backup.BackupProblem
import com.iron.fitness.core.backup.DriveBackup
import com.iron.fitness.core.backup.DriveException
import com.iron.fitness.core.backup.DriveFile
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronDivider
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.ListRow
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.components.SwitchRow
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val drive: DriveBackup,
    private val settings: SettingsRepository,
) : ViewModel() {

    enum class Op(@StringRes val text: Int) {
        EXPORT(R.string.backup_busy_export),
        IMPORT(R.string.backup_busy_import),
        CONNECT(R.string.drive_busy_connect),
        UPLOAD(R.string.drive_busy_upload),
        LIST(R.string.drive_busy_list),
        RESTORE(R.string.drive_busy_restore),
    }

    /** Сообщение под действиями. */
    data class Note(@StringRes val text: Int, val error: Boolean = false)

    val appSettings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow<Op?>(null)
    val busy: StateFlow<Op?> = _busy.asStateFlow()
    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()
    private val _files = MutableStateFlow<List<DriveFile>?>(null)
    val files: StateFlow<List<DriveFile>?> = _files.asStateFlow()

    private val _consent = Channel<PendingIntent>(Channel.BUFFERED)
    /** Окно входа в Google, которое нужно показать. */
    val consent: Flow<PendingIntent> = _consent.receiveAsFlow()
    private val _restart = Channel<Unit>(Channel.BUFFERED)
    /** Данные подготовлены к восстановлению — перезапустить приложение. */
    val restart: Flow<Unit> = _restart.receiveAsFlow()

    private var afterConsent: (suspend (String) -> Unit)? = null
    private var consentOp: Op? = null

    init {
        // Если Диск уже подключён — тихо обновляем список копий (без окон входа).
        viewModelScope.launch {
            if (settings.current().driveAccount != null) {
                withDrive(Op.LIST, interactive = false) { token -> _files.value = drive.list(token) }
            }
        }
    }

    fun exportTo(uri: Uri) = launchOp(Op.EXPORT) {
        val out = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("no stream")
        backup.write(out)
        _note.value = Note(R.string.backup_saved)
    }

    fun importFrom(uri: Uri) = launchOp(Op.IMPORT) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
        input.use { backup.stageRestore(it) }
        _restart.send(Unit)
    }

    fun connect() = withDrive(Op.CONNECT) { token ->
        settings.setDriveAccount(drive.accountEmail(token) ?: GOOGLE)
        _files.value = drive.list(token)
        _note.value = Note(R.string.drive_connected_note)
    }

    fun backupNow() = withDrive(Op.UPLOAD) { token ->
        drive.backupNow(token)
        _files.value = drive.list(token)
        _note.value = Note(R.string.drive_backup_done)
    }

    fun refresh() = withDrive(Op.LIST) { token -> _files.value = drive.list(token) }

    fun restoreFromDrive(file: DriveFile) = withDrive(Op.RESTORE) { token ->
        drive.restore(token, file.id)
        _restart.send(Unit)
    }

    fun setAuto(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoBackup(enabled)
            drive.schedule(enabled)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            settings.setAutoBackup(false)
            drive.schedule(false)
            settings.setDriveAccount(null)
            _files.value = null
            _note.value = Note(R.string.drive_disconnected_note)
        }
    }

    /** Результат окна входа в Google. */
    fun onConsent(resultCode: Int, data: Intent?) {
        val action = afterConsent ?: return
        val op = consentOp ?: return
        afterConsent = null
        consentOp = null
        if (resultCode != Activity.RESULT_OK) {
            _note.value = Note(R.string.drive_err_cancelled, error = true)
            return
        }
        launchOp(op) { action(drive.tokenFromConsent(data)) }
    }

    private fun withDrive(op: Op, interactive: Boolean = true, action: suspend (String) -> Unit) = launchOp(op, quiet = !interactive) {
        when (val auth = drive.authorize()) {
            is DriveBackup.Auth.Granted -> action(auth.token)
            is DriveBackup.Auth.NeedsConsent -> if (interactive) {
                afterConsent = action
                consentOp = op
                _consent.send(auth.intent)
            }
        }
    }

    private fun launchOp(op: Op, quiet: Boolean = false, block: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = op
        if (!quiet) _note.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!quiet) _note.value = e.toNote()
            } finally {
                _busy.value = null
            }
        }
    }

    private fun Exception.toNote(): Note = when (this) {
        is BackupException -> Note(
            when (problem) {
                BackupProblem.NOT_IRON -> R.string.backup_err_not_iron
                BackupProblem.NEWER_FORMAT, BackupProblem.NEWER_APP -> R.string.backup_err_newer
                BackupProblem.BROKEN -> R.string.backup_err_broken
            },
            error = true,
        )
        is DriveException -> Note(
            when (kind) {
                DriveException.Kind.NOT_CONFIGURED -> R.string.drive_err_not_configured
                DriveException.Kind.API_DISABLED -> R.string.drive_err_api_disabled
                DriveException.Kind.AUTH -> R.string.drive_err_auth
                DriveException.Kind.CANCELLED -> R.string.drive_err_cancelled
                DriveException.Kind.NETWORK -> R.string.drive_err_network
                DriveException.Kind.STORAGE_FULL -> R.string.drive_err_storage
                DriveException.Kind.OTHER -> R.string.drive_err_other
            },
            error = true,
        )
        else -> Note(R.string.backup_err_io, error = true)
    }

    private companion object {
        const val GOOGLE = "Google"
    }
}

private val IMPORT_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val s by viewModel.appSettings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmFile by remember { mutableStateOf<Uri?>(null) }
    var confirmDrive by remember { mutableStateOf<DriveFile?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmFile = uri }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        viewModel.onConsent(r.resultCode, r.data)
    }
    LaunchedEffect(Unit) {
        viewModel.consent.collect { pi -> consent.launch(IntentSenderRequest.Builder(pi.intentSender).build()) }
    }
    LaunchedEffect(Unit) {
        viewModel.restart.collect { AppRestart.restart(context) }
    }
    val idle = busy == null

    IronScaffold(title = stringResource(R.string.backup_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_what), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)

            SectionTitle(stringResource(R.string.backup_section_file))
            IronCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        stringResource(R.string.backup_export),
                        { createDoc.launch(BackupFormat.fileName(LocalDateTime.now())) },
                        icon = IronIcons.Download,
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        stringResource(R.string.backup_import),
                        { openDoc.launch(IMPORT_TYPES) },
                        icon = IronIcons.Upload,
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.backup_file_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                }
            }

            SectionTitle(stringResource(R.string.drive_section))
            val settings = s
            val account = settings?.driveAccount
            if (settings != null && account == null) {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.drive_intro), style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton(stringResource(R.string.drive_connect), viewModel::connect, icon = IronIcons.Cloud, enabled = idle, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.drive_setup_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
                    }
                }
            } else if (settings != null && account != null) {
                IronCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                    ListRow(title = account, subtitle = stringResource(R.string.drive_connected), icon = IronIcons.User)
                    IronDivider()
                    ListRow(
                        title = stringResource(R.string.drive_last),
                        subtitle = settings.lastBackupAt?.let { Fmt.dateTime(it) } ?: stringResource(R.string.drive_never),
                        icon = IronIcons.History,
                    )
                    IronDivider()
                    SwitchRow(
                        title = stringResource(R.string.drive_auto),
                        subtitle = stringResource(R.string.drive_auto_sub),
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = viewModel::setAuto,
                        icon = IronIcons.Repeat,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(stringResource(R.string.drive_backup_now), viewModel::backupNow, icon = IronIcons.CloudUpload, enabled = idle, modifier = Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.drive_refresh), viewModel::refresh, icon = IronIcons.Refresh, enabled = idle, modifier = Modifier.weight(1f))
                }
                files?.let { list ->
                    Text(stringResource(R.string.drive_copies, DriveBackup.KEEP).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    if (list.isEmpty()) {
                        Text(stringResource(R.string.drive_no_copies), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                    }
                    list.forEach { f ->
                        IronCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.dateTime(f.createdMillis), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(R.string.backup_size_mb, Fmt.num(f.bytes / (1024.0 * 1024.0), 1)),
                                        style = Iron.numbers.tiny,
                                        color = Iron.colors.textSecondary,
                                    )
                                }
                                IronIconButton(IronIcons.CloudDownload, stringResource(R.string.drive_restore), { confirmDrive = f }, enabled = idle)
                            }
                        }
                    }
                }
                GhostButton(stringResource(R.string.drive_disconnect), { confirmDisconnect = true }, color = Iron.colors.textSecondary, enabled = idle)
            }

            busy?.let { op ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Iron.colors.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(op.text), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
                }
            }
            note?.let { n ->
                Text(
                    stringResource(n.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (n.error) Iron.colors.error else Iron.colors.success,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmFile?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_title),
            text = stringResource(R.string.backup_restore_text),
            confirmText = stringResource(R.string.backup_restore_ok),
            destructive = true,
            onConfirm = {
                confirmFile = null
                viewModel.importFrom(uri)
            },
            onDismiss = { confirmFile = null },
        )
    }
    confirmDrive?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_title),
            text = stringResource(R.string.drive_restore_text, Fmt.dateTime(f.createdMillis)),
            confirmText = stringResource(R.string.backup_restore_ok),
            destructive = true,
            onConfirm = {
                confirmDrive = null
                viewModel.restoreFromDrive(f)
            },
            onDismiss = { confirmDrive = null },
        )
    }
    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.drive_disconnect),
            text = stringResource(R.string.drive_disconnect_text),
            confirmText = stringResource(R.string.drive_disconnect),
            onConfirm = {
                confirmDisconnect = false
                viewModel.disconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}
