package com.danycli.assignmentchecker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API_URL = "https://api.github.com/repos/danycli/Assignly/releases"
private const val RELEASES_FALLBACK_URL = "https://github.com/danycli/Assignly/releases/tag/Android_Application"
private val explicitVersionCodeRegex = Regex("(?i)\\b(?:version\\s*code|versioncode|vc)\\s*[:#-]*\\s*(\\d+)\\b")
private val shortVersionCodeRegex = Regex("(?i)\\bvc(\\d+)\\b")

private const val UPDATE_PREFS = "secure_update_checker_cache"
private const val KEY_CACHED_VERSION = "cached_version_code"
private const val KEY_CACHED_LABEL = "cached_display_label"
private const val KEY_CACHED_URL = "cached_release_url"
private const val KEY_CACHED_ETAG = "cached_etag"
private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val displayLabel: String,
    val releaseUrl: String
)

private sealed class FetchResult {
    data class Success(val info: AppUpdateInfo, val etag: String?) : FetchResult()
    object NotModified : FetchResult()
    object RateLimited : FetchResult()
    object Error : FetchResult()
}

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
        runCatching { Log.e("AppUpdateChecker", "Failed to parse releases API response: ${e.message}", e) }
        null
    }
}

private fun parseStaticJsonInfo(json: String): AppUpdateInfo? {
    return try {
        val obj = JSONObject(json)
        val latestVersionCode = obj.getInt("latestVersionCode")
        val displayLabel = obj.getString("displayLabel")
        val releaseUrl = obj.optString("releaseUrl", RELEASES_FALLBACK_URL)
        AppUpdateInfo(latestVersionCode, displayLabel, releaseUrl)
    } catch (e: Exception) {
        runCatching { Log.e("AppUpdateChecker", "Failed to parse static JSON info: ${e.message}", e) }
        null
    }
}

private fun fetchFromUrl(
    urlStr: String,
    acceptHeader: String? = null,
    ifNoneMatch: String? = null,
    parser: (String) -> AppUpdateInfo?
): FetchResult {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(urlStr).openConnection() as? HttpURLConnection) ?: return FetchResult.Error
        connection.requestMethod = "GET"
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = true
        if (acceptHeader != null) {
            connection.setRequestProperty("Accept", acceptHeader)
        }
        if (ifNoneMatch != null) {
            connection.setRequestProperty("If-None-Match", ifNoneMatch)
        }
        connection.setRequestProperty("User-Agent", "Assignly-Android-App")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            runCatching { Log.d("AppUpdateChecker", "Resource not modified (304) for $urlStr") }
            return FetchResult.NotModified
        }

        if (responseCode == 403) {
            runCatching { Log.w("AppUpdateChecker", "Fetch from $urlStr failed: HTTP 403 (Rate Limited)") }
            return FetchResult.RateLimited
        }

        if (responseCode !in 200..299) {
            runCatching { Log.w("AppUpdateChecker", "Fetch from $urlStr failed: HTTP $responseCode") }
            return FetchResult.Error
        }

        val etag = connection.getHeaderField("ETag")
        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        val info = parser(payload)
        if (info != null) {
            FetchResult.Success(info, etag)
        } else {
            FetchResult.Error
        }
    } catch (e: Exception) {
        runCatching { Log.e("AppUpdateChecker", "Fetch from $urlStr failed: ${e.message}") }
        FetchResult.Error
    } finally {
        connection?.disconnect()
    }
}

private fun performNetworkCheck(cachedEtag: String?): FetchResult {
    // 1. Try Main Update Site
    val mainResult = fetchFromUrl("https://www.assignly.site/latest_version.json") { body ->
        parseStaticJsonInfo(body)
    }
    if (mainResult is FetchResult.Success) {
        return mainResult
    }

    runCatching { Log.w("AppUpdateChecker", "Main site check failed. Trying GitHub Releases API...") }

    // 2. Try GitHub Releases API
    val githubResult = fetchFromUrl(
        RELEASES_API_URL,
        acceptHeader = "application/vnd.github+json",
        ifNoneMatch = cachedEtag
    ) { body ->
        parseLatestReleaseInfo(body)
    }
    if (githubResult is FetchResult.Success || githubResult is FetchResult.NotModified) {
        return githubResult
    }

    val reason = if (githubResult is FetchResult.RateLimited) "rate-limited" else "failed"
    runCatching { Log.w("AppUpdateChecker", "GitHub API $reason. Trying Vercel static fallback...") }

    // 3. Try Vercel Static JSON (Backup)
    val vercelResult = fetchFromUrl("https://assignly-web.vercel.app/latest_version.json") { body ->
        parseStaticJsonInfo(body)
    }
    if (vercelResult is FetchResult.Success) return vercelResult

    runCatching { Log.w("AppUpdateChecker", "Vercel fallback failed. Trying Raw GitHub static fallback...") }

    // 4. Try Raw GitHub static JSON
    return fetchFromUrl("https://raw.githubusercontent.com/danycli/Assignly/main/latest_version.json") { body ->
        parseStaticJsonInfo(body)
    }
}

fun fetchAppUpdateInfo(context: Context, force: Boolean = false): AppUpdateInfo? {
    val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    val cachedEtag = prefs.getString(KEY_CACHED_ETAG, null)

    if (!force && (now - lastCheck < CACHE_EXPIRATION_MS)) {
        val cachedCode = prefs.getInt(KEY_CACHED_VERSION, -1)
        val cachedLabel = prefs.getString(KEY_CACHED_LABEL, null)
        val cachedUrl = prefs.getString(KEY_CACHED_URL, null)
        if (cachedCode != -1 && cachedLabel != null && cachedUrl != null) {
            runCatching { Log.d("AppUpdateChecker", "Returning cached update info: $cachedLabel ($cachedCode)") }
            return AppUpdateInfo(cachedCode, cachedLabel, cachedUrl)
        }
    }

    val result = performNetworkCheck(if (force) null else cachedEtag)
    
    return when (result) {
        is FetchResult.Success -> {
            prefs.edit().apply {
                putInt(KEY_CACHED_VERSION, result.info.latestVersionCode)
                putString(KEY_CACHED_LABEL, result.info.displayLabel)
                putString(KEY_CACHED_URL, result.info.releaseUrl)
                putString(KEY_CACHED_ETAG, result.etag)
                putLong(KEY_LAST_CHECK_TIME, now)
                apply()
            }
            result.info
        }
        is FetchResult.NotModified -> {
            // Update last check time but keep other cached data
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            val cachedCode = prefs.getInt(KEY_CACHED_VERSION, -1)
            val cachedLabel = prefs.getString(KEY_CACHED_LABEL, null)
            val cachedUrl = prefs.getString(KEY_CACHED_URL, null)
            if (cachedCode != -1 && cachedLabel != null && cachedUrl != null) {
                AppUpdateInfo(cachedCode, cachedLabel, cachedUrl)
            } else null
        }
        else -> null
    }
}
