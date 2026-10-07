package com.iron.fitness.feature.assistant

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.ai.SecretStore
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.ListRow
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(secrets: SecretStore) : ViewModel() {
    val hasKey: StateFlow<Boolean> = secrets.hasGeminiKey
}

private data class AiFeature(val route: String, @StringRes val title: Int, @StringRes val sub: Int, @DrawableRes val icon: Int, val needsKey: Boolean = true)

private val features = listOf(
    AiFeature(Routes.AI_WORKOUT, R.string.ai_f_workout, R.string.ai_f_workout_sub, IronIcons.Sparkles),
    AiFeature(Routes.AI_LOG, R.string.ai_f_log, R.string.ai_f_log_sub, IronIcons.Mic),
    AiFeature(Routes.AI_REVIEW, R.string.ai_f_review, R.string.ai_f_review_sub, IronIcons.ChartLine),
    AiFeature(Routes.AI_SUBSTITUTE, R.string.ai_f_substitute, R.string.ai_f_substitute_sub, IronIcons.Repeat),
    AiFeature(Routes.AI_STRETCH, R.string.ai_f_stretch, R.string.ai_f_stretch_sub, IronIcons.Stretch),
    AiFeature(Routes.AI_PROGRESSION, R.string.ai_f_progression, R.string.ai_f_progression_sub, IronIcons.Lightbulb, needsKey = false),
    AiFeature(Routes.INBODY, R.string.ai_f_inbody, R.string.ai_f_inbody_sub, IronIcons.Scan, needsKey = false),
)

@Composable
fun AssistantScreen(onBack: () -> Unit, navigate: (String) -> Unit, viewModel: AssistantViewModel = hiltViewModel()) {
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    IronScaffold(title = stringResource(R.string.nav_assistant), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasKey) {
                item(key = "nokey") {
                    IronCard(borderColor = Iron.colors.accent, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_need_key_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ai_need_key_text), style = MaterialTheme.typography.bodyMedium, color = Iron.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                        PrimaryButton(stringResource(R.string.ai_open_settings), { navigate(Routes.SETTINGS) }, icon = IronIcons.Settings, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(features, key = { it.route }) { f ->
                val enabled = hasKey || !f.needsKey
                IronCard(onClick = if (enabled) ({ navigate(f.route) }) else null, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                    ListRow(
                        title = stringResource(f.title),
                        subtitle = stringResource(f.sub),
                        icon = f.icon,
                        iconTint = if (enabled) Iron.colors.accentText else Iron.colors.textSecondary,
                        trailing = { IronIcon(IronIcons.ChevronRight, null, tint = Iron.colors.textSecondary) },
                    )
                }
            }
            item(key = "privacy") {
                Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            }
        }
    }
}
