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
import kotlinx.coroutines.TimeoutCancellationException

const val DISCLAIMER_URL = "https://github.com/danycli/Assignly#disclaimer"
const val CAPTCHA_RETRY_DELAY_MS = 250L
const val CAPTCHA_BACKGROUND_RECHECK_ATTEMPTS = 2
const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

fun isNetworkError(e: Throwable): Boolean {
    var current: Throwable? = e
    while (current != null) {
        if (current is java.net.UnknownHostException ||
            current is java.net.ConnectException ||
            current is java.net.SocketTimeoutException ||
            current is java.net.NoRouteToHostException ||
            current is javax.net.ssl.SSLException ||
            current.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            current.message?.contains("No address associated with hostname", ignoreCase = true) == true ||
            current.message?.contains("failed to connect to", ignoreCase = true) == true ||
            current.message?.contains("route to host", ignoreCase = true) == true ||
            current.message?.contains("network", ignoreCase = true) == true
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

fun getNetworkErrorMessage(context: Context, e: Throwable): String {
    val isOnline = isDeviceOnline(context)
    var current: Throwable? = e
    var portalSystemEx: PortalSystemException? = null
    var isTimeout = false
    var isPoolError = false
    
    while (current != null) {
        if (current is PortalSystemException) {
            portalSystemEx = current
        }
        val msg = current.message?.lowercase().orEmpty()
        if (msg.contains("max pool size") || 
            msg.contains("pool size reached") || 
            msg.contains("connection pool") || 
            msg.contains("timeout expired") ||
            msg.contains("pool")
        ) {
            isPoolError = true
        }
        if (current is java.net.SocketTimeoutException || 
            current is java.io.InterruptedIOException ||
            current is TimeoutCancellationException ||
            msg.contains("timeout")
        ) {
            isTimeout = true
        }
        current = current.cause
    }
    
    return when {
        portalSystemEx != null -> portalSystemEx.message ?: "Server-side error occurred."
        isPoolError -> "The portal server is currently overloaded (database connection pool size reached). Please try again in a few minutes."
        isTimeout -> {
            if (isOnline) {
                "Connection timed out. The portal server may be overloaded or down. Please try again."
            } else {
                "Connection timed out. Please check your internet connection."
            }
        }
        else -> {
            if (isOnline) {
                "Failed to connect to the portal. The server might be experiencing high load or is currently offline."
            } else {
                "Network unavailable. Please check your internet connection."
            }
        }
    }
}

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

fun writeBytesToDownloads(context: Context, fileName: String, bytes: ByteArray): String? {
    val safeName = sanitizeDownloadFileName(fileName)
    val resolver = context.contentResolver
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, guessMimeType(safeName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Assignly Downloads")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val wrote = resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
                true
            } ?: false
            if (wrote) {
                uri.toString()
            } else {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val dir = File(baseDir, "Assignly Downloads")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, safeName)
            file.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            Uri.fromFile(file).toString()
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "writeBytesToDownloads failed: ${e.javaClass.simpleName}")
        null
    }
}

data class PhysicalDownloadedFile(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

fun getLocalDownloadedFiles(context: Context): List<PhysicalDownloadedFile> {
    val files = mutableListOf<PhysicalDownloadedFile>()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Assignly Downloads%")
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000L
                    val contentUri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    files.add(PhysicalDownloadedFile(contentUri.toString(), name, size, date))
                }
            }
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val dir = File(baseDir, "Assignly Downloads")
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        files.add(
                            PhysicalDownloadedFile(
                                uri = Uri.fromFile(file).toString(),
                                fileName = file.name,
                                sizeBytes = file.length(),
                                lastModifiedMs = file.lastModified()
                            )
                        )
                    }
                }
                files.sortByDescending { it.lastModifiedMs }
            }
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "Failed to fetch local files", e)
    }
    return files
}

fun deleteLocalFile(context: Context, uriString: String): Boolean {
    return try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            File(uri.path ?: return false).delete()
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "Failed to delete file", e)
        false
    }
}

fun renameLocalFile(context: Context, uriString: String, newName: String): Boolean {
    return try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return false)
            val newFile = File(file.parent, newName)
            file.renameTo(newFile)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                context.contentResolver.update(uri, values, null, null) > 0
            } else {
                false
            }
        }
    } catch (e: Exception) {
        Log.e("UiUtils", "Failed to rename file", e)
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

        // Extract original filename and preserve it
        val fileName = getFileNameFromUri(context, uri) ?: "upload_temp_file"
        val safeName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        tempFile = File(context.cacheDir, safeName)
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

fun isDeviceOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun parseCreditHoursValue(creditHoursStr: String): Double {
    val clean = creditHoursStr.trim()
    val parenthesisIndex = clean.indexOf('(')
    val numberPart = if (parenthesisIndex != -1) {
        clean.substring(0, parenthesisIndex).trim()
    } else {
        clean
    }
    val numberRegex = Regex("\\d+(\\.\\d+)?")
    val match = numberRegex.find(numberPart)
    return match?.value?.toDoubleOrNull() ?: 0.0
}

fun formatCreditHours(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

fun parseDateStringToEpoch(dateStr: String): Long {
    val clean = dateStr.trim().lowercase()
    if (clean.isEmpty()) return 0L
    val cleanDateStr = clean.replace(Regex("[/\\s]+"), "-")
    val dateFormats = listOf(
        "dd-MMM-yyyy",
        "dd-MMM-yy",
        "dd-MM-yyyy",
        "yyyy-MM-dd"
    )
    for (fmt in dateFormats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            val d = sdf.parse(cleanDateStr)
            if (d != null) {
                return d.time
            }
        } catch (e: Exception) {
            // continue
        }
    }
    return 0L
}

fun getRelativeDateString(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - epochMs
    if (diffMs < 0) {
        return "Today"
    }
    val diffDays = (diffMs / (24 * 60 * 60 * 1000L)).toInt()
    return when {
        diffDays == 0 -> "Today"
        diffDays == 1 -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        diffDays < 30 -> "${diffDays / 7} weeks ago"
        else -> "${diffDays / 30} months ago"
    }
}

fun getOrdinalSuffix(number: Int): String {
    if (number <= 0) return ""
    return when {
        number % 100 in 11..13 -> "${number}th"
        number % 10 == 1 -> "${number}st"
        number % 10 == 2 -> "${number}nd"
        number % 10 == 3 -> "${number}rd"
        else -> "${number}th"
    }
}


