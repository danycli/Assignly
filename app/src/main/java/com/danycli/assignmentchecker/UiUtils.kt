package com.danycli.assignmentchecker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

const val DISCLAIMER_URL = "https://github.com/danycli/Assignly#disclaimer"
const val CAPTCHA_RETRY_DELAY_MS = 250L
const val CAPTCHA_BACKGROUND_RECHECK_ATTEMPTS = 2
const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

fun mapLoginErrorToMessage(message: String?): String {
    val m = message?.lowercase().orEmpty()
    if (m.contains("portal server is currently overloaded") || m.contains(" experiencing a technical runtime error")) {
        return message!!
    }
    return when {
        m.contains("timed out") -> "Server timeout. Please try again."
        m.contains("ssl") || m.contains("certificate") || m.contains("trust anchor") || m.contains("net::err_cert") ->
            "Connection security warning detected. Complete verification in-app, then sign in again."
        m.contains("network") || m.contains("unable to resolve host") || m.contains("failed to connect") ->
            "Network unavailable. Check your internet connection."
        m.contains("invalid id or password") || m.contains("invalid credentials") || m.contains("http 401") || m.contains("http 403") ->
            "Authentication failed. Check your ID/password."
        else -> "Authentication failed. Please try again."
    }
}

fun pickNonRepeatingMessage(candidates: List<String>, previousMessage: String?): String {
    if (candidates.isEmpty()) return ""
    if (candidates.size == 1) return candidates.first()
    val filtered = candidates.filter { it != previousMessage }
    return if (filtered.isNotEmpty()) filtered.random() else candidates.random()
}

fun generateWelcomeStatusMessage(
    pendingCount: Int,
    submittedCount: Int,
    previousMessage: String?
): String {
    val noAssignments = "Your professors are still thinking how to annoy you in a brutal way possible."
    val sarcastic = listOf(
        "Three or more pending? Your assignments are doing group study without you.",
        "Pending list is crowded. Time to stop scrolling and start submitting.",
        "Those pending assignments are multiplying faster than your excuses.",
        "Big backlog energy detected. Lets bring that number down today."
    )
    val motivational = listOf(
        "Only a few left. Finish them and take the win.",
        "You are close - one focused session can clear your pending work.",
        "Small backlog, strong comeback. You have got this.",
        "Just a couple pending. Keep the momentum and submit them."
    )
    val appreciative = listOf(
        "All done. That is disciplined work.",
        "Zero pending. Excellent consistency.",
        "Everything submitted - great job staying on top.",
        "No pending assignments. You are setting the standard."
    )

    val totalAssignments = pendingCount + submittedCount
    return when {
        pendingCount >= 3 -> pickNonRepeatingMessage(sarcastic, previousMessage)
        pendingCount in 1..2 -> pickNonRepeatingMessage(motivational, previousMessage)
        totalAssignments > 0 -> pickNonRepeatingMessage(appreciative, previousMessage)
        else -> noAssignments
    }
}

fun generateAttendanceSarcasmMessage(insight: AttendanceInsight?): String? {
    if (insight == null) {
        return "Attendance insight is missing right now. If you keep skipping, the report will roast you on its own soon."
    }
    val effectivePercentLabel = String.format(Locale.US, "%.0f%%", insight.effectivePercent)
    val courseName = insight.courseTitle

    return when {
        insight.effectivePercent < 50.0 ->
            "T$courseName is at $effectivePercentLabel attendance. At this point even your empty seat has better attendance."
        insight.effectivePercent < 75.0 ->
            "$courseName is at $effectivePercentLabel attendance. The classroom remembers you mostly as a rumor."
        insight.effectivePercent < 90.0 ->
            "$courseName is at $effectivePercentLabel attendance. Keep this up and your attendance sheet will start looking fictional."
        else ->
            "$courseName is at $effectivePercentLabel attendance. You are safe for now, but don't get too creative with absences."
    }
}

fun countSuccessfulSubmissions(assignments: List<Assignment>): Int {
    return assignments.count { assignment ->
        assignment.status == AssignmentStatus.SUBMITTED || assignment.status == AssignmentStatus.GRADED
    }
}

fun matchesDueFilter(assignment: Assignment, filter: PendingDueFilter): Boolean {
    if (filter == PendingDueFilter.ALL) return true
    val deadlineEpoch = assignmentDeadlineEpoch(assignment.deadline) ?: return false
    val nowEpoch = System.currentTimeMillis()
    return when (filter) {
        PendingDueFilter.TODAY -> {
            val zone = java.time.ZoneId.systemDefault()
            val todayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowStart = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            deadlineEpoch in todayStart until tomorrowStart
        }
        PendingDueFilter.NEXT_3_DAYS -> deadlineEpoch in nowEpoch..(nowEpoch + 3 * 24 * 60 * 60 * 1000L)
        PendingDueFilter.NEXT_7_DAYS -> deadlineEpoch in nowEpoch..(nowEpoch + 7 * 24 * 60 * 60 * 1000L)
        PendingDueFilter.ALL -> true
    }
}

fun suggestDownloadFileName(assignment: Assignment): String {
    val base = assignment.assignmentTitle
        .ifBlank { assignment.courseTitle.ifBlank { "assignment_file" } }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "assignment_file" }

    val cleanedLink = assignment.downloadLink.substringBefore("?").substringBefore("#")
    val extension = cleanedLink.substringAfterLast(".", "").lowercase()
    val isWebEndpoint = extension in setOf("aspx", "ashx", "asmx", "php", "jsp", "jspx", "do", "action", "html", "htm")
    val hasValidExtension = extension.matches(Regex("[a-z0-9]{1,8}")) && !isWebEndpoint
    return if (hasValidExtension) "$base.$extension" else "$base.bin"
}

fun sanitizeDownloadFileName(rawName: String): String {
    return rawName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "assignment_file.bin" }
}

fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    val mime = if (extension.isNotBlank()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) else null
    return mime ?: "application/octet-stream"
}

fun writeBytesToDownloads(context: Context, fileName: String, bytes: ByteArray): Boolean {
    val safeName = sanitizeDownloadFileName(fileName)
    val resolver = context.contentResolver
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, guessMimeType(safeName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Assignly")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val wrote = resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
                true
            } ?: false
            if (!wrote) resolver.delete(uri, null, null)
            wrote
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, safeName)
            file.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            true
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "writeBytesToDownloads failed: ${e.javaClass.simpleName}")
        false
    }
}

fun writeBytesToUri(context: Context, destinationUri: Uri, bytes: ByteArray): Boolean {
    return try {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
        if (outputStream == null) {
            Log.e("UiUtils", "writeBytesToUri failed: unable to open output stream")
            false
        } else {
            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            true
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "writeBytesToUri failed: ${e.javaClass.simpleName}")
        false
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    return when (uri.scheme) {
        "content" -> {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
            null
        }
        "file" -> {
            uri.path?.substringAfterLast("/")
        }
        else -> null
    }
}

fun uriToFile(context: Context, uri: Uri): File? {
    var tempFile: File? = null
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("UiUtils", "uriToFile failed: unable to open input stream")
            return null
        }

        // Extract original filename and extension
        val fileName = getFileNameFromUri(context, uri) ?: "upload_temp_file"
        val extension = if (fileName.contains(".")) {
            fileName.substringAfterLast(".")
        } else {
            ""
        }

        // Create temp file with extension preserved
        val tempFileName = if (extension.isNotEmpty()) {
            "upload_temp_file.$extension"
        } else {
            "upload_temp_file"
        }

        tempFile = File(context.cacheDir, tempFileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        if (tempFile?.exists() == true) {
            tempFile?.delete()
        }
        Log.e("UiUtils", "uriToFile failed: ${e.javaClass.simpleName}")
        null
    }
}
