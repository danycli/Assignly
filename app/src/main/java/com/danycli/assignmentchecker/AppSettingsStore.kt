package com.danycli.assignmentchecker

import android.content.Context

data class AppSettings(
    val backgroundSyncEnabled: Boolean,
    val syncIntervalHours: Long,
    val backgroundUploadEnabled: Boolean,
    val updateNotificationsEnabled: Boolean,
    val uploadNotificationsEnabled: Boolean,
    val assignmentNotificationsEnabled: Boolean,
    val marksNotificationsEnabled: Boolean,
    val downloadBehavior: DownloadBehavior,
    val themeMode: ThemeMode,
    val rememberRegistrationNumber: Boolean = true
)

object AppSettingsStore {
    private const val PREFS_NAME = "assignly_settings"
    private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
    private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
    private const val KEY_BACKGROUND_UPLOAD_ENABLED = "background_upload_enabled"
    private const val KEY_UPDATE_NOTIFICATIONS_ENABLED = "update_notifications_enabled"
    private const val KEY_UPLOAD_NOTIFICATIONS_ENABLED = "upload_notifications_enabled"
    private const val KEY_ASSIGNMENT_NOTIFICATIONS_ENABLED = "assignment_notifications_enabled"
    private const val KEY_MARKS_NOTIFICATIONS_ENABLED = "marks_notifications_enabled"
    private const val KEY_DOWNLOAD_BEHAVIOR = "download_behavior"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_REMEMBER_REGISTRATION_NUMBER = "remember_registration_number"

    private const val DEFAULT_BACKGROUND_SYNC_ENABLED = true
    private const val DEFAULT_SYNC_INTERVAL_HOURS = 6L
    private const val DEFAULT_BACKGROUND_UPLOAD_ENABLED = true
    private const val DEFAULT_UPDATE_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_UPLOAD_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_ASSIGNMENT_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_MARKS_NOTIFICATIONS_ENABLED = true
    private val DEFAULT_DOWNLOAD_BEHAVIOR = DownloadBehavior.ASK_EVERY_TIME
    private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    private const val DEFAULT_REMEMBER_REGISTRATION_NUMBER = true

    fun get(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppSettings(
            backgroundSyncEnabled = prefs.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, DEFAULT_BACKGROUND_SYNC_ENABLED),
            syncIntervalHours = prefs.getLong(KEY_SYNC_INTERVAL_HOURS, DEFAULT_SYNC_INTERVAL_HOURS).coerceIn(1L, 24L),
            backgroundUploadEnabled = prefs.getBoolean(KEY_BACKGROUND_UPLOAD_ENABLED, DEFAULT_BACKGROUND_UPLOAD_ENABLED),
            updateNotificationsEnabled = prefs.getBoolean(
                KEY_UPDATE_NOTIFICATIONS_ENABLED,
                DEFAULT_UPDATE_NOTIFICATIONS_ENABLED
            ),
            uploadNotificationsEnabled = prefs.getBoolean(
                KEY_UPLOAD_NOTIFICATIONS_ENABLED,
                DEFAULT_UPLOAD_NOTIFICATIONS_ENABLED
            ),
            assignmentNotificationsEnabled = prefs.getBoolean(
                KEY_ASSIGNMENT_NOTIFICATIONS_ENABLED,
                DEFAULT_ASSIGNMENT_NOTIFICATIONS_ENABLED
            ),
            marksNotificationsEnabled = prefs.getBoolean(
                KEY_MARKS_NOTIFICATIONS_ENABLED,
                DEFAULT_MARKS_NOTIFICATIONS_ENABLED
            ),
            downloadBehavior = prefs.getString(KEY_DOWNLOAD_BEHAVIOR, DEFAULT_DOWNLOAD_BEHAVIOR.name)
                ?.let { runCatching { DownloadBehavior.valueOf(it) }.getOrNull() }
                ?: DEFAULT_DOWNLOAD_BEHAVIOR,
            themeMode = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE.name)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: DEFAULT_THEME_MODE,
            rememberRegistrationNumber = prefs.getBoolean(
                KEY_REMEMBER_REGISTRATION_NUMBER,
                DEFAULT_REMEMBER_REGISTRATION_NUMBER
            )
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKGROUND_SYNC_ENABLED, settings.backgroundSyncEnabled)
            .putLong(KEY_SYNC_INTERVAL_HOURS, settings.syncIntervalHours.coerceIn(1L, 24L))
            .putBoolean(KEY_BACKGROUND_UPLOAD_ENABLED, settings.backgroundUploadEnabled)
            .putBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, settings.updateNotificationsEnabled)
            .putBoolean(KEY_UPLOAD_NOTIFICATIONS_ENABLED, settings.uploadNotificationsEnabled)
            .putBoolean(KEY_ASSIGNMENT_NOTIFICATIONS_ENABLED, settings.assignmentNotificationsEnabled)
            .putBoolean(KEY_MARKS_NOTIFICATIONS_ENABLED, settings.marksNotificationsEnabled)
            .putString(KEY_DOWNLOAD_BEHAVIOR, settings.downloadBehavior.name)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_REMEMBER_REGISTRATION_NUMBER, settings.rememberRegistrationNumber)
            .apply()
    }
}
