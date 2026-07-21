package com.danycli.assignmentchecker.analytics

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.util.UUID

class InstallationManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("AnalyticsPrefs", Context.MODE_PRIVATE)

    fun getInstallationId(): String {
        var id = prefs.getString("installation_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("installation_id", id).apply()
        }
        return id
    }

    fun getFirstInstallTime(): Long {
        var time = prefs.getLong("first_install_time", -1L)
        if (time == -1L) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                time = packageInfo.firstInstallTime
                prefs.edit().putLong("first_install_time", time).apply()
            } catch (e: PackageManager.NameNotFoundException) {
                // Fallback if package info is somehow unavailable
                time = System.currentTimeMillis()
                prefs.edit().putLong("first_install_time", time).apply()
            }
        }
        return time
    }

    fun isRegistered(): Boolean {
        return prefs.getBoolean("is_registered", false)
    }

    fun setRegistered(registered: Boolean) {
        prefs.edit().putBoolean("is_registered", registered).apply()
    }
}
