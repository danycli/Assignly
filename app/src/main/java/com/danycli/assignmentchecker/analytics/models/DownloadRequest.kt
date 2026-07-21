package com.danycli.assignmentchecker.analytics.models

import com.google.gson.annotations.SerializedName

data class DownloadRequest(
    @SerializedName("platform") val platform: String
)
