package com.danycli.assignmentchecker

import android.content.Context
import java.time.LocalDate

/**
 * Tracks daily app open counts and initial notification prompt state.
 * Used to intelligently prompt users to enable notifications.
 */
object NotificationPromptStore {
    private const val PREFS_NAME = "assignly_notification_prompt"
    private const val KEY_INITIAL_PROMPT_SHOWN = "initial_prompt_shown"
    private const val KEY_DAILY_OPEN_COUNT = "daily_open_count"
    private const val KEY_LAST_OPEN_DATE = "last_open_date"
    private const val KEY_DAILY_PROMPT_SHOWN = "daily_prompt_shown"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasShownInitialPrompt(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INITIAL_PROMPT_SHOWN, false)

    fun markInitialPromptShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_INITIAL_PROMPT_SHOWN, true).apply()
    }

    /**
     * Increments the daily open counter. Resets if it's a new day.
     * Returns the new count for today.
     */
    fun incrementDailyOpen(context: Context): Int {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        val lastDate = p.getString(KEY_LAST_OPEN_DATE, null)

        val currentCount = if (lastDate == today) {
            p.getInt(KEY_DAILY_OPEN_COUNT, 0) + 1
        } else {
            // New day, reset counter and daily prompt flag
            p.edit().putBoolean(KEY_DAILY_PROMPT_SHOWN, false).apply()
            1
        }

        p.edit()
            .putString(KEY_LAST_OPEN_DATE, today)
            .putInt(KEY_DAILY_OPEN_COUNT, currentCount)
            .apply()

        return currentCount
    }

    fun hasDailyPromptBeenShown(context: Context): Boolean {
        val today = LocalDate.now().toString()
        val lastDate = prefs(context).getString(KEY_LAST_OPEN_DATE, null)
        if (lastDate != today) return false
        return prefs(context).getBoolean(KEY_DAILY_PROMPT_SHOWN, false)
    }

    fun markDailyPromptShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_DAILY_PROMPT_SHOWN, true).apply()
    }
}
