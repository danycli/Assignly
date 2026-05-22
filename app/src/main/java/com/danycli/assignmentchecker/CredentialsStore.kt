package com.danycli.assignmentchecker

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences

object CredentialsStore {
    private const val PREFS_NAME = "secure_app_prefs"
    private const val KEY_USERNAME = "saved_username"
    private const val KEY_PASSWORD = "saved_password"
    private const val KEY_MIGRATED = "credentials_migrated"
    private const val LEGACY_PREFS_NAME = "app_prefs"

    fun save(context: Context, username: String, password: String) {
        migratePlaintextCredentialsIfNeeded(context)
        val prefs = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
        prefs.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            apply()
        }
    }

    fun get(context: Context): Pair<String, String>? {
        migratePlaintextCredentialsIfNeeded(context)
        val prefs = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return if (username != null && password != null) Pair(username, password) else null
    }

    fun clear(context: Context) {
        val secure = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
        secure.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
        
        // Clean legacy just in case
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private fun migratePlaintextCredentialsIfNeeded(context: Context) {
        val secure = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
        
        // If we failed to get encrypted prefs (fallback to standard), migration is moot or already standard.
        // We only migrate if the secure prefs are actually EncryptedSharedPreferences (which we can't easily check via interface,
        // but we can check if it's the intended secure file).
        
        val alreadyMigrated = secure.getBoolean(KEY_MIGRATED, false)
        if (alreadyMigrated) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyUsername = legacyPrefs.getString(KEY_USERNAME, null)
        val legacyPassword = legacyPrefs.getString(KEY_PASSWORD, null)

        if (!legacyUsername.isNullOrBlank() && !legacyPassword.isNullOrBlank()) {
            secure.edit()
                .putString(KEY_USERNAME, legacyUsername)
                .putString(KEY_PASSWORD, legacyPassword)
                .putBoolean(KEY_MIGRATED, true)
                .apply()
        } else {
            secure.edit().putBoolean(KEY_MIGRATED, true).apply()
        }

        legacyPrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }
}
