package com.danycli.assignmentchecker.analytics.models

import com.google.gson.annotations.SerializedName

data class HeartbeatRequest(
    @SerializedName("installationId") val installationId: String,
    @SerializedName("version") val version: String
)
