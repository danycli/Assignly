package com.danycli.assignmentchecker.analytics.network

import com.danycli.assignmentchecker.analytics.models.ApiResponse
import com.danycli.assignmentchecker.analytics.models.DownloadRequest
import com.danycli.assignmentchecker.analytics.models.HeartbeatRequest
import com.danycli.assignmentchecker.analytics.models.InstallRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AnalyticsService {

    @POST("api/install")
    suspend fun registerInstallation(
        @Body request: InstallRequest
    ): Response<ApiResponse>

    @POST("api/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<ApiResponse>

    @POST("api/download")
    suspend fun trackDownload(
        @Body request: DownloadRequest
    ): Response<ApiResponse>
}
