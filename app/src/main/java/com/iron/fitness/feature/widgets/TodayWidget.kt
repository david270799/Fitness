package com.iron.fitness.feature.widgets

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.daily.data.ChallengeUi
import com.iron.fitness.feature.daily.ui.challengeValue
import com.iron.fitness.feature.reminders.ui.TodayDose
import com.iron.fitness.feature.reminders.ui.todayDoses
import com.iron.fitness.feature.workouts.data.WorkoutEntity
import com.iron.fitness.navigation.Routes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private data class TodayData(
    val workouts: List<WorkoutEntity>,
    val challenges: List<ChallengeUi>,
    val doses: List<TodayDose>,
    val settings: AppSettings,
)

/** Виджет «Сегодня»: тренировки за день, челленджи, ближайший приём с отметкой «Принял». */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = context.widgetEntryPoint()
        val since = startOfToday()
        val reminders = ep.reminders()
        val data: Flow<TodayData> = combine(
            ep.workouts().observeFinishedSince(since),
            ep.challenges().observeActive(),
            combine(reminders.observeAll(), reminders.observeLogsSince(since)) { list, logs -> todayDoses(list, logs) },
            ep.settings().settings,
        ) { w, ch, doses, s -> TodayData(w, ch, doses, s) }
        val initial = data.first()
        val res = context.withRussianLocale().resources
        provideContent {
            val d by remember { data }.collectAsState(initial)
            TodayContent(context, d, res)
        }
    }
}

@Composable
private fun TodayContent(context: Context, d: TodayData, res: Resources) {
    val c = widgetColors(d.settings.themeId)
    val open = actionStartActivity(MainActivity.routeIntent(context, Routes.TODAY))
    Column(modifier = GlanceModifier.fillMaxSize().background(c.background).cornerRadius(12.dp).padding(12.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            WText(res.getString(R.string.widget_today_title).uppercase(), c.accentText, size = 11.sp, bold = true, modifier = GlanceModifier.defaultWeight())
            WText(Fmt.date(LocalDate.now()), c.textSecondary, size = 11.sp, mono = true)
        }
        Spacer(GlanceModifier.height(6.dp))
        val workouts = d.workouts
        val workoutText = if (workouts.isEmpty()) {
            res.getString(R.string.widget_no_workouts)
        } else {
            val minutes = (workouts.sumOf { it.durationSec } / 60).toInt()
            val kcal = workouts.sumOf { it.caloriesKcal ?: 0.0 }.toInt()
            res.getString(R.string.widget_workouts, workouts.size, minutes, kcal)
        }
        WText(workoutText, if (workouts.isEmpty()) c.textSecondary else c.text, size = 13.sp, bold = workouts.isNotEmpty(), modifier = GlanceModifier.clickable(open))
        if (d.challenges.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            d.challenges.take(3).forEach { ui ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)
                        .clickable(actionStartActivity(MainActivity.routeIntent(context, Routes.challenge(ui.challenge.id)))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WText(ui.challenge.name, c.text, size = 13.sp, modifier = GlanceModifier.defaultWeight())
                    WText(
                        challengeValue(ui.stats.todayTotal, ui.challenge.unit) + "/" + challengeValue(ui.stats.todayGoal, ui.challenge.unit),
                        if (ui.doneToday) c.success else c.textSecondary,
                        size = 13.sp,
                        mono = true,
                    )
                }
            }
        }
        if (d.doses.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            val next = d.doses.firstOrNull { it.status == DoseStatus.PENDING }
            if (next == null) {
                WText(res.getString(R.string.widget_doses_done), c.success, size = 12.sp)
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WText(
                        res.getString(R.string.widget_dose_next, next.reminder.name, Fmt.time(next.at)),
                        c.text,
                        size = 13.sp,
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 2,
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    WButton(
                        res.getString(R.string.widget_dose_taken),
                        actionRunCallback<TakeDoseAction>(actionParametersOf(TakeDoseAction.ID to next.reminder.id, TakeDoseAction.AT to next.at)),
                        background = c.accent,
                        color = c.onAccent,
                        height = 34.dp,
                        modifier = GlanceModifier.width(88.dp),
                    )
                }
            }
        }
    }
}

/** Отметить приём прямо с виджета. */
class TakeDoseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        val at = parameters[AT] ?: return
        context.widgetEntryPoint().reminderAlarms().onTaken(id, at)
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val ID = ActionParameters.Key<Long>("reminder_id")
        val AT = ActionParameters.Key<Long>("at")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
