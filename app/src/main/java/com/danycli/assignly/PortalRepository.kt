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
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.HashMap
import java.util.concurrent.TimeUnit

sealed class LoginResult {
    object Success : LoginResult()
    object InvalidCredentials : LoginResult()
    object CaptchaRequired : LoginResult()
    data class Error(val message: String) : LoginResult()
}

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

sealed class InstructionFilesResult {
    data class Success(val files: List<InstructionFile>) : InstructionFilesResult()
    object NetworkError : InstructionFilesResult()
    data class Rejected(val reason: String) : InstructionFilesResult()
    data class Error(val message: String) : InstructionFilesResult()
}

class PortalRepository {
    private enum class PortalStatusState {
        NOT_SUBMITTED, NOT_SUBMITTED_CLOSED, SUBMITTED, GRADED, UNKNOWN
    }

    private val postBackPrefix = "portal-postback:"
    private data class PostBackInfo(val target: String, val argument: String)
    private data class PostBackLink(val info: PostBackInfo, val sourcePageUrl: String?)
    private data class HtmlDownloadCandidate(val url: String? = null, val postBackInfo: PostBackInfo? = null)
    private data class StudentProfile(val name: String?, val rollNo: String?, val program: String?)

    private fun debugLog(message: String) {
        if (com.danycli.assignmentchecker.BuildConfig.DEBUG) {
            Log.d("PortalAuth", message)
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

    private fun parseStudentProfileFromHtml(html: String): StudentProfile {
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
        return StudentProfile(name = name, rollNo = rollNo, program = program)
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
        profile: StudentProfile,
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
                .header("Referer", "$baseUrl/CoursePortal.aspx")
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
                if (htmlLike) {
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
        val hasGradedStatus = Regex("\\bgraded\\b|\\bclosed\\b")
            .containsMatchIn(normalizedStatus)
        val hasSubmittedStatus = Regex("\\bsubmitted\\b")
            .containsMatchIn(normalizedStatus)

        val hasChangeAction = normalizedAction.contains("change submitted file")
        val hasSubmitAction = normalizedAction.contains("submit") && !hasChangeAction
        val hasClosedAction = normalizedAction.contains("closed")

        return when {
            hasNotSubmittedStatus && (hasClosedAction || hasGradedStatus) -> PortalStatusState.NOT_SUBMITTED_CLOSED
            hasNotSubmittedStatus -> PortalStatusState.NOT_SUBMITTED
            hasGradedStatus -> PortalStatusState.GRADED
            hasSubmittedStatus -> PortalStatusState.SUBMITTED
            hasChangeAction -> PortalStatusState.SUBMITTED
            hasSubmitAction -> PortalStatusState.NOT_SUBMITTED
            hasClosedAction -> PortalStatusState.GRADED
            else -> PortalStatusState.UNKNOWN
        }
    }

    private fun isLoginPage(url: String, html: String): Boolean {
        if (url.contains("Login.aspx", true)) return true
        val doc = Jsoup.parse(html)
        return doc.select("input[name*=txtUsername], input[id*=txtUsername], input[name*=btnLogin], input[id*=btnLogin]").isNotEmpty()
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

    private fun clearSessionState() {
        synchronized(cookieStore) {
            cookieStore.clear()
        }
        currentStudentName = null
        currentStudentPhotoUrl = null
        currentStudentPhotoBytes = null
        currentStudentPhotoBytesUrl = null
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
    private val userAgent = com.danycli.assignmentchecker.BuildConfig.PORTAL_USER_AGENT
    private val baseHost = runCatching { baseUrl.toHttpUrl().host }.getOrDefault("")

    fun login(username: String, password: String): LoginResult {
        return try {
            val loginUrl = "$baseUrl/Login.aspx"
            val originalUsername = username.trim()
            fun normalizeToken(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
            val registrationParts = originalUsername.split("-").map { it.trim() }.filter { it.isNotEmpty() }
            if (registrationParts.size != 3 || password.isBlank()) {
                return LoginResult.InvalidCredentials
            }
            clearSessionState()

            debugLog("=== LOGIN START ===")
            debugLog("Login URL: ${sanitizeUrl(loginUrl)}")

            // 1. Initial GET to extraction hidden state tokens and discover field names
            debugLog("Step 1: Fetching login page...")
            val initialHtml = client.newCall(
                Request.Builder().url(loginUrl).header("User-Agent", userAgent).build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return LoginResult.Error("HTTP ${response.code}")
                }
                response.body?.string() ?: return LoginResult.Error("Empty server response")
            }

            debugLog("Initial page fetched")

            if (initialHtml.contains("captcha", true)) {
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
                if (!response.isSuccessful) {
                    return LoginResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return LoginResult.Error("Empty server response")
                response.request.url.toString() to body
            }
            val finalUrl = finalPayload.first
            val finalHtml = finalPayload.second

            if (finalHtml.contains("captcha", true) || finalHtml.contains("security verification", true)) {
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
            val verifyUrl = "$baseUrl/CoursePortal.aspx"
            val verifyRequest = Request.Builder()
                .url(verifyUrl)
                .header("Referer", loginUrl)
                .header("User-Agent", userAgent)
                .build()
            val verifyPayload = client.newCall(verifyRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return LoginResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return LoginResult.Error("Empty server response")
                response.request.url.toString() to body
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

            if (verifyHtml.contains("captcha", true) || verifyHtml.contains("security verification", true)) {
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
        } catch (e: Exception) {
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
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second

            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) {
                Log.d("PortalAuth", "Not authenticated")
                return Pair(emptyList(), emptyList())
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
            val rows = table.select("tbody tr")
            Log.d("PortalAuth", "Total rows: ${rows.size}")
            
            for (i in 0 until rows.size) {
                try {
                    val cols = rows[i].select("td")
                    
                    if (cols.size < 9) {
                        Log.d("PortalAuth", "Row $i: Only ${cols.size} cols, skipping")
                        continue
                    }
                    
                    val course = cols[1].text().trim()
                    val title = cols[2].text().trim()
                    val deadline = cols[4].text().trim()
                    val statusText = cols[5].text().trim().lowercase()
                    val downloadLink = extractAssignmentDownloadLink(cols[7])
                    
                    // Action column may contain submit/change/closed indicators.
                    val actionElement = cols[8]
                    val actionText = actionElement.text().trim().lowercase()
                    val actionLink = extractAssignmentSubmitLink(actionElement, finalUrl)
                    val normalizedState = normalizePortalStatusState(statusText, actionText)
                    
                    Log.d("PortalAuth", "Row $i: Action column - Link: '$actionLink', Text: '$actionText'")
                    
                    // If assignment is closed, there is no usable submit URL.
                    // Otherwise use action link for both pending ("submit")
                    // and submitted-open ("change submitted file") rows.
                    val submitUrl = if (normalizedState == PortalStatusState.NOT_SUBMITTED_CLOSED || actionText.contains("closed")) "" else actionLink
                    
                    if (course.isEmpty() || title.isEmpty()) continue
                    
                    Log.d("PortalAuth", "Row $i: $course - $title - Status: '$statusText'")
                    Log.d("PortalAuth", "  Final submitUrl: $submitUrl")
                    Log.d("PortalAuth", "  Parsed normalized state: $normalizedState")

                    val normalizedSubmitUrl = submitUrl
                    Log.d("PortalAuth", "  Parsed submitUrl: '$normalizedSubmitUrl'")

                    when (normalizedState) {
                        PortalStatusState.NOT_SUBMITTED, PortalStatusState.NOT_SUBMITTED_CLOSED -> {
                            val pendingStatus = if (normalizedState == PortalStatusState.NOT_SUBMITTED_CLOSED) {
                                AssignmentStatus.NOT_SUBMITTED_CLOSED
                            } else {
                                AssignmentStatus.PENDING
                            }
                            pendingAssignments.add(
                                Assignment(
                                    course, title, deadline,
                                    downloadLink,
                                    normalizedSubmitUrl,
                                    status = pendingStatus,
                                    submittedDate = null,
                                    grade = null,
                                    feedback = null
                                )
                            )
                        }
                        PortalStatusState.SUBMITTED, PortalStatusState.GRADED -> {
                            val assignmentStatus = if (normalizedState == PortalStatusState.GRADED) {
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
            Pair(emptyList(), emptyList())
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
                response.request.url.toString() to body
            }
            val finalUrl = payload.first
            val html = payload.second

            val notAuthenticated = isLoginPage(finalUrl, html) || !hasSessionCookiesForHost(baseHost)
            if (notAuthenticated) return emptyList()
            currentStudentName = parseStudentNameFromHtml(html) ?: currentStudentName
            updateCurrentStudentPhotoUrl(parseStudentPhotoUrlFromHtml(html, finalUrl))

            val doc = Jsoup.parse(html)
            val submitted = mutableListOf<Assignment>()
            
            val table = doc.select("table[id*='gvPortalSummary']").firstOrNull()
                ?: doc.select("table.Grid").firstOrNull() ?: return emptyList()
            
            val rows = table.select("tbody tr")
            
            for (i in 0 until rows.size) {
                try {
                    val cols = rows[i].select("td")
                    if (cols.size < 9) continue
                    
                    val course = cols[1].text().trim()
                    val title = cols[2].text().trim()
                    val deadline = cols[4].text().trim()
                    val statusText = cols[5].text().trim().lowercase()
                    val downloadLink = extractAssignmentDownloadLink(cols[7])
                    
                    val actionText = cols[8].text().trim().lowercase()
                    val submitLinkHref = extractAssignmentSubmitLink(cols[8], finalUrl)
                    val submitLink = if (actionText.contains("closed")) {
                        ""
                    } else {
                        submitLinkHref
                    }
                    val normalizedState = normalizePortalStatusState(statusText, actionText)
                    Log.d("PortalAuth", "  Historical row: course='$course', title='$title', actionText='$actionText', submitLink='$submitLink'")
                    
                    if (course.isEmpty()) continue
                    if (normalizedState != PortalStatusState.SUBMITTED && normalizedState != PortalStatusState.GRADED) continue
                    
                    submitted.add(Assignment(
                        course, title, deadline,
                        downloadLink, submitLink,
                        status = if (normalizedState == PortalStatusState.GRADED) AssignmentStatus.GRADED else AssignmentStatus.SUBMITTED,
                        submittedDate = "",
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
            
            // Add file with correct input name
            Log.d("PortalAuth", "Adding file: ${file.name}")
            Log.d("PortalAuth", "Using file input name: $fileInputName")
            val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
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
            
            // Look for spans/divs with "RequiredFieldValidator" or validation class
            val validatorSpans = doc2.select("[id*='Validator']")
            if (validatorSpans.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${validatorSpans.size} validator elements")
                validatorSpans.forEach { elem ->
                    val text = elem.text()
                    if (text.isNotEmpty()) {
                        Log.d("PortalAuth", "  Validator text: $text")
                    }
                }
            }
            
            // Look for any error or notification divs
            val errorDivs = doc2.select(".error, [id*='error'], .notification")
            if (errorDivs.isNotEmpty()) {
                Log.d("PortalAuth", "Found ${errorDivs.size} error divs")
                errorDivs.forEach { elem ->
                    val text = elem.text()
                    if (text.isNotEmpty()) {
                        Log.d("PortalAuth", "  Error div: $text")
                    }
                }
            }
            
            // Look for any summary control
            val summaryControls = doc2.select("[id*='ValidationSummary'], [id*='Summary']")
            summaryControls.forEach { elem ->
                val text = elem.text()
                if (text.isNotEmpty()) {
                    Log.d("PortalAuth", "  Summary control: $text")
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
            
            // Check for actual errors
            val hasError = responseHtml.contains("error", ignoreCase = true) && 
                          (responseHtml.contains("notification error", ignoreCase = true) ||
                           responseHtml.contains("invalid file", ignoreCase = true))

            val rejectionReason = when {
                responseHtml.contains("only .zip,.rar,.doc,.docx and .pdf allowed", true) ->
                    "Upload rejected: only .zip, .rar, .doc, .docx, .pdf are allowed."
                responseHtml.contains("required", true) && responseHtml.contains("fileuploadvalidator", true) ->
                    "Upload rejected: file missing or form not accepted."
                responseHtml.contains("invalid file", true) ->
                    "Upload rejected: invalid file."
                responseHtml.contains("maximum", true) && responseHtml.contains("size", true) ->
                    "Upload rejected: file too large."
                responseHtml.contains("closed", true) && responseHtml.contains("assignment", true) ->
                    "Upload rejected: assignment is closed."
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
            // 1. If we got a valid HTML page with form and viewstate = likely success
            // 2. If no file input field AND form reloaded = success
            // 3. If explicit success message = success
            // 4. If no actual error found AND page reloaded = success
            
            val successProof = when {
                hasSuccessMessage -> {
                    Log.d("PortalAuth", "Success: Found success message")
                    "Server returned explicit success confirmation."
                }
                hasError -> {
                    Log.d("PortalAuth", "Failed: Found error message")
                    null
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

    private fun normalizeUrl(href: String?, resolveBaseUrl: String = baseUrl): String {
        if (href.isNullOrBlank()) return ""
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed == "#" || trimmed.startsWith("javascript", true)) return ""
        if (trimmed.startsWith("http", true)) return trimmed
        return runCatching {
            resolveBaseUrl.toHttpUrl().resolve(trimmed)?.toString().orEmpty()
        }.getOrDefault("")
    }
}
