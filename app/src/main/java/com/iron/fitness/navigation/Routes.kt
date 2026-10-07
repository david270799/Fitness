package com.iron.fitness.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons

/** Разделы нижней панели. */
enum class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(Routes.TODAY, R.string.nav_today, IronIcons.Today),
    WORKOUTS(Routes.WORKOUTS, R.string.nav_workouts, IronIcons.Workouts),
    DAILY(Routes.DAILY, R.string.nav_daily, IronIcons.Daily),
    BODY(Routes.BODY, R.string.nav_body, IronIcons.Body),
    MORE(Routes.MORE, R.string.nav_more, IronIcons.More),
}

object Routes {
    const val TODAY = "today"
    const val WORKOUTS = "workouts"
    const val DAILY = "daily"
    const val BODY = "body"
    const val MORE = "more"

    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ASSISTANT = "assistant"
}
