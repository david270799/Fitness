package com.iron.fitness.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.daily.data.ChallengeUi
import com.iron.fitness.feature.daily.ui.challengeValue
import com.iron.fitness.feature.daily.ui.stepLabel
import com.iron.fitness.navigation.Routes
import kotlinx.coroutines.flow.first

/**
 * Виджет дневного челленджа: прогресс за сегодня, серия, быстрые кнопки +шаг1/+шаг2,
 * «+…» — своё число в приложении. Нажатие на название — следующий челлендж.
 */
class CounterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = context.widgetEntryPoint()
        val challengesFlow = ep.challenges().observeActive()
        val settingsFlow = ep.settings().settings
        val initialList = challengesFlow.first()
        val initialSettings = settingsFlow.first()
        val res = context.withRussianLocale().resources
        provideContent {
            val list by remember { challengesFlow }.collectAsState(initialList)
            val settings by remember { settingsFlow }.collectAsState(initialSettings)
            val selectedId = currentState<Preferences>()[KEY_CHALLENGE]
            val ui = list.firstOrNull { it.challenge.id == selectedId } ?: list.firstOrNull()
            CounterContent(context, ui, list.size, settings, res)
        }
    }

    companion object {
        val KEY_CHALLENGE = longPreferencesKey("challenge_id")
    }
}

@Composable
private fun CounterContent(context: Context, ui: ChallengeUi?, count: Int, settings: AppSettings, res: android.content.res.Resources) {
    val c = widgetColors(settings.themeId)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(c.background).cornerRadius(12.dp).padding(12.dp),
    ) {
        if (ui == null) {
            WText(res.getString(R.string.widget_counter_name), c.textSecondary, size = 11.sp, bold = true)
            Spacer(GlanceModifier.height(6.dp))
            WText(
                res.getString(R.string.widget_no_challenges),
                c.text,
                maxLines = 3,
                modifier = GlanceModifier.clickable(actionStartActivity(MainActivity.routeIntent(context, Routes.DAILY))),
            )
            return@Column
        }
        val ch = ui.challenge
        val nameAction = if (count > 1) {
            actionRunCallback<NextChallengeAction>()
        } else {
            actionStartActivity(MainActivity.routeIntent(context, Routes.challenge(ch.id)))
        }
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(nameAction), verticalAlignment = Alignment.CenterVertically) {
            WText(ch.name.uppercase(), c.textSecondary, size = 11.sp, bold = true, modifier = GlanceModifier.defaultWeight())
            if (ui.stats.streak > 0) {
                WText(res.getString(R.string.widget_streak, ui.stats.streak), c.accentText, size = 11.sp, bold = true, mono = true)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            WText(
                challengeValue(ui.stats.todayTotal, ch.unit),
                if (ui.doneToday) c.success else c.text,
                size = 28.sp,
                bold = true,
                mono = true,
            )
            WText(" / " + challengeValue(ui.stats.todayGoal, ch.unit), c.textSecondary, size = 14.sp, mono = true)
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = ui.progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = ColorProvider(c.accent),
            backgroundColor = ColorProvider(c.track),
        )
        Spacer(GlanceModifier.defaultWeight())
        val sec = res.getString(R.string.unit_sec)
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WButton(
                stepLabel(ch.step1, ch.unit, sec),
                actionRunCallback<AddToChallengeAction>(actionParametersOf(AddToChallengeAction.ID to ch.id, AddToChallengeAction.AMOUNT to ch.step1)),
                background = c.accent,
                color = c.onAccent,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WButton(
                stepLabel(ch.step2, ch.unit, sec),
                actionRunCallback<AddToChallengeAction>(actionParametersOf(AddToChallengeAction.ID to ch.id, AddToChallengeAction.AMOUNT to ch.step2)),
                background = c.accent,
                color = c.onAccent,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WButton(
                res.getString(R.string.widget_more),
                actionStartActivity(MainActivity.routeIntent(context, Routes.challenge(ch.id))),
                background = c.track,
                color = c.text,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** +N к челленджу прямо с виджета. */
class AddToChallengeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        val amount = parameters[AMOUNT] ?: return
        context.widgetEntryPoint().challenges().add(id, amount)
        CounterWidget().update(context, glanceId)
    }

    companion object {
        val ID = ActionParameters.Key<Long>("challenge_id")
        val AMOUNT = ActionParameters.Key<Int>("amount")
    }
}

/** Переключить виджет на следующий активный челлендж. */
class NextChallengeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val list = context.widgetEntryPoint().challenges().getActive()
        if (list.isEmpty()) return
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[CounterWidget.KEY_CHALLENGE]
            val index = list.indexOfFirst { it.id == current }
            prefs[CounterWidget.KEY_CHALLENGE] = list[(index + 1).mod(list.size)].id
        }
        CounterWidget().update(context, glanceId)
    }
}

class CounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}
