package com.danycli.assignmentchecker.analytics.models

import com.google.gson.annotations.SerializedName

data class InstallRequest(
    @SerializedName("installationId") val installationId: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("version") val version: String,
    @SerializedName("firstInstallTime") val firstInstallTime: Long
)
