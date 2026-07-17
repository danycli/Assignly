package com.danycli.assignmentchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateNavigationManager {
    private const val PRIMARY_UPDATE_URL = "https://www.assignly.site/download"

    suspend fun launchUpdateDownload(
        context: Context,
        githubReleaseUrl: String?,
        onShowMessage: (String) -> Unit = {}
    ) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIMARY_UPDATE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateNavigation", "Failed to launch browser: ${e.message}")
            withContext(Dispatchers.Main) {
                onShowMessage("Unable to open update link.")
            }
        }
    }
}
