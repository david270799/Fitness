package com.iron.fitness.feature.assistant

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.IronTextField
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.RU
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogWorkoutViewModel @Inject constructor(private val service: AssistantService) : ViewModel() {
    private val _state = MutableStateFlow<AiState<Pair<AiLog, List<Matched<AiLogExercise>>>>>(AiState.Idle)
    val state: StateFlow<AiState<Pair<AiLog, List<Matched<AiLogExercise>>>>> = _state.asStateFlow()
    private var lastText = ""

    fun parse(text: String) {
        lastText = text
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { AiState.Done(service.parseLog(text)) }.getOrElse { it.toAiError() }
        }
    }

    fun retry() = parse(lastText)

    fun save(minutes: Int, defaultName: String, onSaved: (Long) -> Unit) {
        val done = _state.value as? AiState.Done ?: return
        viewModelScope.launch {
            val start = System.currentTimeMillis() - minutes * 60_000L
            onSaved(service.saveLog(done.value.first, done.value.second, start, minutes, defaultName))
        }
    }
}

/** Запись тренировки словами: текст или голос → разбор ассистентом → проверка → журнал. */
@Composable
fun LogWorkoutScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: LogWorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    val defaultName = stringResource(R.string.workouts_default_name)
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) text = (text.trim() + " " + heard).trim()
        }
    }
    val prompt = stringResource(R.string.ai_log_speak)

    IronScaffold(title = stringResource(R.string.ai_f_log), onBack = onBack) { padding ->
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
            Text(stringResource(R.string.ai_log_hint), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
            IronTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.ai_log_text),
                placeholder = stringResource(R.string.ai_log_example),
                singleLine = false,
                minLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    stringResource(R.string.ai_log_voice),
                    {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, RU.toLanguageTag())
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                        runCatching { speech.launch(intent) }
                    },
                    icon = IronIcons.Mic,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    stringResource(R.string.ai_log_parse),
                    { viewModel.parse(text) },
                    enabled = text.isNotBlank() && state !is AiState.Loading,
                    modifier = Modifier.weight(1f),
                )
            }
            SentDataNote(stringResource(R.string.ai_sent_log))
            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Loading -> AiLoading()
                is AiState.Error -> AiError(s.kind, viewModel::retry) { navigate(Routes.SETTINGS) }
                is AiState.Done -> {
                    val (log, matched) = s.value
                    Text(stringResource(R.string.ai_check_before_save), style = MaterialTheme.typography.titleSmall, color = Iron.colors.accentText)
                    IronCard(modifier = Modifier.fillMaxWidth()) {
                        log.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        matched.forEach { m ->
                            MatchedLine(
                                title = m.ai.nameRu,
                                detail = m.ai.sets.joinToString(", ") { st ->
                                    when {
                                        st.weightKg != null && st.reps != null -> "${Fmt.num(st.weightKg)}×${st.reps}"
                                        st.reps != null -> st.reps.toString()
                                        st.seconds != null -> Fmt.duration(st.seconds)
                                        else -> "?"
                                    }
                                },
                                exercise = m.exercise,
                                onOpen = { navigate(Routes.exercise(it)) },
                            )
                        }
                    }
                    if (matched.any { it.exercise == null }) {
                        Text(stringResource(R.string.ai_unmatched_skipped), style = MaterialTheme.typography.bodySmall, color = Iron.colors.warning)
                    }
                    LaunchedEffect(log) {
                        if (minutes.isEmpty()) log.durationMin?.let { minutes = it.toString() }
                    }
                    IronTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter(Char::isDigit).take(3) },
                        label = stringResource(R.string.session_edit_duration),
                        keyboardType = KeyboardType.Number,
                    )
                    PrimaryButton(
                        stringResource(R.string.ai_log_save),
                        { viewModel.save(minutes.toIntOrNull() ?: 60, defaultName) { id -> onBack(); navigate(Routes.workoutDetail(id)) } },
                        icon = IronIcons.Save,
                        enabled = matched.any { it.exercise != null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
