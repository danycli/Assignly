package com.danycli.assignmentchecker

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API_URL = "https://api.github.com/repos/danycli/Assignly/releases"
private const val RELEASES_FALLBACK_URL = "https://github.com/danycli/Assignly/releases/tag/Android_Application"
private val explicitVersionCodeRegex = Regex("(?i)\\b(?:version\\s*code|versioncode|vc)\\s*[:#-]*\\s*(\\d+)\\b")
private val shortVersionCodeRegex = Regex("(?i)\\bvc(\\d+)\\b")

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val displayLabel: String,
    val releaseUrl: String
)

private fun extractReleaseVersionCode(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val explicitMatch = explicitVersionCodeRegex.find(value)?.groupValues?.getOrNull(1)
        ?.toIntOrNull()
    if (explicitMatch != null) return explicitMatch
    return shortVersionCodeRegex.find(value)?.groupValues?.getOrNull(1)
        ?.toIntOrNull()
}

private fun parseLatestReleaseInfo(json: String): AppUpdateInfo? {
    return try {
        val releases = JSONArray(json)
        var bestVersionCode: Int? = null
        var bestLabel: String? = null
        var bestUrl: String? = null

        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) continue

            val tagName = release.optString("tag_name")
            val releaseName = release.optString("name")
            val releaseBody = release.optString("body")
            val parsedVersionCode = extractReleaseVersionCode(tagName)
                ?: extractReleaseVersionCode(releaseName)
                ?: extractReleaseVersionCode(releaseBody)
                ?: continue
            val releaseUrl = release.optString("html_url").ifBlank { RELEASES_FALLBACK_URL }
            val displayLabel = releaseName.ifBlank { tagName }.ifBlank { "v$parsedVersionCode" }

            if (bestVersionCode == null || parsedVersionCode > bestVersionCode) {
                bestVersionCode = parsedVersionCode
                bestLabel = displayLabel
                bestUrl = releaseUrl
            }
        }

        if (bestVersionCode == null) null else AppUpdateInfo(
            latestVersionCode = bestVersionCode,
            displayLabel = bestLabel ?: "v$bestVersionCode",
            releaseUrl = bestUrl ?: RELEASES_FALLBACK_URL
        )
    } catch (e: JSONException) {
        Log.e("AppUpdateChecker", "Failed to parse releases API response: ${e.message}", e)
        null
    }
}

fun fetchAppUpdateInfo(): AppUpdateInfo? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(RELEASES_API_URL).openConnection() as? HttpURLConnection)
            ?: return null
        connection.requestMethod = "GET"
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Assignly-Android-App")

        if (connection.responseCode !in 200..299) {
            Log.w("AppUpdateChecker", "Update check failed: HTTP ${connection.responseCode}")
            return null
        }

        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        parseLatestReleaseInfo(payload)
    } catch (e: Exception) {
        Log.e("AppUpdateChecker", "Update check failed: ${e.message}", e)
        null
    } finally {
        connection?.disconnect()
    }
}
