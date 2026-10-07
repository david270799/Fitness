package com.iron.fitness.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.ui.theme.IronPalette
import com.iron.fitness.core.ui.theme.IronPalettes
import com.iron.fitness.di.ApplicationScope
import com.iron.fitness.feature.daily.data.ChallengeRepository
import com.iron.fitness.feature.reminders.data.ReminderAlarms
import com.iron.fitness.feature.reminders.data.ReminderRepository
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Доступ виджетов к репозиториям (виджеты создаются системой, не Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun challenges(): ChallengeRepository
    fun workouts(): WorkoutRepository
    fun reminders(): ReminderRepository
    fun reminderAlarms(): ReminderAlarms
    fun settings(): SettingsRepository
}

fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Цвета виджета из выбранной темы приложения. */
class WidgetColors(p: IronPalette) {
    val background = p.surface
    val text = ColorProvider(p.text)
    val textSecondary = ColorProvider(p.textSecondary)
    val accent = p.accent
    val accentText = ColorProvider(p.accentText)
    val onAccent = ColorProvider(p.onAccent)
    val button = p.background
    val success = ColorProvider(p.success)
    val track = p.border
}

fun widgetColors(themeId: String?): WidgetColors = WidgetColors(IronPalettes.byId(themeId))

fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun WText(
    text: String,
    color: ColorProvider,
    size: TextUnit = 13.sp,
    bold: Boolean = false,
    mono: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else null,
        ),
    )
}

/** Плоская кнопка виджета. */
@Composable
fun WButton(
    text: String,
    onClick: Action,
    background: Color,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    height: Dp = 40.dp,
) {
    Box(
        modifier = modifier.height(height).background(background).cornerRadius(6.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        WText(text, color, size = 14.sp, bold = true, mono = true)
    }
}

/**
 * Обновляет виджеты, когда меняются данные (запись в челлендж, тренировка, приём, тема).
 * Работает, пока жив процесс приложения — а данные меняются только в нём.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val challenges: ChallengeRepository,
    private val workouts: WorkoutRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        val since = startOfToday()
        scope.launch {
            merge(
                challenges.observeActive().map { },
                workouts.observeFinishedSince(since).map { },
                reminders.observeLogsSince(since).map { },
                reminders.observeAll().map { },
                settings.settings.map { it.themeId },
            ).debounce(1_500).collect { refreshNow() }
        }
    }

    suspend fun refreshNow() {
        runCatching { CounterWidget().updateAll(context) }
        runCatching { TodayWidget().updateAll(context) }
        runCatching { RestTimerWidget().updateAll(context) }
    }
}
