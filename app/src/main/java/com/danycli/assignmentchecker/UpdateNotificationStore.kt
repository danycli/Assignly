package com.danycli.assignmentchecker

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

object UpdateNotificationStore {
    private const val PREFS_NAME = "update_notification_prefs"
    private const val KEY_LAST_NOTIFIED_DAY = "last_update_notification_day"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_update_notification_version"

    fun shouldNotifyToday(context: Context): Boolean {
        val lastNotifiedDay = prefs(context).getString(KEY_LAST_NOTIFIED_DAY, null)
        return lastNotifiedDay != todayKey()
    }

    fun markNotifiedToday(context: Context, versionCode: Int) {
        prefs(context)
            .edit()
            .putString(KEY_LAST_NOTIFIED_DAY, todayKey())
            .putInt(KEY_LAST_NOTIFIED_VERSION, versionCode)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String = LocalDate.now(ZoneId.systemDefault()).toString()
}
