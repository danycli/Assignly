package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONObject

data class ProfileSnapshot(
    val profile: StudentProfile,
    val cachedAtEpochMs: Long
)

object ProfileCacheStore {
    private const val PREFS_NAME = "secure_profile_cache"
    private const val KEY_SNAPSHOT_JSON = "profile_json"

    fun saveSnapshot(context: Context, profile: StudentProfile) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("name", profile.name)
            put("regNumber", profile.regNumber)
            put("program", profile.program)
            put("section", profile.section)
            put("fatherName", profile.fatherName)
            put("email", profile.email)
            put("phone", profile.phone)
            put("campus", profile.campus)
            put("address", profile.address)
            put("scholarshipStatus", profile.scholarshipStatus)
            put("dob", profile.dob)
            put("mobile", profile.mobile)
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): ProfileSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            ProfileSnapshot(
                profile = StudentProfile(
                    name = json.optString("name"),
                    regNumber = json.optString("regNumber"),
                    program = json.optString("program"),
                    section = json.optString("section"),
                    fatherName = json.optString("fatherName"),
                    email = json.optString("email"),
                    phone = json.optString("phone"),
                    campus = json.optString("campus"),
                    address = json.optString("address"),
                    scholarshipStatus = json.optString("scholarshipStatus"),
                    dob = json.optString("dob"),
                    mobile = json.optString("mobile")
                ),
                cachedAtEpochMs = json.optLong("cachedAtEpochMs", 0L)
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
        clearPhoto(context)
    }

    fun savePhoto(context: Context, bytes: ByteArray) {
        runCatching {
            context.openFileOutput("profile_photo.bin", Context.MODE_PRIVATE).use {
                it.write(bytes)
            }
        }
    }

    fun loadPhoto(context: Context): ByteArray? {
        return runCatching {
            context.openFileInput("profile_photo.bin").use {
                it.readBytes()
            }
        }.getOrNull()
    }

    fun clearPhoto(context: Context) {
        runCatching {
            context.deleteFile("profile_photo.bin")
        }
    }
}
