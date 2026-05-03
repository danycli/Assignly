package com.danycli.assignmentchecker

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialsStore {
    fun save(context: Context, username: String, password: String) {
        migratePlaintextCredentialsIfNeeded(context)
        val prefs = securePrefs(context)
        prefs.edit().apply {
            putString("saved_username", username)
            putString("saved_password", password)
            apply()
        }
    }

    fun get(context: Context): Pair<String, String>? {
        migratePlaintextCredentialsIfNeeded(context)
        val prefs = securePrefs(context)
        val username = prefs.getString("saved_username", null)
        val password = prefs.getString("saved_password", null)
        return if (username != null && password != null) Pair(username, password) else null
    }

    fun clear(context: Context) {
        val secure = securePrefs(context)
        secure.edit()
            .remove("saved_username")
            .remove("saved_password")
            .apply()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("saved_username")
            .remove("saved_password")
            .apply()
    }
}

private fun securePrefs(context: Context): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "secure_app_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun migratePlaintextCredentialsIfNeeded(context: Context) {
    val legacyPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val secure = securePrefs(context)
    val alreadyMigrated = secure.getBoolean("credentials_migrated", false)
    if (alreadyMigrated) {
        legacyPrefs.edit().remove("saved_username").remove("saved_password").apply()
        return
    }

    val legacyUsername = legacyPrefs.getString("saved_username", null)
    val legacyPassword = legacyPrefs.getString("saved_password", null)
    if (!legacyUsername.isNullOrBlank() && !legacyPassword.isNullOrBlank()) {
        secure.edit()
            .putString("saved_username", legacyUsername)
            .putString("saved_password", legacyPassword)
            .putBoolean("credentials_migrated", true)
            .apply()
    } else {
        secure.edit().putBoolean("credentials_migrated", true).apply()
    }

    legacyPrefs.edit().remove("saved_username").remove("saved_password").apply()
}
