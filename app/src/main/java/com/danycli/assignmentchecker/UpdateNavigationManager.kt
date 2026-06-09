package com.danycli.assignmentchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateNavigationManager {
    private const val PRIMARY_UPDATE_URL = "https://assignly-web.vercel.app/"
    private const val FALLBACK_GITHUB_URL = "https://github.com/danycli/Assignly"

    suspend fun launchUpdateDownload(context: Context, githubReleaseUrl: String?) {
        val available = withContext(Dispatchers.IO) {
            isWebsiteAvailable(PRIMARY_UPDATE_URL)
        }

        val targetUrl = if (available) {
            PRIMARY_UPDATE_URL
        } else {
            githubReleaseUrl ?: FALLBACK_GITHUB_URL
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateNavigation", "Failed to launch browser: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unable to open update link.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isWebsiteAvailable(url: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as? HttpURLConnection) ?: return false
            connection.requestMethod = "HEAD" // Lightweight check
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            responseCode in listOf(200, 301, 302)
        } catch (e: Exception) {
            Log.w("UpdateNavigation", "Website availability check failed: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }
}
