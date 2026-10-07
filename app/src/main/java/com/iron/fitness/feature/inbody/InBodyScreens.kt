package com.iron.fitness.feature.inbody

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.ai.InlineImage
import com.iron.fitness.core.media.PhotoKind
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.charts.ChartPoint
import com.iron.fitness.core.ui.charts.LineChart
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.SwitchRow
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.assistant.AiError
import com.iron.fitness.feature.assistant.AiLoading
import com.iron.fitness.feature.assistant.AiState
import com.iron.fitness.feature.assistant.AssistantService
import com.iron.fitness.feature.assistant.InBodyReading
import com.iron.fitness.feature.assistant.toAiError
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.MeasurementEntity
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InBodyRepository @Inject constructor(
    private val dao: InBodyDao,
    private val photos: PhotoStorage,
) {
    fun observeAll(): Flow<List<InBodyEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<InBodyEntity?> = dao.observe(id)
    suspend fun save(e: InBodyEntity): Long = dao.insert(e)
    suspend fun update(e: InBodyEntity) = dao.update(e)
    suspend fun delete(id: Long) {
        val e = dao.get(id) ?: return
        dao.delete(id)
        photos.delete(e.photoPath)
    }

    suspend fun previous(e: InBodyEntity): InBodyEntity? =
        dao.getAll().filter { it.id != e.id && (it.day < e.day || (it.day == e.day && it.createdAt < e.createdAt)) }.maxByOrNull { it.day }
}

/** Текст результатов для разбора ассистентом. */
fun InBodyEntity.summary(prev: InBodyEntity?, label: (InBodyKey) -> String): String = buildString {
    appendLine("Дата: ${LocalDate.ofEpochDay(day)}")
    InBodyKey.entries.forEach { k ->
        val v = k.fromEntity(this@summary) ?: return@forEach
        append("${label(k)}: ${Fmt.num(v, 2)}")
        prev?.let { p -> k.fromEntity(p)?.let { pv -> append(" (было ${Fmt.num(pv, 2)})") } }
        appendLine()
    }
}

// ======================= Список и дашборд =======================

@HiltViewModel
class InBodyListViewModel @Inject constructor(repo: InBodyRepository) : ViewModel() {
    val list: StateFlow<List<InBodyEntity>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun InBodyListScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: InBodyListViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val kg = stringResource(R.string.unit_kg)
    val percent = stringResource(R.string.unit_percent)
    IronScaffold(
        title = stringResource(R.string.ai_f_inbody),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Add, stringResource(R.string.ib_new), { navigate(Routes.INBODY_NEW) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyState(stringResource(R.string.ib_empty), icon = IronIcons.Scan, action = {
                        PrimaryButton(stringResource(R.string.ib_new), { navigate(Routes.INBODY_NEW) }, icon = IronIcons.Camera)
                    })
                }
            }
            val latest = list.firstOrNull()
            if (latest != null) {
                item(key = "dash") {
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ib_latest, Fmt.date(LocalDate.ofEpochDay(latest.day))).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                        listOf(InBodyKey.WEIGHT, InBodyKey.SMM, InBodyKey.PBF, InBodyKey.VISCERAL).chunked(2).forEach { row ->
                            Row(Modifier.padding(top = 8.dp)) {
                                row.forEach { k ->
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(k.label), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                                        Text(k.fromEntity(latest)?.let { Fmt.num(it, 1) } ?: "—", style = Iron.numbers.medium)
                                    }
                                }
                            }
                        }
                    }
                }
                if (list.size >= 2) {
                    listOf(InBodyKey.SMM, InBodyKey.PBF).forEach { k ->
                        item(key = "chart_${k.name}") {
                            val points = list.mapNotNull { e -> k.fromEntity(e)?.let { ChartPoint(e.day.toDouble(), it, Fmt.dateCompact(LocalDate.ofEpochDay(e.day))) } }.sortedBy { it.x }
                            if (points.size >= 2) {
                                IronCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(k.label).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                                    LineChart(points, formatY = { Fmt.num(it, 1) }, height = 140.dp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            items(list, key = { it.id }) { e ->
                IronCard(onClick = { navigate(Routes.inBodyDetail(e.id)) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dateFull(LocalDate.ofEpochDay(e.day)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            listOfNotNull(e.weightKg?.let { Fmt.num(it, 1) + " " + kg }, e.bodyFatPct?.let { Fmt.num(it, 1) + " " + percent }).joinToString(" · "),
                            style = Iron.numbers.tiny,
                            color = Iron.colors.textSecondary,
                        )
                    }
                }
            }
            item(key = "disclaimer") {
                Text(stringResource(R.string.body_disclaimer), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
        }
    }
}

// ======================= Новое фото и проверка =======================

/** Значение поля при проверке: текст, сомнение ассистента. */
data class ReviewValue(val text: String, val uncertain: Boolean)

@HiltViewModel
class InBodyNewViewModel @Inject constructor(
    private val service: AssistantService,
    private val repo: InBodyRepository,
    private val body: BodyRepository,
    private val photos: PhotoStorage,
    private val settings: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val warningAccepted: StateFlow<Boolean?> = settings.settings
        .map { it.inBodyWarningAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _photo = MutableStateFlow<String?>(savedStateHandle.get<String>("photo"))
    val photo: StateFlow<String?> = _photo.asStateFlow()
    private val _state = MutableStateFlow<AiState<Unit>>(AiState.Idle)
    val state: StateFlow<AiState<Unit>> = _state.asStateFlow()
    private val _values = MutableStateFlow<Map<InBodyKey, ReviewValue>>(emptyMap())
    val values: StateFlow<Map<InBodyKey, ReviewValue>> = _values.asStateFlow()
    private val _day = MutableStateFlow(LocalDate.now().toEpochDay())
    val day: StateFlow<Long> = _day.asStateFlow()
    private var saved = false

    fun acceptWarning() = viewModelScope.launch { settings.setInBodyWarningAccepted(true) }

    fun newCameraUri(): Uri {
        val (uri, file) = photos.newCameraTarget()
        savedStateHandle["pending_camera"] = file.absolutePath
        return uri
    }

    fun onCameraResult(ok: Boolean) {
        val path = savedStateHandle.get<String>("pending_camera") ?: return
        savedStateHandle.remove<String>("pending_camera")
        val file = File(path)
        if (!ok) {
            file.delete()
            return
        }
        viewModelScope.launch { photos.importFile(file, PhotoKind.INBODY, 2000)?.let { setPhoto(it) } }
    }

    fun onGallery(uri: Uri) = viewModelScope.launch { photos.import(uri, PhotoKind.INBODY, 2000)?.let { setPhoto(it) } }

    private fun setPhoto(path: String) {
        _photo.value?.let { photos.delete(it) }
        _photo.value = path
        savedStateHandle["photo"] = path
        recognize()
    }

    fun recognize() {
        val path = _photo.value ?: return
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                val reading: InBodyReading = service.readInBody(InlineImage(bytes, "image/jpeg"))
                _values.value = InBodyKey.entries.associateWith { k ->
                    val f = k.fromReading(reading)
                    ReviewValue(f.value?.let { Fmt.num(it, 2) }.orEmpty(), f.uncertain || f.value == null)
                }
                reading.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { _day.value = it.toEpochDay() } }
                AiState.Done(Unit)
            }.getOrElse { it.toAiError() }
        }
    }

    /** Ручной ввод без распознавания. */
    fun manual() {
        _values.value = InBodyKey.entries.associateWith { ReviewValue("", false) }
        _state.value = AiState.Done(Unit)
    }

    fun setValue(k: InBodyKey, text: String) {
        _values.value = _values.value + (k to ReviewValue(text.filter { it.isDigit() || it == ',' || it == '.' }.take(7), false))
    }

    fun save(addToMeasurements: Boolean, onSaved: (Long) -> Unit) {
        val vals = _values.value.mapValues { Fmt.parse(it.value.text) }
        viewModelScope.launch {
            val entity = buildEntity(_day.value, _photo.value, vals)
            val id = repo.save(entity)
            saved = true
            if (addToMeasurements && (entity.weightKg != null || entity.bodyFatPct != null)) {
                body.saveMeasurement(MeasurementEntity(day = entity.day, weightKg = entity.weightKg, bodyFatPct = entity.bodyFatPct, note = "InBody"))
            }
            onSaved(id)
        }
    }

    override fun onCleared() {
        // Фото без сохранённого результата не храним.
        if (!saved) _photo.value?.let { photos.delete(it) }
    }
}

@Composable
fun InBodyNewScreen(onBack: () -> Unit, navigate: (String) -> Unit, onSaved: (Long) -> Unit, viewModel: InBodyNewViewModel = hiltViewModel()) {
    val accepted by viewModel.warningAccepted.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    var addToMeasurements by remember { mutableStateOf(true) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.onCameraResult(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.onGallery(uri) }
    // Перед первой отправкой фото — предупреждение о данных на бесплатном ключе.
    var pendingPhoto by remember { mutableStateOf<(() -> Unit)?>(null) }
    val withWarning: (() -> Unit) -> Unit = { action -> if (accepted == true) action() else pendingPhoto = action }

    IronScaffold(title = stringResource(R.string.ib_new), onBack = onBack) { padding ->
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
            Text(stringResource(R.string.ib_how), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            photo?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    stringResource(R.string.action_camera),
                    { withWarning { camera.launch(viewModel.newCameraUri()) } },
                    icon = IronIcons.Camera,
                    modifier = Modifier.weight(1f),
                    enabled = accepted != null && state !is AiState.Loading,
                )
                SecondaryButton(
                    stringResource(R.string.action_gallery),
                    { withWarning { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                    icon = IronIcons.Image,
                    modifier = Modifier.weight(1f),
                    enabled = accepted != null && state !is AiState.Loading,
                )
            }
            if (state is AiState.Idle) {
                SecondaryButton(stringResource(R.string.ib_manual), viewModel::manual, icon = IronIcons.Edit, modifier = Modifier.fillMaxWidth())
            }
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading(stringResource(R.string.ib_reading))
                is AiState.Error -> AiError(s.kind, viewModel::recognize) { navigate(Routes.SETTINGS) }
                is AiState.Done -> {
                    Text(stringResource(R.string.ib_review_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ib_review_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.warning)
                    Text(stringResource(R.string.ib_date, Fmt.dateFull(LocalDate.ofEpochDay(day))), style = MaterialTheme.typography.bodyMedium)
                    InBodyKey.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { k ->
                                val v = values[k] ?: ReviewValue("", true)
                                val unit = k.unit?.let { stringResource(it) }
                                IronTextField(
                                    value = v.text,
                                    onValueChange = { viewModel.setValue(k, it) },
                                    label = stringResource(k.label) + (unit?.let { ", $it" } ?: ""),
                                    keyboardType = KeyboardType.Decimal,
                                    isError = v.uncertain,
                                    supportingText = if (v.uncertain) stringResource(R.string.ib_check) else null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    IronCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                        SwitchRow(
                            title = stringResource(R.string.ib_add_measurement),
                            subtitle = stringResource(R.string.ib_add_measurement_sub),
                            checked = addToMeasurements,
                            onCheckedChange = { addToMeasurements = it },
                        )
                    }
                    PrimaryButton(
                        stringResource(R.string.action_save),
                        { viewModel.save(addToMeasurements, onSaved) },
                        icon = IronIcons.Save,
                        enabled = values.values.any { it.text.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    pendingPhoto?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.ib_warning_title),
            text = stringResource(R.string.ib_warning_text),
            confirmText = stringResource(R.string.ib_warning_ok),
            onConfirm = {
                pendingPhoto = null
                viewModel.acceptWarning()
                action()
            },
            onDismiss = { pendingPhoto = null },
        )
    }
}

// ======================= Результат и разбор =======================

@HiltViewModel
class InBodyDetailViewModel @Inject constructor(
    private val repo: InBodyRepository,
    private val service: AssistantService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    val entity: StateFlow<InBodyEntity?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _prev = MutableStateFlow<InBodyEntity?>(null)
    val prev: StateFlow<InBodyEntity?> = _prev.asStateFlow()
    private val _ai = MutableStateFlow<AiState<Unit>>(AiState.Idle)
    val ai: StateFlow<AiState<Unit>> = _ai.asStateFlow()

    init {
        viewModelScope.launch {
            val e = repo.observe(id).first() ?: return@launch
            _prev.value = repo.previous(e)
        }
    }

    fun analyze(label: (InBodyKey) -> String) {
        val e = entity.value ?: return
        _ai.value = AiState.Loading
        viewModelScope.launch {
            _ai.value = runCatching {
                val text = service.analyzeInBody(e.summary(_prev.value, label))
                repo.update(e.copy(analysis = text))
                AiState.Done(Unit)
            }.getOrElse { it.toAiError() }
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.delete(id)
        onDone()
    }
}

@Composable
fun InBodyDetailScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: InBodyDetailViewModel = hiltViewModel()) {
    val e by viewModel.entity.collectAsStateWithLifecycle()
    val prev by viewModel.prev.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val labels = InBodyKey.entries.associateWith { stringResource(it.label) }
    IronScaffold(
        title = stringResource(R.string.ai_f_inbody),
        onBack = onBack,
        actions = { IronIconButton(IronIcons.Delete, stringResource(R.string.action_delete), { confirmDelete = true }) },
    ) { padding ->
        val rec = e ?: return@IronScaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Text(Fmt.dateFull(LocalDate.ofEpochDay(rec.day)), style = MaterialTheme.typography.titleLarge)
            }
            rec.photoPath?.let { path ->
                item(key = "photo") {
                    AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(6.dp)))
                }
            }
            item(key = "values") {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    InBodyKey.entries.forEach { k ->
                        val v = k.fromEntity(rec) ?: return@forEach
                        val p = prev?.let { k.fromEntity(it) }
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels[k].orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.num(v, 2) + (k.unit?.let { " " + stringResource(it) } ?: ""), style = Iron.numbers.small)
                            if (p != null) {
                                val d = v - p
                                val good = k.higherIsBetter?.let { if (it) d > 0 else d < 0 }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    Fmt.signed(d, 1),
                                    style = Iron.numbers.tiny,
                                    color = when {
                                        kotlin.math.abs(d) < 0.05 || good == null -> Iron.colors.textSecondary
                                        good -> Iron.colors.success
                                        else -> Iron.colors.warning
                                    },
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "analysis") {
                IronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ib_analysis).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    rec.analysis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                    when (val s = ai) {
                        AiState.Loading -> AiLoading()
                        is AiState.Error -> AiError(s.kind, { viewModel.analyze { labels[it].orEmpty() } }) { navigate(Routes.SETTINGS) }
                        else -> Unit
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        stringResource(if (rec.analysis == null) R.string.ib_analyze else R.string.ib_analyze_again),
                        { viewModel.analyze { labels[it].orEmpty() } },
                        icon = IronIcons.Sparkles,
                        enabled = ai !is AiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.ib_analysis_note), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.ib_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
