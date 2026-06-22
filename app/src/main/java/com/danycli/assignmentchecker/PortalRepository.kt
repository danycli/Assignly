package com.danycli.assignmentchecker

import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.math.abs
import kotlin.random.Random

sealed class LoginResult {
    object Success : LoginResult()
    object InvalidCredentials : LoginResult()
    object CaptchaRequired : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class PortalSystemException(message: String) : IOException(message)

sealed class UploadResult {
    object Success : UploadResult()
    object NetworkError : UploadResult()
    object Timeout : UploadResult()
    data class Rejected(val reason: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}

sealed class DownloadResult {
    data class Success(val bytes: ByteArray, val fileName: String, val mimeType: String) : DownloadResult()
    object NetworkError : DownloadResult()
    data class Rejected(val reason: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

data class InstructionFile(
    val fileName: String,
    val downloadLink: String
)

data class AttendanceInsight(
    val courseTitle: String,
    val theoryPercent: Double?,
    val labPercent: Double?,
    val effectivePercent: Double
)

sealed class InstructionFilesResult {
    data class Success(val files: List<InstructionFile>) : InstructionFilesResult()
    object NetworkError : InstructionFilesResult()
    data class Rejected(val reason: String) : InstructionFilesResult()
    data class Error(val message: String) : InstructionFilesResult()
}

data class LoginPacing(
    val minDelayMs: Long,
    val maxDelayMs: Long
) {
    fun nextDelayMs(): Long {
        val min = minDelayMs.coerceAtLeast(0)
        val max = maxDelayMs.coerceAtLeast(min)
        return if (max == min) min else Random.nextLong(min, max + 1)
    }
}

class PortalRepository {
    private enum class PortalStatusState {
        NOT_SUBMITTED, NOT_SUBMITTED_CLOSED, SUBMITTED, GRADED, UNKNOWN
    }

    private val portalDeadlineFormatter = DateTimeFormatter.ofPattern("MMM dd ,yyyy HH:mm", Locale.US)
    private val portalDeadlineZoneId = ZoneId.systemDefault()
    private val coursePostbackTargets = mutableMapOf<String, String>()
    private val courseTitleToCodeMap = mutableMapOf<String, String>()
    private val courseTitleToCreditMap = mutableMapOf<String, String>()
    private var latestSemesterName = ""

    private val postBackPrefix = "portal-postback:"
    private data class PostBackInfo(val target: String, val argument: String)
    private data class PostBackLink(val info: PostBackInfo, val sourcePageUrl: String?)
    private data class HtmlDownloadCandidate(val url: String? = null, val postBackInfo: PostBackInfo? = null)
    private data class InternalStudentProfile(val name: String?, val rollNo: String?, val program: String?)
    private data class AttendanceColumnMapping(
        val courseIndex: Int,
        val theoryPercentIndex: Int,
        val labPercentIndex: Int?
    )

    private fun debugLog(message: String) {
        if (com.danycli.assignmentchecker.BuildConfig.DEBUG) {
            Log.d("PortalAuth", message)
        }
    }

    private fun detectPortalSystemErrors(html: String) {
        val lowerHtml = html.lowercase()
        val isPoolTimeout = (lowerHtml.contains("timeout expired") && (lowerHtml.contains("pool") || lowerHtml.contains("connection"))) ||
            lowerHtml.contains("max pool size") ||
            (lowerHtml.contains("connection pool") && lowerHtml.contains("timeout")) ||
            lowerHtml.contains("database connection pool") ||
            lowerHtml.contains("has reached its maximum pool size")
        
        if (isPoolTimeout) {
            throw PortalSystemException("The portal server is currently overloaded and cannot connect to its database. Please try again in a few minutes.")
        }
        
        if (lowerHtml.contains("runtime error") && lowerHtml.contains("customerrors mode=\"off\"")) {
            throw PortalSystemException("The portal is currently experiencing a technical runtime error. Please try again later.")
        }
    }

    private fun pauseForLoginPacing(pacing: LoginPacing?) {
        val delayMs = pacing?.nextDelayMs() ?: return
        if (delayMs <= 0) return
        try {
            Thread.sleep(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun sanitizeUrl(url: String): String {
        val q = url.indexOf('?')
        return if (q >= 0) url.substring(0, q) else url
    }

    private fun normalizeIdentityToken(value: String?): String {
        return value
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]"), "")
            .orEmpty()
    }

    private fun parseStudentNameFromHtml(html: String): String? {
        val doc = Jsoup.parse(html)

        val idBased = doc.select("[id*=lblName], [id*=StudentName], [id*=FullName], [id*=txtName]")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("Name", true) && !it.equals("Name :", true) && !it.equals("NA", true) }
        if (!idBased.isNullOrBlank()) return idBased

        val labelCell = doc.select("td, th, span, label")
            .firstOrNull { element ->
                val normalized = element.text().trim().replace(Regex("\\s+"), " ")
                normalized.matches(Regex("(?i)^name\\s*:?\$"))
            }

        val siblingValue = labelCell
            ?.nextElementSibling()
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("NA", true) }
        if (!siblingValue.isNullOrBlank()) return siblingValue

        val rowValue = labelCell
            ?.parent()
            ?.select("td")
            ?.drop(1)
            ?.firstOrNull()
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("NA", true) }
        if (!rowValue.isNullOrBlank()) return rowValue

        return null
    }

    private fun parseStudentProfileFromHtml(html: String): InternalStudentProfile {
        val doc = Jsoup.parse(html)

        val tablePairs = linkedMapOf<String, String>()
        doc.select("tr").forEach { row ->
            val cells = row.select("th, td")
            if (cells.size < 2) return@forEach
            var i = 0
            while (i + 1 < cells.size) {
                val key = cells[i].text().trim().trimEnd(':').lowercase()
                val value = cells[i + 1].text().trim()
                if (key.isNotEmpty() && value.isNotEmpty() && !value.equals("NA", true)) {
                    tablePairs.putIfAbsent(key, value)
                }
                i += 2
            }
        }

        val rollNo = tablePairs.entries.firstOrNull { (k, _) ->
            k.contains("roll no") || k.contains("rollno") || k.contains("registration no")
        }?.value
            ?: doc.select("[id*=RollNo], [id*=rollno], [id*=lblRoll]").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() && !it.equals("NA", true) }

        val program = tablePairs.entries.firstOrNull { (k, _) ->
            k == "program" || k.contains("program")
        }?.value
            ?: doc.select("[id*=Program], [id*=lblProgram]").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() && !it.equals("NA", true) }

        val name = parseStudentNameFromHtml(html)
        return InternalStudentProfile(name = name, rollNo = rollNo, program = program)
    }

    private fun parseStudentPhotoUrlFromHtml(html: String, pageUrl: String): String? {
        val doc = Jsoup.parse(html, pageUrl)
        val scoredCandidates = doc
            .select("img[src], input[type=image][src]")
            .mapNotNull { element ->
                val rawSrc = element.attr("src").trim()
                if (rawSrc.isBlank() || rawSrc.startsWith("data:", true)) return@mapNotNull null
                val normalizedSrc = normalizeUrl(rawSrc, pageUrl)
                if (normalizedSrc.isBlank()) return@mapNotNull null

                val fingerprint = listOf(
                    element.attr("id"),
                    element.attr("name"),
                    element.className(),
                    element.attr("alt"),
                    element.attr("title"),
                    rawSrc
                ).joinToString(" ").lowercase()

                val score = buildList {
                    if (fingerprint.contains("student")) add(4)
                    if (fingerprint.contains("profile")) add(3)
                    if (fingerprint.contains("photo")) add(3)
                    if (fingerprint.contains("pic")) add(2)
                    if (fingerprint.contains("image")) add(1)
                    if (fingerprint.contains("logo")) add(-5)
                    if (fingerprint.contains("banner")) add(-4)
                    if (fingerprint.contains("icon")) add(-2)
                }.sum()

                score to normalizedSrc
            }
            .sortedByDescending { it.first }

        return scoredCandidates.firstOrNull { it.first > 0 }?.second
            ?: scoredCandidates.firstOrNull()?.second
    }

    private fun doesProfileMatchRequestedUsername(
        requestedUsername: String,
        profile: InternalStudentProfile,
        html: String
    ): Boolean {
        val parts = requestedUsername.trim().split("-").map { it.trim() }.filter { it.isNotEmpty() }
        val expectedSession = parts.getOrNull(0).orEmpty()
        val expectedProgram = parts.getOrNull(1).orEmpty()
        val expectedRoll = parts.getOrNull(2).orEmpty()
        if (expectedSession.isBlank() || expectedProgram.isBlank() || expectedRoll.isBlank()) {
            return false
        }

        val expectedProgramToken = normalizeIdentityToken(expectedProgram)
        val expectedRollToken = normalizeIdentityToken(expectedRoll)
        val expectedSessionToken = normalizeIdentityToken(expectedSession)
        val expectedCompositeToken = normalizeIdentityToken("$expectedSession-$expectedProgram-$expectedRoll")
        val normalizedHtml = normalizeIdentityToken(html)
        if (expectedCompositeToken.isNotBlank() && normalizedHtml.contains(expectedCompositeToken)) {
            return true
        }

        val actualProgramToken = normalizeIdentityToken(profile.program)
        val actualRollToken = normalizeIdentityToken(profile.rollNo)
        if (actualProgramToken.isBlank() || actualRollToken.isBlank()) {
            return false
        }

        val programMatches = actualProgramToken == expectedProgramToken ||
            actualProgramToken.contains(expectedProgramToken)
        if (!programMatches) return false

        val compositeMatch = expectedCompositeToken.isNotBlank() && actualRollToken.contains(expectedCompositeToken)
        val partsMatch = actualRollToken.contains(expectedProgramToken) &&
            actualRollToken.contains(expectedRollToken) &&
            (expectedSessionToken.isBlank() || actualRollToken.contains(expectedSessionToken))

        return compositeMatch || partsMatch
    }

    @Volatile
    private var currentStudentName: String? = null

    fun getCurrentStudentName(): String? = currentStudentName

    @Volatile
    private var currentStudentPhotoUrl: String? = null

    fun getCurrentStudentPhotoUrl(): String? = currentStudentPhotoUrl

    @Volatile
    private var currentStudentPhotoBytes: ByteArray? = null

    @Volatile
    private var currentStudentPhotoBytesUrl: String? = null

    private fun updateCurrentStudentPhotoUrl(photoUrl: String?) {
        val normalized = photoUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (normalized != currentStudentPhotoUrl) {
            currentStudentPhotoBytes = null
            currentStudentPhotoBytesUrl = null
        }
        currentStudentPhotoUrl = normalized
    }

    fun fetchCurrentStudentPhoto(): ByteArray? {
        val photoUrl = currentStudentPhotoUrl ?: return null
        val cachedBytes = currentStudentPhotoBytes
        if (cachedBytes != null && currentStudentPhotoBytesUrl == photoUrl) {
            return cachedBytes
        }

        return try {
            val request = Request.Builder()
                .url(photoUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val responseBody = response.body ?: return null
                val mimeType = responseBody.contentType()?.toString()
                val bytes = responseBody.bytes()
                if (bytes.isEmpty()) return null
                val htmlLike = mimeType?.contains("text/html", true) == true || looksLikeHtmlPayload(bytes)
                if (htmlLike || !isValidImagePayload(bytes)) {
                    null
                } else {
                    currentStudentPhotoBytes = bytes
                    currentStudentPhotoBytesUrl = photoUrl
                    bytes
                }
            }
        } catch (e: IOException) {
            Log.e("PortalAuth", "Photo fetch IO error: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("PortalAuth", "Photo fetch error: ${e.message}")
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifEmpty { "assignment_file" }
    }

    private fun isWebEndpointExtension(extension: String): Boolean {
        val normalized = extension.lowercase()
        return normalized in setOf(
            "aspx", "ashx", "asmx", "php", "jsp", "jspx", "do", "action", "html", "htm"
        )
    }

    private fun getExtensionFromMimeType(mimeType: String?): String {
        val normalized = mimeType?.lowercase().orEmpty()
        return when {
            normalized.contains("pdf") -> ".pdf"
            normalized.contains("msword") -> ".doc"
            normalized.contains("officedocument.wordprocessingml") -> ".docx"
            normalized.contains("vnd.ms-excel") -> ".xls"
            normalized.contains("officedocument.spreadsheetml") -> ".xlsx"
            normalized.contains("zip") -> ".zip"
            normalized.contains("rar") -> ".rar"
            normalized.contains("ms-powerpoint") -> ".ppt"
            normalized.contains("officedocument.presentationml") -> ".pptx"
            normalized.contains("image/png") -> ".png"
            normalized.contains("image/jpeg") -> ".jpg"
            normalized.contains("text/plain") -> ".txt"
            else -> ".bin"
        }
    }

    private fun extractNameFromUrl(finalUrl: String): String? {
        return runCatching {
            val httpUrl = finalUrl.toHttpUrl()
            val queryBasedName = listOf("filename", "file", "name", "download", "attachment", "doc", "document")
                .firstNotNullOfOrNull { key ->
                    httpUrl.queryParameter(key)?.trim()?.takeIf { it.isNotEmpty() }
                }
            queryBasedName ?: httpUrl.pathSegments.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun extractFileName(contentDisposition: String?, finalUrl: String, mimeType: String?): String {
        val headerName = contentDisposition?.let { cd ->
            val patterns = listOf(
                Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE),
                Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
                Regex("filename=([^;]+)", RegexOption.IGNORE_CASE)
            )
            patterns.firstNotNullOfOrNull { pattern ->
                pattern.find(cd)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
            }?.let { raw ->
                runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
            }
        }

        val urlName = extractNameFromUrl(finalUrl)
        val rawBaseName = headerName ?: urlName ?: "assignment_file"
        val baseName = sanitizeFileName(rawBaseName)
        val extension = baseName.substringAfterLast(".", "").lowercase()
        val hasValidExtension = extension.matches(Regex("[a-z0-9]{1,8}")) && !isWebEndpointExtension(extension)
        if (hasValidExtension) {
            return baseName
        }

        val nameWithoutExtension = baseName.substringBeforeLast(".", baseName)
        return sanitizeFileName(nameWithoutExtension) + getExtensionFromMimeType(mimeType)
    }

    private fun looksLikeHtmlPayload(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val probe = bytes.copyOfRange(0, minOf(bytes.size, 4096)).toString(Charsets.UTF_8).trimStart()
        return probe.startsWith("<!doctype html", true) ||
            probe.startsWith("<html", true) ||
            probe.contains("__VIEWSTATE", true) ||
            probe.contains("<form", true)
    }

    private fun isValidImagePayload(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return true
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return true
        return false
    }

    private fun encodePostBackPart(value: String): String {
        return runCatching { URLEncoder.encode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }

    private fun decodePostBackPart(value: String): String {
        return runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }

    private fun extractPostBackInfo(value: String?): PostBackInfo? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf(
            Regex(
                "__doPostBack\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]?([^'\"]*)['\"]?\\s*\\)",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "WebForm_DoPostBackWithOptions\\(\\s*new\\s+WebForm_PostBackOptions\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]?([^'\"]*)['\"]?",
                RegexOption.IGNORE_CASE
            )
        )
        for (pattern in patterns) {
            val match = pattern.find(value) ?: continue
            val target = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (target.isEmpty()) continue
            val argument = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return PostBackInfo(target, argument)
        }
        return null
    }

    private fun isPostBackDownloadLink(link: String): Boolean {
        return link.startsWith(postBackPrefix) || link.startsWith("$postBackPrefix@")
    }

    private fun extractPostBackLinkFromLink(link: String): PostBackLink? {
        val encoded = when {
            link.startsWith("$postBackPrefix@") -> link.removePrefix("$postBackPrefix@")
            link.startsWith(postBackPrefix) -> link.removePrefix(postBackPrefix)
            else -> return null
        }
        if (encoded.isBlank()) return null
        val parts = encoded.split("|")
        if (parts.isEmpty()) return null
        val target = decodePostBackPart(parts[0]).trim()
        if (target.isEmpty()) return null
        val argument = if (parts.size > 1) decodePostBackPart(parts[1]).trim() else ""
        val sourceUrl = if (parts.size > 2) decodePostBackPart(parts[2]).trim().ifEmpty { null } else null
        return PostBackLink(PostBackInfo(target, argument), sourceUrl)
    }

    private fun toPostBackDownloadLink(info: PostBackInfo, sourcePageUrl: String? = null): String {
        val encodedTarget = encodePostBackPart(info.target)
        val encodedArgument = encodePostBackPart(info.argument)
        val encodedSource = sourcePageUrl?.let { encodePostBackPart(it) }
        return if (encodedSource.isNullOrBlank()) {
            "$postBackPrefix$encodedTarget|$encodedArgument"
        } else {
            "$postBackPrefix@$encodedTarget|$encodedArgument|$encodedSource"
        }
    }

    private fun extractUrlFromJavascript(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("window\\.open\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
            Regex("window\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
            Regex("location\\.replace\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(value)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun extractAssignmentDownloadLink(downloadCell: Element): String {
        val anchors = downloadCell.select("a")
        for (anchor in anchors) {
            val href = anchor.attr("href")
            val onClick = anchor.attr("onclick")

            val postBackInfo = extractPostBackInfo(href) ?: extractPostBackInfo(onClick)
            if (postBackInfo != null) {
                return toPostBackDownloadLink(postBackInfo)
            }

            val rawUrl = when {
                href.isBlank() || href == "#" -> extractUrlFromJavascript(onClick)
                href.startsWith("javascript", true) -> extractUrlFromJavascript(href) ?: extractUrlFromJavascript(onClick)
                else -> href
            }
            val normalized = normalizeUrl(rawUrl)
            if (normalized.isNotBlank()) {
                return normalized
            }
        }

        val cellPostBackInfo = extractPostBackInfo(downloadCell.attr("onclick"))
        if (cellPostBackInfo != null) {
            return toPostBackDownloadLink(cellPostBackInfo)
        }
        return normalizeUrl(extractUrlFromJavascript(downloadCell.attr("onclick")))
    }

    private fun extractPostBackFromSubmitLikeControl(element: Element): PostBackInfo? {
        val tag = element.tagName().lowercase()
        val type = element.attr("type").lowercase()
        val isSubmitLike = tag == "button" || (tag == "input" && (type == "submit" || type == "button" || type == "image"))
        if (!isSubmitLike) return null

        val controlName = element.attr("name").trim().ifBlank {
            element.attr("id").trim().replace("_", "$")
        }
        if (controlName.isBlank()) return null

        val fingerprint = listOf(
            controlName,
            element.attr("id"),
            element.attr("value"),
            element.text(),
            element.className()
        ).joinToString(" ").lowercase()

        val looksLikeSubmitAction = fingerprint.contains("submit") ||
            fingerprint.contains("upload") ||
            fingerprint.contains("change") ||
            fingerprint.contains("addfile") ||
            fingerprint.contains("updatefile") ||
            fingerprint.contains("assignment") ||
            fingerprint.contains("attach")

        return if (looksLikeSubmitAction) PostBackInfo(controlName, "") else null
    }

    private fun extractAssignmentSubmitLink(actionCell: Element, pageUrl: String): String {
        val actionElements = actionCell.select("a, button, input[type=submit], input[type=button], input[type=image], input[onclick], span[onclick]")
        for (element in actionElements) {
            val href = element.attr("href")
            val onClick = element.attr("onclick")
            val postBackInfo = extractPostBackInfo(href) ?: extractPostBackInfo(onClick) ?: extractPostBackFromSubmitLikeControl(element)
            if (postBackInfo != null) {
                return toPostBackDownloadLink(postBackInfo, sourcePageUrl = pageUrl)
            }

            val rawUrl = when {
                href.isBlank() || href == "#" -> extractUrlFromJavascript(onClick)
                href.startsWith("javascript", true) -> extractUrlFromJavascript(href) ?: extractUrlFromJavascript(onClick)
                else -> href
            }
            val normalized = normalizeUrl(rawUrl, pageUrl)
            if (normalized.isNotBlank()) {
                return normalized
            }
        }

        val cellPostBackInfo = extractPostBackInfo(actionCell.attr("onclick")) ?: extractPostBackFromSubmitLikeControl(actionCell)
        if (cellPostBackInfo != null) {
            return toPostBackDownloadLink(cellPostBackInfo, sourcePageUrl = pageUrl)
        }
        return normalizeUrl(extractUrlFromJavascript(actionCell.attr("onclick")), pageUrl)
    }

    private fun normalizePortalStatusState(statusText: String, actionText: String): PortalStatusState {
        val normalizedStatus = statusText.lowercase().replace(Regex("\\s+"), " ").trim()
        val normalizedAction = actionText.lowercase().replace(Regex("\\s+"), " ").trim()

        val hasNotSubmittedStatus = Regex("\\bnot\\s+submitted\\b|\\bunsubmitted\\b|\\bpending\\b")
            .containsMatchIn(normalizedStatus)
        val hasGradedStatus = Regex("\\bgraded\\b")
            .containsMatchIn(normalizedStatus)
        val hasSubmittedStatus = Regex("\\bsubmitted\\b")
            .containsMatchIn(normalizedStatus)
        val hasClosedStatus = Regex("\\bclosed\\b")
            .containsMatchIn(normalizedStatus)

        val hasChangeAction = normalizedAction.contains("change submitted file")
        val hasSubmitAction = normalizedAction.contains("submit") && !hasChangeAction
        val hasClosedAction = normalizedAction.contains("closed")
        val hasClosedIndicator = hasClosedStatus || hasClosedAction

        return when {
            hasNotSubmittedStatus && (hasClosedIndicator || hasGradedStatus) -> PortalStatusState.NOT_SUBMITTED_CLOSED
            hasNotSubmittedStatus -> PortalStatusState.NOT_SUBMITTED
            hasGradedStatus -> PortalStatusState.GRADED
            hasChangeAction -> PortalStatusState.SUBMITTED
            hasSubmittedStatus -> PortalStatusState.SUBMITTED
            hasClosedIndicator -> PortalStatusState.NOT_SUBMITTED_CLOSED
            hasSubmitAction -> PortalStatusState.NOT_SUBMITTED
            else -> PortalStatusState.UNKNOWN
        }
    }

    private fun resolveAssignmentStateForDeadline(
        statusState: PortalStatusState,
        deadline: String
    ): PortalStatusState {
        if (isAssignmentDeadlineOpen(deadline)) return statusState
        return when (statusState) {
            PortalStatusState.NOT_SUBMITTED,
            PortalStatusState.UNKNOWN -> PortalStatusState.NOT_SUBMITTED_CLOSED
            else -> statusState
        }
    }

    private fun isAssignmentDeadlineOpen(deadline: String): Boolean {
        val deadlineEpoch = runCatching {
            LocalDateTime.parse(deadline, portalDeadlineFormatter)
                .atZone(portalDeadlineZoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: return false
        return System.currentTimeMillis() < deadlineEpoch
    }

    private fun isLoginPage(url: String, html: String): Boolean {
        if (url.contains("Login.aspx", true)) return true
        val doc = Jsoup.parse(html)
        return doc.select("input[name*=txtUsername], input[id*=txtUsername], input[name*=btnLogin], input[id*=btnLogin]").isNotEmpty()
    }

    private fun hasStandardPortalLoginForm(html: String): Boolean {
        val doc = Jsoup.parse(html)
        val hasUserLikeField = doc.select(
            "input[name*=txtUsername], input[id*=txtUsername], input[name*=RollNo], input[id*=RollNo], " +
                "input[name*=roll], input[id*=roll]"
        ).isNotEmpty()
        val hasPasswordField = doc.select("input[type=password], input[name*=password], input[id*=password]").isNotEmpty()
        return hasUserLikeField && hasPasswordField
    }

    private fun isSecurityVerificationPage(url: String?, html: String?): Boolean {
        val normalizedUrl = url?.lowercase().orEmpty()
        val normalizedHtml = html?.lowercase().orEmpty()

        val urlSignals = normalizedUrl.contains("/cdn-cgi/") ||
            normalizedUrl.contains("challenge-platform") ||
            normalizedUrl.startsWith("chrome-error://")

        if (urlSignals) return true
        if (normalizedHtml.isBlank()) return false

        val hasChallengeArtifacts = listOf(
            "cf_chl",
            "cf-browser-verification",
            "challenge-platform",
            "cf-turnstile",
            "challenges.cloudflare.com",
            "id=\"challenge-form\"",
            "name=\"cf-turnstile-response\""
        ).any { marker -> normalizedHtml.contains(marker) }

        val hasChallengeLanguage = listOf(
            "security verification",
            "verify you are human",
            "performing security verification",
            "just a moment",
            "checking your browser before accessing"
        ).any { marker -> normalizedHtml.contains(marker) }

        val hasConnectionPrivacyLanguage = listOf(
            "your connection is not private",
            "privacy error",
            "net::err_cert",
            "certificate is not trusted",
            "certificate has expired",
            "secure connection failed"
        ).any { marker -> normalizedHtml.contains(marker) }

        if (hasStandardPortalLoginForm(html.orEmpty())) {
            return false
        }

        return hasConnectionPrivacyLanguage || (hasChallengeArtifacts && hasChallengeLanguage)
    }

    private fun isSecurityVerificationResponse(response: Response, resolvedUrl: String, body: String): Boolean {
        if (isSecurityVerificationPage(resolvedUrl, body)) {
            return true
        }

        val statusCodeSignals = response.code == 403 ||
            response.code == 429 ||
            response.code == 503 ||
            response.code == 525 ||
            response.code == 526
        if (!statusCodeSignals) {
            return false
        }

        val serverHeader = response.header("Server").orEmpty().lowercase()
        val hasCloudflareHeaders = serverHeader.contains("cloudflare") ||
            !response.header("CF-RAY").isNullOrBlank() ||
            !response.header("cf-mitigated").isNullOrBlank()
        if (!hasCloudflareHeaders) {
            return false
        }

        val contentType = response.header("Content-Type").orEmpty().lowercase()
        val isHtmlLike = contentType.contains("text/html") || contentType.contains("application/xhtml")
        return isHtmlLike || body.isNotBlank()
    }

    private fun isRecoverableSecurityVerificationException(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            val isSslException = current is SSLHandshakeException || current is SSLException
            val privacyWarningSignal = message.contains("your connection is not private") ||
                message.contains("net::err_cert") ||
                message.contains("trust anchor") ||
                message.contains("unable to find valid certification path") ||
                message.contains("certpathvalidatorexception") ||
                message.contains("cert path") ||
                message.contains("peer not authenticated") ||
                message.contains("hostname") && message.contains("not verified") ||
                message.contains("ssl handshake") ||
                (
                    message.contains("certificate") &&
                        (message.contains("validation") || message.contains("trust") || message.contains("path"))
                    )
            if (isSslException || privacyWarningSignal) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun hasSessionCookiesForHost(host: String): Boolean {
        val cookies = cookieStore[host]?.values ?: return false
        val now = System.currentTimeMillis()
        return cookies.any { cookie ->
            val name = cookie.name.lowercase()
            cookie.expiresAt > now && (
                name.contains("session") ||
                    name.contains("auth") ||
                    name.contains("asp.net")
                )
        }
    }

    private fun clearSessionState(preserveSecurityCookies: Boolean = false) {
        synchronized(cookieStore) {
            if (!preserveSecurityCookies) {
                cookieStore.clear()
            } else {
                val now = System.currentTimeMillis()
                val preserved = HashMap<String, MutableMap<String, Cookie>>()
                cookieStore.forEach { (host, cookiesByName) ->
                    val preservedByName = cookiesByName.values
                        .filter { cookie ->
                            cookie.expiresAt > now && (
                                cookie.name.equals("cf_clearance", true) ||
                                    cookie.name.equals("__cf_bm", true) ||
                                    cookie.name.equals("_cfuvid", true) ||
                                    cookie.name.equals("cf_chl_rc_i", true) ||
                                    cookie.name.equals("cf_chl_rc_ni", true) ||
                                    cookie.name.equals("cf_chl_rc_m", true)
                                )
                        }
                        .associateByTo(mutableMapOf()) { it.name }
                    if (preservedByName.isNotEmpty()) {
                        preserved[host] = preservedByName
                    }
                }
                cookieStore.clear()
                cookieStore.putAll(preserved)
            }
        }
        currentStudentName = null
        currentStudentPhotoUrl = null
        currentStudentPhotoBytes = null
        currentStudentPhotoBytesUrl = null
    }

    fun injectCookiesFromWebView(rawCookieHeader: String?, sourceUrl: String = baseUrl): Int {
        if (rawCookieHeader.isNullOrBlank()) return 0
        val host = runCatching { sourceUrl.toHttpUrl().host }.getOrDefault(baseHost)
        if (host.isBlank()) return 0

        val parsedCookies = rawCookieHeader
            .split(";")
            .mapNotNull { cookieToken ->
                val pairIndex = cookieToken.indexOf('=')
                if (pairIndex <= 0) return@mapNotNull null
                val name = cookieToken.substring(0, pairIndex).trim()
                if (name.isEmpty()) return@mapNotNull null
                val value = cookieToken.substring(pairIndex + 1).trim()
                runCatching {
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(host)
                        .path("/")
                        .apply {
                            if (baseUrl.startsWith("https://", true)) secure()
                        }
                        .build()
                }.getOrNull()
            }

        if (parsedCookies.isEmpty()) return 0

        synchronized(cookieStore) {
            val hostCookies = cookieStore.getOrPut(host) { mutableMapOf() }
            parsedCookies.forEach { cookie ->
                hostCookies[cookie.name] = cookie
            }
        }

        return parsedCookies.size
    }

    private val cookieStore = HashMap<String, MutableMap<String, Cookie>>()
    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = cookieStore.getOrPut(url.host) { mutableMapOf() }
                cookies.forEach { hostCookies[it.name] = it }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host]?.values?.toList() ?: listOf()
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val baseUrl = com.danycli.assignmentchecker.BuildConfig.PORTAL_BASE_URL
    @Volatile
    private var userAgent = com.danycli.assignmentchecker.BuildConfig.PORTAL_USER_AGENT
    private val baseHost = runCatching { baseUrl.toHttpUrl().host }.getOrDefault("")

    fun getPortalBaseUrl(): String = baseUrl
    fun getPortalLoginUrl(): String = "$baseUrl/Login.aspx"

    fun setUserAgentForSession(candidate: String?) {
        val normalized = candidate?.trim().orEmpty()
        if (normalized.isNotEmpty()) {
            userAgent = normalized
            debugLog("Updated session user-agent from WebView")
        }
    }

    fun isSecurityVerificationStillRequired(): Boolean {
        val loginUrl = getPortalLoginUrl()
        val request = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", userAgent)
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val resolvedUrl = response.request.url.toString()
            isSecurityVerificationResponse(response, resolvedUrl, body)
        }
    }

    fun login(username: String, password: String, pacing: LoginPacing? = null): LoginResult {
        return try {
            val loginUrl = getPortalLoginUrl()
            val originalUsername = username.trim()
            fun normalizeToken(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
            val registrationParts = originalUsername.split("-").map { it.trim() }.filter { it.isNotEmpty() }
            if (registrationParts.size != 3 || password.isBlank()) {
                return LoginResult.InvalidCredentials
            }
            clearSessionState(preserveSecurityCookies = true)

            debugLog("=== LOGIN START ===")
            debugLog("Login URL: ${sanitizeUrl(loginUrl)}")

            // 1. Initial GET to extraction hidden state tokens and discover field names
            debugLog("Step 1: Fetching login page...")
            pauseForLoginPacing(pacing)
            val initialPayload = client.newCall(
                Request.Builder().url(loginUrl).header("User-Agent", userAgent).build()
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val resolvedUrl = response.request.url.toString()
                val securityVerificationDetected = isSecurityVerificationResponse(response, resolvedUrl, body)
                if (!response.isSuccessful) {
                    if (securityVerificationDetected) {
                        debugLog("Security verification detected on initial GET (HTTP ${response.code})")
                        return LoginResult.CaptchaRequired
                    }
                    return LoginResult.Error("HTTP ${response.code}")
                }
                if (securityVerificationDetected) {
                    debugLog("Security verification detected on initial GET content")
                    return LoginResult.CaptchaRequired
                }
                if (body.isBlank()) return LoginResult.Error("Empty server response")
                detectPortalSystemErrors(body)
                resolvedUrl to body
            }
            val initialUrl = initialPayload.first
            val initialHtml = initialPayload.second

            debugLog("Initial page fetched")
            pauseForLoginPacing(pacing)

            if (isSecurityVerificationPage(initialUrl, initialHtml)) {
                debugLog("CAPTCHA detected")
                return LoginResult.CaptchaRequired
            }

            val doc = Jsoup.parse(initialHtml)
            val form = doc.select("form").first()
            if (form == null) {
                Log.e("PortalAuth", "Form not found in HTML")
                return LoginResult.Error("Form not found")
            }
            val formBuilder = FormBody.Builder()
            
            // Registration Number Parts: Parse "SP25-BCS-001" into 3 parts
            val parts = registrationParts
            val sessCode = parts.getOrNull(0) ?: ""           // "SP25"
            val progCode = parts.getOrNull(1) ?: ""           // "BCS"
            val rollNumber = parts.getOrNull(2) ?: ""         // "001"
            val sessSeason = if (sessCode.contains("SP", true)) "Spring" else "Fall"
            val sessYear = sessCode.filter { it.isDigit() }

            debugLog("Parsed registration metadata")

            // 2. Find the three registration-related fields
            var sessionFieldName = ""
            var programFieldName = ""
            var rollnoFieldName = ""
            var userFieldName = ""
            var passFieldName = ""
            var btnFieldName = ""
            var btnValue = "Login"

            // First pass to find all field names
            debugLog("Step 2: Scanning form fields...")
            form.select("input, select").forEach { el ->
                val name = el.attr("name")
                val id = el.attr("id")
                val type = el.attr("type")
                
                if (el.tagName() == "select") {
                    debugLog("Scanning SELECT field")
                    val normalizedName = normalizeToken(name)
                    val normalizedId = normalizeToken(id)
                    
                    when {
                        normalizedName.contains("session") || normalizedId.contains("session") ||
                        normalizedName.contains("dropdown") && normalizedName.contains("sess") ||
                        name.contains("Session", true) -> {
                            sessionFieldName = name
                            debugLog("Found session field")
                        }
                        normalizedName.contains("program") || normalizedId.contains("program") ||
                        normalizedName.contains("dropdown") && normalizedName.contains("prog") ||
                        name.contains("Program", true) -> {
                            programFieldName = name
                            debugLog("Found program field")
                        }
                    }
                } else {
                    debugLog("Scanning input field")
                    val normalizedName = normalizeToken(name)
                    val normalizedId = normalizeToken(id)
                    
                    when {
                        type.equals("password", true) ||
                        normalizedName.contains("password") ||
                        normalizedId.contains("password") -> {
                            passFieldName = name
                            debugLog("Found password field")
                        }
                        normalizedName.contains("rollno") || normalizedName.contains("roll") ||
                        normalizedId.contains("rollno") || normalizedId.contains("roll") ||
                        name.contains("RollNo", true) -> {
                            rollnoFieldName = name
                            debugLog("Found roll number field")
                        }
                        normalizedName.contains("username") || normalizedName.contains("userid") ||
                        normalizedId.contains("username") ||
                        name.contains("Username", true) -> {
                            userFieldName = name
                            debugLog("Found username field")
                        }
                    }
                }
            }

            // Find login button
            form.select("input[type=submit], button[type=submit], button[name]").forEach { el ->
                val name = el.attr("name")
                val normalizedName = normalizeToken(name)
                if (normalizedName.contains("login") || normalizedName.contains("signin") || 
                    name.contains("btn", true)) {
                    btnFieldName = name
                    btnValue = el.attr("value").ifEmpty { el.text().ifEmpty { "Login" } }
                    debugLog("Found login button")
                }
            }

            debugLog("Step 3: Extracting form fields and tokens...")
            val submittedFields = mutableMapOf<String, String>()

            // Second pass to populate ALL fields (hidden tokens + specific dropdowns)
            form.select("input, select").forEach { el ->
                val name = el.attr("name")
                if (name.isEmpty() || name == userFieldName || name == passFieldName || name == btnFieldName ||
                    name == sessionFieldName || name == programFieldName || name == rollnoFieldName) return@forEach
                
                var value = el.attr("value")
                
                if (el.tagName() == "select") {
                    val options = el.select("option")
                    debugLog("SELECT field options parsed")
                    value = el.select("option[selected]").attr("value").ifEmpty { options.firstOrNull()?.attr("value") ?: "" }
                } else {
                    debugLog("Hidden/input field parsed")
                }
                submittedFields[name] = value
                formBuilder.add(name, value)
            }

            // Add Session field
            if (sessionFieldName.isNotEmpty()) {
                val sessionDropdown = form.selectFirst("select[name=$sessionFieldName]")
                if (sessionDropdown != null) {
                    val options = sessionDropdown.select("option")
                    val matched = options.find { 
                        (it.text().contains(sessSeason, true) && it.text().contains(sessYear)) || 
                        it.attr("value").contains(sessCode, true) 
                    }
                    val sessionValue = matched?.attr("value") ?: options.firstOrNull()?.attr("value") ?: ""
                    debugLog("Session dropdown value selected")
                    submittedFields[sessionFieldName] = sessionValue
                    formBuilder.add(sessionFieldName, sessionValue)
                }
            }

            // Add Program field
            if (programFieldName.isNotEmpty()) {
                val programDropdown = form.selectFirst("select[name=$programFieldName]")
                if (programDropdown != null) {
                    val options = programDropdown.select("option")
                    val matched = options.find {
                        it.text().equals(progCode, true) ||
                        it.attr("value").equals(progCode, true) ||
                        normalizeToken(it.text()).contains(normalizeToken(progCode)) ||
                        normalizeToken(it.attr("value")).contains(normalizeToken(progCode))
                    }
                    val programValue = matched?.attr("value") ?: options.firstOrNull()?.attr("value") ?: ""
                    debugLog("Program dropdown value selected")
                    submittedFields[programFieldName] = programValue
                    formBuilder.add(programFieldName, programValue)
                }
            }

            // Add Roll Number field
            if (rollnoFieldName.isNotEmpty()) {
                debugLog("Roll number field populated")
                submittedFields[rollnoFieldName] = rollNumber
                formBuilder.add(rollnoFieldName, rollNumber)
            }

            // Add the credentials using discovered names
            debugLog("Step 4: Adding credentials...")
            // Add password (only if we found a password field)
            if (passFieldName.isNotEmpty()) {
                formBuilder.add(passFieldName, password)
                submittedFields[passFieldName] = password
                debugLog("Password field populated")
            }
            
            // Add login button if found
            if (btnFieldName.isNotEmpty()) {
                formBuilder.add(btnFieldName, btnValue)
                submittedFields[btnFieldName] = btnValue
                debugLog("Login button field populated")
            }

            // 3. POST the login
            debugLog("Step 5: Posting login request...")
            pauseForLoginPacing(pacing)
            val formAction = form.attr("action")
            val postUrl = when {
                formAction.isBlank() -> loginUrl
                formAction.startsWith("http", true) -> formAction
                formAction.startsWith("/") -> "$baseUrl$formAction"
                else -> "$baseUrl/$formAction"
            }
            debugLog("Posting to URL: ${sanitizeUrl(postUrl)}")
            val postRequest = Request.Builder()
                .url(postUrl)
                .post(formBuilder.build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", loginUrl)
                .header("Origin", baseUrl)
                .header("User-Agent", userAgent)
                .build()

            val finalPayload = client.newCall(postRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val resolvedUrl = response.request.url.toString()
                val securityVerificationDetected = isSecurityVerificationResponse(response, resolvedUrl, body)
                if (!response.isSuccessful) {
                    if (securityVerificationDetected) {
                        debugLog("Security verification detected after login submit (HTTP ${response.code})")
                        return LoginResult.CaptchaRequired
                    }
                    return LoginResult.Error("HTTP ${response.code}")
                }
                if (securityVerificationDetected) {
                    debugLog("Security verification detected after login submit content")
                    return LoginResult.CaptchaRequired
                }
                if (body.isBlank()) return LoginResult.Error("Empty server response")
                detectPortalSystemErrors(body)
                resolvedUrl to body
            }
            val finalUrl = finalPayload.first
            val finalHtml = finalPayload.second

            if (isSecurityVerificationPage(finalUrl, finalHtml)) {
                debugLog("CAPTCHA/security verification detected after login submit")
                return LoginResult.CaptchaRequired
            }

            debugLog("Step 6: Response received")
            debugLog("Final URL: ${sanitizeUrl(finalUrl)}")
            debugLog("Contains 'Logout': ${finalHtml.contains("Logout", true)}")
            debugLog("Contains 'CoursePortal': ${finalHtml.contains("CoursePortal", true)}")
            debugLog("Contains 'Login.aspx': ${finalUrl.contains("Login.aspx", true)}")
            debugLog("Contains 'txtUsername' (login form): ${finalHtml.contains("txtUsername", true)}")

            // 4. Success Check: verify by opening a protected page with same cookies.
            debugLog("Step 7: Verifying session on protected page...")
            pauseForLoginPacing(pacing)
            val verifyUrl = "$baseUrl/CoursePortal.aspx"
            val verifyRequest = Request.Builder()
                .url(verifyUrl)
                .header("Referer", loginUrl)
                .header("User-Agent", userAgent)
                .build()
            val verifyPayload = client.newCall(verifyRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val resolvedUrl = response.request.url.toString()
                val securityVerificationDetected = isSecurityVerificationResponse(response, resolvedUrl, body)
                if (!response.isSuccessful) {
                    if (securityVerificationDetected) {
                        debugLog("Security verification detected on verify page (HTTP ${response.code})")
                        return LoginResult.CaptchaRequired
                    }
                    return LoginResult.Error("HTTP ${response.code}")
                }
                if (securityVerificationDetected) {
                    debugLog("Security verification detected on verify page content")
                    return LoginResult.CaptchaRequired
                }
                if (body.isBlank()) return LoginResult.Error("Empty server response")
                detectPortalSystemErrors(body)
                resolvedUrl to body
            }
            val verifyFinalUrl = verifyPayload.first
            val verifyHtml = verifyPayload.second
            val verifiedProfile = parseStudentProfileFromHtml(verifyHtml)
            val profileMatchesRequestedUser = doesProfileMatchRequestedUsername(
                requestedUsername = originalUsername,
                profile = verifiedProfile,
                html = verifyHtml
            )
            currentStudentName = verifiedProfile.name ?: parseStudentNameFromHtml(finalHtml)
            updateCurrentStudentPhotoUrl(parseStudentPhotoUrlFromHtml(verifyHtml, verifyFinalUrl))

            if (isSecurityVerificationPage(verifyFinalUrl, verifyHtml)) {
                debugLog("CAPTCHA/security verification detected on verify page")
                return LoginResult.CaptchaRequired
            }

            val verifyShowsLogin = isLoginPage(verifyFinalUrl, verifyHtml)
            val hasSessionCookie = hasSessionCookiesForHost(baseHost)
            val verifyLikelyAuthenticated = !verifyShowsLogin && hasSessionCookie

            val isSuccess = verifyLikelyAuthenticated && profileMatchesRequestedUser
            debugLog("Verify URL: ${sanitizeUrl(verifyFinalUrl)}")
            debugLog("Verify shows login form: $verifyShowsLogin")
            debugLog("Verify has session cookie: $hasSessionCookie")
            debugLog("Verify profile matches request: $profileMatchesRequestedUser")
            debugLog("Final result: isSuccess=$isSuccess")
            debugLog("=== LOGIN END ===")
            
            if (isSuccess) {
                LoginResult.Success
            } else {
                clearSessionState()
                LoginResult.InvalidCredentials
            }
        } catch (e: PortalSystemException) {
            clearSessionState()
            LoginResult.Error(e.message ?: "Portal system error")
        } catch (e: Exception) {
            if (isRecoverableSecurityVerificationException(e)) {
                debugLog("Connection privacy warning detected during login flow")
                return LoginResult.CaptchaRequired
            }
            clearSessionState()
            Log.e("PortalAuth", "Exception during login", e)
            LoginResult.Error(e.message ?: "Network error")
        }
    }

    fun fetchAssignments(): Pair<List<Assignment>, List<Assignment>> {
        return try {
            val assignmentsUrl = "$baseUrl/CoursePortal.aspx"
            Log.d("PortalAuth", "Fetching assignments from: $assignmentsUrl")
            
            val request = Request.Builder()
                .url(assignmentsUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PortalAuth", "fetchAssignments HTTP ${response.code}")
                    return Pair(emptyList(), emptyList())
                }
                val body = response.body?.string() ?: run {
                    Log.e("PortalAuth", "fetchAssignments empty server response")
                    return Pair(emptyList(), emptyList())
                }
                detectPortalSystemErrors(body)
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second

            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) {
                Log.d("PortalAuth", "Not authenticated")
                throw PortalSystemException("Session expired")
            }
            currentStudentName = parseStudentNameFromHtml(html) ?: currentStudentName
            updateCurrentStudentPhotoUrl(parseStudentPhotoUrlFromHtml(html, finalUrl))

            val doc = Jsoup.parse(html)
            val pendingAssignments = mutableListOf<Assignment>()
            val submittedAssignments = mutableListOf<Assignment>()
            
            // Find the assignments table by ID
            val table = doc.select("table[id*='gvPortalSummary']").firstOrNull()
                ?: doc.select("table.Grid").firstOrNull()
            
            if (table == null) {
                Log.d("PortalAuth", "Assignments table not found")
                return Pair(emptyList(), emptyList())
            }
            
            Log.d("PortalAuth", "Found assignments table")
            val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
            Log.d("PortalAuth", "Total rows: ${rows.size}")
            
            for (i in 0 until rows.size) {
                try {
                    val cols = rows[i].select("td")
                    
                    if (cols.size < 6) {
                        Log.d("PortalAuth", "Row $i: Only ${cols.size} cols, skipping")
                        continue
                    }
                    
                    val course = cols.getOrNull(1)?.text()?.trim().orEmpty()
                    val title = cols.getOrNull(2)?.text()?.trim().orEmpty()
                    val deadline = cols.getOrNull(4)?.text()?.trim().orEmpty()
                    val statusText = cols.getOrNull(5)?.text()?.trim()?.lowercase().orEmpty()
                    val downloadLink = cols.getOrNull(7)?.let { extractAssignmentDownloadLink(it) }.orEmpty()
                    
                    // Action column may contain submit/change/closed indicators.
                    val actionElement = cols.getOrNull(8)
                    val actionText = actionElement?.text()?.trim()?.lowercase().orEmpty()
                    val actionLink = actionElement?.let { extractAssignmentSubmitLink(it, finalUrl) }.orEmpty()
                    val normalizedState = normalizePortalStatusState(statusText, actionText)
                    val effectiveState = resolveAssignmentStateForDeadline(normalizedState, deadline)
                    
                    Log.d("PortalAuth", "Row $i: Action column - Link: '$actionLink', Text: '$actionText'")
                    
                    // If assignment is closed, there is no usable submit URL.
                    // Otherwise use action link for both pending ("submit")
                    // and submitted-open ("change submitted file") rows.
                    val submitUrl = if (effectiveState == PortalStatusState.NOT_SUBMITTED_CLOSED || actionText.contains("closed")) "" else actionLink
                    
                    if (course.isEmpty() || title.isEmpty()) continue
                    
                    Log.d("PortalAuth", "Row $i: $course - $title - Status: '$statusText'")
                    Log.d("PortalAuth", "  Final submitUrl: $submitUrl")
                    Log.d("PortalAuth", "  Parsed normalized state: $normalizedState")
                    Log.d("PortalAuth", "  Effective state: $effectiveState")

                    val normalizedSubmitUrl = submitUrl
                    Log.d("PortalAuth", "  Parsed submitUrl: '$normalizedSubmitUrl'")

                    when (effectiveState) {
                        PortalStatusState.NOT_SUBMITTED -> {
                            pendingAssignments.add(
                                Assignment(
                                    course, title, deadline,
                                    downloadLink,
                                    normalizedSubmitUrl,
                                    status = AssignmentStatus.PENDING,
                                    submittedDate = null,
                                    grade = null,
                                    feedback = null
                                )
                            )
                        }
                        PortalStatusState.NOT_SUBMITTED_CLOSED -> {
                            submittedAssignments.add(
                                Assignment(
                                    course, title, deadline,
                                    downloadLink,
                                    normalizedSubmitUrl,
                                    status = AssignmentStatus.NOT_SUBMITTED_CLOSED,
                                    submittedDate = null,
                                    grade = null,
                                    feedback = null
                                )
                            )
                            Log.d("PortalAuth", "  Added to history as not submitted: course='$course', title='$title'")
                        }
                        PortalStatusState.SUBMITTED, PortalStatusState.GRADED -> {
                            val assignmentStatus = if (effectiveState == PortalStatusState.GRADED) {
                                AssignmentStatus.GRADED
                            } else {
                                AssignmentStatus.SUBMITTED
                            }
                            submittedAssignments.add(
                                Assignment(
                                    course, title, deadline,
                                    downloadLink,
                                    normalizedSubmitUrl,
                                    status = assignmentStatus,
                                    submittedDate = "",
                                    grade = null,
                                    feedback = null
                                )
                            )
                            Log.d("PortalAuth", "  Added to submitted: course='$course', title='$title', submitLink='$normalizedSubmitUrl'")
                        }
                        PortalStatusState.UNKNOWN -> {
                            Log.d("PortalAuth", "  Row ignored (unknown status): status='$statusText', action='$actionText'")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PortalAuth", "Error row $i: ${e.message}")
                }
            }
            
            Log.d("PortalAuth", "Fetched: ${pendingAssignments.size} pending, ${submittedAssignments.size} submitted")
            Pair(pendingAssignments, submittedAssignments)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching: ${e.message}", e)
            if (e is PortalSystemException) throw e
            Pair(emptyList(), emptyList())
        }
    }

    private fun parseAttendancePercent(value: String?): Double? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val lowered = normalized.lowercase()
        if (lowered == "na" || lowered == "n/a" || lowered == "-" || lowered == "--") return null

        // Prefer explicit percentage tokens when present.
        Regex("""(\d+(?:\.\d+)?)\s*%""").find(lowered)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { percent ->
            return percent.coerceIn(0.0, 100.0)
        }

        // Support ratio-formatted values like "4/5" by converting to percentage.
        val ratioMatch = Regex("""^\s*(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)\s*$""").find(lowered)
        if (ratioMatch != null) {
            val obtained = ratioMatch.groupValues.getOrNull(1)?.toDoubleOrNull()
            val total = ratioMatch.groupValues.getOrNull(2)?.toDoubleOrNull()
            if (obtained != null && total != null && total > 0.0) {
                return ((obtained / total) * 100.0).coerceIn(0.0, 100.0)
            }
        }

        // If the portal gives only a numeric value in a percentage column, accept it as-is.
        val plainNumeric = Regex("""^\s*(\d+(?:\.\d+)?)\s*$""").find(lowered)?.groupValues?.getOrNull(1)
            ?.toDoubleOrNull()
        return plainNumeric?.coerceIn(0.0, 100.0)
    }

    private fun isAttendanceNaToken(value: String?): Boolean {
        val lowered = value?.trim()?.lowercase().orEmpty()
        return lowered == "na" || lowered == "n/a" || lowered == "-" || lowered == "--"
    }

    private fun isTheoryHeader(header: String): Boolean {
        return header.contains("theory") ||
            header.contains("lecture") ||
            Regex("""\bth\b""").containsMatchIn(header)
    }

    private fun isLabHeader(header: String): Boolean {
        return header.contains("lab") ||
            header.contains("practical") ||
            Regex("""\bpr\b""").containsMatchIn(header)
    }

    private fun isPercentHeader(header: String): Boolean {
        return header.contains("%") ||
            header.contains("percent") ||
            header.contains("percentage")
    }

    private fun pickNearestIndex(targetIndices: List<Int>, candidateIndices: List<Int>): Int? {
        if (targetIndices.isEmpty() || candidateIndices.isEmpty()) return null
        return candidateIndices.minByOrNull { candidate ->
            targetIndices.minOf { target -> abs(candidate - target) }
        }
    }

    private fun findAttendanceColumnMapping(table: Element): AttendanceColumnMapping? {
        val rows = table.select("tr")
        for (row in rows) {
            val headerCells = row.select("th, td")
            if (headerCells.size < 3) continue
            val normalizedHeaders = headerCells.map { cell ->
                cell.text().lowercase().replace(Regex("\\s+"), " ").trim()
            }
            val firstHeader = normalizedHeaders.firstOrNull().orEmpty()
            val likelyAttendanceHeaderRow = normalizedHeaders.any { header ->
                header.contains("attendance") || header.contains("%")
            } || firstHeader.contains("course") || firstHeader.contains("subject")
            if (!likelyAttendanceHeaderRow) continue

            val percentIndices = normalizedHeaders.mapIndexedNotNull { index, header ->
                if (isPercentHeader(header)) index else null
            }
            if (percentIndices.isEmpty()) continue

            val theoryHeaderIndices = normalizedHeaders.mapIndexedNotNull { index, header ->
                if (isTheoryHeader(header)) index else null
            }
            val labHeaderIndices = normalizedHeaders.mapIndexedNotNull { index, header ->
                if (isLabHeader(header)) index else null
            }

            val theoryPercentIndex = percentIndices.firstOrNull { index ->
                isTheoryHeader(normalizedHeaders[index])
            } ?: pickNearestIndex(theoryHeaderIndices, percentIndices)
            if (theoryPercentIndex == null) continue

            val labPercentIndex = percentIndices.firstOrNull { index ->
                isLabHeader(normalizedHeaders[index])
            } ?: pickNearestIndex(labHeaderIndices, percentIndices.filter { it != theoryPercentIndex })

            val courseIndex = normalizedHeaders.indexOfFirst { header ->
                header.contains("course") ||
                    header.contains("subject") ||
                    header.contains("title") ||
                    header.contains("code")
            }.takeIf { it >= 0 } ?: 0
            return AttendanceColumnMapping(
                courseIndex = courseIndex,
                theoryPercentIndex = theoryPercentIndex,
                labPercentIndex = labPercentIndex?.takeIf { it != theoryPercentIndex }
            )
        }
        return null
    }

    private fun extractAttendanceInsights(html: String): List<AttendanceInsight> {
        val doc = Jsoup.parse(html)
        val attendanceInsights = mutableListOf<AttendanceInsight>()
        val summaryTables = doc.select("table")
        for (table in summaryTables) {
            val mapping = findAttendanceColumnMapping(table) ?: continue
            val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
            for (row in rows) {
                val cols = row.select("td")
                if (cols.isEmpty()) continue
                val courseName = cols.getOrNull(mapping.courseIndex)?.text()?.trim().orEmpty()
                val normalizedCourseName = courseName.lowercase()
                if (courseName.isBlank() ||
                    courseName.equals("course title", true) ||
                    courseName.equals("subject", true) ||
                    normalizedCourseName.contains("hybrid") ||
                    Regex("""\bhyb\b""").containsMatchIn(normalizedCourseName) ||
                    normalizedCourseName.contains("total") ||
                    normalizedCourseName.contains("overall")
                ) {
                    continue
                }
                val theoryPercent = parseAttendancePercent(cols.getOrNull(mapping.theoryPercentIndex)?.text())
                val labPercent = mapping.labPercentIndex?.let { index ->
                    parseAttendancePercent(cols.getOrNull(index)?.text())
                }
                val theoryRaw = cols.getOrNull(mapping.theoryPercentIndex)?.text()
                val labRaw = mapping.labPercentIndex?.let { index -> cols.getOrNull(index)?.text() }
                if (theoryPercent == null && labPercent == null &&
                    !isAttendanceNaToken(theoryRaw) &&
                    !isAttendanceNaToken(labRaw)
                ) {
                    continue
                }
                val effectivePercent = when {
                    theoryPercent != null && labPercent != null -> (theoryPercent + labPercent) / 2.0
                    theoryPercent != null -> theoryPercent
                    labPercent != null -> labPercent
                    else -> 100.0
                }
                attendanceInsights.add(
                    AttendanceInsight(
                        courseTitle = courseName,
                        theoryPercent = theoryPercent,
                        labPercent = labPercent,
                        effectivePercent = effectivePercent
                    )
                )
            }
            if (attendanceInsights.isNotEmpty()) {
                break
            }
        }
        return attendanceInsights
    }

    fun fetchLowestAttendanceInsight(): AttendanceInsight? {
        return try {
            val summaryUrl = "$baseUrl/Summary.aspx"
            val request = Request.Builder()
                .url(summaryUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PortalAuth", "fetchLowestAttendanceInsight HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: run {
                    Log.e("PortalAuth", "fetchLowestAttendanceInsight empty server response")
                    return null
                }
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second
            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) return null

            val attendanceInsights = extractAttendanceInsights(html)
            if (attendanceInsights.isEmpty()) return null
            attendanceInsights.minByOrNull { insight -> insight.effectivePercent }
        } catch (e: Exception) {
            Log.e("PortalAuth", "fetchLowestAttendanceInsight error: ${e.message}", e)
            null
        }
    }

    fun fetchHistoricalAssignments(): List<Assignment> {
        return try {
            val assignmentsUrl = "$baseUrl/CoursePortal.aspx"
            Log.d("PortalAuth", "Fetching historical...")
            
            val request = Request.Builder()
                .url(assignmentsUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PortalAuth", "fetchHistoricalAssignments HTTP ${response.code}")
                    return emptyList()
                }
                val body = response.body?.string() ?: run {
                    Log.e("PortalAuth", "fetchHistoricalAssignments empty server response")
                    return emptyList()
                }
                detectPortalSystemErrors(body)
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second

            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) {
                Log.d("PortalAuth", "Not authenticated")
                throw PortalSystemException("Session expired")
            }
            currentStudentName = parseStudentNameFromHtml(html) ?: currentStudentName
            updateCurrentStudentPhotoUrl(parseStudentPhotoUrlFromHtml(html, finalUrl))

            val doc = Jsoup.parse(html)
            val submitted = mutableListOf<Assignment>()
            
            val table = doc.select("table[id*='gvPortalSummary']").firstOrNull()
                ?: doc.select("table.Grid").firstOrNull() ?: return emptyList()
            
            val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
            
            for (i in 0 until rows.size) {
                try {
                    val cols = rows[i].select("td")
                    if (cols.size < 6) continue
                    
                    val course = cols.getOrNull(1)?.text()?.trim().orEmpty()
                    val title = cols.getOrNull(2)?.text()?.trim().orEmpty()
                    val deadline = cols.getOrNull(4)?.text()?.trim().orEmpty()
                    val statusText = cols.getOrNull(5)?.text()?.trim()?.lowercase().orEmpty()
                    val downloadLink = cols.getOrNull(7)?.let { extractAssignmentDownloadLink(it) }.orEmpty()
                    
                    val actionElement = cols.getOrNull(8)
                    val actionText = actionElement?.text()?.trim()?.lowercase().orEmpty()
                    val normalizedState = normalizePortalStatusState(statusText, actionText)
                    val effectiveState = resolveAssignmentStateForDeadline(normalizedState, deadline)
                    val submitLinkHref = actionElement?.let { extractAssignmentSubmitLink(it, finalUrl) }.orEmpty()
                    val submitLink = if (actionText.contains("closed") || effectiveState == PortalStatusState.NOT_SUBMITTED_CLOSED) {
                        ""
                    } else {
                        submitLinkHref
                    }
                    Log.d("PortalAuth", "  Historical row: course='$course', title='$title', actionText='$actionText', submitLink='$submitLink'")
                    
                    if (course.isEmpty()) continue
                    if (effectiveState != PortalStatusState.SUBMITTED &&
                        effectiveState != PortalStatusState.GRADED &&
                        effectiveState != PortalStatusState.NOT_SUBMITTED_CLOSED
                    ) continue
                    
                    submitted.add(Assignment(
                        course, title, deadline,
                        downloadLink, submitLink,
                        status = when (effectiveState) {
                            PortalStatusState.GRADED -> AssignmentStatus.GRADED
                            PortalStatusState.NOT_SUBMITTED_CLOSED -> AssignmentStatus.NOT_SUBMITTED_CLOSED
                            else -> AssignmentStatus.SUBMITTED
                        },
                        submittedDate = if (effectiveState == PortalStatusState.NOT_SUBMITTED_CLOSED) null else "",
                        grade = null,
                        feedback = null
                    ))
                } catch (e: Exception) {
                    Log.e("PortalAuth", "Error: ${e.message}")
                }
            }
            Log.d("PortalAuth", "Historical fetched: ${submitted.size}")
            submitted
        } catch (e: Exception) {
            Log.e("PortalAuth", "Historical error: ${e.message}", e)
            emptyList()
        }
    }

    fun uploadAssignment(submitPageUrl: String, file: File): UploadResult {
        return try {
            Log.d("PortalAuth", "=== UPLOAD START ===")
            Log.d("PortalAuth", "Submit URL: $submitPageUrl")
            Log.d("PortalAuth", "File: ${file.name} (${file.length()} bytes)")

            if (isPostBackDownloadLink(submitPageUrl)) {
                val postBackLink = extractPostBackLinkFromLink(submitPageUrl)
                    ?: return UploadResult.Rejected("Upload link is invalid.")
                return uploadAssignmentViaPostBack(postBackLink, file)
            }
            
            // Validate that we have a URL
            if (submitPageUrl.isBlank()) {
                Log.e("PortalAuth", "Submit URL is empty or blank!")
                Log.d("PortalAuth", "This might be a re-upload of an already-submitted assignment")
                Log.d("PortalAuth", "Trying to fetch CoursePortal page instead...")
                
                // For re-uploads of already-submitted assignments, fetch the CoursePortal page
                // which should have the submission form even for submitted items if deadline is open
                val altUrl = "$baseUrl/CoursePortal.aspx"
                Log.d("PortalAuth", "Using alternate URL: $altUrl")
                
                val getRequest = Request.Builder()
                    .url(altUrl)
                    .header("Referer", "$baseUrl/CoursePortal.aspx")
                    .header("User-Agent", userAgent)
                    .build()
                
                val pageHtml = client.newCall(getRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("PortalAuth", "Upload prefetch HTTP ${response.code}")
                        return UploadResult.Rejected("Server rejected request (HTTP ${response.code}).")
                    }
                    response.body?.string() ?: return UploadResult.Error("Empty server response.")
                }
                
                Log.d("PortalAuth", "Page HTML length: ${pageHtml.length}")
                
                // Try to find a form or upload element on this page
                val doc = Jsoup.parse(pageHtml)
                val form = doc.select("form").first()
                if (form == null) {
                    Log.e("PortalAuth", "Form not found on alternate page")
                    return UploadResult.Rejected("Upload form not found.")
                }
                
                Log.d("PortalAuth", "Using CoursePortal form for re-upload")
                // Continue with the form we found
                return uploadWithForm(form, file, pageHtml)
            }
            
            // Step 1: GET the submission page to get ASP.NET viewstate and validation fields
            Log.d("PortalAuth", "Step 1: Fetching submission page...")
            val getRequest = Request.Builder()
                .url(submitPageUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            
            val pageHtml = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PortalAuth", "Upload page fetch HTTP ${response.code}")
                    return UploadResult.Rejected("Server rejected request (HTTP ${response.code}).")
                }
                response.body?.string() ?: return UploadResult.Error("Empty server response.")
            }
            
            Log.d("PortalAuth", "Page HTML length: ${pageHtml.length}")
            
            val doc = Jsoup.parse(pageHtml)
            val form = doc.select("form").first()
            if (form == null) {
                Log.e("PortalAuth", "Form not found")
                return UploadResult.Rejected("Upload form not found.")
            }
            
            Log.d("PortalAuth", "Form found, action: ${form.attr("action")}, method: ${form.attr("method")}")
            
            // Debug: Check what's visible on the page (not in form)
            Log.d("PortalAuth", "=== PAGE CONTENT ===")
            val allText = doc.body()?.text() ?: ""
            Log.d("PortalAuth", "Page body text length: ${allText.length}")
            
            // Look for any labels or visible text that might indicate required fields
            val labels = doc.select("label")
            if (labels.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${labels.size} labels on page:")
                labels.take(10).forEach { label ->
                    Log.d("PortalAuth", "  Label: ${label.text()}")
                }
            }
            
            // Check for any textareas or input fields visible to user
            val textareas = doc.select("textarea")
            if (textareas.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${textareas.size} textareas")
                textareas.forEach { ta ->
                    Log.d("PortalAuth", "  Textarea: name='${ta.attr("name")}', id='${ta.attr("id")}'")
                }
            }
            
            val visibleInputs = doc.select("input[type=text], input[type=password], input[type=email]")
            if (visibleInputs.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${visibleInputs.size} visible input fields")
                visibleInputs.forEach { inp ->
                    Log.d("PortalAuth", "  Input: name='${inp.attr("name")}', placeholder='${inp.attr("placeholder")}'")
                }
            }
            
            return uploadWithForm(form, file, pageHtml)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Upload error: ${e.message}", e)
            e.printStackTrace()
            if (e is IOException) UploadResult.NetworkError else UploadResult.Error(e.message ?: "Upload failed.")
        }
    }
    
    private fun uploadWithForm(form: Element, file: File, pageHtml: String): UploadResult {
        return try {
            Log.d("PortalAuth", "Step 2: Processing form for upload...")
            
            // Parse the page again to extract ASP.NET fields
            val doc = Jsoup.parse(pageHtml)
            
            // For re-uploads from CoursePortal, we need to find the form that contains the file input
            // The main form on CoursePortal might not have it - look for it in any form on the page
            var targetForm = form
            val fileInput = form.select("input[type=file]").firstOrNull()
            if (fileInput == null) {
                Log.d("PortalAuth", "File input not in main form, searching all forms on page...")
                val allForms = doc.select("form")
                Log.d("PortalAuth", "Found ${allForms.size} total forms on page")
                
                // Try to find a form with file input
                for (f in allForms) {
                    val fi = f.select("input[type=file]").firstOrNull()
                    if (fi != null) {
                        Log.d("PortalAuth", "Found file input in a different form!")
                        targetForm = f
                        break
                    }
                }
            }
            
            // Extract ASP.NET viewstate and event validation from the actual form we'll use
            val viewState = targetForm.select("input[name='__VIEWSTATE']").attr("value") ?: ""
            val eventValidation = targetForm.select("input[name='__EVENTVALIDATION']").attr("value") ?: ""
            val viewStateGenerator = targetForm.select("input[name='__VIEWSTATEGENERATOR']").attr("value") ?: ""
            
            Log.d("PortalAuth", "ViewState found: ${viewState.isNotEmpty()}")
            Log.d("PortalAuth", "EventValidation found: ${eventValidation.isNotEmpty()}")
            
            // Count total hidden fields for debugging
            val hiddenFields = targetForm.select("input[type=hidden]")
            Log.d("PortalAuth", "Total hidden fields in form: ${hiddenFields.size}")
            
            // Step 3: Build multipart form with file
            Log.d("PortalAuth", "Step 3: Building form with file...")
            val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            
            // Log ALL form fields before building
            Log.d("PortalAuth", "=== ALL FORM FIELDS ===")
            Log.d("PortalAuth", "Form tag attributes: action='${targetForm.attr("action")}', method='${targetForm.attr("method")}'")
            val allInputs = targetForm.select("input")
            Log.d("PortalAuth", "Total input fields: ${allInputs.size}")
            allInputs.forEach { input ->
                val type = input.attr("type")
                val name = input.attr("name")
                val value = input.attr("value").take(50)
                Log.d("PortalAuth", "  $type: $name = $value")
            }
            
            // Add ASP.NET postback fields - detect the actual upload trigger control first.
            val defaultEventTarget = "ctl00\$DataContent\$btnAddFile"
            val submitButtons = targetForm.select("input[type=button], input[type=submit], button[type=button], button[type=submit], button[name]")
            Log.d("PortalAuth", "Found ${submitButtons.size} submit buttons")
            submitButtons.forEach { btn ->
                val btnName = btn.attr("name")
                Log.d("PortalAuth", "  Button: name='$btnName', value='${btn.attr("value")}', text='${btn.text()}'")
            }

            val preferredButton = submitButtons.maxByOrNull { btn ->
                val fingerprint = listOf(
                    btn.attr("name"),
                    btn.attr("id"),
                    btn.attr("value"),
                    btn.text(),
                    btn.className()
                ).joinToString(" ").lowercase()
                when {
                    fingerprint.contains("addfile") || fingerprint.contains("updatefile") -> 6
                    fingerprint.contains("upload") -> 5
                    fingerprint.contains("change") -> 4
                    fingerprint.contains("submit") -> 3
                    fingerprint.contains("assignment") || fingerprint.contains("attach") -> 2
                    else -> 0
                }
            }

            val preferredButtonName = preferredButton?.attr("name")
                ?.trim()
                ?.ifEmpty { null }
                ?: preferredButton?.attr("id")
                    ?.trim()
                    ?.replace("_", "$")
                    ?.ifEmpty { null }

            val eventTarget = preferredButtonName ?: defaultEventTarget
            Log.d("PortalAuth", "Using __EVENTTARGET: $eventTarget")

            formBuilder.addFormDataPart("__EVENTTARGET", eventTarget)
            formBuilder.addFormDataPart("__EVENTARGUMENT", "")
            formBuilder.addFormDataPart("__VIEWSTATE", viewState)
            formBuilder.addFormDataPart("__EVENTVALIDATION", eventValidation)
            if (viewStateGenerator.isNotEmpty()) {
                formBuilder.addFormDataPart("__VIEWSTATEGENERATOR", viewStateGenerator)
            }

            if (!preferredButtonName.isNullOrBlank()) {
                val preferredButtonValue = preferredButton?.attr("value").orEmpty().ifBlank { preferredButton?.text().orEmpty() }
                if (preferredButtonValue.isNotBlank()) {
                    Log.d("PortalAuth", "Adding trigger button field: $preferredButtonName = $preferredButtonValue")
                    formBuilder.addFormDataPart(preferredButtonName, preferredButtonValue)
                }
            }
            
            // Add all other hidden form fields - __PREVIOUSPAGE might be needed for re-uploads
            val hiddenInputs = targetForm.select("input[type=hidden]")
            Log.d("PortalAuth", "Processing ${hiddenInputs.size} hidden fields:")
            hiddenInputs.forEach { input ->
                val name = input.attr("name")
                val value = input.attr("value")
                Log.d("PortalAuth", "  Hidden: $name = ${value.take(100)}")
                // Skip ones we already added
                if (name.isNotEmpty() && 
                    !name.startsWith("__VIEWSTATE") && 
                    !name.startsWith("__EVENTVALIDATION") &&
                    !name.startsWith("__EVENTTARGET") &&
                    !name.startsWith("__EVENTARGUMENT")) {
                    formBuilder.addFormDataPart(name, value)
                }
            }
            
            // Also check for file input field to see its exact name attribute
            val fileInputs = targetForm.select("input[type=file]")
            Log.d("PortalAuth", "Found ${fileInputs.size} file input fields:")
            var fileInputName = "ctl00\$DataContent\$fileAssignment1"  // Default name
            fileInputs.forEach { input ->
                val name = input.attr("name")
                Log.d("PortalAuth", "  File input name: '$name'")
                if (name.isNotEmpty()) {
                    fileInputName = name  // Use actual name if found
                }
            }
            
            val mimeType = guessMimeType(file.name)
            val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            formBuilder.addFormDataPart(fileInputName, file.name, fileBody)
            
            // Step 4: POST the form
            Log.d("PortalAuth", "Step 4: Submitting form with file...")
            
            // Build the form - log what we're sending
            val formBody = formBuilder.build()
            Log.d("PortalAuth", "Form has ${formBody.parts.size} parts")
            
            val formAction = targetForm.attr("action")
            val postUrl = when {
                formAction.isBlank() -> "$baseUrl/CoursePortal.aspx"
                formAction.startsWith("http") -> formAction
                formAction.startsWith("/") -> "$baseUrl$formAction"
                else -> "$baseUrl/$formAction"
            }
            
            Log.d("PortalAuth", "Posting to: $postUrl")
            
            val postRequest = Request.Builder()
                .url(postUrl)
                .post(formBody)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("Origin", baseUrl)
                .header("User-Agent", userAgent)
                .build()
            
            val uploadResponsePayload = client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("PortalAuth", "Upload submit HTTP ${response.code}")
                    return UploadResult.Rejected("Server rejected request (HTTP ${response.code}).")
                }
                val body = response.body?.string() ?: return UploadResult.Error("Empty server response.")
                response.request.url.toString() to body
            }
            val responseUrl = uploadResponsePayload.first
            val responseHtml = uploadResponsePayload.second
            
            Log.d("PortalAuth", "Response URL: $responseUrl")
            Log.d("PortalAuth", "Response HTML length: ${responseHtml.length}")

            if (isUploadSizeErrorRedirect(responseUrl, responseHtml)) {
                Log.d("PortalAuth", "Upload failed: redirected to portal upload error page")
                return UploadResult.Rejected("Upload rejected: file too large.")
            }
            
            // Log response snippet for debugging
            val lines = responseHtml.split("\n")
            Log.d("PortalAuth", "Response has ${lines.size} lines")
            if (responseHtml.contains("</form>")) {
                val formEndIdx = responseHtml.indexOf("</form>")
                Log.d("PortalAuth", "Form snippet (last 500 chars): ${responseHtml.substring(maxOf(0, formEndIdx - 500), formEndIdx)}")
            }
            
            // Check for validation errors
            if (responseHtml.contains("aspNetHidden", true)) {
                Log.d("PortalAuth", "Found aspNetHidden - ASP.NET validation error")
            }
            if (responseHtml.contains("__VIEWSTATE", true)) {
                Log.d("PortalAuth", "Response contains __VIEWSTATE - page reloaded")
            }
            
            // Debug: Look for actual error message in response
            val errorPattern = "(?i)<div class=\"notification error.*?</div>".toRegex()
            val errorMatch = errorPattern.find(responseHtml)
            if (errorMatch != null) {
                Log.d("PortalAuth", "Error div found: ${errorMatch.value.take(200)}")
            }
            
            // Search for any visible text that might be an error or validation message
            val doc2 = Jsoup.parse(responseHtml)
            val visibleValidationMessages = mutableListOf<String>()
            fun Element.isLikelyVisible(): Boolean {
                var node: Element? = this
                while (node != null) {
                    val style = node.attr("style").lowercase().replace("\\s".toRegex(), "")
                    val className = node.className().lowercase()
                    if (node.hasAttr("hidden")) return false
                    if (node.attr("aria-hidden").equals("true", ignoreCase = true)) return false
                    if (style.contains("display:none") || style.contains("visibility:hidden")) return false
                    if (className.contains("d-none") || className.contains("invisible")) return false
                    node = node.parent()
                }
                return true
            }
             
            // Look for spans/divs with "RequiredFieldValidator" or validation class
            val validatorSpans = doc2.select("[id*='Validator']")
            if (validatorSpans.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${validatorSpans.size} validator elements")
                validatorSpans.forEach { elem ->
                    if (!elem.isLikelyVisible()) return@forEach
                    val text = elem.text()
                    if (text.isNotEmpty()) {
                        Log.d("PortalAuth", "  Validator text: $text")
                        visibleValidationMessages.add(text)
                    }
                }
            }
            
            // Look for any error or notification divs
            val errorDivs = doc2.select(".error, [id*='error'], .notification")
            if (errorDivs.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${errorDivs.size} error divs")
                errorDivs.forEach { elem ->
                    if (!elem.isLikelyVisible()) return@forEach
                    val text = elem.text()
                    if (text.isNotEmpty()) {
                        Log.d("PortalAuth", "  Error div: $text")
                        visibleValidationMessages.add(text)
                    }
                }
            }
            
            // Look for any summary control
            val summaryControls = doc2.select("[id*='ValidationSummary'], [id*='Summary']")
            summaryControls.forEach { elem ->
                if (!elem.isLikelyVisible()) return@forEach
                val text = elem.text()
                if (text.isNotEmpty()) {
                    Log.d("PortalAuth", "  Summary control: $text")
                    visibleValidationMessages.add(text)
                }
            }
            
            // Check for success indicators
            // For successful uploads (both initial and re-uploads):
            // 1. If "File once uploaded cannot be changed" appears - file was already there, now replaced = SUCCESS
            // 2. If file input field disappears = SUCCESS (form reloaded without file field)
            // 3. If we got a valid response with form reload = likely SUCCESS
            
            val hasFileInput = responseHtml.contains("fileAssignment1", ignoreCase = true)
            val hasSuccessMessage = responseHtml.contains("File once uploaded cannot be changed", ignoreCase = true) ||
                                   responseHtml.contains("successfully uploaded", ignoreCase = true) ||
                                   responseHtml.contains("submission successful", ignoreCase = true) ||
                                   responseHtml.contains("file uploaded", ignoreCase = true) ||
                                   responseHtml.contains("your file has been submitted", ignoreCase = true) ||
                                   responseHtml.contains("Assignment file updated succefully", ignoreCase = true) ||
                                   responseHtml.contains("Assignment file updated successfully", ignoreCase = true)
            
            val hasViewstate = responseHtml.contains("__VIEWSTATE", ignoreCase = true)
            val hasForm = responseHtml.contains("<form", ignoreCase = true)
            val cleanedValidationMessages = visibleValidationMessages
                .map { message ->
                    message
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                .filter { it.isNotEmpty() }
            val normalizedValidationText = cleanedValidationMessages
                .joinToString(" | ")
                .lowercase()
                .replace(Regex("\\s+"), " ")
            Log.d("PortalAuth", "Visible validation text: $normalizedValidationText")

            val portalSizeMessage = cleanedValidationMessages.firstOrNull { message ->
                val normalized = message.lowercase()
                val hasSizeToken = normalized.contains("size") || normalized.contains("mb") || normalized.contains("kb")
                val hasLimitToken = normalized.contains("max") ||
                    normalized.contains("maximum") ||
                    normalized.contains("limit") ||
                    normalized.contains("exceed") ||
                    normalized.contains("less than")
                hasSizeToken && hasLimitToken
            }
            val portalValidationErrorMessage = cleanedValidationMessages.firstOrNull { message ->
                val normalized = message.lowercase()
                normalized.contains("required") ||
                    normalized.contains("invalid") ||
                    normalized.contains("not allowed") ||
                    normalized.contains("closed") ||
                    normalized.contains("too large") ||
                    normalized.contains("maximum") ||
                    normalized.contains("exceed")
            }

            // Check only visible validation messages to avoid false rejects from static page hints.
            val hasFormatError = normalizedValidationText.contains("only .zip,.rar,.doc,.docx and .pdf allowed") ||
                normalizedValidationText.contains("format is not allowed") ||
                normalizedValidationText.contains("file format is not allowed")
            val hasMissingFileError = normalizedValidationText.contains("required") &&
                (normalizedValidationText.contains("fileuploadvalidator") || normalizedValidationText.contains("file"))
            val hasInvalidFileError = normalizedValidationText.contains("invalid file")
            val hasSizeError = (normalizedValidationText.contains("size") &&
                (normalizedValidationText.contains("maximum") ||
                    normalizedValidationText.contains("max") ||
                    normalizedValidationText.contains("limit") ||
                    normalizedValidationText.contains("exceed") ||
                    normalizedValidationText.contains("less than"))) ||
                portalSizeMessage != null
            val hasClosedError = normalizedValidationText.contains("closed") && normalizedValidationText.contains("assignment")
            val hasGenericPortalError = portalValidationErrorMessage != null

            val hasError = hasFormatError ||
                hasMissingFileError ||
                hasInvalidFileError ||
                hasSizeError ||
                hasClosedError ||
                hasGenericPortalError

            val rejectionReason = when {
                hasFormatError ->
                    "Upload rejected: only .zip, .rar, .doc, .docx, .pdf are allowed."
                hasMissingFileError ->
                    "Upload rejected: file missing or form not accepted."
                hasInvalidFileError ->
                    "Upload rejected: invalid file."
                hasSizeError ->
                    portalSizeMessage?.let { "Upload rejected: $it" } ?: "Upload rejected: file too large."
                hasClosedError ->
                    "Upload rejected: assignment is closed."
                hasGenericPortalError ->
                    "Upload rejected: $portalValidationErrorMessage"
                else -> "Upload rejected by server."
            }
            
            Log.d("PortalAuth", "Success indicators:")
            Log.d("PortalAuth", "  Has file input field: $hasFileInput")
            Log.d("PortalAuth", "  Has success message: $hasSuccessMessage")
            Log.d("PortalAuth", "  Has viewstate: $hasViewstate")
            Log.d("PortalAuth", "  Has form: $hasForm")
            Log.d("PortalAuth", "  Has error: $hasError")
            Log.d("PortalAuth", "  Response length: ${responseHtml.length}")
            
            // Success detection logic:
            // 1. Any visible validation error from portal = reject
            // 2. Explicit success message = success
            // 3. If no file input field AND form reloaded = success
            // 4. If we got a valid HTML page with form and viewstate = likely success
            
            val successProof = when {
                hasError -> {
                    Log.d("PortalAuth", "Failed: Found error message")
                    null
                }
                hasSuccessMessage -> {
                    Log.d("PortalAuth", "Success: Found success message")
                    "Server returned explicit success confirmation."
                }
                !hasFileInput && hasViewstate && hasForm -> {
                    Log.d("PortalAuth", "Success: File input disappeared and page reloaded")
                    "Server reloaded submission page and removed file input after submit."
                }
                hasViewstate && hasForm && responseHtml.length > 5000 -> {
                    Log.d("PortalAuth", "Success: Valid page response (${responseHtml.length} bytes)")
                    "Server accepted multipart form and returned full portal response."
                }
                responseUrl.contains("CoursePortal", ignoreCase = true) -> {
                    Log.d("PortalAuth", "Success: Redirected to CoursePortal")
                    "Server redirected back to CoursePortal after submission."
                }
                else -> {
                    Log.d("PortalAuth", "Failed: Could not verify success")
                    null
                }
            }
            
            val isSuccess = successProof != null
            Log.d("PortalAuth", "Upload result: ${if (isSuccess) "SUCCESS" else "FAILED"}")
            Log.d("PortalAuth", "=== UPLOAD END ===")
            
            if (isSuccess) UploadResult.Success else UploadResult.Rejected(rejectionReason)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Upload error: ${e.message}", e)
            e.printStackTrace()
            if (e is IOException) UploadResult.NetworkError else UploadResult.Error(e.message ?: "Upload failed.")
        }
    }

    private fun isUploadSizeErrorRedirect(responseUrl: String, responseHtml: String): Boolean {
        val parsed = runCatching { responseUrl.toHttpUrl() }.getOrNull()
        val path = parsed?.encodedPath.orEmpty().lowercase()
        val aspxErrorPath = parsed?.queryParameter("aspxerrorpath").orEmpty().lowercase()
        val normalizedUrl = responseUrl.lowercase()
        val normalizedHtml = responseHtml.lowercase()

        val isPortalUploadErrorPath = (
            (path.endsWith("/error.html") || normalizedUrl.contains("/error.html")) &&
                (aspxErrorPath.contains("courseportalsubmitassignment.aspx") ||
                    normalizedUrl.contains("aspxerrorpath=%2fcourseportalsubmitassignment.aspx") ||
                    normalizedUrl.contains("aspxerrorpath=/courseportalsubmitassignment.aspx"))
            )

        if (isPortalUploadErrorPath) return true

        val hasPortalSizeMessage = normalizedHtml.contains("maximum request length exceeded") ||
            normalizedHtml.contains("request entity too large") ||
            (normalizedHtml.contains("file") && normalizedHtml.contains("too large"))

        return hasPortalSizeMessage
    }

    private fun extractRedirectUrlFromHtml(html: String): String? {
        val patterns = listOf(
            Regex("window\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
            Regex("location\\.replace\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
            Regex("window\\.open\\(\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE),
            Regex("url\\s*=\\s*([^;\"'>]+)", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() }
        }
    }

    private fun buildDownloadFollowRequest(url: String, referer: String): Request {
        return Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", userAgent)
            .build()
    }

    private fun resolveOrigin(url: String): String {
        return runCatching {
            val parsed = url.toHttpUrl()
            val defaultPort = (parsed.scheme == "https" && parsed.port == 443) || (parsed.scheme == "http" && parsed.port == 80)
            if (defaultPort) "${parsed.scheme}://${parsed.host}" else "${parsed.scheme}://${parsed.host}:${parsed.port}"
        }.getOrDefault(baseUrl)
    }

    private fun buildPostBackRequestFromPage(pageUrl: String, html: String, info: PostBackInfo): Request? {
        val doc = Jsoup.parse(html, pageUrl)
        val form = doc.select("form").firstOrNull { it.selectFirst("input[name=__VIEWSTATE]") != null }
            ?: doc.selectFirst("form")
            ?: return null
        val postBuilder = FormBody.Builder()
        form.select("input[type=hidden]").forEach { hidden ->
            val name = hidden.attr("name")
            if (name.isBlank() || name == "__EVENTTARGET" || name == "__EVENTARGUMENT") return@forEach
            postBuilder.add(name, hidden.attr("value"))
        }
        postBuilder.add("__EVENTTARGET", info.target)
        postBuilder.add("__EVENTARGUMENT", info.argument)

        val formAction = form.attr("action")
        val postUrl = when {
            formAction.isBlank() -> pageUrl
            formAction.startsWith("http", true) -> formAction
            else -> normalizeUrl(formAction, pageUrl)
        }
        if (postUrl.isBlank()) return null

        return Request.Builder()
            .url(postUrl)
            .post(postBuilder.build())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", pageUrl)
            .header("Origin", resolveOrigin(pageUrl))
            .header("User-Agent", userAgent)
            .build()
    }

    private fun extractCandidateFromClickable(element: Element, pageUrl: String): HtmlDownloadCandidate? {
        val href = element.attr("href")
        val onClick = element.attr("onclick")
        val postBackInfo = extractPostBackInfo(href) ?: extractPostBackInfo(onClick)
        if (postBackInfo != null) {
            return HtmlDownloadCandidate(postBackInfo = postBackInfo)
        }
        val rawUrl = when {
            href.isBlank() || href == "#" -> extractUrlFromJavascript(onClick)
            href.startsWith("javascript", true) -> extractUrlFromJavascript(href) ?: extractUrlFromJavascript(onClick)
            else -> href
        }
        val normalized = normalizeUrl(rawUrl, pageUrl)
        return if (normalized.isNotBlank()) HtmlDownloadCandidate(url = normalized) else null
    }

    private fun extractInstructionFileNameFromRow(row: Element): String {
        val cells = row.select("td")
        if (cells.isNotEmpty()) {
            val cellTexts = cells
                .map { it.text().trim() }
                .filter { it.isNotBlank() && !it.equals("download", true) && !it.matches(Regex("^\\d+$")) }
            val extensionLike = cellTexts.firstOrNull { it.matches(Regex(".*\\.[a-zA-Z0-9]{1,8}$")) }
            val best = extensionLike ?: cellTexts.maxByOrNull { it.length }
            if (!best.isNullOrBlank()) {
                return sanitizeFileName(best)
            }
        }
        val linkText = row.select("a").firstOrNull { it.text().isNotBlank() && !it.text().contains("download", true) }?.text()?.trim().orEmpty()
        return sanitizeFileName(linkText.ifBlank { "instruction_file" })
    }

    private fun extractDownloadCandidateFromElement(element: Element, pageUrl: String): String? {
        val href = element.attr("href")
        val onClick = element.attr("onclick")
        val postBackInfo = extractPostBackInfo(href) ?: extractPostBackInfo(onClick)
        if (postBackInfo != null) {
            return toPostBackDownloadLink(postBackInfo, sourcePageUrl = pageUrl)
        }
        val rawUrl = when {
            href.isBlank() || href == "#" -> extractUrlFromJavascript(onClick)
            href.startsWith("javascript", true) -> extractUrlFromJavascript(href) ?: extractUrlFromJavascript(onClick)
            else -> href
        }
        val normalized = normalizeUrl(rawUrl, pageUrl)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun parseInstructionFilesFromHtml(html: String, pageUrl: String): List<InstructionFile> {
        val doc = Jsoup.parse(html, pageUrl)
        val files = mutableListOf<InstructionFile>()

        val tableRows = doc.select("table tr")
        for (row in tableRows) {
            val rowText = row.text()
            if (rowText.isBlank()) continue
            val rowLower = rowText.lowercase()
            if (row.select("th").isNotEmpty()) continue
            if (!(rowLower.contains("download") || row.select("a,button,input").any { it.text().contains("download", true) || it.attr("value").contains("download", true) })) {
                continue
            }
            val fileName = extractInstructionFileNameFromRow(row)
            val clickable = row.select("a[href], a[onclick], button[onclick], input[onclick], span[onclick], input[type=submit], button")
            val link = clickable.firstNotNullOfOrNull { extractDownloadCandidateFromElement(it, pageUrl) }
            if (!link.isNullOrBlank()) {
                files.add(InstructionFile(fileName = fileName, downloadLink = link))
            }
        }

        if (files.isNotEmpty()) {
            return files.distinctBy { it.downloadLink }
        }

        val allClickable = doc.select("a[href], a[onclick], button[onclick], input[onclick], span[onclick]")
        for (element in allClickable) {
            val text = element.text().ifBlank { element.attr("value") }.trim()
            if (!text.contains("download", true)) continue
            val link = extractDownloadCandidateFromElement(element, pageUrl) ?: continue
            files.add(
                InstructionFile(
                    fileName = sanitizeFileName(text.replace("download", "", ignoreCase = true).trim().ifBlank { "instruction_file" }),
                    downloadLink = link
                )
            )
        }
        return files.distinctBy { it.downloadLink }
    }

    private fun looksLikeDownloadTrigger(element: Element): Boolean {
        val fingerprint = listOf(
            element.text(),
            element.attr("title"),
            element.attr("aria-label"),
            element.attr("id"),
            element.attr("name"),
            element.className(),
            element.attr("href"),
            element.attr("onclick")
        ).joinToString(" ").lowercase()
        return fingerprint.contains("download") ||
            fingerprint.contains("attachment") ||
            fingerprint.contains("instruction") ||
            fingerprint.contains("file")
    }

    private fun findDownloadCandidateInHtml(html: String, pageUrl: String): HtmlDownloadCandidate? {
        val doc = Jsoup.parse(html, pageUrl)

        // Explicit handling for AssignmentFiles.aspx where files are listed in a table.
        val assignmentFilesLinks = doc
            .select("table tr")
            .asSequence()
            .filter { row ->
                val rowText = row.text().lowercase()
                rowText.contains("download") && !row.select("th").isNotEmpty()
            }
            .flatMap { row ->
                row.select("a[href], a[onclick], button[onclick], input[onclick], span[onclick]").asSequence()
            }
            .toList()
        for (element in assignmentFilesLinks) {
            val candidate = extractCandidateFromClickable(element, pageUrl)
            if (candidate != null) return candidate
        }

        val refreshContent = doc.selectFirst("meta[http-equiv~=(?i)refresh]")?.attr("content")
        val refreshUrl = refreshContent
            ?.let { Regex("url\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
            ?.trim()
            ?.trim('"', '\'')
        if (!refreshUrl.isNullOrBlank()) {
            val normalizedRefresh = normalizeUrl(refreshUrl, pageUrl)
            if (normalizedRefresh.isNotBlank()) {
                return HtmlDownloadCandidate(url = normalizedRefresh)
            }
        }

        val embeddedFileUrl = doc.select("iframe[src], frame[src], embed[src], object[data]")
            .firstNotNullOfOrNull { element ->
                val raw = when {
                    element.hasAttr("src") -> element.attr("src")
                    else -> element.attr("data")
                }
                normalizeUrl(raw, pageUrl).takeIf { it.isNotBlank() }
            }
        if (embeddedFileUrl != null) {
            return HtmlDownloadCandidate(url = embeddedFileUrl)
        }

        val clickableElements = doc.select("a[href], a[onclick], button[onclick], input[onclick], span[onclick]")
        val prioritized = clickableElements.sortedByDescending { if (looksLikeDownloadTrigger(it)) 1 else 0 }

        for (element in prioritized) {
            if (looksLikeDownloadTrigger(element)) {
                val candidate = extractCandidateFromClickable(element, pageUrl)
                if (candidate != null) {
                    return candidate
                }
            }
        }

        for (element in prioritized) {
            val candidate = extractCandidateFromClickable(element, pageUrl)
            if (candidate != null) {
                return candidate
            }
        }

        return null
    }

    private fun executeDownloadRequest(request: Request, depth: Int = 0): DownloadResult {
        if (depth > 6) {
            return DownloadResult.Rejected("Download redirect chain is too long.")
        }

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return DownloadResult.Rejected("Server rejected download (HTTP ${response.code}).")
            }

            val responseBody = response.body ?: return DownloadResult.Error("Empty server response.")
            val mimeType = responseBody.contentType()?.toString()?.ifBlank { null }
            val contentDisposition = response.header("Content-Disposition")
            val hasAttachmentHeader = contentDisposition?.contains("attachment", true) == true ||
                contentDisposition?.contains("filename", true) == true
            val bytes = responseBody.bytes()
            val finalUrl = response.request.url.toString()

            if (finalUrl.contains("Login.aspx", true)) {
                return DownloadResult.Rejected("Session expired. Please sign in again.")
            }

            val isHtmlLike = (mimeType?.contains("text/html", true) == true || looksLikeHtmlPayload(bytes)) && !hasAttachmentHeader
            if (isHtmlLike) {
                val html = bytes.toString(Charsets.UTF_8)
                if (isLoginPage(finalUrl, html)) {
                    return DownloadResult.Rejected("Session expired. Please sign in again.")
                }

                val redirectUrl = extractRedirectUrlFromHtml(html)
                if (!redirectUrl.isNullOrBlank()) {
                    val normalizedRedirect = normalizeUrl(redirectUrl, finalUrl)
                    if (normalizedRedirect.isNotBlank() && !normalizedRedirect.equals(finalUrl, true)) {
                        val followRequest = buildDownloadFollowRequest(normalizedRedirect, finalUrl)
                        return executeDownloadRequest(followRequest, depth + 1)
                    }
                }

                val candidate = findDownloadCandidateInHtml(html, finalUrl)
                if (candidate != null) {
                    if (!candidate.url.isNullOrBlank() && !candidate.url.equals(finalUrl, true)) {
                        val candidateRequest = buildDownloadFollowRequest(candidate.url, finalUrl)
                        return executeDownloadRequest(candidateRequest, depth + 1)
                    }
                    if (candidate.postBackInfo != null) {
                        val postBackRequest = buildPostBackRequestFromPage(finalUrl, html, candidate.postBackInfo)
                        if (postBackRequest != null) {
                            return executeDownloadRequest(postBackRequest, depth + 1)
                        }
                    }
                }

                if (html.contains("__VIEWSTATE", true) || html.contains("CoursePortal", true)) {
                    return DownloadResult.Rejected("Portal did not return the assignment instruction file.")
                }
            }

            val fileName = extractFileName(
                contentDisposition = contentDisposition,
                finalUrl = finalUrl,
                mimeType = mimeType
            )

            return DownloadResult.Success(
                bytes = bytes,
                fileName = fileName,
                mimeType = mimeType ?: "application/octet-stream"
            )
        }
    }

    private fun downloadAssignmentViaPostBack(info: PostBackInfo): DownloadResult {
        val assignmentsUrl = "$baseUrl/CoursePortal.aspx"
        val getRequest = Request.Builder()
            .url(assignmentsUrl)
            .header("Referer", assignmentsUrl)
            .header("User-Agent", userAgent)
            .build()

        val payload = client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return DownloadResult.Rejected("Server rejected download (HTTP ${response.code}).")
            }
            val body = response.body?.string() ?: return DownloadResult.Error("Empty server response.")
            response.request.url.toString() to body
        }

        val finalUrl = payload.first
        val html = payload.second
        val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
        if (notAuthenticated) {
            return DownloadResult.Rejected("Session expired. Please sign in again.")
        }

        val doc = Jsoup.parse(html)
        val form = doc.select("form").firstOrNull { it.selectFirst("input[name=__VIEWSTATE]") != null }
            ?: doc.selectFirst("form")
            ?: return DownloadResult.Error("Portal form not found.")
        val postBuilder = FormBody.Builder()

        form.select("input[type=hidden]").forEach { hidden ->
            val name = hidden.attr("name")
            if (name.isBlank() || name == "__EVENTTARGET" || name == "__EVENTARGUMENT") return@forEach
            postBuilder.add(name, hidden.attr("value"))
        }
        postBuilder.add("__EVENTTARGET", info.target)
        postBuilder.add("__EVENTARGUMENT", info.argument)

        val formAction = form.attr("action")
        val postUrl = when {
            formAction.isBlank() -> assignmentsUrl
            formAction.startsWith("http", true) -> formAction
            formAction.startsWith("/") -> "$baseUrl$formAction"
            else -> "$baseUrl/$formAction"
        }

        val postRequest = Request.Builder()
            .url(postUrl)
            .post(postBuilder.build())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", assignmentsUrl)
            .header("Origin", baseUrl)
            .header("User-Agent", userAgent)
            .build()

        return executeDownloadRequest(postRequest)
    }

    private fun downloadAssignmentViaPostBack(postBackLink: PostBackLink): DownloadResult {
        val sourcePageUrl = postBackLink.sourcePageUrl ?: "$baseUrl/CoursePortal.aspx"
        val getRequest = Request.Builder()
            .url(sourcePageUrl)
            .header("Referer", sourcePageUrl)
            .header("User-Agent", userAgent)
            .build()

        val payload = client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return DownloadResult.Rejected("Server rejected download (HTTP ${response.code}).")
            }
            val body = response.body?.string() ?: return DownloadResult.Error("Empty server response.")
            response.request.url.toString() to body
        }

        val finalUrl = payload.first
        val html = payload.second
        val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
        if (notAuthenticated) {
            return DownloadResult.Rejected("Session expired. Please sign in again.")
        }

        val postRequest = buildPostBackRequestFromPage(finalUrl, html, postBackLink.info)
            ?: return DownloadResult.Error("Portal form not found.")

        return executeDownloadRequest(postRequest)
    }

    private fun uploadAssignmentViaPostBack(postBackLink: PostBackLink, file: File): UploadResult {
        val sourcePageUrl = postBackLink.sourcePageUrl ?: "$baseUrl/CoursePortal.aspx"
        val getRequest = Request.Builder()
            .url(sourcePageUrl)
            .header("Referer", sourcePageUrl)
            .header("User-Agent", userAgent)
            .build()

        val payload = client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return UploadResult.Rejected("Server rejected request (HTTP ${response.code}).")
            }
            val body = response.body?.string() ?: return UploadResult.Error("Empty server response.")
            response.request.url.toString() to body
        }

        val finalUrl = payload.first
        val html = payload.second
        val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
        if (notAuthenticated) {
            return UploadResult.Rejected("Session expired. Please sign in again.")
        }

        val postRequest = buildPostBackRequestFromPage(finalUrl, html, postBackLink.info)
            ?: return UploadResult.Rejected("Upload form not found.")

        val pagePayload = client.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return UploadResult.Rejected("Server rejected request (HTTP ${response.code}).")
            }
            val body = response.body?.string() ?: return UploadResult.Error("Empty server response.")
            response.request.url.toString() to body
        }

        val uploadPageUrl = pagePayload.first
        val uploadPageHtml = pagePayload.second
        val uploadDoc = Jsoup.parse(uploadPageHtml, uploadPageUrl)
        val uploadForm = uploadDoc.select("form").firstOrNull { it.selectFirst("input[type=file]") != null }
            ?: uploadDoc.select("form").firstOrNull { it.selectFirst("input[name=__VIEWSTATE]") != null }
            ?: uploadDoc.selectFirst("form")
            ?: return UploadResult.Rejected("Upload form not found.")

        return uploadWithForm(uploadForm, file, uploadPageHtml)
    }

    fun fetchInstructionFiles(downloadUrl: String): InstructionFilesResult {
        return try {
            if (downloadUrl.isBlank()) {
                return InstructionFilesResult.Rejected("Download link is unavailable.")
            }

            if (isPostBackDownloadLink(downloadUrl)) {
                val postBackLink = extractPostBackLinkFromLink(downloadUrl)
                    ?: return InstructionFilesResult.Rejected("Download link is invalid.")
                val sourcePageUrl = postBackLink.sourcePageUrl ?: "$baseUrl/CoursePortal.aspx"
                return InstructionFilesResult.Success(
                    listOf(
                        InstructionFile(
                            fileName = "instruction_file",
                            downloadLink = toPostBackDownloadLink(postBackLink.info, sourcePageUrl)
                        )
                    )
                )
            }

            val request = Request.Builder()
                .url(normalizeUrl(downloadUrl).ifBlank { return InstructionFilesResult.Rejected("Download link is invalid.") })
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()

            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return InstructionFilesResult.Rejected("Server rejected file list (HTTP ${response.code}).")
                }
                val body = response.body?.string() ?: return InstructionFilesResult.Error("Empty server response.")
                response.request.url.toString() to body
            }

            val finalUrl = payload.first
            val html = payload.second
            if (isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)) {
                return InstructionFilesResult.Rejected("Session expired. Please sign in again.")
            }

            val files = parseInstructionFilesFromHtml(html, finalUrl)
            if (files.isEmpty()) {
                return InstructionFilesResult.Rejected("No instruction files found for this assignment.")
            }
            InstructionFilesResult.Success(files)
        } catch (e: IOException) {
            InstructionFilesResult.NetworkError
        } catch (e: Exception) {
            Log.e("PortalAuth", "fetchInstructionFiles error: ${e.message}", e)
            InstructionFilesResult.Error(e.message ?: "Failed to load instruction files.")
        }
    }

    fun downloadAssignment(downloadUrl: String): DownloadResult {
        return try {
            if (downloadUrl.isBlank()) {
                return DownloadResult.Rejected("Download link is unavailable.")
            }

            if (isPostBackDownloadLink(downloadUrl)) {
                val postBackLink = extractPostBackLinkFromLink(downloadUrl)
                if (postBackLink == null) {
                    return DownloadResult.Rejected("Download link is invalid.")
                }
                return downloadAssignmentViaPostBack(postBackLink)
            }

            val normalizedUrl = normalizeUrl(downloadUrl)
            if (normalizedUrl.isBlank()) {
                return DownloadResult.Rejected("Download link is invalid.")
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()

            executeDownloadRequest(request)
        } catch (e: IOException) {
            DownloadResult.NetworkError
        } catch (e: Exception) {
            Log.e("PortalAuth", "Download error: ${e.message}", e)
            DownloadResult.Error(e.message ?: "Download failed.")
        }
    }

    fun fetchTimetable(): List<TimetableLecture> {
        return try {
            val timetableUrl = "$baseUrl/Timetable.aspx"
            Log.d("PortalAuth", "Fetching timetable from: $timetableUrl")

            val request = Request.Builder()
                .url(timetableUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()

            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchTimetable HTTP ${response.code}")
                }
                val body = response.body?.string() ?: run {
                    throw PortalSystemException("fetchTimetable empty server response")
                }
                detectPortalSystemErrors(body)
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second

            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) {
                throw PortalSystemException("Not authenticated for timetable. Please log in again.")
            }

            val lectures = parseTimetableFromHtml(html)
            Log.d("PortalAuth", "Fetched ${lectures.size} timetable lectures")
            lectures
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching timetable: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    private fun normalizeTime(time: String): String {
        val trimmed = time.trim()
        if (trimmed.isBlank()) return ""

        // Handle cases like "08:30-10:00" passed accidentally or "08:30 AM"
        val cleanTime = trimmed.split("-", " ").first()
        val parts = cleanTime.split(":")
        val hourRaw = parts.getOrNull(0)?.toIntOrNull() ?: return trimmed
        val minuteRaw = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0

        val hasAm = trimmed.contains("AM", ignoreCase = true)
        val hasPm = trimmed.contains("PM", ignoreCase = true)

        val (finalHour, suffix) = when {
            hasAm -> {
                val h = if (hourRaw == 12) 0 else hourRaw
                h to "AM"
            }
            hasPm -> {
                val h = if (hourRaw == 12) 12 else hourRaw
                h to "PM"
            }
            else -> {
                // Heuristic for 24h or missing suffix
                // If hour is 8-11, assume AM unless it's explicitly PM.
                // If hour is 1-7, assume PM if it's a typical afternoon class time.
                // However, most portals use 24h if no suffix.
                when {
                    hourRaw >= 12 -> {
                        val h = if (hourRaw > 12) hourRaw - 12 else 12
                        h to "PM"
                    }
                    hourRaw in 1..7 -> hourRaw to "PM" // Typical afternoon
                    else -> hourRaw to "AM"
                }
            }
        }

        val displayHour = if (finalHour == 0) "12" else String.format(Locale.US, "%02d", finalHour)
        val displayMinute = String.format(Locale.US, "%02d", minuteRaw)
        return "$displayHour:$displayMinute $suffix"
    }

    private fun parseTimetableFromHtml(html: String): List<TimetableLecture> {
        val doc = Jsoup.parse(html)
        val lectures = mutableListOf<TimetableLecture>()

        val tables = doc.select("table.Grid, table[id*='gvTimeTable']")
        var targetTable: org.jsoup.nodes.Element? = null
        for (t in tables) {
            val text = t.text()
            if (!text.contains("Father Name", ignoreCase = true) &&
                !text.contains("Roll No", ignoreCase = true) &&
                !text.contains("Student Information", ignoreCase = true) &&
                !text.contains("Demographics", ignoreCase = true)
            ) {
                targetTable = t
                break
            }
        }

        if (targetTable == null && tables.isEmpty()) {
            targetTable = doc.select("table").firstOrNull()
        }

        if (targetTable == null) {
            throw PortalSystemException("No timetable table found.")
        }

        val rows = targetTable.select("tr")
        if (rows.isEmpty()) return emptyList()

        val timeHeaders = mutableListOf<String>()
        val headerRow = rows.first()
        val headerCells = headerRow?.select("th, td") ?: emptyList()
        for (cell in headerCells) {
            timeHeaders.add(cell.text().trim())
        }

        val firstHeader = timeHeaders.firstOrNull()?.lowercase() ?: ""
        
        // Detect List Layout
        val isListLayout = timeHeaders.any {
            it.lowercase().contains("subject") || it.lowercase().contains("course") || 
            it.lowercase().contains("teacher") || it.lowercase().contains("instructor")
        }
        
        if (isListLayout) {
            val dayIdx = timeHeaders.indexOfFirst { it.lowercase().contains("day") }
            val timeIdx = timeHeaders.indexOfFirst { it.lowercase().contains("time") }
            val subjectIdx = timeHeaders.indexOfFirst { 
                it.lowercase().contains("subject") || it.lowercase().contains("course") || it.lowercase().contains("class") 
            }
            val roomIdx = timeHeaders.indexOfFirst { it.lowercase().contains("room") || it.lowercase().contains("class room") }
            val teacherIdx = timeHeaders.indexOfFirst { it.lowercase().contains("teacher") || it.lowercase().contains("instructor") }

            for (i in 1 until rows.size) {
                val row = rows[i]
                val cells = row.select("td, th")
                if (cells.size < 3) continue

                val dayVal = if (dayIdx != -1) cells.getOrNull(dayIdx)?.text()?.trim().orEmpty() else ""
                val timeVal = if (timeIdx != -1) cells.getOrNull(timeIdx)?.text()?.trim().orEmpty() else ""
                val subjectVal = if (subjectIdx != -1) cells.getOrNull(subjectIdx)?.text()?.trim().orEmpty() else ""
                val roomVal = if (roomIdx != -1) cells.getOrNull(roomIdx)?.text()?.trim().orEmpty() else ""
                val teacherVal = if (teacherIdx != -1) cells.getOrNull(teacherIdx)?.text()?.trim().orEmpty() else ""

                if (subjectVal.isBlank()) continue

                val times = timeVal.split("-", " to ", " - ").map { it.trim() }
                val start = normalizeTime(times.getOrNull(0).orEmpty())
                val end = normalizeTime(times.getOrNull(1).orEmpty())

                val courseName = subjectVal.substringBeforeLast("(").trim()
                val courseCode = subjectVal.substringAfterLast("(", "").removeSuffix(")").trim()

                lectures.add(
                    TimetableLecture(
                        courseName = if (courseName.isBlank()) subjectVal else courseName,
                        courseCode = courseCode,
                        instructor = teacherVal,
                        room = roomVal,
                        day = dayVal,
                        startTime = start,
                        endTime = end,
                        duration = "",
                        creditHours = "",
                        sessionType = if (subjectVal.contains("Lab", ignoreCase = true)) "Lab" else "Lecture"
                    )
                )
            }
            return lectures
        }

        // Detect Days-as-Columns Matrix Layout
        val isDaysAsColumns = firstHeader.contains("time")
        if (isDaysAsColumns) {
            for (i in 1 until rows.size) {
                val row = rows[i]
                val cells = row.select("td, th")
                if (cells.isEmpty()) continue

                val timeRange = cells.first()?.text()?.trim() ?: continue
                if (timeRange.isBlank() || timeRange.lowercase() == "time") continue

                val startTimeStr = normalizeTime(extractStartTime(timeRange))
                val endTimeStr = normalizeTime(extractEndTime(timeRange))

                for (colIdx in 1 until cells.size) {
                    val cell = cells[colIdx]
                    val cellText = cell.text().trim()
                    val dayName = timeHeaders.getOrNull(colIdx) ?: continue

                    if (cellText.isNotBlank() && cellText != "*" && cellText != "—" && cellText != "-" && !cellText.matches(Regex("^-+$")) && !cellText.contains("Lunch", ignoreCase = true)) {
                        val (courseName, room, instructor, sessionType) = extractTimetableLectureData(cell)

                        lectures.add(
                            TimetableLecture(
                                courseName = courseName,
                                courseCode = "",
                                instructor = instructor,
                                room = room,
                                day = dayName,
                                startTime = startTimeStr,
                                endTime = endTimeStr,
                                duration = "",
                                creditHours = "",
                                sessionType = sessionType
                            )
                        )
                    }
                }
            }
            return lectures
        }

        // Layout A: Days as Rows (Default)
        for (i in 1 until rows.size) {
            val row = rows[i]
            val cells = row.select("td, th")
            if (cells.isEmpty()) continue

            val day = cells.first()?.text()?.trim() ?: continue
            if (day.isBlank() || day.lowercase() == "day") continue

            var slotIndex = 1
            var cellIndex = 1

            while (cellIndex < cells.size && slotIndex < timeHeaders.size) {
                val cell = cells[cellIndex]
                val colspan = cell.attr("colspan").toIntOrNull() ?: 1
                val cellText = cell.text().trim()

                if (cellText.isNotBlank() && cellText != "*" && cellText != "—" && cellText != "-" && !cellText.matches(Regex("^-+$")) && !cellText.contains("Lunch", ignoreCase = true)) {
                    val startTimeStr = normalizeTime(extractStartTime(timeHeaders.getOrNull(slotIndex)))
                    val endTimeStr = normalizeTime(extractEndTime(timeHeaders.getOrNull(slotIndex + colspan - 1)))

                    val (courseName, room, instructor, sessionType) = extractTimetableLectureData(cell)

                    lectures.add(
                        TimetableLecture(
                            courseName = courseName,
                            courseCode = "",
                            instructor = instructor,
                            room = room,
                            day = day,
                            startTime = startTimeStr,
                            endTime = endTimeStr,
                            duration = "",
                            creditHours = "",
                            sessionType = sessionType
                        )
                    )
                }
                slotIndex += colspan
                cellIndex++
            }
        }
        return lectures
    }

    private fun extractStartTime(timeRange: String?): String {
        if (timeRange.isNullOrBlank()) return ""
        val parts = timeRange.split(" to ", "-", " - ")
        return parts.getOrNull(0)?.trim() ?: timeRange
    }

    private fun extractEndTime(timeRange: String?): String {
        if (timeRange.isNullOrBlank()) return ""
        val parts = timeRange.split(" to ", "-", " - ")
        return parts.getOrNull(1)?.trim() ?: extractStartTime(timeRange)
    }

    private fun normalizeUrl(href: String?, resolveBaseUrl: String = baseUrl): String {
        if (href.isNullOrBlank()) return ""
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed == "#" || trimmed.startsWith("javascript", true)) return ""
        if (trimmed.startsWith("http", true)) return trimmed
        return runCatching {
            resolveBaseUrl.toHttpUrl().resolve(trimmed)?.toString().orEmpty()
        }.getOrDefault("")
    }

    private data class ParsedLecture(
        val courseName: String,
        val room: String,
        val instructor: String,
        val sessionType: String
    )

    private fun extractTimetableLectureData(cell: org.jsoup.nodes.Element): ParsedLecture {
        val cellText = cell.text().trim()
        val htmlParts = cell.html()
            .split(Regex("(?i)<br\\s*/?>"))
            .map { org.jsoup.Jsoup.parse(it).text().trim() }
            .filter { it.isNotEmpty() }
            
        var courseName = cellText
        var room = "Unknown"
        var instructor = "Unknown"
        
        if (htmlParts.size >= 2) {
            val roomIndex = htmlParts.indexOfFirst { 
                it.lowercase().contains("room") || 
                it.matches(Regex(".*\\[[^\\]]+\\].*")) || 
                it.matches(Regex(".*\\b(A|B|C|D|S|L|Lab|Auditorium|Hall|CR|Lab)-\\d+.*", RegexOption.IGNORE_CASE))
            }
            
            if (roomIndex != -1) {
                courseName = htmlParts.subList(0, roomIndex).joinToString(" ")
                
                val roomPart = htmlParts[roomIndex]
                room = roomPart.replace(Regex("(?i)\\broom\\b"), "").trim()
                room = room.replace(Regex("\\[[^\\]]+\\]"), "").trim()
                val parensMatch = Regex("\\(([^)]+)\\)").find(room)
                if (parensMatch != null) {
                    val inside = parensMatch.groupValues[1].trim()
                    if (inside.matches(Regex("\\d+M?", RegexOption.IGNORE_CASE))) {
                        room = room.replace(parensMatch.value, "").trim()
                    } else {
                        room = inside
                    }
                } else {
                    room = roomPart
                }
                
                for (i in (roomIndex + 1) until htmlParts.size) {
                    val candidate = htmlParts[i]
                        .replace(Regex("\\[.*?\\]"), "")
                        .replace(Regex("\\(.*?\\)"), "")
                        .replace(Regex("\\b\\d+\\b"), "")
                        .trim()
                    if (candidate.length >= 2 && candidate.any { it.isLetter() }) {
                        instructor = candidate
                        break
                    }
                }
            } else {
                courseName = htmlParts[0]
                if (htmlParts.size >= 3) {
                    var roomPart = htmlParts[1]
                    instructor = "Unknown"
                    for (i in 2 until htmlParts.size) {
                        val candidate = htmlParts[i]
                            .replace(Regex("\\[.*?\\]"), "")
                            .replace(Regex("\\(.*?\\)"), "")
                            .replace(Regex("\\b\\d+\\b"), "")
                            .trim()
                        if (candidate.length >= 2 && candidate.any { it.isLetter() }) {
                            instructor = candidate
                            break
                        }
                    }
                    roomPart = roomPart.replace(Regex("\\[[^\\]]+\\]"), "").trim()
                    val parensMatch = Regex("\\(([^)]+)\\)").find(roomPart)
                    if (parensMatch != null) {
                        val inside = parensMatch.groupValues[1].trim()
                        if (inside.matches(Regex("\\d+M?", RegexOption.IGNORE_CASE))) {
                            room = roomPart.replace(parensMatch.value, "").trim()
                        } else {
                            room = inside
                        }
                    } else {
                        room = roomPart
                    }
                } else {
                    room = htmlParts.getOrNull(1) ?: "Unknown"
                }
            }
        } else {
            val text = cellText.replace(Regex("(?:\\s+\\d+)+$"), "").trim()
            val formatB = Regex("^(.*?)\\s*\\(([^)]+)\\)\\s*\\[[^\\]]+\\]\\s*(.*)$")
            val matchB = formatB.find(text)
            if (matchB != null) {
                courseName = matchB.groupValues[1].trim()
                room = matchB.groupValues[2].trim()
                instructor = matchB.groupValues[3].trim()
            } else {
                val formatA = Regex("^(.*?)\\s+([A-Za-z0-9\\-]+)\\s*\\([^)]+\\)\\s*(.*)$")
                val matchA = formatA.find(text)
                if (matchA != null) {
                    courseName = matchA.groupValues[1].trim()
                    room = matchA.groupValues[2].trim()
                    instructor = matchA.groupValues[3].trim()
                }
            }
        }
        
        var sessionType = "Lecture"
        if (courseName.contains("Lab", ignoreCase = true) || cellText.contains("Lab", ignoreCase = true)) {
            sessionType = "Lab"
        }
        
        val cleanCourseName = courseName
            .replace(Regex("(?i)\\b(theory|lab|computer)\\b"), "")
            .replace(Regex("\\b\\d{2}\\b$"), "")
            .trim()
            
        return ParsedLecture(cleanCourseName, room, instructor, sessionType)
    }

    private fun populateCourseCodesMap() {
        if (courseTitleToCreditMap.isNotEmpty()) return
        try {
            val resultCardUrl = "$baseUrl/StudentResultCard.aspx"
            val getRequest = Request.Builder()
                .url(resultCardUrl)
                .header("User-Agent", userAgent)
                .build()
            val resultHtml = client.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (!resultHtml.isNullOrBlank()) {
                val doc = org.jsoup.Jsoup.parse(resultHtml)
                val semesterRegex = Regex("(Spring|Fall|Summer)\\s*\\d{4}", RegexOption.IGNORE_CASE)
                val semestersFound = mutableListOf<String>()
                
                for (table in doc.select("table")) {
                    val tableText = table.text()
                    val match = semesterRegex.find(tableText)
                    if (match != null && (tableText.contains("Result Semester", ignoreCase = true) || tableText.contains("Semester Result", ignoreCase = true))) {
                        semestersFound.add(match.value)
                    }
                    for (row in table.select("tr")) {
                        val cells = row.select("td")
                        if (cells.size >= 2) {
                            val codeVal = cells[0].text().trim()
                            val nameVal = cells[1].text().trim()
                            if (codeVal.matches("^[A-Z]{2,4}\\s*-?\\d{2,4}$".toRegex()) && nameVal.isNotEmpty()) {
                                val cleanTitle = nameVal.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                                courseTitleToCodeMap[cleanTitle] = codeVal
                                
                                val creditVal = cells.getOrNull(2)?.text()?.trim().orEmpty()
                                if (creditVal.isNotEmpty()) {
                                    courseTitleToCreditMap[cleanTitle] = creditVal
                                }
                            }
                        }
                    }
                }
                
                if (semestersFound.isNotEmpty()) {
                    latestSemesterName = semestersFound.maxWithOrNull { s1, s2 ->
                        val y1 = s1.split(" ").lastOrNull()?.toIntOrNull() ?: 0
                        val y2 = s2.split(" ").lastOrNull()?.toIntOrNull() ?: 0
                        if (y1 != y2) {
                            y1.compareTo(y2)
                        } else {
                            val t1 = s1.split(" ").firstOrNull()?.lowercase() ?: ""
                            val t2 = s2.split(" ").firstOrNull()?.lowercase() ?: ""
                            val order = listOf("spring", "summer", "fall")
                            val o1 = order.indexOf(t1)
                            val o2 = order.indexOf(t2)
                            o1.compareTo(o2)
                        }
                    } ?: ""
                    Log.d("PortalAuth", "Determined latest semester from ResultCard: $latestSemesterName")
                }
            }
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching/parsing StudentResultCard.aspx for course code resolution: ${e.message}", e)
        }
    }

    fun fetchAttendanceSummary(resolvedCodes: Map<String, String>? = null): List<AttendanceSummary> {
        if (resolvedCodes != null) {
            courseTitleToCodeMap.putAll(resolvedCodes)
        }
        if (courseTitleToCodeMap.isEmpty()) {
            populateCourseCodesMap()
        }
        return try {
            val summaryUrl = "$baseUrl/Summary.aspx"
            val request = Request.Builder()
                .url(summaryUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchAttendanceSummary HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw PortalSystemException("fetchAttendanceSummary empty response")
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second
            detectPortalSystemErrors(html)
            if (isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)) {
                throw PortalSystemException("Session expired")
            }
            
            val doc = Jsoup.parse(html)
            val list = mutableListOf<AttendanceSummary>()
            
            for (table in doc.select("table")) {
                val tt = table.text().lowercase()
                if (tt.contains("father name") && tt.contains("roll no")) continue
                if (tt.contains("cnic") && tt.contains("date of birth")) continue
                val rows = table.select("tr")
                if (rows.size < 2) continue

                var hdr: org.jsoup.nodes.Element? = null
                var hdrIdx = -1
                for (i in 0 until rows.size) {
                    if (rows[i].select("th, td").size > 1) {
                        hdr = rows[i]
                        hdrIdx = i
                        break
                    }
                }
                if (hdr == null) continue

                val ths = hdr.select("th, td")
                val headers = ths.map { it.text().trim().lowercase() }

                var codeIdx = -1
                var titleIdx = -1
                var classIdx = -1
                var facIdx = -1
                var totIdx = -1
                var presIdx = -1
                var absIdx = -1
                var thyIdx = -1
                var labIdx = -1
                var pctIdx = -1

                for (i in headers.indices) {
                    val h = headers[i]
                    if (h.contains("code")) codeIdx = i
                    else if (h.contains("title") || h.contains("subject")) titleIdx = i
                    else if (h == "class") classIdx = i
                    else if (h.contains("faculty") || h.contains("teacher") || h.contains("member")) facIdx = i
                    else if (h.contains("lectures") || h.contains("total")) totIdx = i
                    else if (h == "p" || h.contains("present")) presIdx = i
                    else if (h == "a" || h.contains("absent")) absIdx = i
                    else if (h.contains("thy%") || h == "thy") thyIdx = i
                    else if (h.contains("lab%") || h == "lab") labIdx = i
                    else if (h.contains("percentage") || h.contains("%")) pctIdx = i
                }

                if (codeIdx == -1 && titleIdx == -1) {
                    titleIdx = 1; classIdx = 2; facIdx = 3; totIdx = 4; presIdx = 5; absIdx = 6; thyIdx = 7; labIdx = 8
                }

                for (r in (hdrIdx + 1) until rows.size) {
                    val cells = rows[r].select("td")
                    if (cells.size <= Math.max(codeIdx, titleIdx)) continue

                    var code = if (codeIdx >= 0 && codeIdx < cells.size) cells[codeIdx].text().trim() else ""
                    var title = if (titleIdx >= 0 && titleIdx < cells.size) cells[titleIdx].text().trim() else ""
                    val totalClassesStr = if (totIdx >= 0 && totIdx < cells.size) cells[totIdx].text().trim() else "0"
                    val presentsStr = if (presIdx >= 0 && presIdx < cells.size) cells[presIdx].text().trim() else "0"
                    val absentsStr = if (absIdx >= 0 && absIdx < cells.size) cells[absIdx].text().trim() else "0"
                    
                    val thyPercentageStr = if (thyIdx >= 0 && thyIdx < cells.size) cells[thyIdx].text().trim() else "N/A"
                    val labPercentageStr = if (labIdx >= 0 && labIdx < cells.size) cells[labIdx].text().trim() else "N/A"
                    val hasTheory = thyPercentageStr != "N/A" && thyPercentageStr.isNotBlank()
                    val hasLab = labPercentageStr != "N/A" && labPercentageStr.isNotBlank()
                    
                    var percentageVal = 0.0
                    if (hasTheory && hasLab) {
                        val thyVal = thyPercentageStr.replace("%", "").trim().toDoubleOrNull() ?: 0.0
                        val labVal = labPercentageStr.replace("%", "").trim().toDoubleOrNull() ?: 0.0
                        percentageVal = (thyVal + labVal) / 2.0
                    } else if (pctIdx >= 0 && pctIdx < cells.size) {
                        percentageVal = cells[pctIdx].text().replace("%", "").trim().toDoubleOrNull() ?: 0.0
                    } else if (hasTheory) {
                        percentageVal = thyPercentageStr.replace("%", "").trim().toDoubleOrNull() ?: 0.0
                    } else if (hasLab) {
                        percentageVal = labPercentageStr.replace("%", "").trim().toDoubleOrNull() ?: 0.0
                    }

                    if (code.isEmpty() && title.isNotEmpty() && title.contains("\n")) {
                        val parts = title.split("\n")
                        code = parts[0].trim()
                        title = parts[1].trim()
                    } else if (code.isEmpty() && title.isNotEmpty()) {
                        val m = java.util.regex.Pattern.compile("^([A-Za-z]{2,4}-?\\d{3})[\\s\\-•]*(.*)$").matcher(title)
                        if (m.find()) {
                            code = m.group(1)?.trim() ?: ""
                            title = m.group(2)?.trim() ?: ""
                        }
                    }

                    if (code.isEmpty() && title.isNotEmpty()) {
                        val cleanTitle = title.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                        for ((mapTitle, mapCode) in courseTitleToCodeMap) {
                            if (mapTitle.contains(cleanTitle) || cleanTitle.contains(mapTitle)) {
                                code = mapCode
                                Log.d("PortalAuth", "Resolved empty attendance code for '$title' to '$code'")
                                break
                            }
                        }
                    }

                    if (title.isEmpty() && code.isEmpty()) continue

                    var postbackTarget = ""
                    var aTag = if (titleIdx >= 0 && titleIdx < cells.size) cells[titleIdx].select("a").firstOrNull() else null
                    if (aTag == null && codeIdx >= 0 && codeIdx < cells.size) aTag = cells[codeIdx].select("a").firstOrNull()
                    if (aTag != null) {
                        val href = aTag.attr("href")
                        if (href.contains("__doPostBack")) {
                            val start = href.indexOf("'") + 1
                            val end = href.indexOf("'", start)
                            if (start > 0 && end > start) {
                                postbackTarget = href.substring(start, end)
                            }
                        }
                    }

                    val cleanCode = code.trim().uppercase()
                    val cleanTitle = title.trim().uppercase()
                    if (cleanCode.isNotEmpty() && postbackTarget.isNotEmpty()) {
                        coursePostbackTargets[cleanCode] = postbackTarget
                    }
                    if (cleanTitle.isNotEmpty() && postbackTarget.isNotEmpty()) {
                        coursePostbackTargets[cleanTitle] = postbackTarget
                    }

                    val totalLectures = totalClassesStr.toIntOrNull() ?: 0
                    val presents = presentsStr.toIntOrNull() ?: 0
                    val absents = absentsStr.toIntOrNull() ?: 0

                    list.add(
                        AttendanceSummary(
                            courseCode = code,
                            courseName = title,
                            totalLectures = totalLectures,
                            present = presents,
                            absent = absents,
                            leaves = 0,
                            percentage = percentageVal
                        )
                    )
                }
            }
            list.distinctBy { "${it.courseCode.trim().uppercase()}_${it.courseName.trim().uppercase()}" }
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching attendance summary: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    fun fetchAttendanceDetail(courseCode: String): List<AttendanceDetail> {
        val cleanCode = courseCode.trim().uppercase()
        var postbackTarget = coursePostbackTargets[cleanCode]
        
        if (postbackTarget.isNullOrEmpty()) {
            fetchAttendanceSummary()
            postbackTarget = coursePostbackTargets[cleanCode]
        }
        
        if (postbackTarget.isNullOrEmpty()) {
            Log.d("PortalAuth", "No postback target found for course $courseCode")
            return emptyList()
        }
        
        return try {
            val summaryUrl = "$baseUrl/Summary.aspx"
            val procPageUrl = "$baseUrl/classproceedings.aspx"
            
            val getRequest = Request.Builder()
                .url(summaryUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchAttendanceDetail GET Summary.aspx failed HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw PortalSystemException("fetchAttendanceDetail empty response")
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val summaryHtml = payload.second
            detectPortalSystemErrors(summaryHtml)
            if (isLoginPage(finalUrl, summaryHtml) || !hasSessionCookiesForHost(baseHost)) {
                throw PortalSystemException("Session expired")
            }
            
            val postBackInfo = PostBackInfo(postbackTarget, "")
            val postRequest = buildPostBackRequestFromPage(summaryUrl, summaryHtml, postBackInfo)
                ?: throw PortalSystemException("Failed to construct postback request for course $courseCode")
                
            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchAttendanceDetail postback failed HTTP ${response.code}")
                }
                response.body?.string()
            }
            
            val procRequest = Request.Builder()
                .url(procPageUrl)
                .header("Referer", summaryUrl)
                .header("User-Agent", userAgent)
                .build()
            val procHtml = client.newCall(procRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchAttendanceDetail GET classproceedings.aspx failed HTTP ${response.code}")
                }
                response.body?.string() ?: throw PortalSystemException("fetchAttendanceDetail classproceedings empty response")
            }
            detectPortalSystemErrors(procHtml)
            
            val doc = Jsoup.parse(procHtml)
            val details = mutableListOf<AttendanceDetail>()
            
            for (table in doc.select("table")) {
                val rows = table.select("tr")
                if (rows.size < 2) continue
                val hdr = rows.firstOrNull() ?: continue
                val hdrText = hdr.text().lowercase()
                if (hdrText.contains("lecture") || hdrText.contains("date") || hdrText.contains("topic") || hdrText.contains("status")) {
                    val ths = hdr.select("th, td")
                    val headers = ths.map { it.text().trim().lowercase() }
                    
                    var dateIdx = -1
                    var topicIdx = -1
                    var statusIdx = -1
                    
                    for (i in headers.indices) {
                        val h = headers[i]
                        if (h.contains("date")) dateIdx = i
                        else if (h.contains("topic") || h.contains("particular") || h.contains("description")) topicIdx = i
                        else if (h.contains("status")) statusIdx = i
                    }
                    
                    for (r in 1 until rows.size) {
                        val cells = rows[r].select("td")
                        if (cells.size <= Math.max(dateIdx, statusIdx)) continue
                        
                        val date = if (dateIdx >= 0 && dateIdx < cells.size) cells[dateIdx].text().trim() else ""
                        val topic = if (topicIdx >= 0 && topicIdx < cells.size) cells[topicIdx].text().trim() else "No Topic Specified"
                        val status = if (statusIdx >= 0 && statusIdx < cells.size) cells[statusIdx].text().trim() else ""
                        
                        if (date.isNotEmpty()) {
                            details.add(
                                AttendanceDetail(
                                    date = date,
                                    status = status,
                                    remarks = topic,
                                    courseCode = courseCode
                                )
                            )
                        }
                    }
                }
            }
            
            details
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching attendance detail: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    fun fetchGrades(): GpaSummary {
        return try {
            val url = "$baseUrl/StudentResultCard.aspx"
            val request = Request.Builder()
                .url(url)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchGrades HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw PortalSystemException("fetchGrades empty response")
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second
            detectPortalSystemErrors(html)
            if (isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)) {
                throw PortalSystemException("Session expired")
            }
            
            val doc = Jsoup.parse(html)
            val resultTables = mutableListOf<SemesterGrades>()
            val allTables = doc.select("table")

            for (i in 0 until allTables.size) {
                val table = allTables[i]
                val rows = table.select("tr")
                if (rows.size < 2) continue

                var titleText = "Semester Result"
                var headerRowIndex = -1

                for (j in 0 until rows.size) {
                    val cells = rows[j].select("th, td")
                    if (cells.size > 3) {
                        headerRowIndex = j
                        break
                    } else if (cells.size == 1) {
                        val possibleTitle = cells.firstOrNull()?.text()?.trim() ?: ""
                        if (possibleTitle.isNotEmpty()) {
                            titleText = possibleTitle
                        }
                    }
                }

                if (headerRowIndex == -1) continue

                if (titleText == "Semester Result") {
                    var prev = table.previousElementSibling()
                    while (prev != null) {
                        val txt = prev.text().trim()
                        if (txt.isNotEmpty()) {
                            titleText = txt
                            break
                        }
                        prev = prev.previousElementSibling()
                    }
                }

                val headerCells = rows[headerRowIndex].select("th, td")
                val headers = headerCells.map { it.text().trim().lowercase() }

                var isTranscript = false
                for (header in headers) {
                    if (header.contains("course") || header.contains("credit") || header.contains("marks") || header.contains("grade") || header == "lg") {
                        isTranscript = true
                        break
                    }
                }
                if (!isTranscript) continue

                val courses = mutableListOf<CourseGrade>()
                
                var codeIdx = -1
                var titleIdx = -1
                var creditIdx = -1
                var marksIdx = -1
                var gradeIdx = -1
                var gpIdx = -1

                for (idx in headers.indices) {
                    val h = headers[idx]
                    if (h.contains("code")) codeIdx = idx
                    else if (h.contains("title") || h.contains("course")) titleIdx = idx
                    else if (h.contains("credit") || h.contains("cr.")) creditIdx = idx
                    else if (h.contains("marks") || h.contains("obt") || h == "marks" || h == "mks") marksIdx = idx
                    else if (h.contains("grade") || h == "lg") gradeIdx = idx
                    else if (h.contains("points") || h == "gp") gpIdx = idx
                }
                
                if (codeIdx == -1) {
                    codeIdx = 0
                }
                if (titleIdx == -1) {
                    titleIdx = if (headers.size >= 6) 1 else 0
                }
                if (creditIdx == -1) {
                    creditIdx = if (headers.size >= 6) 2 else 1
                }
                if (marksIdx == -1) {
                    marksIdx = if (headers.size >= 6) 3 else -1
                }
                if (gradeIdx == -1) {
                    gradeIdx = if (headers.size >= 6) 4 else 2
                }
                if (gpIdx == -1) {
                    gpIdx = if (headers.size >= 6) 5 else 3
                }

                for (r in (headerRowIndex + 1) until rows.size) {
                    val cells = rows[r].select("td, th")
                    val maxIdx = maxOf(codeIdx, titleIdx, creditIdx, gradeIdx, gpIdx, marksIdx)
                    if (cells.size <= maxIdx) continue
                    
                    val rowText = cells.joinToString(" ") { it.text().trim() }.uppercase()
                    if (rowText.contains("SGPA") || rowText.contains("CGPA") || rowText.contains("GPA") || rowText.contains("CREDIT HOURS")) {
                        continue
                    }

                    val code = cells[codeIdx].text().trim()
                    val courseTitle = cells[titleIdx].text().trim()
                    val creditStr = if (creditIdx >= 0) cells[creditIdx].text().trim() else "3"
                    
                    val rawGrade = if (gradeIdx >= 0) cells[gradeIdx].text().trim() else ""
                    val grade = if (rawGrade.lowercase().contains("non credit") || 
                                    rawGrade.lowercase().contains("non-credit") || 
                                    rawGrade.lowercase().contains("noncredit") ||
                                    rawGrade.lowercase().contains("ncr")) "NC" else rawGrade
                                    
                    val gpStr = if (gpIdx >= 0) cells[gpIdx].text().trim() else "0"
                    
                    val rawMarks = if (marksIdx >= 0) cells[marksIdx].text().trim() else null
                    val marksStr = if (rawMarks != null) {
                        if (rawMarks.lowercase().contains("non credit") || 
                            rawMarks.lowercase().contains("non-credit") || 
                            rawMarks.lowercase().contains("noncredit") ||
                            rawMarks.lowercase().contains("ncr")) "NC" else rawMarks
                    } else null

                    val credit = creditStr.toDoubleOrNull() ?: 0.0
                    val gp = gpStr.toDoubleOrNull() ?: 0.0

                    if (code.isNotEmpty() && courseTitle.isNotEmpty()) {
                        courses.add(
                            CourseGrade(
                                courseCode = code,
                                courseName = courseTitle,
                                creditHours = credit,
                                grade = grade,
                                gradePoints = gp,
                                marks = marksStr
                            )
                        )
                    }
                }

                if (courses.isNotEmpty()) {
                    var sgpa = -1.0
                    var cgpa = -1.0
                    val semesterCredits = courses.filter {
                        val g = it.grade.uppercase().trim()
                        g != "NC" && g != "NCR" && !g.contains("NON CREDIT") && !g.contains("NON-CREDIT")
                    }.sumOf { it.creditHours }

                    // Strategy 1: Fallback checks for explicit ID patterns in rows or table siblings (parent's children)
                    val parent = table.parent()
                    if (parent != null) {
                        val sSpan = parent.selectFirst("[id*=lblSGPA], [id*=sgpa]")
                        if (sSpan != null) {
                            sgpa = sSpan.text().trim().toDoubleOrNull() ?: -1.0
                        }
                        val cSpan = parent.selectFirst("[id*=lblCGPA], [id*=cgpa]")
                        if (cSpan != null) {
                            cgpa = cSpan.text().trim().toDoubleOrNull() ?: -1.0
                        }
                    }

                    // Strategy 2: Simple regex to find SGPA and CGPA numbers in the row
                    for (row in rows) {
                        val rowText = row.text().uppercase()
                        if (sgpa == -1.0 || cgpa == -1.0) {
                            if (rowText.contains("SGPA") || rowText.contains("CGPA")) {
                                val mSgpa = java.util.regex.Pattern.compile("SGPA\\s*(?::|\\-|=)?\\s*([0-9]+\\.[0-9]+)").matcher(rowText)
                                if (mSgpa.find() && sgpa == -1.0) {
                                    sgpa = mSgpa.group(1)?.toDoubleOrNull() ?: -1.0
                                }
                                val mCgpa = java.util.regex.Pattern.compile("CGPA\\s*(?::|\\-|=)?\\s*([0-9]+\\.[0-9]+)").matcher(rowText)
                                if (mCgpa.find() && cgpa == -1.0) {
                                    cgpa = mCgpa.group(1)?.toDoubleOrNull() ?: -1.0
                                }
                            }
                        }
                    }

                    if (cgpa == -1.0 && i + 1 < allTables.size) {
                        val nextTable = allTables[i + 1]
                        val nextTableText = nextTable.text().uppercase()
                        if (nextTableText.contains("CGPA")) {
                            val mCgpa = java.util.regex.Pattern.compile("CGPA\\s*(?::|\\-|=)?\\s*([0-9]+\\.[0-9]+)").matcher(nextTableText)
                            if (mCgpa.find() && cgpa == -1.0) {
                                cgpa = mCgpa.group(1)?.toDoubleOrNull() ?: -1.0
                            }
                            val mSgpa = java.util.regex.Pattern.compile("SGPA\\s*(?::|\\-|=)?\\s*([0-9]+\\.[0-9]+)").matcher(nextTableText)
                            if (mSgpa.find() && sgpa == -1.0) {
                                sgpa = mSgpa.group(1)?.toDoubleOrNull() ?: -1.0
                            }
                        }
                    }

                    if (sgpa == -1.0) {
                        val creditedCourses = courses.filter { 
                            val g = it.grade.uppercase().trim()
                            g != "NC" && g != "NCR" && !g.contains("NON CREDIT") && !g.contains("NON-CREDIT")
                        }
                        val totalQualityPoints = creditedCourses.sumOf { it.creditHours * it.gradePoints }
                        val totalCredits = creditedCourses.sumOf { it.creditHours }
                        sgpa = if (totalCredits > 0) totalQualityPoints / totalCredits else 0.0
                    }
                    if (cgpa == -1.0) {
                        cgpa = sgpa
                    }

                    resultTables.add(
                        SemesterGrades(
                            semesterName = titleText,
                            sgpa = sgpa,
                            cgpa = cgpa,
                            creditHours = semesterCredits,
                            courses = courses
                        )
                    )
                }
            }

            val overallCgpa = getOverallCgpa(resultTables)
            val overallCreditHours = calculateTotalEarnedCredits(resultTables)

            val docText = doc.text()
            val standingPatterns = listOf(
                java.util.regex.Pattern.compile("(?i)Academic\\s*Standing\\s*(?::|-|=)?\\s*([a-zA-Z\\s]{3,30})"),
                java.util.regex.Pattern.compile("(?i)Standing\\s*(?::|-|=)?\\s*([a-zA-Z\\s]{3,30})")
            )
            var parsedStanding: String? = null
            for (pattern in standingPatterns) {
                val matcher = pattern.matcher(docText)
                if (matcher.find()) {
                    parsedStanding = matcher.group(1)?.trim()
                    break
                }
            }

            GpaSummary(
                cgpa = overallCgpa,
                totalCreditHours = overallCreditHours,
                semesters = resultTables,
                academicStanding = parsedStanding
            )
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching grades: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    fun fetchMarks(courseCode: String): List<MarksCategory> {
        val cleanCode = courseCode.trim().uppercase()
        var postbackTarget = coursePostbackTargets[cleanCode]
        
        if (postbackTarget.isNullOrEmpty()) {
            fetchAttendanceSummary()
            postbackTarget = coursePostbackTargets[cleanCode]
        }
        
        if (postbackTarget.isNullOrEmpty()) {
            Log.d("PortalAuth", "No postback target found for course $courseCode")
            return emptyList()
        }
        
        return try {
            val summaryUrl = "$baseUrl/Summary.aspx"
            val marksPageUrl = "$baseUrl/QAMarks.aspx"
            
            val getRequest = Request.Builder()
                .url(summaryUrl)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchMarks GET Summary.aspx failed HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw PortalSystemException("fetchMarks empty response")
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val summaryHtml = payload.second
            detectPortalSystemErrors(summaryHtml)
            if (isLoginPage(finalUrl, summaryHtml) || !hasSessionCookiesForHost(baseHost)) {
                throw PortalSystemException("Session expired")
            }
            
            val postBackInfo = PostBackInfo(postbackTarget, "")
            val postRequest = buildPostBackRequestFromPage(summaryUrl, summaryHtml, postBackInfo)
                ?: throw PortalSystemException("Failed to construct postback request for course $courseCode")
                
            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchMarks postback failed HTTP ${response.code}")
                }
                response.body?.string()
            }
            
            val marksRequest = Request.Builder()
                .url(marksPageUrl)
                .header("Referer", summaryUrl)
                .header("User-Agent", userAgent)
                .build()
            val marksHtml = client.newCall(marksRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchMarks GET QAMarks.aspx failed HTTP ${response.code}")
                }
                response.body?.string() ?: throw PortalSystemException("fetchMarks QAMarks empty response")
            }
            detectPortalSystemErrors(marksHtml)
            
            val doc = Jsoup.parse(marksHtml)
            val categories = mutableListOf<MarksCategory>()
            
            for (table in doc.select("table")) {
                val rows = table.select("tr")
                if (rows.size < 2) continue
                
                var hdrRow: org.jsoup.nodes.Element? = null
                var hdrIdx = -1
                var tableTitle = ""
                
                for (i in 0 until rows.size) {
                    val cells = rows[i].select("th, td")
                    if (cells.size == 1 && hdrRow == null) {
                        tableTitle = cells.firstOrNull()?.text()?.trim() ?: ""
                    }
                    if (cells.size > 1) {
                        hdrRow = rows[i]
                        hdrIdx = i
                        break
                    }
                }
                if (hdrRow == null) continue
                
                val ths = hdrRow.select("th, td")
                val headers = ths.map { it.text().trim().lowercase() }
                
                var skip = false
                for (h in headers) {
                    if (h.contains("father name") || h.contains("cnic") || h.contains("advisor") || h.contains("roll no")) {
                        skip = true
                        break
                    }
                }
                if (skip) continue
                
                var titleIdx = -1
                var dateIdx = -1
                var totalIdx = -1
                var obtIdx = -1
                var pctIdx = -1
                
                for (i in headers.indices) {
                    val h = headers[i]
                    if (h.contains("quiz") || h.contains("assignment") || h.contains("particular") || h.contains("topic") || h.contains("title") || h.contains("subject") || h.contains("name") || h.contains("description") || h.contains("activity") || h.contains("item") || h.contains("assessment")) titleIdx = i
                    else if (h.contains("date")) dateIdx = i
                    else if (h.contains("total") || h.contains("max")) totalIdx = i
                    else if (h.contains("obtain") || h.contains("obt") || h.contains("marks")) {
                        if (!h.contains("total")) obtIdx = i
                    }
                    else if (h.contains("percentage") || h.contains("%")) pctIdx = i
                }
                
                if (titleIdx >= 0) {
                    // Robust table title fallback: check preceding sibling elements
                    var parsedTitle = tableTitle
                    if (parsedTitle.isEmpty()) {
                        var sibling = table.previousElementSibling()
                        var limit = 2
                        while (sibling != null && limit > 0) {
                            val text = sibling.text().trim()
                            val tagName = sibling.tagName().lowercase()
                            if (text.isNotEmpty() && (tagName.matches(Regex("h[1-6]|span|label|p|div|td|th")) || sibling.className().contains("title", ignoreCase = true))) {
                                if (text.length < 60) {
                                    parsedTitle = text
                                    break
                                }
                            }
                            sibling = sibling.previousElementSibling()
                            limit--
                        }
                    }
                    
                    // Clean up trailing colons, dashes or extra details
                    parsedTitle = parsedTitle.replace(Regex("(?i)marks\\s*details|assessment\\s*details"), "")
                        .replace(Regex("^\\s*[:\\-•~#=]+\\s*|\\s*[:\\-•~#=]+\\s*$"), "")
                        .trim()
                        
                    val categoryName = if (parsedTitle.isEmpty()) "Assessment Details" else parsedTitle
                    val itemsList = mutableListOf<MarkItem>()
                    var totalMax = 0.0
                    var totalObtained = 0.0
                    
                    for (r in (hdrIdx + 1) until rows.size) {
                        val row = rows[r]
                        val rowText = row.text().lowercase()
                        if (!row.select(".GridFooter").isEmpty() || rowText.contains("projected") || rowText.contains("aggregate") || rowText.contains("total marks") || rowText.trim() == "=") {
                            continue
                        }
                        val cells = row.select("td")
                        if (cells.size <= titleIdx) continue
                        
                        val title = cells[titleIdx].text().trim()
                        val date = if (dateIdx >= 0 && dateIdx < cells.size) cells[dateIdx].text().trim() else ""
                        val totalMarksStr = if (totalIdx >= 0 && totalIdx < cells.size) cells[totalIdx].text().trim() else "10"
                        val obtainedMarksStr = if (obtIdx >= 0 && obtIdx < cells.size) cells[obtIdx].text().trim() else ""
                        var percentage = if (pctIdx >= 0 && pctIdx < cells.size) cells[pctIdx].text().trim() else ""
                        
                        if (obtainedMarksStr.isEmpty()) continue
                        
                        val max = totalMarksStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 10.0
                        val obt = obtainedMarksStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                        
                        totalMax += max
                        totalObtained += obt
                        
                        if (percentage.isEmpty() && max > 0) {
                            percentage = String.format("%.0f%%", (obt / max) * 100)
                        }
                        
                        itemsList.add(
                            MarkItem(
                                title = title,
                                date = date,
                                totalMarks = max,
                                obtainedMarks = obt,
                                percentage = percentage
                            )
                        )
                    }
                    
                    if (itemsList.isNotEmpty()) {
                        val averagePct = if (totalMax > 0.0) (totalObtained / totalMax) * 100.0 else 0.0
                        categories.add(
                            MarksCategory(
                                categoryName = categoryName,
                                items = itemsList,
                                totalMax = totalMax,
                                totalObtained = totalObtained,
                                averagePct = averagePct
                            )
                        )
                    }
                }
            }
            
            categories
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching marks for course $courseCode: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    fun fetchStudentProfile(): StudentProfile {
        return try {
            val url = "$baseUrl/Dashboard.aspx"
            val request = Request.Builder()
                .url(url)
                .header("Referer", "$baseUrl/CoursePortal.aspx")
                .header("User-Agent", userAgent)
                .build()
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PortalSystemException("fetchStudentProfile HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw PortalSystemException("fetchStudentProfile empty response")
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second
            detectPortalSystemErrors(html)
            if (isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)) {
                throw PortalSystemException("Session expired")
            }

            updateCurrentStudentPhotoUrl(parseStudentPhotoUrlFromHtml(html, finalUrl))

            val profile = parseFullStudentProfileFromHtml(html)
            var emailFromContact = ""
            var mobileFromContact = ""
            runCatching {
                val contactUrl = "$baseUrl/AddCellEmailInfo.aspx"
                val contactRequest = Request.Builder()
                    .url(contactUrl)
                    .header("Referer", "$baseUrl/Dashboard.aspx")
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(contactRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val contactHtml = response.body?.string().orEmpty()
                        val parsed = parseContactInfo(contactHtml)
                        emailFromContact = parsed.first
                        mobileFromContact = parsed.second
                    }
                }
            }

            var scholarshipStatus = "No"
            runCatching {
                val scholarshipUrl = "$baseUrl/scholarship/ViewScholarshipStatuse.aspx"
                val scholarshipRequest = Request.Builder()
                    .url(scholarshipUrl)
                    .header("Referer", "$baseUrl/Dashboard.aspx")
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(scholarshipRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val scholarshipHtml = response.body?.string().orEmpty()
                        if (hasActiveScholarshipRows(scholarshipHtml)) {
                            scholarshipStatus = "Active"
                        }
                    }
                }
            }

            profile.copy(
                email = emailFromContact.takeIf { it.isNotBlank() } ?: profile.email,
                mobile = mobileFromContact.takeIf { it.isNotBlank() } ?: profile.mobile,
                scholarshipStatus = scholarshipStatus
            )
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching student profile: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    internal fun hasActiveScholarshipRows(html: String): Boolean {
        val doc = Jsoup.parse(html)
        return doc.select("table").any { table ->
            val text = table.text().lowercase()
            (text.contains("scholarship") || text.contains("financial assistance")) && 
            table.select("tr").drop(1).any { tr ->
                val cells = tr.select("td")
                val rowText = tr.text().lowercase()
                val isHeader = rowText.contains("s.no") || 
                               rowText.contains("sr.no") || 
                               rowText.contains("serial no") || 
                               rowText.contains("scholarship status") || 
                               rowText.contains("scholarship name") || 
                               rowText.contains("financial assistance status")
                cells.isNotEmpty() && !isHeader &&
                    !rowText.contains("no record") && 
                    !rowText.contains("no scholarship") && 
                    !rowText.contains("no assistance")
            }
        }
    }

    internal fun parseFullStudentProfileFromHtml(html: String): StudentProfile {
        val doc = Jsoup.parse(html)
        val tablePairs = linkedMapOf<String, String>()

        doc.select("tr").forEach { row ->
            val cells = row.select("th, td")
            if (cells.size < 2) return@forEach
            var i = 0
            while (i + 1 < cells.size) {
                val key = cells[i].text().trim().trimEnd(':').lowercase()
                val value = cells[i + 1].text().trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    tablePairs.putIfAbsent(key, value)
                }
                i += 2
            }
        }

        fun findValue(vararg keys: String): String? {
            for (k in keys) {
                val lowerKey = k.lowercase()
                val matched = tablePairs.entries.firstOrNull { (key, _) ->
                    key == lowerKey || key.contains(lowerKey)
                }?.value
                if (!matched.isNullOrBlank() && !matched.equals("NA", true)) {
                    return matched
                }
            }
            return null
        }

        fun findByIdWildcard(idPart: String): String? {
            return doc.select("[id*=$idPart]").firstOrNull()?.text()?.trim()?.takeIf {
                it.isNotEmpty() && !it.equals("NA", true)
            }
        }

        val name = findByIdWildcard("lblStudentName")
            ?: findByIdWildcard("lblName")
            ?: findValue("student name", "name", "full name")
            ?: parseStudentNameFromHtml(html)
            ?: ""

        val regNumber = findByIdWildcard("lblRegNo")
            ?: findByIdWildcard("lblRegistrationNo")
            ?: findByIdWildcard("lblRegNumber")
            ?: findValue("registration no", "reg no", "registration number", "roll no")
            ?: ""

        val program = findByIdWildcard("lblProgram")
            ?: findValue("program", "discipline", "degree")
            ?: ""

        val section = findByIdWildcard("lblSection")
            ?: findValue("section", "class section")
            ?: ""

        val fatherName = findByIdWildcard("lblFatherName")
            ?: findByIdWildcard("lblFather")
            ?: findValue("father's name", "father name", "father")
            ?: ""

        val mobile = findByIdWildcard("lblMobile")
            ?: findValue("mobile", "cell", "mobile number")
            ?: ""

        val phone = findByIdWildcard("lblPhone")
            ?: findByIdWildcard("lblTelephone")
            ?: findValue("phone", "landline", "phone number", "telephone")
            ?: ""

        val campus = findByIdWildcard("lblCampus")
            ?: findValue("campus", "institute", "location")
            ?: ""

        val address = findByIdWildcard("lblPermanentAddress")
            ?: findByIdWildcard("lblPostalAddress")
            ?: findByIdWildcard("lblAddress")
            ?: findValue("permanent address", "postal address", "address")
            ?: ""

        val scholarshipStatus = findByIdWildcard("lblScholarshipStatus")
            ?: findByIdWildcard("lblScholarship")
            ?: findValue("scholarship status", "scholarship", "financial aid")
            ?: "No"

        val dob = findByIdWildcard("lblDOB")
            ?: findByIdWildcard("lblDateOfBirth")
            ?: findValue("date of birth", "dob", "birth date")
            ?: ""

        val email = findByIdWildcard("lblEmail")
            ?: findByIdWildcard("lblEmailAddress")
            ?: findValue("email", "email address")
            ?: ""

        return StudentProfile(
            name = name,
            regNumber = regNumber,
            program = program,
            section = section,
            fatherName = fatherName,
            email = email,
            phone = phone,
            campus = campus,
            address = address,
            scholarshipStatus = scholarshipStatus,
            dob = dob,
            mobile = mobile
        )
    }

    internal fun parseContactInfo(contactHtml: String): Pair<String, String> {
        val contactDoc = Jsoup.parse(contactHtml)
        var prefix = ""
        var mainNumber = ""
        var email = ""
        var phone = ""
        for (element in contactDoc.select("input, select")) {
            val name = element.attr("name").lowercase()
            val value = if (element.tagName() == "select") {
                val selected = element.select("option[selected]").first()
                    ?: element.select("option").firstOrNull()
                selected?.attr("value") ?: ""
            } else {
                element.attr("value")
            }.trim()

            if (name.contains("serviceno") || name.contains("network")) {
                prefix = value
            } else if ((name.contains("cellno") || name.contains("mobile") || name.contains("phone") || (name.contains("cell") && !name.contains("serviceno")))
                && !name.contains("detail") && !name.contains("desc") && !name.contains("type") && !name.contains("status")
            ) {
                mainNumber = value
            } else if (name.contains("email")) {
                email = value
            }
        }
        if (prefix.isNotEmpty() || mainNumber.isNotEmpty()) {
            val cleanPrefix = prefix.filter { it.isDigit() || it == '+' }
            val cleanNumber = mainNumber.filter { it.isDigit() }
            val combined = cleanPrefix + cleanNumber
            if (combined.length >= 9) {
                phone = combined
            }
        }
        return Pair(email, phone)
    }

    fun fetchFeeDetails(): FeeSnapshot {
        return try {
            // 1. Fetch Challans
            val challansUrl = "$baseUrl/FeeChallans.aspx"
            val challansRequest = Request.Builder()
                .url(challansUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            val challansHtml = client.newCall(challansRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val finalUrl = response.request.url.toString()
                    val body = response.body?.string().orEmpty()
                    detectPortalSystemErrors(body)
                    if (isLoginPage(finalUrl, body) || !hasSessionCookiesForHost(baseHost)) {
                        throw PortalSystemException("Session expired")
                    }
                    body
                } else null
            }

            // 2. Fetch History
            val historyUrl = "$baseUrl/FeeHistorySFMS.aspx"
            val historyRequest = Request.Builder()
                .url(historyUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            val historyHtml = client.newCall(historyRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val finalUrl = response.request.url.toString()
                    val body = response.body?.string().orEmpty()
                    detectPortalSystemErrors(body)
                    if (isLoginPage(finalUrl, body) || !hasSessionCookiesForHost(baseHost)) {
                        throw PortalSystemException("Session expired")
                    }
                    body
                } else null
            }

            if (challansHtml == null || historyHtml == null) {
                throw PortalSystemException("Failed to fetch fee details from portal")
            }

            parseFeeDetailsFromHtml(challansHtml, historyHtml)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching fee details: ${e.message}", e)
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    private fun parseFeeDetailsFromHtml(challansHtml: String?, historyHtml: String?): FeeSnapshot {
        // Helper to clean and parse double values safely
        fun parseDoubleClean(text: String): Double? {
            return runCatching {
                val clean = text.replace(",", "")
                    .replace("PKR", "", ignoreCase = true)
                    .replace("Rs", "", ignoreCase = true)
                    .replace("Rs.", "", ignoreCase = true)
                    .replace("PKR.", "", ignoreCase = true)
                    .replace("=", "")
                    .trim()
                if (clean.isEmpty() || clean == "-" || clean == "nil" || clean.equals("null", ignoreCase = true)) null else clean.toDouble()
            }.getOrNull()
        }



        // Parse Challans
        val challansList = mutableListOf<FeeChallan>()
        challansHtml?.let { html ->
            val doc = Jsoup.parse(html)
            val rows = doc.select("tr")
            for (row in rows) {
                // If a row contains a link with print/download/view, it is a challan row!
                var challanLink = ""
                val hasChallanLink = row.select("a").any { anchor ->
                    val href = anchor.attr("href")
                    val hrefLower = href.lowercase()
                    val text = anchor.text().lowercase()
                    val matches = hrefLower.contains("dopostback") && (text.contains("print") || text.contains("download") || text.contains("view"))
                    if (matches) {
                        val postBackInfo = extractPostBackInfo(href)
                        challanLink = if (postBackInfo != null) {
                            toPostBackDownloadLink(postBackInfo, sourcePageUrl = "$baseUrl/FeeChallans.aspx")
                        } else {
                            href
                        }
                    }
                    matches
                }
                if (hasChallanLink) {
                    var semester = ""
                    var challanId = ""
                    var dueDate = ""
                    var amount = 0.0
                    val cells = row.select("td, th")
                    for (cell in cells) {
                        val cellText = cell.text().trim()
                        val lowerText = cellText.lowercase()
                        if (lowerText.contains("spring") || lowerText.contains("fall")) {
                            semester = cellText
                        } else if (cellText.matches(Regex("""\d{2}[-/.\s]([a-zA-Z]{3}|\d{2})[-/.\s]\d{2,4}""")) || lowerText.contains("due") || cellText.contains("-") && cellText.length in 9..11) {
                            dueDate = cellText.replace("due", "", ignoreCase = true).replace(":", "").trim()
                        } else if (lowerText.contains("challan") || lowerText.contains("no") || Regex("""\b\d{5,}\b""").containsMatchIn(cellText)) {
                            val match = Regex("""\d+""").find(cellText)
                            if (match != null) {
                                challanId = match.value
                            }
                        } else {
                            val clean = cellText.replace(",", "").replace("PKR", "", ignoreCase = true).replace("Rs", "", ignoreCase = true).trim()
                            val match = Regex("""\d+(\.\d+)?""").find(clean)
                            val amt = match?.value?.toDoubleOrNull()
                            if (amt != null && amt > 0.0) {
                                amount = amt
                            }
                        }
                    }
                    if (semester.isNotEmpty() || challanId.isNotEmpty() || amount > 0.0) {
                        challansList.add(
                            FeeChallan(
                                challanId = if (challanId.isNotEmpty()) challanId else "CH-" + (100000..999999).random(),
                                semester = if (semester.isNotEmpty()) semester else "Semester",
                                amount = amount,
                                dueDate = if (dueDate.isNotEmpty()) dueDate else "N/A",
                                status = "Unpaid",
                                downloadLink = challanLink
                            )
                        )
                    }
                }
            }
        }

        // Parse Ledger/History
        val semesterFees = mutableListOf<FeeHistorySectionRecord>()
        val boardingFees = mutableListOf<FeeHistorySectionRecord>()
        val miscCharges = mutableListOf<FeeHistorySectionRecord>()
        val scholarships = mutableListOf<ScholarshipRecord>()

        val ledgerList = mutableListOf<FeeLedgerEntry>()
        var totalFees = 0.0
        var totalPaid = 0.0
        var totalOutstanding = 0.0
        var totalScholarship = 0.0

        historyHtml?.let { html ->
            val doc = Jsoup.parse(html)
            val allTables = doc.select("table").filter { it.select("table table").isEmpty() }
            val feeTables = allTables.filter { table ->
                table.select("tr").any { row -> row.select("td, th").size >= 10 }
            }
            var rIdx = 0
            for (tableIdx in feeTables.indices) {
                val table = feeTables[tableIdx]
                val rows = table.select("tr")
                if (rows.size < 2) continue
                
                // Parse headers dynamically to match columns correctly
                val headerRow = rows[0]
                val headerCells = headerRow.select("td, th").map { it.text().lowercase() }
                
                var sessionIdx = 1
                var feeTypeIdx = 2
                var prevDuesIdx = 3
                var semesterDuesIdx = 4
                var assistanceIdx = 5
                var assistancePaidIdx = 6
                var duesPaidIdx = 7
                var refundIdx = 8
                var outstandingIdx = 9

                for (idx in headerCells.indices) {
                    val text = headerCells[idx]
                    when {
                        text.contains("session") -> sessionIdx = idx
                        text.contains("fee type") || text.contains("particulars") -> feeTypeIdx = idx
                        text.contains("previous dues") || text.contains("cr hour") -> prevDuesIdx = idx
                        text.contains("semester dues") || text.contains("semester fee") -> semesterDuesIdx = idx
                        text.contains("assistance paid") -> assistancePaidIdx = idx
                        text.contains("assistance") && !text.contains("assistance paid") -> assistanceIdx = idx
                        text.contains("dues paid") || (text == "paid") -> duesPaidIdx = idx
                        text.contains("refund") || text.contains("receipt no") -> refundIdx = idx
                        text.contains("outstanding") -> outstandingIdx = idx
                    }
                }
                
                // Fallback to checking "scholarship" header (e.g. mock test format) if "assistance paid" isn't present
                if (!headerCells.any { it.contains("assistance paid") }) {
                    val scholIdx = headerCells.indexOfFirst { it.contains("scholarship") }
                    if (scholIdx != -1) {
                        assistancePaidIdx = scholIdx
                    }
                }

                var lastRowOutstanding = 0.0
                
                for (i in 1 until rows.size) {
                    val cells = rows[i].select("td")
                    if (cells.size > outstandingIdx && cells.size > assistancePaidIdx) {
                        val sessionVal = cells.getOrNull(sessionIdx)?.text()?.trim().orEmpty()
                        val feeTypeVal = cells.getOrNull(feeTypeIdx)?.text()?.trim().orEmpty()
                        val prevDues = parseDoubleClean(cells.getOrNull(prevDuesIdx)?.text().orEmpty()) ?: 0.0
                        val semesterDues = parseDoubleClean(cells.getOrNull(semesterDuesIdx)?.text().orEmpty()) ?: 0.0
                        val assistance = parseDoubleClean(cells.getOrNull(assistanceIdx)?.text().orEmpty()) ?: 0.0
                        val assistancePaid = parseDoubleClean(cells.getOrNull(assistancePaidIdx)?.text().orEmpty()) ?: 0.0
                        val duesPaid = parseDoubleClean(cells.getOrNull(duesPaidIdx)?.text().orEmpty()) ?: 0.0
                        val refund = parseDoubleClean(cells.getOrNull(refundIdx)?.text().orEmpty()) ?: 0.0
                        val outstandingVal = parseDoubleClean(cells.getOrNull(outstandingIdx)?.text().orEmpty()) ?: 0.0

                        if (sessionVal.isBlank()) continue

                        val record = FeeHistorySectionRecord(
                            session = sessionVal,
                            feeType = feeTypeVal,
                            previousDues = prevDues,
                            semesterDues = semesterDues,
                            assistance = assistance,
                            assistancePaid = assistancePaid,
                            duesPaid = duesPaid,
                            refund = refund,
                            outstandingBalance = outstandingVal
                        )

                        when (tableIdx) {
                            0 -> semesterFees.add(record)
                            1 -> boardingFees.add(record)
                            else -> miscCharges.add(record)
                        }

                        lastRowOutstanding = outstandingVal

                        // Accumulate totals
                        totalFees += semesterDues
                        totalPaid += duesPaid

                        // Compute actual scholarship award (prioritize positive assistancePaid, fallback to positive assistance)
                        val scholarshipAmt = if (assistancePaid > 0.0) {
                            assistancePaid
                        } else if (assistance > 0.0) {
                            assistance
                        } else {
                            0.0
                        }

                        if (scholarshipAmt > 0.0) {
                            totalScholarship += scholarshipAmt
                            scholarships.add(
                                ScholarshipRecord(
                                    session = sessionVal,
                                    feeType = feeTypeVal,
                                    amount = scholarshipAmt,
                                    type = "Scholarship Awarded"
                                )
                            )
                        }

                        // 1. Add Debit Entry (Billed Fee)
                        if (semesterDues > 0.0) {
                            ledgerList.add(
                                FeeLedgerEntry(
                                    date = getPaymentDateForSession(sessionVal, "Debit"),
                                    description = "$feeTypeVal Billed",
                                    amount = semesterDues,
                                    type = "Debit"
                                )
                            )
                        }

                        // 2. Add Credit Entry (Scholarship/Assistance)
                        if (scholarshipAmt > 0.0) {
                            ledgerList.add(
                                FeeLedgerEntry(
                                    date = getPaymentDateForSession(sessionVal, "Scholarship"),
                                    description = "$feeTypeVal Scholarship/Assistance",
                                    amount = scholarshipAmt,
                                    type = "Credit"
                                )
                            )
                        }

                        // 3. Add Credit Entry (Payment)
                        if (duesPaid > 0.0) {
                            ledgerList.add(
                                FeeLedgerEntry(
                                    date = getPaymentDateForSession(sessionVal, "Credit"),
                                    description = "$feeTypeVal Paid",
                                    amount = duesPaid,
                                    type = "Credit"
                                )
                            )
                        }
                        rIdx++
                    }
                }
                totalOutstanding += lastRowOutstanding
            }
        }

        val sortedLedger = ledgerList.sortedWith(Comparator { e1, e2 ->
            val p1 = parseSessionInfo(e1)
            val p2 = parseSessionInfo(e2)

            if (p1.first != p2.first) {
                p2.first.compareTo(p1.first)
            } else if (p1.second != p2.second) {
                p2.second.compareTo(p1.second)
            } else {
                p2.third.compareTo(p1.third)
            }
        })

        val outstandingBalance = if (historyHtml != null) totalOutstanding else null
        val totalDebits = if (historyHtml != null) totalFees else null
        val totalCredits = if (historyHtml != null) (totalPaid + totalScholarship) else null
        val lastTransactionDate = if (historyHtml != null && sortedLedger.isNotEmpty()) {
            sortedLedger.firstOrNull { it.type == "Credit" }?.date ?: sortedLedger.firstOrNull()?.date
        } else null

        val finalChallans = if (challansHtml != null) challansList else null
        val finalLedger = if (historyHtml != null) sortedLedger else null

        return FeeSnapshot(
            outstandingBalance = outstandingBalance,
            totalCredits = totalCredits,
            totalDebits = totalDebits,
            lastTransactionDate = lastTransactionDate,
            challans = finalChallans,
            ledger = finalLedger,
            semesterFees = if (historyHtml != null) semesterFees else null,
            boardingFees = if (historyHtml != null) boardingFees else null,
            miscCharges = if (historyHtml != null) miscCharges else null,
            scholarships = if (historyHtml != null) scholarships else null,
            cachedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun fetchPageHtmlDebug(page: String): String {
        val url = "$baseUrl/$page"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "$baseUrl/Dashboard.aspx")
            .header("User-Agent", userAgent)
            .build()
        return client.newCall(request).execute().use { response ->
            response.body?.string().orEmpty()
        }
    }

    private fun getPaymentDateForSession(session: String, type: String): String {
        val yearMatch = Regex("""\d{4}""").find(session)
        val year = yearMatch?.value ?: "2024"
        val isSpring = session.lowercase().contains("spring")
        
        return when (type) {
            "Debit" -> if (isSpring) "01 Feb $year" else "01 Sep $year"
            "Scholarship" -> if (isSpring) "10 Mar $year" else "10 Oct $year"
            else -> if (isSpring) "20 Mar $year" else "20 Oct $year"
        }
    }

    private fun parseSessionInfo(entry: FeeLedgerEntry): Triple<Int, Int, Int> {
        val date = entry.date
        val yearMatch = Regex("""\d{4}""").find(date)
        val year = yearMatch?.value?.toIntOrNull() ?: 2026
        
        val monthStr = date.split(" ").getOrNull(1)?.lowercase() ?: ""
        val monthPriority = when {
            monthStr.contains("feb") || monthStr.contains("sep") -> 0
            monthStr.contains("mar") || monthStr.contains("oct") -> 1
            else -> 2
        }

        val typePriority = when {
            entry.type == "Credit" && entry.description.lowercase().contains("paid") -> 2
            entry.type == "Credit" -> 1
            else -> 0
        }
        return Triple(year, monthPriority, typePriority)
    }

    fun fetchEnrolledCourses(): EnrolledCoursesData {
        runCatching { populateCourseCodesMap() }
        return try {
            val url = "$baseUrl/EnrolledCourses.aspx"
            val request = Request.Builder()
                .url(url)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            
            val payload = client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    null
                } else {
                    if (!response.isSuccessful) {
                        throw PortalSystemException("fetchEnrolledCourses HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: throw PortalSystemException("fetchEnrolledCourses empty response")
                    response.request.url.toString() to body
                }
            }

            val html = if (payload != null) {
                val finalUrl = payload.first
                val bodyHtml = payload.second
                detectPortalSystemErrors(bodyHtml)
                if (isLoginPage(finalUrl, bodyHtml) || !hasSessionCookiesForHost(baseHost)) {
                    throw PortalSystemException("Session expired")
                }
                bodyHtml
            } else {
                null
            }

            var coursesData = html?.let { runCatching { parseEnrolledCoursesFromHtml(it) }.getOrNull() }

            if (coursesData == null || coursesData.courses.isEmpty()) {
                val fallbackUrl = "$baseUrl/StudentRegistration.aspx"
                val fallbackRequest = Request.Builder()
                    .url(fallbackUrl)
                    .header("Referer", "$baseUrl/Dashboard.aspx")
                    .header("User-Agent", userAgent)
                    .build()
                val fallbackPayload = client.newCall(fallbackRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PortalSystemException("fetchEnrolledCourses Fallback HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: throw PortalSystemException("fetchEnrolledCourses Fallback empty response")
                    response.request.url.toString() to body
                }
                val fallbackFinalUrl = fallbackPayload.first
                val fallbackHtml = fallbackPayload.second
                detectPortalSystemErrors(fallbackHtml)
                if (isLoginPage(fallbackFinalUrl, fallbackHtml) || !hasSessionCookiesForHost(baseHost)) {
                    throw PortalSystemException("Session expired")
                }
                coursesData = runCatching { parseEnrolledCoursesFromHtml(fallbackHtml) }.getOrNull()
            }

            if (coursesData == null || coursesData.courses.isEmpty()) {
                val summaryUrl = "$baseUrl/Summary.aspx"
                val summaryRequest = Request.Builder()
                    .url(summaryUrl)
                    .header("Referer", "$baseUrl/Dashboard.aspx")
                    .header("User-Agent", userAgent)
                    .build()
                val summaryPayload = client.newCall(summaryRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PortalSystemException("fetchEnrolledCourses Summary HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: throw PortalSystemException("fetchEnrolledCourses Summary empty response")
                    response.request.url.toString() to body
                }
                val summaryFinalUrl = summaryPayload.first
                val summaryHtml = summaryPayload.second
                detectPortalSystemErrors(summaryHtml)
                if (isLoginPage(summaryFinalUrl, summaryHtml) || !hasSessionCookiesForHost(baseHost)) {
                    throw PortalSystemException("Session expired")
                }
                coursesData = parseEnrolledCoursesFromHtml(summaryHtml)
            }

            coursesData
        } catch (e: Exception) {
            throw if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e
        }
    }

    private fun parseEnrolledCoursesFromHtml(html: String): EnrolledCoursesData {
        val doc = Jsoup.parse(html)
        val enrolledCourses = mutableListOf<Pair<String, EnrolledCourse>>()
        
        val tables = doc.select("table")
        val semesterRegex = Regex("(Spring|Fall|Summer)\\s*\\d{4}", RegexOption.IGNORE_CASE)
        
        for (table in tables) {
            val rows = table.select("tr")
            if (rows.isEmpty()) continue
            
            var headerRow: org.jsoup.nodes.Element? = null
            var headerRowIndex = -1
            for (i in 0 until minOf(5, rows.size)) {
                val cells = rows[i].select("th, td")
                val hasCode = cells.any { it.text().contains("code", ignoreCase = true) }
                val hasTitle = cells.any { 
                    val text = it.text().trim().lowercase()
                    (text.contains("title") || text.contains("subject") || text.contains("course")) && 
                    !text.contains("registered") && !text.contains("total")
                }
                if (hasCode || (hasTitle && cells.size >= 3 && cells.none { it.text().length > 40 })) {
                    headerRow = rows[i]
                    headerRowIndex = i
                    break
                }
            }
            
            if (headerRow == null) continue
            
            val ths = headerRow.select("th, td")
            var codeIdx = -1
            var titleIdx = -1
            var creditIdx = -1
            var sectionIdx = -1
            var instructorIdx = -1
            
            for (i in 0 until ths.size) {
                val text = ths[i].text().trim().lowercase()
                if (text.contains("code")) codeIdx = i
                else if (text.contains("title") || text.contains("subject") || text.contains("course")) titleIdx = i
                else if (text.contains("credit") || text.contains("cr.hr") || text.contains("cr hr") || text.contains("hour") || text.contains("cr")) creditIdx = i
                else if (text.contains("section") || text.contains("sec") || text.equals("class")) sectionIdx = i
                else if (text.contains("instructor") || text.contains("teacher") || text.contains("faculty") || text.contains("member") || text.contains("professor")) instructorIdx = i
            }
            
            if (titleIdx == -1) continue
            
            var semesterName = ""
            
            var prev = table.previousElementSibling()
            while (prev != null) {
                val txt = prev.text().trim()
                val match = semesterRegex.find(txt)
                if (match != null) {
                    semesterName = match.value
                    break
                }
                prev = prev.previousElementSibling()
            }
            
            if (semesterName.isEmpty()) {
                val caption = table.selectFirst("caption")?.text()?.trim().orEmpty()
                val match = semesterRegex.find(caption)
                if (match != null) {
                    semesterName = match.value
                }
            }
            
            if (semesterName.isEmpty()) {
                for (i in 0 until headerRowIndex) {
                    val rowText = rows[i].text()
                    val m = semesterRegex.find(rowText)
                    if (m != null) {
                        semesterName = m.value
                        break
                    }
                }
            }
            
            if (semesterName.isEmpty()) {
                semesterName = latestSemesterName
            }
            
            for (r in (headerRowIndex + 1) until rows.size) {
                val cells = rows[r].select("td")
                if (cells.size <= titleIdx) continue
                
                val title = cells.getOrNull(titleIdx)?.text()?.trim().orEmpty()
                if (title.isBlank() || 
                    title.lowercase().contains("total") ||
                    title.lowercase().contains("grand")
                ) {
                    continue
                }
                
                var code = ""
                if (codeIdx != -1) {
                    code = cells.getOrNull(codeIdx)?.text()?.trim().orEmpty()
                }
                
                val cleanTitleForLookup = title.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                if (code.isBlank()) {
                    code = courseTitleToCodeMap[cleanTitleForLookup].orEmpty()
                }
                
                var credit = ""
                if (creditIdx != -1) {
                    credit = cells.getOrNull(creditIdx)?.text()?.trim().orEmpty()
                }
                if (credit.isBlank()) {
                    credit = courseTitleToCreditMap[cleanTitleForLookup].orEmpty()
                }
                
                val section = if (sectionIdx != -1) cells.getOrNull(sectionIdx)?.text()?.trim().orEmpty() else ""
                val instructor = if (instructorIdx != -1) cells.getOrNull(instructorIdx)?.text()?.trim().orEmpty() else ""
                
                val cleanCode = code.replace("\\s+".toRegex(), " ")
                val cleanTitle = title.replace("\\s+".toRegex(), " ")
                val cleanCredit = credit.replace("\\s+".toRegex(), " ")
                val cleanSection = section.replace("\\s+".toRegex(), " ")
                val cleanInstructor = instructor.replace("\\s+".toRegex(), " ")
                
                enrolledCourses.add(
                    semesterName to EnrolledCourse(
                        courseCode = cleanCode,
                        courseTitle = cleanTitle,
                        creditHours = cleanCredit,
                        section = cleanSection,
                        instructorName = cleanInstructor
                    )
                )
            }
        }
        
        if (enrolledCourses.isEmpty()) {
            throw PortalSystemException("No enrolled courses found for the current semester.")
        }
        
        val semesters = enrolledCourses.map { it.first }.distinct().filter { it.isNotEmpty() }
        val currentSemester = if (semesters.isNotEmpty()) {
            semesters.maxWithOrNull { s1, s2 ->
                val y1 = s1.split(" ").lastOrNull()?.toIntOrNull() ?: 0
                val y2 = s2.split(" ").lastOrNull()?.toIntOrNull() ?: 0
                if (y1 != y2) {
                    y1.compareTo(y2)
                } else {
                    val t1 = s1.split(" ").firstOrNull()?.lowercase() ?: ""
                    val t2 = s2.split(" ").firstOrNull()?.lowercase() ?: ""
                    val order = listOf("spring", "summer", "fall")
                    val o1 = order.indexOf(t1)
                    val o2 = order.indexOf(t2)
                    o1.compareTo(o2)
                }
            } ?: ""
        } else {
            val pageMatch = semesterRegex.find(doc.text())
            pageMatch?.value ?: ""
        }
        
        val displaySemester = currentSemester.ifEmpty {
            latestSemesterName.ifEmpty {
                val cal = java.util.Calendar.getInstance()
                val year = cal.get(java.util.Calendar.YEAR)
                val month = cal.get(java.util.Calendar.MONTH)
                val term = if (month < java.util.Calendar.JULY) "Spring" else "Fall"
                "$term $year"
            }
        }
        
        val filteredCourses = if (currentSemester.isNotEmpty()) {
            enrolledCourses.filter { it.first == currentSemester }.map { it.second }
        } else {
            enrolledCourses.map { it.second }
        }
        
        if (filteredCourses.isEmpty()) {
            throw PortalSystemException("No enrolled courses found for the current semester.")
        }
        
        return EnrolledCoursesData(
            courses = filteredCourses,
            semesterName = displaySemester
        )
    }

    fun fetchCourseFiles(courseCode: String, courseTitle: String): List<CourseFile> {
        val summaryUrl = "$baseUrl/CoursePortalContentsSummary.aspx"
        if (courseTitleToCodeMap.isEmpty() || courseTitleToCreditMap.isEmpty()) {
            populateCourseCodesMap()
        }
        return try {
            val getRequest = Request.Builder()
                .url(summaryUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
                
            val pageHtml = client.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: throw PortalSystemException("Failed to load CoursePortalContentsSummary.aspx")
            
            detectPortalSystemErrors(pageHtml)
            
            val doc = Jsoup.parse(pageHtml)
            var bestSelect = doc.select("select").firstOrNull { el ->
                val id = el.attr("id").lowercase()
                val name = el.attr("name").lowercase()
                id.contains("course") || name.contains("course") || id.contains("ddl") || name.contains("ddl")
            }
            
            if (bestSelect == null) {
                bestSelect = doc.select("select").firstOrNull { el ->
                    el.text().matches(".*[A-Z]{3,4}-?\\d{3,4}.*".toRegex()) || el.text().lowercase().contains("select")
                } ?: doc.select("select").firstOrNull()
            }
            
            if (bestSelect == null) {
                Log.d("PortalAuth", "No select dropdown found on CoursePortalContentsSummary.aspx")
                return emptyList()
            }
            
            val dropdownName = bestSelect.attr("name")
            val cleanTargetTitle = courseTitle.lowercase().replace("\\s+|-|•|–".toRegex(), "")
            val cleanTargetCode = courseCode.lowercase().replace("\\s+|-|•|–".toRegex(), "")
            
            var selectedOptionValue = ""
            for (opt in bestSelect.select("option")) {
                val valStr = opt.attr("value")
                val text = opt.text().trim()
                if (valStr.isNotEmpty() && !text.lowercase().startsWith("select") && !text.startsWith("--")) {
                    val cleanText = text.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                    if (cleanText.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanText) ||
                        cleanText.contains(cleanTargetCode) || text.contains(courseCode, ignoreCase = true)
                    ) {
                        selectedOptionValue = valStr
                        break
                    }
                }
            }
            
            if (selectedOptionValue.isEmpty()) {
                Log.d("PortalAuth", "No matching dropdown option found for course code $courseCode, title $courseTitle")
                return emptyList()
            }
            
            val form = doc.select("form").firstOrNull() ?: throw PortalSystemException("Form not found in CoursePortalContentsSummary.aspx")
            val formBuilder = FormBody.Builder()
            
            for (input in form.select("input")) {
                val name = input.attr("name")
                val value = input.attr("value")
                val type = input.attr("type").lowercase()
                if (name.isNotEmpty() && (type == "hidden" || type == "text" || type == "password" || type == "radio" || type == "checkbox")) {
                    formBuilder.add(name, value)
                }
            }
            
            for (el in form.select("select")) {
                val name = el.attr("name")
                if (name == dropdownName) {
                    formBuilder.add(name, selectedOptionValue)
                } else {
                    val selected = el.select("option[selected]").firstOrNull()
                    val valStr = selected?.attr("value") ?: el.select("option").firstOrNull()?.attr("value").orEmpty()
                    formBuilder.add(name, valStr)
                }
            }
            
            val onchange = bestSelect.attr("onchange")
            if (onchange.contains("__doPostBack")) {
                formBuilder.add("__EVENTTARGET", dropdownName)
                formBuilder.add("__EVENTARGUMENT", "")
            } else {
                val submitBtn = form.selectFirst("input[type=submit], button[type=submit]")
                if (submitBtn != null) {
                    formBuilder.add(submitBtn.attr("name"), submitBtn.attr("value"))
                }
            }
            
            val formAction = form.attr("action")
            val postUrl = when {
                formAction.isBlank() -> summaryUrl
                formAction.startsWith("http") -> formAction
                formAction.startsWith("/") -> baseUrl + formAction
                else -> "$baseUrl/$formAction"
            }
            
            val postRequest = Request.Builder()
                .url(postUrl)
                .post(formBuilder.build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", summaryUrl)
                .header("Origin", baseUrl)
                .header("User-Agent", userAgent)
                .build()
                
            val resultHtml = client.newCall(postRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: throw PortalSystemException("Postback to select course failed")
            
            detectPortalSystemErrors(resultHtml)
            
            parseCourseFilesFromHtml(resultHtml, summaryUrl)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching course files: ${e.message}", e)
            throw e
        }
    }

    fun parseCourseFilesFromHtml(html: String, pageUrl: String): List<CourseFile> {
        val doc = Jsoup.parse(html)
        val files = mutableListOf<CourseFile>()
        val table = doc.getElementById("DataContent_gvPortalSummary") ?: return emptyList()
        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()
        
        for (r in 1 until rows.size) {
            val row = rows[r]
            val cells = row.select("td")
            if (cells.size >= 3) {
                val title = cells[1].text().trim().replace("\\s+".toRegex(), " ")
                val description = if (cells.size > 2) cells[2].text().trim().replace("\\s+".toRegex(), " ") else ""
                val uploadDate = if (cells.size > 3) cells[3].text().trim() else ""
                
                var downloadLink = ""
                for (cell in cells) {
                    val aTag = cell.select("a").firstOrNull()
                    if (aTag != null && !aTag.attr("href").isNullOrBlank() && aTag.attr("href").contains("download", ignoreCase = true)) {
                        val href = aTag.attr("href")
                        val onClick = aTag.attr("onclick")
                        val postBackInfo = extractPostBackInfo(href) ?: extractPostBackInfo(onClick)
                        if (postBackInfo != null) {
                            downloadLink = toPostBackDownloadLink(postBackInfo, sourcePageUrl = pageUrl)
                        } else {
                            val rawUrl = when {
                                href.isBlank() || href == "#" -> extractUrlFromJavascript(onClick)
                                href.startsWith("javascript", true) -> extractUrlFromJavascript(href) ?: extractUrlFromJavascript(onClick)
                                else -> href
                            }
                            downloadLink = normalizeUrl(rawUrl)
                        }
                        break
                    }
                }
                
                if (title.isNotEmpty() && !title.lowercase().contains("no content found")) {
                    files.add(
                        CourseFile(
                            title = title,
                            description = description,
                            uploadDate = uploadDate,
                            downloadLink = downloadLink
                        )
                    )
                }
            }
        }
        return files
    }

    fun extractPasswordRules(html: String?): String {
        if (html == null) return "Password must be at least 8 characters long, include a number, an uppercase letter, and a special character."
        val doc = Jsoup.parse(html)
        
        val list = doc.select("ol, ul").firstOrNull { e ->
            val text = e.text().lowercase()
            text.contains("character") || text.contains("special") || text.contains("number")
        }
        if (list != null) {
            val items = list.select("li").map { "• " + it.text().trim() }
            if (items.isNotEmpty()) {
                return "Note: New Password Policy\n" + items.joinToString("\n")
            }
        }

        val container = doc.select("div.alert, span[id*=lblRules], td, li").firstOrNull { e ->
            val text = e.text().lowercase()
            text.contains("must contain") || text.contains("policy")
        }
        if (container != null && container.text().isNotBlank()) {
            return container.text().replace("(?i)password policy:?".toRegex(), "").trim()
        }
        return "Password must be at least 8 characters long, include a number, an uppercase letter, and a special character."
    }

    suspend fun fetchPasswordRules(): String {
        return try {
            val pageUrl = "$baseUrl/changepassword.aspx"
            val request = Request.Builder()
                .url(pageUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            val pageHtml = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PortalSystemException("HTTP ${response.code}")
                response.body?.string() ?: throw PortalSystemException("Empty response")
            }
            extractPasswordRules(pageHtml)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error fetching password rules: ${e.message}", e)
            "Password must be at least 8 characters long, include a number, an uppercase letter, and a special character."
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String): Result<String> {
        return try {
            val pageUrl = "$baseUrl/changepassword.aspx"
            val request = Request.Builder()
                .url(pageUrl)
                .header("Referer", "$baseUrl/Dashboard.aspx")
                .header("User-Agent", userAgent)
                .build()
            val pageHtml = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PortalSystemException("HTTP ${response.code}")
                response.body?.string() ?: throw PortalSystemException("Empty response")
            }

            val doc = Jsoup.parse(pageHtml)
            val form = doc.select("form").first() ?: throw PortalSystemException("Form not found on password change page")

            val formBuilder = FormBody.Builder()
            var btnName = ""
            form.select("input, select").forEach { input ->
                val name = input.attr("name")
                val type = input.attr("type").lowercase()
                var valStr = input.attr("value")

                if (input.tagName() == "select") {
                    val selected = input.select("option[selected]").first() ?: input.select("option").firstOrNull()
                    valStr = selected?.attr("value").orEmpty()
                    formBuilder.add(name, valStr)
                    return@forEach
                }

                if (type == "hidden") {
                    formBuilder.add(name, valStr)
                } else if (name.lowercase().contains("btnchange") || name.lowercase().contains("btnsubmit") || type == "submit" || name.lowercase().contains("btn")) {
                    if (btnName.isEmpty() && !name.lowercase().contains("cancel")) {
                        btnName = name
                        formBuilder.add(name, "Ok")
                    }
                } else if (name.lowercase().contains("old") && (type == "password" || type == "text")) {
                    formBuilder.add(name, currentPassword)
                } else if (name.lowercase().contains("new") && (type == "password" || type == "text")) {
                    formBuilder.add(name, newPassword)
                } else if (name.lowercase().contains("confirm") && (type == "password" || type == "text")) {
                    formBuilder.add(name, confirmPassword)
                } else if (type == "text" || type == "password") {
                    formBuilder.add(name, valStr)
                }
            }

            val postRequest = Request.Builder()
                .url(pageUrl)
                .post(formBuilder.build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", pageUrl)
                .header("User-Agent", userAgent)
                .build()

            val responseBody = client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) throw PortalSystemException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }

            parsePasswordChangeResult(responseBody)
        } catch (e: Exception) {
            Log.e("PortalAuth", "Error changing password: ${e.message}", e)
            Result.failure(if (e !is PortalSystemException) PortalSystemException(e.message ?: "Unknown error") else e)
        }
    }

    internal fun parsePasswordChangeResult(html: String): Result<String> {
        val doc = org.jsoup.Jsoup.parse(html)
        
        // 1. Look for explicit message elements on the page
        val msgElement = doc.select(
            "span[id*=lblMsg], span[id*=lblMessage], span[id*=lblError], [id*=ValidationSummary], [class*=ValidationSummary], " +
            "span[id*=Validator], span[id*=cv], span[id*=rfv], span[id*=Compare], span[id*=Required], div.alert, " +
            ".text-danger, .text-error, [class*=error], [class*=danger]"
        ).firstOrNull { el ->
            val style = el.attr("style").replace(" ", "").lowercase()
            val isHidden = style.contains("display:none")
            
            el.text().isNotBlank() &&
            !isHidden &&
            !el.parents().any { it.tagName().equals("noscript", ignoreCase = true) } &&
            !el.text().contains("javascript is disabled", ignoreCase = true)
        }
        
        val extractedMessage = msgElement?.text()?.trim()
        
        // 2. Determine success based on the extracted message or input fields
        val isSuccess = if (extractedMessage != null) {
            val lowerMsg = extractedMessage.lowercase()
            val hasSuccessKeyword = lowerMsg.contains("success") || lowerMsg.contains("changed") || lowerMsg.contains("completed")
            val hasFailureKeyword = lowerMsg.contains("not") || 
                                    lowerMsg.contains("fail") || 
                                    lowerMsg.contains("incorrect") || 
                                    lowerMsg.contains("invalid") || 
                                    lowerMsg.contains("error") || 
                                    lowerMsg.contains("wrong") || 
                                    lowerMsg.contains("mismatch") ||
                                    lowerMsg.contains("could not") ||
                                    lowerMsg.contains("unable")
                                    
            hasSuccessKeyword && !hasFailureKeyword
        } else {
            // Check if password input fields are still present on the page.
            // If they are present, it is highly likely the change failed.
            val hasPasswordFields = doc.select("input[type=password]").isNotEmpty()
            !hasPasswordFields
        }
        
        return if (isSuccess) {
            Result.success(extractedMessage ?: "Password changed successfully.")
        } else {
            Result.failure(PortalSystemException(extractedMessage ?: "Incorrect current password or invalid password change request."))
        }
    }
}
