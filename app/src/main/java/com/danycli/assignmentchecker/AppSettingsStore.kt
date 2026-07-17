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
    val classNotificationsEnabled: Boolean = true,
    val autoLogin: Boolean,
    val checkUpdates: Boolean,
    val themeMode: ThemeMode,
    val rememberRegistrationNumber: Boolean = true,
    val biometricLockEnabled: Boolean = false
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
    private const val KEY_CLASS_NOTIFICATIONS_ENABLED = "class_notifications_enabled"
    private const val KEY_AUTO_LOGIN = "auto_login"
    private const val KEY_CHECK_UPDATES = "check_updates"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_REMEMBER_REGISTRATION_NUMBER = "remember_registration_number"
    private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"

    private const val DEFAULT_BACKGROUND_SYNC_ENABLED = true
    private const val DEFAULT_SYNC_INTERVAL_HOURS = 6L
    private const val DEFAULT_BACKGROUND_UPLOAD_ENABLED = true
    private const val DEFAULT_UPDATE_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_UPLOAD_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_ASSIGNMENT_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_MARKS_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_CLASS_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_AUTO_LOGIN = false
    private const val DEFAULT_CHECK_UPDATES = true
    private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    private const val DEFAULT_REMEMBER_REGISTRATION_NUMBER = true
    private const val DEFAULT_BIOMETRIC_LOCK_ENABLED = false

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
            classNotificationsEnabled = prefs.getBoolean(
                KEY_CLASS_NOTIFICATIONS_ENABLED,
                DEFAULT_CLASS_NOTIFICATIONS_ENABLED
            ),
            autoLogin = prefs.getBoolean(KEY_AUTO_LOGIN, DEFAULT_AUTO_LOGIN),
            checkUpdates = prefs.getBoolean(KEY_CHECK_UPDATES, DEFAULT_CHECK_UPDATES),
            themeMode = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE.name)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: DEFAULT_THEME_MODE,
            rememberRegistrationNumber = prefs.getBoolean(
                KEY_REMEMBER_REGISTRATION_NUMBER,
                DEFAULT_REMEMBER_REGISTRATION_NUMBER
            ),
            biometricLockEnabled = prefs.getBoolean(
                KEY_BIOMETRIC_LOCK_ENABLED,
                DEFAULT_BIOMETRIC_LOCK_ENABLED
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
            .putBoolean(KEY_CLASS_NOTIFICATIONS_ENABLED, settings.classNotificationsEnabled)
            .putBoolean(KEY_AUTO_LOGIN, settings.autoLogin)
            .putBoolean(KEY_CHECK_UPDATES, settings.checkUpdates)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_REMEMBER_REGISTRATION_NUMBER, settings.rememberRegistrationNumber)
            .putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, settings.biometricLockEnabled)
            .apply()
    }
}
