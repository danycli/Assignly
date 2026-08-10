package com.danycli.assignmentchecker

import android.content.Context

object UpdateNotificationStore {
    private const val PREFS_NAME = "update_notification_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_update_notification_version"

    fun shouldNotify(context: Context, latestVersionCode: Int): Boolean {
        val lastNotifiedVersion = prefs(context).getInt(KEY_LAST_NOTIFIED_VERSION, -1)
        return latestVersionCode > lastNotifiedVersion
    }

    fun markNotified(context: Context, versionCode: Int) {
        prefs(context)
            .edit()
            .putInt(KEY_LAST_NOTIFIED_VERSION, versionCode)
            .apply()
    }
    
    fun hasNotified(context: Context, versionCode: Int): Boolean {
        val lastNotifiedVersion = prefs(context).getInt(KEY_LAST_NOTIFIED_VERSION, -1)
        return lastNotifiedVersion == versionCode
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
