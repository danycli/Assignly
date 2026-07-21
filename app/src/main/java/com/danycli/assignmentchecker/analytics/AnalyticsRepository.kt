package com.danycli.assignmentchecker.analytics

import com.danycli.assignmentchecker.BuildConfig
import com.danycli.assignmentchecker.analytics.models.HeartbeatRequest
import com.danycli.assignmentchecker.analytics.models.InstallRequest
import com.danycli.assignmentchecker.analytics.network.RetrofitClient

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AnalyticsRepository(
    private val installationManager: InstallationManager
) {

    suspend fun registerInstallation(): Boolean {
        if (installationManager.isRegistered()) return true

        val request = InstallRequest(
            installationId = installationManager.getInstallationId(),
            platform = "android",
            version = BuildConfig.VERSION_NAME,
            firstInstallTime = installationManager.getFirstInstallTime()
        )

        return try {
            val response = RetrofitClient.service.registerInstallation(request)
            val body = response.body()
            
            // Registration is successful if the API explicitly returns registered = true
            // or if it returns HTTP 409 (already registered)
            val success = (response.isSuccessful && body?.registered == true) || response.code() == 409
            
            if (success) {
                installationManager.setRegistered(true)
            }
            success
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            false
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendHeartbeat(): Boolean {
        if (!installationManager.isRegistered()) return false

        val request = HeartbeatRequest(
            installationId = installationManager.getInstallationId(),
            version = BuildConfig.VERSION_NAME
        )

        return try {
            val response = RetrofitClient.service.sendHeartbeat(request)
            response.isSuccessful
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            false
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
