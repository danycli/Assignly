package com.danycli.assignmentchecker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityUtils {
    private val prefCache = mutableMapOf<String, SharedPreferences>()

    fun getSecurePrefs(context: Context, fileName: String): SharedPreferences {
        synchronized(prefCache) {
            prefCache[fileName]?.let { return it }

            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val prefs = EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                prefCache[fileName] = prefs
                prefs
            } catch (e: Exception) {
                Log.e("SecurityUtils", "Failed to create encrypted shared preferences for $fileName", e)
                // Fallback to standard prefs if encryption fails (e.g. Keystore issues)
                // but strictly log it. For credentials, we might want to be more strict, 
                // but for general cache it's better than a crash.
                context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            }
        }
    }
}
