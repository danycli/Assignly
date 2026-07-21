package com.danycli.assignmentchecker

import android.content.Context
import android.content.SharedPreferences

object RegistrationHistoryStore {
    private const val PREFS_NAME = "assignly_reg_history"
    private const val KEY_REGISTRATIONS = "saved_registrations"
    private const val MAX_ACCOUNTS = 10

    // Allow overriding SharedPreferences for local unit testing
    var getPrefs: (Context?) -> SharedPreferences = { context ->
        context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedRegistrations(context: Context?): List<String> {
        val prefs = getPrefs(context)
        val rawStr = prefs.getString(KEY_REGISTRATIONS, null) ?: return emptyList()
        if (rawStr.isBlank()) return emptyList()
        return rawStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveRegistration(context: Context?, regNumber: String) {
        val cleaned = regNumber.trim().uppercase()
        if (cleaned.isBlank()) return

        val currentList = getSavedRegistrations(context).toMutableList()
        
        // Remove if it already exists to avoid duplicates and move it to the top
        currentList.remove(cleaned)
        
        // Add to the top
        currentList.add(0, cleaned)

        // Limit stored accounts to MAX_ACCOUNTS (10)
        val trimmedList = if (currentList.size > MAX_ACCOUNTS) {
            currentList.subList(0, MAX_ACCOUNTS)
        } else {
            currentList
        }

        saveList(context, trimmedList)
    }

    fun removeRegistration(context: Context?, regNumber: String) {
        val cleaned = regNumber.trim().uppercase()
        val currentList = getSavedRegistrations(context).toMutableList()
        if (currentList.remove(cleaned)) {
            saveList(context, currentList)
        }
    }

    fun clearAll(context: Context?) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_REGISTRATIONS).apply()
    }

    private fun saveList(context: Context?, list: List<String>) {
        val rawStr = list.joinToString(",")
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_REGISTRATIONS, rawStr).apply()
    }
}
