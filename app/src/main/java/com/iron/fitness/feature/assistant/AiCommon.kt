package com.iron.fitness.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ai.GeminiException
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.feature.exercises.data.ExerciseEntity

/** Состояние запроса к ассистенту. */
sealed interface AiState<out T> {
    data object Idle : AiState<Nothing>
    data object Loading : AiState<Nothing>
    data class Error(val kind: GeminiException.Kind) : AiState<Nothing>
    data class Done<T>(val value: T) : AiState<T>
}

fun Throwable.toAiError(): AiState.Error =
    AiState.Error((this as? GeminiException)?.kind ?: GeminiException.Kind.BAD_RESPONSE)

@Composable
fun aiErrorText(kind: GeminiException.Kind): String = stringResource(
    when (kind) {
        GeminiException.Kind.NO_KEY -> R.string.ai_err_no_key
        GeminiException.Kind.INVALID_KEY -> R.string.ai_err_invalid_key
        GeminiException.Kind.QUOTA -> R.string.ai_err_quota
        GeminiException.Kind.OVERLOADED -> R.string.ai_err_overloaded
        GeminiException.Kind.MODEL_NOT_FOUND -> R.string.ai_err_model
        GeminiException.Kind.BLOCKED -> R.string.ai_err_blocked
        GeminiException.Kind.NETWORK -> R.string.ai_err_network
        GeminiException.Kind.BAD_RESPONSE -> R.string.ai_err_bad_response
    },
)

@Composable
fun AiLoading(text: String = stringResource(R.string.ai_thinking)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Iron.colors.accent, strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary)
    }
}

@Composable
fun AiError(kind: GeminiException.Kind, onRetry: () -> Unit, onSettings: () -> Unit) {
    IronCard(borderColor = Iron.colors.error, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIcon(IronIcons.Warning, null, tint = Iron.colors.error)
            Spacer(Modifier.width(10.dp))
            Text(aiErrorText(kind), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == GeminiException.Kind.NO_KEY || kind == GeminiException.Kind.INVALID_KEY || kind == GeminiException.Kind.MODEL_NOT_FOUND) {
                SecondaryButton(stringResource(R.string.ai_open_settings), onSettings, modifier = Modifier.weight(1f))
            }
            SecondaryButton(stringResource(R.string.action_retry), onRetry, modifier = Modifier.weight(1f))
        }
    }
}

/** Строка сопоставления: что предложил ассистент → что нашлось в библиотеке. */
@Composable
fun MatchedLine(title: String, detail: String?, exercise: ExerciseEntity?, onOpen: ((String) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IronIcon(
            if (exercise != null) IronIcons.CircleCheck else IronIcons.Warning,
            null,
            tint = if (exercise != null) Iron.colors.success else Iron.colors.warning,
            size = 18.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(exercise?.name ?: title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (exercise == null) {
                Text(stringResource(R.string.ai_not_in_library, title), style = MaterialTheme.typography.labelSmall, color = Iron.colors.warning)
            }
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary) }
        }
        if (exercise != null && onOpen != null) {
            com.iron.fitness.core.ui.components.IronIconButton(IronIcons.Info, stringResource(R.string.session_menu_open), { onOpen(exercise.id) }, tint = Iron.colors.textSecondary)
        }
    }
}

@Composable
fun SentDataNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
}
