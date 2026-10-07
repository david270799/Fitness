package com.iron.fitness.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.timer.TimerService
import com.iron.fitness.navigation.Routes
import kotlinx.coroutines.flow.first

/** Быстрый старт таймера отдыха: 60 / 90 / 120 секунд. Отсчёт — в уведомлении. */
class RestTimerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settingsFlow = context.widgetEntryPoint().settings().settings
        val initial = settingsFlow.first()
        val res = context.withRussianLocale().resources
        provideContent {
            val settings by remember { settingsFlow }.collectAsState(initial)
            val c = widgetColors(settings.themeId)
            Column(modifier = GlanceModifier.fillMaxSize().background(c.background).cornerRadius(12.dp).padding(12.dp)) {
                WText(
                    res.getString(R.string.widget_rest_title).uppercase(),
                    c.textSecondary,
                    size = 11.sp,
                    bold = true,
                    modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(MainActivity.routeIntent(context, Routes.TOOLS))),
                )
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    SECONDS.forEachIndexed { i, sec ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        WButton(
                            Fmt.duration(sec),
                            actionStartService(restIntent(context, sec), isForegroundService = true),
                            background = c.accent,
                            color = c.onAccent,
                            height = 48.dp,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }

    private fun restIntent(context: Context, seconds: Int): Intent =
        Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_REST_START)
            // Своя data у каждой кнопки — чтобы PendingIntent не склеились.
            .setData(Uri.parse("iron://rest/$seconds"))
            .putExtra(TimerService.EXTRA_SECONDS, seconds)
            .putExtra(TimerService.EXTRA_FOREGROUND, true)

    private companion object {
        val SECONDS = listOf(60, 90, 120)
    }
}

class RestTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RestTimerWidget()
}
