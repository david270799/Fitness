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

    const val LIBRARY_PATTERN = "library?mode={mode}&category={category}"
    const val EXERCISE_PATTERN = "exercise/{id}"
    const val EXERCISE_EDIT_PATTERN = "exercise_edit?id={id}&category={category}"

    const val HISTORY = "workout_history"
    const val TOOLS = "tools"
    const val WORKOUT_SESSION_PATTERN = "workout_session/{id}?edit={edit}"
    const val WORKOUT_SUMMARY_PATTERN = "workout_summary/{id}"
    const val WORKOUT_DETAIL_PATTERN = "workout/{id}"
    const val ROUTINE_EDIT_PATTERN = "routine_edit?id={id}"
    const val INTERVAL_RUN = "interval_run"
    const val INTERVAL_EDIT_PATTERN = "interval_edit?id={id}"
    const val CARDIO_LOG_PATTERN = "cardio_log?id={id}"
    const val STRETCH_EDIT_PATTERN = "stretch_edit?id={id}"
    const val CHALLENGE_PATTERN = "challenge/{id}"
    const val REMINDERS = "reminders"
    const val STATS = "stats"
    const val PERMISSIONS = "permissions"
    const val REMINDER_DETAIL_PATTERN = "reminder/{id}"
    const val REMINDER_EDIT_PATTERN = "reminder_edit?id={id}"
    const val CHALLENGE_EDIT_PATTERN = "challenge_edit?id={id}"

    /** Ключ результата выбора упражнений в SavedStateHandle предыдущего экрана. */
    const val RESULT_PICKED_EXERCISES = "picked_exercises"

    fun library(mode: String = "browse", category: String? = null) =
        "library?mode=$mode" + (category?.let { "&category=$it" } ?: "")

    fun workoutSession(id: Long, edit: Boolean = false) = "workout_session/$id?edit=$edit"
    fun workoutSummary(id: Long) = "workout_summary/$id"
    fun workoutDetail(id: Long) = "workout/$id"
    fun routineEdit(id: Long? = null) = "routine_edit" + (id?.let { "?id=$it" } ?: "")
    fun intervalEdit(id: Long? = null) = "interval_edit" + (id?.let { "?id=$it" } ?: "")
    fun cardioLog(id: Long? = null) = "cardio_log" + (id?.let { "?id=$it" } ?: "")
    fun stretchEdit(id: Long? = null) = "stretch_edit" + (id?.let { "?id=$it" } ?: "")
    fun challenge(id: Long) = "challenge/$id"
    fun reminderDetail(id: Long) = "reminder/$id"
    fun reminderEdit(id: Long? = null) = "reminder_edit" + (id?.let { "?id=$it" } ?: "")
    fun challengeEdit(id: Long? = null) = "challenge_edit" + (id?.let { "?id=$it" } ?: "")

    fun exercise(id: String) = "exercise/${android.net.Uri.encode(id)}"

    fun exerciseEdit(id: String? = null, category: String? = null): String {
        val params = buildList {
            if (id != null) add("id=${android.net.Uri.encode(id)}")
            if (category != null) add("category=$category")
        }
        return "exercise_edit" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
}
