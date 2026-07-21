package com.danycli.assignmentchecker.analytics.models

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("registered") val registered: Boolean? = null
)
