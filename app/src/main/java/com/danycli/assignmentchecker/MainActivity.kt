package com.danycli.assignmentchecker

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import com.danycli.assignmentchecker.ui.theme.Cyprus
import com.danycli.assignmentchecker.ui.theme.Sand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.Locale

private const val DISCLAIMER_URL = "https://github.com/danycli/Assignly#disclaimer"
private const val RELEASES_API_URL = "https://api.github.com/repos/danycli/Assignly/releases"
private const val RELEASES_FALLBACK_URL = "https://github.com/danycli/Assignly/releases/tag/Android_Application"
private const val CAPTCHA_RETRY_DELAY_MS = 250L
private const val CAPTCHA_BACKGROUND_RECHECK_ATTEMPTS = 2
private val explicitVersionRegex = Regex("(?i)(?:^|[^A-Za-z0-9])v(\\d+(?:\\.\\d+)*)\\b")
private val anyVersionTokenRegex = Regex("\\b(\\d+(?:\\.\\d+)*)\\b")
private val strictVersionRegex = Regex("^\\d+(?:\\.\\d+)*$")

private data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String
)

class MainActivity : ComponentActivity() {
    private val repository = PortalRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssignmentCheckerTheme {
                AppEntry(repository)
            }
        }
    }
}

@Composable
private fun AppEntry(repository: PortalRepository) {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var startupCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        val splashStart = System.currentTimeMillis()
        startupCredentials = withContext(Dispatchers.IO) {
            context.getSavedCredentials()
        }
        val elapsedMs = System.currentTimeMillis() - splashStart
        val remainingMs = 1_000L - elapsedMs
        if (remainingMs > 0) {
            delay(remainingMs)
        }
        showSplash = false
    }

    if (showSplash) {
        AppSplashScreen()
    } else {
        MainScreen(
            repository = repository,
            initialCredentials = startupCredentials,
            hasPerformedInitialCredentialCheck = true
        )
    }
}

@Composable
private fun AppSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Assignly Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Helper functions for session management
private fun Context.securePrefs() : android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(this)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        this,
        "secure_app_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun Context.migratePlaintextCredentialsIfNeeded() {
    val legacyPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val secure = securePrefs()
    val alreadyMigrated = secure.getBoolean("credentials_migrated", false)
    if (alreadyMigrated) {
        legacyPrefs.edit().remove("saved_username").remove("saved_password").apply()
        return
    }

    val legacyUsername = legacyPrefs.getString("saved_username", null)
    val legacyPassword = legacyPrefs.getString("saved_password", null)
    if (!legacyUsername.isNullOrBlank() && !legacyPassword.isNullOrBlank()) {
        secure.edit()
            .putString("saved_username", legacyUsername)
            .putString("saved_password", legacyPassword)
            .putBoolean("credentials_migrated", true)
            .apply()
    } else {
        secure.edit().putBoolean("credentials_migrated", true).apply()
    }

    legacyPrefs.edit().remove("saved_username").remove("saved_password").apply()
}

fun Context.saveCredentials(username: String, password: String) {
    migratePlaintextCredentialsIfNeeded()
    val prefs = securePrefs()
    prefs.edit().apply {
        putString("saved_username", username)
        putString("saved_password", password)
        apply()
    }
}

fun Context.getSavedCredentials(): Pair<String, String>? {
    migratePlaintextCredentialsIfNeeded()
    val prefs = securePrefs()
    val username = prefs.getString("saved_username", null)
    val password = prefs.getString("saved_password", null)
    return if (username != null && password != null) Pair(username, password) else null
}

fun Context.clearCredentials() {
    val secure = securePrefs()
    secure.edit()
        .remove("saved_username")
        .remove("saved_password")
        .apply()
    getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit()
        .remove("saved_username")
        .remove("saved_password")
        .apply()
}

private suspend fun <T> retryIo(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500,
    block: suspend () -> T
): T {
    var delayMs = initialDelayMs
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: IOException) {
            if (attempt >= maxAttempts) throw e
            delay(delayMs)
            delayMs *= 2
            attempt++
        }
    }
}

private fun mapLoginErrorToMessage(message: String?): String {
    val m = message?.lowercase().orEmpty()
    return when {
        m.contains("timed out") -> "Server timeout. Please try again."
        m.contains("network") || m.contains("unable to resolve host") || m.contains("failed to connect") ->
            "Network unavailable. Check your internet connection."
        m.contains("invalid id or password") || m.contains("invalid credentials") || m.contains("http 401") || m.contains("http 403") ->
            "Authentication failed. Check your ID/password."
        else -> "Authentication failed. Please try again."
    }
}

private fun extractVersionToken(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val withPrefix = explicitVersionRegex.find(value)?.groupValues?.getOrNull(1)
    if (!withPrefix.isNullOrBlank()) return withPrefix
    return anyVersionTokenRegex.find(value)?.groupValues?.getOrNull(1)
}

private fun extractReleaseVersionToken(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val withPrefix = explicitVersionRegex.find(value)?.groupValues?.getOrNull(1)
    if (!withPrefix.isNullOrBlank()) return withPrefix
    val trimmed = value.trim()
    return trimmed.takeIf { strictVersionRegex.matches(it) }
}

private fun compareVersionTokens(left: String, right: String): Int {
    val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
    val max = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until max) {
        val l = leftParts.getOrElse(index) { 0 }
        val r = rightParts.getOrElse(index) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}

private fun isRemoteVersionNewer(localVersionName: String, remoteVersion: String): Boolean {
    val localToken = extractVersionToken(localVersionName) ?: return false
    val remoteToken = extractVersionToken(remoteVersion) ?: return false
    return compareVersionTokens(remoteToken, localToken) > 0
}

private fun parseLatestReleaseInfo(json: String): AppUpdateInfo? {
    return try {
        val releases = JSONArray(json)
        var bestVersion: String? = null
        var bestUrl: String? = null

        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) continue

            val tagName = release.optString("tag_name")
            val releaseName = release.optString("name")
            val parsedVersion = extractReleaseVersionToken(tagName)
                ?: extractReleaseVersionToken(releaseName)
                ?: continue
            val releaseUrl = release.optString("html_url").ifBlank { RELEASES_FALLBACK_URL }

            if (bestVersion == null || compareVersionTokens(parsedVersion, bestVersion) > 0) {
                bestVersion = parsedVersion
                bestUrl = releaseUrl
            }
        }

        if (bestVersion == null) null else AppUpdateInfo(
            latestVersion = bestVersion,
            releaseUrl = bestUrl ?: RELEASES_FALLBACK_URL
        )
    } catch (e: JSONException) {
        Log.e("MainActivity", "Failed to parse releases API response: ${e.message}", e)
        null
    }
}

private fun fetchAppUpdateInfo(): AppUpdateInfo? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(RELEASES_API_URL).openConnection() as? HttpURLConnection)
            ?: return null
        connection.requestMethod = "GET"
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Assignly-Android-App")

        if (connection.responseCode !in 200..299) {
            Log.w("MainActivity", "Update check failed: HTTP ${connection.responseCode}")
            return null
        }

        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        parseLatestReleaseInfo(payload)
    } catch (e: Exception) {
        Log.e("MainActivity", "Update check failed: ${e.message}", e)
        null
    } finally {
        connection?.disconnect()
    }
}

private data class DashboardLoadResult(
    val pendingAssignments: List<Assignment>,
    val historicalAssignments: List<Assignment>,
    val profilePhoto: ByteArray?,
    val weakestAttendanceInsight: AttendanceInsight?
)

private suspend fun loadDashboardData(repository: PortalRepository): DashboardLoadResult {
    return withContext(Dispatchers.IO) {
        val (pending, submitted) = repository.fetchAssignments()
        DashboardLoadResult(
            pendingAssignments = pending,
            historicalAssignments = submitted,
            profilePhoto = repository.fetchCurrentStudentPhoto(),
            weakestAttendanceInsight = repository.fetchLowestAttendanceInsight()
        )
    }
}

private suspend fun loadHistoricalAndProfile(repository: PortalRepository): Pair<List<Assignment>, ByteArray?> {
    return withContext(Dispatchers.IO) {
        coroutineScope {
            val historicalDeferred = async { repository.fetchHistoricalAssignments() }
            val photoDeferred = async { repository.fetchCurrentStudentPhoto() }
            Pair(historicalDeferred.await(), photoDeferred.await())
        }
    }
}

private fun pickNonRepeatingMessage(candidates: List<String>, previousMessage: String?): String {
    if (candidates.isEmpty()) return ""
    if (candidates.size == 1) return candidates.first()
    val filtered = candidates.filter { it != previousMessage }
    return if (filtered.isNotEmpty()) filtered.random() else candidates.random()
}

private fun generateWelcomeStatusMessage(
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

private fun generateAttendanceSarcasmMessage(insight: AttendanceInsight?): String? {
    if (insight == null) {
        return "Attendance insight is missing right now. If you keep skipping, the report will roast you on its own soon."
    }
    val effectivePercentLabel = String.format(Locale.US, "%.0f%%", insight.effectivePercent)
    val courseName = insight.courseTitle

    return when {
        insight.effectivePercent < 50.0 ->
            "This subject $courseName is at $effectivePercentLabel attendance. At this point even your empty seat has better attendance."
        insight.effectivePercent < 75.0 ->
            "This subject $courseName is at $effectivePercentLabel attendance. The classroom remembers you mostly as a rumor."
        insight.effectivePercent < 90.0 ->
            "This subject $courseName is at $effectivePercentLabel attendance. Keep this up and your attendance sheet will start looking fictional."
        else ->
            "This subject $courseName is at $effectivePercentLabel attendance. You are safe for now, but don't get too creative with absences."
    }
}

private fun countSuccessfulSubmissions(assignments: List<Assignment>): Int {
    return assignments.count { assignment ->
        assignment.status == AssignmentStatus.SUBMITTED || assignment.status == AssignmentStatus.GRADED
    }
}

private data class InstructionFileDialogState(
    val assignment: Assignment,
    val files: List<InstructionFile>
)

enum class ScreenType {
    PENDING, HISTORICAL
}

private enum class AppPage {
    LOGIN, PENDING, HISTORICAL
}

@Composable
fun MainScreen(
    repository: PortalRepository,
    initialCredentials: Pair<String, String>? = null,
    hasPerformedInitialCredentialCheck: Boolean = false
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(initialCredentials != null) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var historicalAssignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var loggedInStudentName by remember { mutableStateOf<String?>(null) }
    var loggedInStudentPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var welcomeStatusMessage by remember { mutableStateOf("Your professors are still thinking how to annoy you in a brutal way possible.") }
    var attendanceInsightMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf<ScreenType>(ScreenType.PENDING) }
    var loadingTargetScreen by remember { mutableStateOf(ScreenType.PENDING) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var instructionFilesDialog by remember { mutableStateOf<InstructionFileDialogState?>(null) }
    var selectedInstructionFile by remember { mutableStateOf<InstructionFile?>(null) }
    var pendingCaptchaCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var shownUpdateVersion by remember { mutableStateOf<String?>(null) }
    var updateDialogInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var pendingExitConfirmation by remember { mutableStateOf(false) }
    var isPendingRefreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val pageForUi = when {
        !isLoggedIn -> AppPage.LOGIN
        currentScreen == ScreenType.HISTORICAL -> AppPage.HISTORICAL
        else -> AppPage.PENDING
    }

    suspend fun refreshAssignmentsState() {
        val dashboardData = loadDashboardData(repository)
        assignments = dashboardData.pendingAssignments
        historicalAssignments = dashboardData.historicalAssignments
        loggedInStudentName = repository.getCurrentStudentName()
        loggedInStudentPhoto = dashboardData.profilePhoto
        welcomeStatusMessage = generateWelcomeStatusMessage(
            pendingCount = dashboardData.pendingAssignments.size,
            submittedCount = countSuccessfulSubmissions(dashboardData.historicalAssignments),
            previousMessage = welcomeStatusMessage
        )
        attendanceInsightMessage = generateAttendanceSarcasmMessage(dashboardData.weakestAttendanceInsight)
    }

    suspend fun checkForAppUpdateIfNeeded() {
        val localVersion = com.danycli.assignmentchecker.BuildConfig.VERSION_NAME
        val remoteInfo = withContext(Dispatchers.IO) { fetchAppUpdateInfo() } ?: return
        if (shownUpdateVersion == remoteInfo.latestVersion) return
        if (!isRemoteVersionNewer(localVersion, remoteInfo.latestVersion)) {
            updateDialogInfo = null
            return
        }
        updateDialogInfo = remoteInfo
        shownUpdateVersion = remoteInfo.latestVersion
    }

    suspend fun syncWebViewSessionIntoRepository() {
        withContext(Dispatchers.Main) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            val browserUserAgent = runCatching { WebSettings.getDefaultUserAgent(context) }
                .getOrDefault(com.danycli.assignmentchecker.BuildConfig.PORTAL_USER_AGENT)
            repository.setUserAgentForSession(browserUserAgent)
            val portalBaseUrl = repository.getPortalBaseUrl()
            val portalLoginUrl = repository.getPortalLoginUrl()
            val portalOrigin = runCatching {
                val parsed = portalLoginUrl.toHttpUrl()
                "${parsed.scheme}://${parsed.host}"
            }.getOrDefault(portalBaseUrl)
            linkedSetOf(portalBaseUrl, portalLoginUrl, portalOrigin).forEach { sourceUrl ->
                repository.injectCookiesFromWebView(cookieManager.getCookie(sourceUrl), sourceUrl)
            }
        }
    }

    LaunchedEffect(pendingExitConfirmation) {
        if (pendingExitConfirmation) {
            delay(2_000)
            pendingExitConfirmation = false
        }
    }

    BackHandler(enabled = isLoggedIn && !showCaptchaDialog) {
        when (pageForUi) {
            AppPage.HISTORICAL -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.PENDING -> {
                if (pendingExitConfirmation) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    pendingExitConfirmation = true
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
            AppPage.LOGIN -> Unit
        }
    }

    suspend fun attemptPortalLogin(
        usernameInput: String,
        passwordInput: String,
        saveCredentialsOnSuccess: Boolean
    ) {
        val normalizedUser = usernameInput.trim().uppercase()
        suspend fun performLoginRequest(): LoginResult {
            syncWebViewSessionIntoRepository()
            return try {
                withTimeout(45_000) {
                    withContext(Dispatchers.IO) {
                        retryIo { repository.login(normalizedUser, passwordInput) }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                LoginResult.Error("Login timed out. Please try again.")
            } catch (e: IOException) {
                LoginResult.Error("Network error. Please try again.")
            }
        }
        suspend fun isCaptchaStillRequiredInBackground(): Boolean {
            syncWebViewSessionIntoRepository()
            return try {
                withTimeout(10_000) {
                    withContext(Dispatchers.IO) {
                        retryIo(maxAttempts = 2, initialDelayMs = 250) {
                            repository.isSecurityVerificationStillRequired()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                true
            } catch (e: IOException) {
                true
            }
        }
        var result = performLoginRequest()
        var silentCaptchaRechecksRemaining = CAPTCHA_BACKGROUND_RECHECK_ATTEMPTS
        while (result is LoginResult.CaptchaRequired && silentCaptchaRechecksRemaining > 0) {
            val captchaStillRequired = isCaptchaStillRequiredInBackground()
            if (!captchaStillRequired) {
                result = performLoginRequest()
                if (result !is LoginResult.CaptchaRequired) {
                    break
                }
            }
            silentCaptchaRechecksRemaining--
            if (silentCaptchaRechecksRemaining > 0) {
                delay(CAPTCHA_RETRY_DELAY_MS)
            }
        }
        if (result is LoginResult.CaptchaRequired) {
            val captchaStillRequiredBeforeDialog = isCaptchaStillRequiredInBackground()
            if (!captchaStillRequiredBeforeDialog) {
                result = performLoginRequest()
            }
        }

        when (result) {
            is LoginResult.Success -> {
                pendingCaptchaCredentials = null
                showCaptchaDialog = false
                try {
                    refreshAssignmentsState()
                    isLoggedIn = true
                    if (saveCredentialsOnSuccess) {
                        context.saveCredentials(normalizedUser, passwordInput)
                    }
                    scope.launch {
                        checkForAppUpdateIfNeeded()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error fetching assignments: ${e.message}", e)
                    Toast.makeText(context, "Error loading assignments: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            is LoginResult.InvalidCredentials -> {
                pendingCaptchaCredentials = null
                showCaptchaDialog = false
                Toast.makeText(context, "Authentication failed. Check your ID/password.", Toast.LENGTH_LONG).show()
            }
            is LoginResult.CaptchaRequired -> {
                pendingCaptchaCredentials = normalizedUser to passwordInput
                showCaptchaDialog = true
                Toast.makeText(context, "Security verification required. Complete CAPTCHA in-app, then continue.", Toast.LENGTH_LONG).show()
            }
            is LoginResult.Error -> {
                Toast.makeText(context, mapLoginErrorToMessage(result.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    val instructionDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val file = selectedInstructionFile
        if (uri != null && file != null) {
            loadingTargetScreen = currentScreen
            isLoading = true
            scope.launch {
                var downloadTimeout = false
                val result = try {
                    withTimeout(90_000) {
                        withContext(Dispatchers.IO) {
                            when (val downloadResult = repository.downloadAssignment(file.downloadLink)) {
                                is DownloadResult.Success -> {
                                    if (writeBytesToUri(context, uri, downloadResult.bytes)) {
                                        downloadResult
                                    } else {
                                        DownloadResult.Error("Could not save downloaded file.")
                                    }
                                }
                                else -> downloadResult
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    downloadTimeout = true
                    DownloadResult.Error("Download timed out.")
                } catch (e: IOException) {
                    DownloadResult.NetworkError
                }

                withContext(Dispatchers.Main) {
                    val msg = when {
                        downloadTimeout -> "Server timeout during download."
                        result is DownloadResult.Success -> "Instruction file downloaded: ${result.fileName}"
                        result is DownloadResult.NetworkError -> "Network unavailable. Download could not start."
                        result is DownloadResult.Rejected -> result.reason
                        result is DownloadResult.Error -> result.message
                        else -> "Download failed."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                isLoading = false
            }
        }
        selectedInstructionFile = null
    }
    
    // Try auto-login on app startup
    LaunchedEffect(Unit) {
        val savedCreds = if (hasPerformedInitialCredentialCheck) {
            initialCredentials
        } else {
            context.getSavedCredentials()
        }
        if (savedCreds != null) {
            val (savedUser, savedPass) = savedCreds
            loadingTargetScreen = ScreenType.PENDING
            isLoading = true
            scope.launch {
                attemptPortalLogin(
                    usernameInput = savedUser,
                    passwordInput = savedPass,
                    saveCredentialsOnSuccess = false
                )
                isLoading = false
            }
        } else if (hasPerformedInitialCredentialCheck) {
            isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Sand) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = pageForUi,
                animationSpec = tween(durationMillis = 280, easing = LinearEasing),
                label = "screen_crossfade"
            ) { page ->
                when (page) {
                    AppPage.LOGIN -> {
                        LoginScreen(
                            isLoading = isLoading,
                            onOpenDisclaimer = { uriHandler.openUri(DISCLAIMER_URL) },
                            onLogin = { user, pass ->
                                loadingTargetScreen = ScreenType.PENDING
                                isLoading = true
                                scope.launch {
                                    attemptPortalLogin(
                                        usernameInput = user,
                                        passwordInput = pass,
                                        saveCredentialsOnSuccess = true
                                    )
                                    isLoading = false
                                }
                            }
                        )
                    }
                    AppPage.PENDING -> {
                        AssignmentsList(
                            assignments = assignments,
                            historicalAssignments = historicalAssignments,
                            loggedInStudentName = loggedInStudentName,
                            loggedInStudentPhoto = loggedInStudentPhoto,
                            welcomeStatusMessage = welcomeStatusMessage,
                            attendanceInsightMessage = attendanceInsightMessage,
                            isRefreshing = isPendingRefreshing,
                            onOpenDisclaimer = { uriHandler.openUri(DISCLAIMER_URL) },
                            onRefresh = {
                                if (isPendingRefreshing) return@AssignmentsList
                                loadingTargetScreen = ScreenType.PENDING
                                isPendingRefreshing = true
                                isLoading = true
                                scope.launch {
                                    runCatching { refreshAssignmentsState() }
                                        .onFailure { e ->
                                            Log.e("MainActivity", "Refresh failed: ${e.message}", e)
                                            Toast.makeText(context, "Refresh failed. Please try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    isPendingRefreshing = false
                                    isLoading = false
                                }
                            },
                            onLogout = {
                                scope.coroutineContext.cancelChildren()
                                uploadJob?.cancel()
                                uploadJob = null
                                isPendingRefreshing = false
                                isLoading = false
                                isLoggedIn = false
                                assignments = emptyList()
                                historicalAssignments = emptyList()
                                loggedInStudentName = null
                                loggedInStudentPhoto = null
                                welcomeStatusMessage = "Your professors are still thinking how to annoy you in a brutal way possible."
                                attendanceInsightMessage = null
                                currentScreen = ScreenType.PENDING
                                context.clearCredentials()
                            },
                            onDownloadRequested = { assignment ->
                                loadingTargetScreen = ScreenType.PENDING
                                isLoading = true
                                scope.launch {
                                    val result = try {
                                        withTimeout(45_000) {
                                            withContext(Dispatchers.IO) {
                                                repository.fetchInstructionFiles(assignment.downloadLink)
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        InstructionFilesResult.Error("Loading instruction files timed out.")
                                    } catch (e: IOException) {
                                        InstructionFilesResult.NetworkError
                                    }
                                    withContext(Dispatchers.Main) {
                                        val msg = when {
                                            result is InstructionFilesResult.Success -> null
                                            result is InstructionFilesResult.NetworkError -> "Network unavailable. Could not load instruction files."
                                            result is InstructionFilesResult.Rejected -> result.reason
                                            result is InstructionFilesResult.Error -> result.message
                                            else -> "Failed to load instruction files."
                                        }
                                        if (result is InstructionFilesResult.Success) {
                                            instructionFilesDialog = InstructionFileDialogState(assignment, result.files)
                                        } else if (msg != null) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            onUploadRequested = { assignment, uri ->
                                loadingTargetScreen = ScreenType.PENDING
                                isLoading = true
                                uploadJob?.cancel()
                                uploadJob = scope.launch {
                                    var uploadTimeout = false
                                    val result = try {
                                        withTimeout(90_000) {
                                            withContext(Dispatchers.IO) {
                                                val file = uriToFile(context, uri)
                                                if (file != null) {
                                                    retryIo { repository.uploadAssignment(assignment.submitLink, file) }
                                                } else UploadResult.Error("Could not read selected file.")
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        uploadTimeout = true
                                        UploadResult.Timeout
                                    } catch (e: IOException) {
                                        UploadResult.NetworkError
                                    }
                                    if (result is UploadResult.Success) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Uploaded Successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            val msg = when {
                                                uploadTimeout -> "Server timeout during upload."
                                                result is UploadResult.NetworkError -> "Network unavailable. Upload could not start."
                                                result is UploadResult.Rejected -> result.reason
                                                result is UploadResult.Error -> result.message
                                                else -> "Upload rejected by server."
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    runCatching { refreshAssignmentsState() }
                                        .onFailure { refreshError ->
                                            Log.e("MainActivity", "Auto-refresh after upload failed: ${refreshError.message}", refreshError)
                                        }
                                    isLoading = false
                                }
                            },
                            onViewHistorical = {
                                currentScreen = ScreenType.HISTORICAL
                                loadingTargetScreen = ScreenType.HISTORICAL
                                isLoading = true
                                scope.launch {
                                    val (fetched, photoBytes) = loadHistoricalAndProfile(repository)
                                    historicalAssignments = fetched
                                    loggedInStudentPhoto = photoBytes
                                    welcomeStatusMessage = generateWelcomeStatusMessage(
                                        pendingCount = assignments.size,
                                        submittedCount = countSuccessfulSubmissions(fetched),
                                        previousMessage = welcomeStatusMessage
                                    )
                                    isLoading = false
                                }
                            }
                        )
                    }
                    AppPage.HISTORICAL -> {
                        HistoricalAssignmentsScreen(
                            assignments = historicalAssignments,
                            loggedInStudentName = loggedInStudentName,
                            onOpenDisclaimer = { uriHandler.openUri(DISCLAIMER_URL) },
                            onNavigateBack = {
                                currentScreen = ScreenType.PENDING
                            },
                            onDownloadRequested = { assignment ->
                                loadingTargetScreen = ScreenType.HISTORICAL
                                isLoading = true
                                scope.launch {
                                    val result = try {
                                        withTimeout(45_000) {
                                            withContext(Dispatchers.IO) {
                                                repository.fetchInstructionFiles(assignment.downloadLink)
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        InstructionFilesResult.Error("Loading instruction files timed out.")
                                    } catch (e: IOException) {
                                        InstructionFilesResult.NetworkError
                                    }
                                    withContext(Dispatchers.Main) {
                                        val msg = when {
                                            result is InstructionFilesResult.Success -> null
                                            result is InstructionFilesResult.NetworkError -> "Network unavailable. Could not load instruction files."
                                            result is InstructionFilesResult.Rejected -> result.reason
                                            result is InstructionFilesResult.Error -> result.message
                                            else -> "Failed to load instruction files."
                                        }
                                        if (result is InstructionFilesResult.Success) {
                                            instructionFilesDialog = InstructionFileDialogState(assignment, result.files)
                                        } else if (msg != null) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            onReuploadRequested = { assignment, uri ->
                                loadingTargetScreen = ScreenType.HISTORICAL
                                isLoading = true
                                uploadJob?.cancel()
                                uploadJob = scope.launch {
                                    var uploadTimeout = false
                                    val result = try {
                                        withTimeout(90_000) {
                                            withContext(Dispatchers.IO) {
                                                val file = uriToFile(context, uri)
                                                if (file != null) {
                                                    retryIo { repository.uploadAssignment(assignment.submitLink, file) }
                                                } else UploadResult.Error("Could not read selected file.")
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        uploadTimeout = true
                                        UploadResult.Timeout
                                    } catch (e: IOException) {
                                        UploadResult.NetworkError
                                    }
                                    if (result is UploadResult.Success) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Re-uploaded Successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            val msg = when {
                                                uploadTimeout -> "Server timeout during upload."
                                                result is UploadResult.NetworkError -> "Network unavailable. Upload could not start."
                                                result is UploadResult.Rejected -> result.reason
                                                result is UploadResult.Error -> result.message
                                                else -> "Upload rejected by server."
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    runCatching { refreshAssignmentsState() }
                                        .onFailure { refreshError ->
                                            Log.e("MainActivity", "Auto-refresh after re-upload failed: ${refreshError.message}", refreshError)
                                        }
                                    isLoading = false
                                }
                            }
                        )
                    }
                }
            }

            if (isLoading) {
                val loadingMessage = when {
                    pageForUi == AppPage.LOGIN -> "Signing in..."
                    loadingTargetScreen == ScreenType.HISTORICAL -> "Loading historical assignments..."
                    else -> "Loading assignments..."
                }
                LoadingStatusOverlay(message = loadingMessage)
            }
        }
    }

    if (showCaptchaDialog) {
        CaptchaWebViewDialog(
            repository = repository,
            onDismiss = { showCaptchaDialog = false },
            onCaptchaSolved = {
                showCaptchaDialog = false
                val creds = pendingCaptchaCredentials
                if (creds != null) {
                    loadingTargetScreen = ScreenType.PENDING
                    isLoading = true
                    scope.launch {
                        attemptPortalLogin(
                            usernameInput = creds.first,
                            passwordInput = creds.second,
                            saveCredentialsOnSuccess = true
                        )
                        isLoading = false
                    }
                }
            }
        )
    }

    val updateInfo = updateDialogInfo
    if (updateInfo != null) {
        Dialog(
            onDismissRequest = { updateDialogInfo = null },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF101418) else Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update Available",
                            color = Cyprus,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { updateDialogInfo = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close update dialog",
                                tint = Cyprus
                            )
                        }
                    }
                    Text(
                        text = "A newer version (v${updateInfo.latestVersion}) is available.",
                        color = Color(0xFF2E2E2E),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { uriHandler.openUri(updateInfo.releaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyprus)
                    ) {
                        Text("Update now", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    val dialogState = instructionFilesDialog
    if (dialogState != null) {
        AlertDialog(
            onDismissRequest = { instructionFilesDialog = null },
            title = {
                Text(
                    text = "Instruction Files",
                    fontWeight = FontWeight.Bold,
                    color = Cyprus
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dialogState.assignment.assignmentTitle,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 2
                    )
                    dialogState.files.forEach { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = file.fileName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = Color.Black,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        selectedInstructionFile = file
                                        instructionDownloadLauncher.launch(file.fileName)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Cyprus)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download file",
                                        tint = Cyprus,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", color = Cyprus, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { instructionFilesDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun rememberSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color.LightGray.copy(alpha = 0.45f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.45f)
        ),
        start = Offset(shimmerOffset - 200f, shimmerOffset - 200f),
        end = Offset(shimmerOffset, shimmerOffset)
    )
}

@Composable
private fun SkeletonBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingAssignmentsSkeleton() {
    val brush = rememberSkeletonBrush()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "My Assignments",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Cyprus),
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White)
                    }
                }
            )
        },
        containerColor = Sand
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SkeletonBlock(
                    brush = brush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            for (index in 0 until 5) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color.White else Color(0xFFF0F0F0)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.75f).height(16.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.35f).height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SkeletonBlock(
                                    brush = brush,
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                SkeletonBlock(
                                    brush = brush,
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoricalAssignmentsSkeleton() {
    val brush = rememberSkeletonBrush()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Assignment History",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Cyprus),
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            )
        },
        containerColor = Sand
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (index in 0 until 5) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color.White else Color(0xFFF0F0F0)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(1f).height(32.dp))
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(1f).height(32.dp))
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(0.7f).height(32.dp))
                            }
                            SkeletonBlock(
                                brush = brush,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerFooter(
    modifier: Modifier = Modifier,
    onOpenDisclaimer: () -> Unit
) {
    TextButton(
        onClick = onOpenDisclaimer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Disclaimer",
            color = Cyprus,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LoadingStatusOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.08f))
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Cyprus,
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    color = Cyprus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onOpenDisclaimer: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var usernameFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Cyprus, Cyprus.copy(alpha = 0.7f))
                        )
                    )
                    .shadow(8.dp, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Assignment Portal",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Cyprus,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Manage your coursework efficiently",
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Registration Number", fontWeight = FontWeight.Medium) },
                        placeholder = { Text("e.g. SP25-BCS-001", fontWeight = FontWeight.Light) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { usernameFocused = it.isFocused },
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyprus,
                            unfocusedBorderColor = Cyprus.copy(alpha = 0.2f),
                            focusedLabelColor = Cyprus,
                            cursorColor = Cyprus,
                            focusedContainerColor = Cyprus.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = if (usernameFocused) Cyprus else Cyprus.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", fontWeight = FontWeight.Medium) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { passwordFocused = it.isFocused },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = null,
                                    tint = Cyprus.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        enabled = !isLoading,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyprus,
                            unfocusedBorderColor = Cyprus.copy(alpha = 0.2f),
                            focusedLabelColor = Cyprus,
                            cursorColor = Cyprus,
                            focusedContainerColor = Cyprus.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                tint = if (passwordFocused) Cyprus else Cyprus.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { onLogin(username, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyprus),
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Signing in...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Text(
                                "SIGN IN",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0ED))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Cyprus),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Registration Format",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyprus
                        )
                        Text(
                            text = "Session-Program-RollNo",
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "e.g. SP25-BCS-001",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.45f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptchaWebViewDialog(
    repository: PortalRepository,
    onDismiss: () -> Unit,
    onCaptchaSolved: () -> Unit
) {
    val context = LocalContext.current
    val portalBaseUrl = remember { repository.getPortalBaseUrl() }
    val loginUrl = remember { repository.getPortalLoginUrl() }
    val portalHost = remember(loginUrl) { runCatching { loginUrl.toHttpUrl().host }.getOrDefault("") }
    val loginScheme = remember(loginUrl) { runCatching { loginUrl.toHttpUrl().scheme }.getOrDefault("https") }
    var pageTitle by remember { mutableStateOf("Security Verification") }
    var isPageLoading by remember { mutableStateOf(true) }
    var challengeLooksSolved by remember { mutableStateOf(false) }
    var challengeEncountered by remember { mutableStateOf(false) }
    var clearanceCookieSeen by remember { mutableStateOf(false) }
    var hasAutoSubmitted by remember { mutableStateOf(false) }
    var noChallengeBypassReady by remember { mutableStateOf(false) }
    var isCompletingVerification by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(loginUrl) }
    var shouldRenderWebView by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val webViewUa = remember {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrDefault(com.danycli.assignmentchecker.BuildConfig.PORTAL_USER_AGENT)
    }

    fun isLikelyChallenge(url: String, title: String): Boolean {
        val normalizedUrl = url.lowercase()
        val normalizedTitle = title.lowercase()
        return normalizedUrl.contains("/cdn-cgi/") ||
            normalizedUrl.contains("challenge-platform") ||
            normalizedUrl.contains("captcha") ||
            normalizedUrl.contains("security") ||
            normalizedTitle.contains("security verification") ||
            normalizedTitle.contains("just a moment") ||
            normalizedTitle.contains("verify you are human")
    }

    fun isPortalHostUrl(url: String): Boolean {
        if (portalHost.isBlank()) return false
        val candidateHost = runCatching { url.toHttpUrl().host.lowercase() }.getOrDefault("")
        if (candidateHost.isBlank()) return false
        val canonicalPortalHost = portalHost.lowercase()
        return candidateHost == canonicalPortalHost ||
            candidateHost.endsWith(".$canonicalPortalHost") ||
            canonicalPortalHost.endsWith(".$candidateHost")
    }

    fun injectCookiesFromCurrentSession(): Int {
        val manager = CookieManager.getInstance()
        var totalInjected = 0
        val targetUrls = linkedSetOf(
            portalBaseUrl,
            loginUrl,
            currentUrl,
            "$loginScheme://$portalHost"
        ).filter { it.isNotBlank() }
        targetUrls.forEach { url ->
            val cookieHeader = manager.getCookie(url)
            totalInjected += repository.injectCookiesFromWebView(cookieHeader, url)
        }
        return totalInjected
    }

    suspend fun isCaptchaStillRequiredBeforeWebView(): Boolean {
        return try {
            withTimeout(10_000) {
                withContext(Dispatchers.IO) {
                    retryIo(maxAttempts = 2, initialDelayMs = CAPTCHA_RETRY_DELAY_MS) {
                        repository.isSecurityVerificationStillRequired()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            true
        } catch (e: IOException) {
            true
        }
    }

    fun completeCaptchaFlow(message: String? = null, showToast: Boolean = true) {
        if (hasAutoSubmitted) return
        repository.setUserAgentForSession(webViewUa)
        CookieManager.getInstance().flush()
        val injected = injectCookiesFromCurrentSession()
        if (injected <= 0) {
            isCompletingVerification = false
            return
        }
        hasAutoSubmitted = true
        isCompletingVerification = true
        webViewRef.value?.stopLoading()
        webViewRef.value?.loadUrl("about:blank")
        if (showToast && !message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        onCaptchaSolved()
    }

    fun scheduleAutoContinueIfReady() {
        if (hasAutoSubmitted || !challengeEncountered || !clearanceCookieSeen || !challengeLooksSolved || !isPortalHostUrl(currentUrl)) {
            isCompletingVerification = false
            return
        }
        completeCaptchaFlow("Verification completed. Signing in...")
    }

    fun scheduleNoChallengeContinueIfReady() {
        if (hasAutoSubmitted || !noChallengeBypassReady || !isPortalHostUrl(currentUrl)) {
            isCompletingVerification = false
            return
        }
        completeCaptchaFlow(showToast = false)
    }

    fun shouldFinishBeforePortalPaint(targetUrl: String): Boolean {
        if (hasAutoSubmitted || targetUrl.isBlank()) return false
        return challengeEncountered &&
            isPortalHostUrl(targetUrl) &&
            !isLikelyChallenge(targetUrl, "")
    }

    LaunchedEffect(Unit) {
        val captchaStillRequired = isCaptchaStillRequiredBeforeWebView()
        if (!captchaStillRequired) {
            onCaptchaSolved()
            return@LaunchedEffect
        }
        shouldRenderWebView = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF101418) else Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 460.dp, max = 700.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle.ifBlank { "Security Verification" },
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close verification"
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { webViewRef.value?.reload() }) {
                            Text("Reload")
                        }
                    }
                )

                if (isPageLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Cyprus
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (shouldRenderWebView) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                WebView(viewContext).apply {
                                    webViewRef.value = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    settings.databaseEnabled = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.userAgentString = webViewUa
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    settings.loadsImagesAutomatically = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    repository.setUserAgentForSession(webViewUa)

                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            isPageLoading = true
                                            noChallengeBypassReady = false
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                                val normalizedUrl = url.lowercase()
                                                if (normalizedUrl.contains("/cdn-cgi/") || normalizedUrl.contains("challenge-platform")) {
                                                    challengeEncountered = true
                                                }
                                                val cookieSnapshot = CookieManager.getInstance().getCookie(url)
                                                    ?: CookieManager.getInstance().getCookie(portalBaseUrl)
                                                val hasClearanceCookie = cookieSnapshot?.contains("cf_clearance=", ignoreCase = true) == true
                                                clearanceCookieSeen = clearanceCookieSeen || hasClearanceCookie
                                                if (shouldFinishBeforePortalPaint(url)) {
                                                    completeCaptchaFlow("Verification completed. Signing in...")
                                                }
                                            }
                                            super.onPageStarted(view, url, favicon)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                            }
                                            pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Security Verification"
                                            isPageLoading = false
                                            val resolvedUrl = url.orEmpty()
                                            val resolvedTitle = view?.title.orEmpty()
                                            val cookieSnapshot = CookieManager.getInstance().getCookie(resolvedUrl)
                                                ?: CookieManager.getInstance().getCookie(portalBaseUrl)
                                            val hasClearanceCookie = cookieSnapshot?.contains("cf_clearance=", ignoreCase = true) == true
                                            val hasChallengeCookie = cookieSnapshot?.let { cookies ->
                                                cookies.contains("__cf_bm=", ignoreCase = true) ||
                                                    cookies.contains("cf_chl", ignoreCase = true)
                                            } == true
                                            val normalizedUrl = resolvedUrl.lowercase()
                                            val onChallengeEndpoint = normalizedUrl.contains("/cdn-cgi/") ||
                                                normalizedUrl.contains("challenge-platform")
                                            val likelyChallenge = isLikelyChallenge(resolvedUrl, resolvedTitle) || onChallengeEndpoint
                                            if (likelyChallenge || (hasChallengeCookie && !hasClearanceCookie)) {
                                                challengeEncountered = true
                                            }
                                            clearanceCookieSeen = clearanceCookieSeen || hasClearanceCookie
                                            val portalWithoutChallenge = isPortalHostUrl(resolvedUrl) &&
                                                !onChallengeEndpoint &&
                                                !likelyChallenge
                                            noChallengeBypassReady = !challengeEncountered && portalWithoutChallenge
                                            challengeLooksSolved = isPortalHostUrl(resolvedUrl) &&
                                                !onChallengeEndpoint &&
                                                !likelyChallenge &&
                                                challengeEncountered &&
                                                clearanceCookieSeen
                                            injectCookiesFromCurrentSession()
                                            scheduleAutoContinueIfReady()
                                            scheduleNoChallengeContinueIfReady()
                                            super.onPageFinished(view, url)
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString().orEmpty()
                                            if ((request?.isForMainFrame != false) && shouldFinishBeforePortalPaint(targetUrl)) {
                                                completeCaptchaFlow("Verification completed. Signing in...")
                                                return true
                                            }
                                            return false
                                        }
                                    }
                                    loadUrl(loginUrl)
                                }
                            },
                            update = { webView ->
                                webViewRef.value = webView
                            }
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (isSystemInDarkTheme()) Color(0xFF101418) else Color.White
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Cyprus)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Checking verification status...",
                                    color = Cyprus,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (isCompletingVerification || hasAutoSubmitted) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (isSystemInDarkTheme()) Color(0xFF101418) else Color.White
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Cyprus)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Verification completed. Finishing sign-in...",
                                    color = Cyprus,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (noChallengeBypassReady && !hasAutoSubmitted) {
                        OutlinedButton(
                            onClick = {
                                completeCaptchaFlow("Continuing sign-in...")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyprus)
                        ) {
                            Text("Continue")
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyprus)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyprus)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                }

                if (noChallengeBypassReady && !hasAutoSubmitted) {
                    Text(
                        text = "No CAPTCHA prompt detected. Sign-in continues automatically.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = Cyprus
                    )
                } else if (!challengeLooksSolved) {
                    Text(
                        text = "Solve CAPTCHA. Sign-in continues automatically.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = Cyprus
                    )
                } else if (!hasAutoSubmitted) {
                    Text(
                        text = "Verification detected. Completing sign-in...",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = Cyprus
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading()
                clearHistory()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webViewRef.value = null
            CookieManager.getInstance().flush()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsList(
    assignments: List<Assignment>,
    historicalAssignments: List<Assignment>,
    loggedInStudentName: String?,
    loggedInStudentPhoto: ByteArray?,
    welcomeStatusMessage: String,
    attendanceInsightMessage: String?,
    isRefreshing: Boolean,
    onOpenDisclaimer: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit,
    onUploadRequested: (Assignment, Uri) -> Unit,
    onViewHistorical: () -> Unit
) {
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAssignment?.let { assignment ->
                onUploadRequested(assignment, it)
            }
        }
    }

    val profileBitmap = remember(loggedInStudentPhoto) {
        loggedInStudentPhoto?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "My Assignments",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (!loggedInStudentName.isNullOrBlank()) {
                            Text(
                                "Logged in: $loggedInStudentName",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Cyprus),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Sand
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StudentWelcomeCard(
                        studentName = loggedInStudentName,
                        profileBitmap = profileBitmap,
                        statusMessage = welcomeStatusMessage,
                        attendanceInsightMessage = attendanceInsightMessage
                    )
                }

                if (assignments.isEmpty() && historicalAssignments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No assignments yet",
                                color = Cyprus.copy(alpha = 0.55f),
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    // Assignment Summary Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                                .clickable { onViewHistorical() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Cyprus)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Assignment Summary",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val pendingAssignmentsCount = assignments.count { it.status == AssignmentStatus.PENDING }
                                    val submittedAssignmentsCount = countSuccessfulSubmissions(historicalAssignments)
                                    val totalAssignmentsCount = pendingAssignmentsCount + submittedAssignmentsCount

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("$totalAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Total", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("$pendingAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                                        Text("Pending", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("$submittedAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        Text("Submitted", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        if (assignments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No pending assignments",
                                    color = Cyprus.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Text(
                                text = "Pending Assignments",
                                color = Cyprus,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    itemsIndexed(
                        items = assignments,
                        key = { index, assignment ->
                            "${assignment.courseTitle}|${assignment.assignmentTitle}|${assignment.deadline}|${assignment.status}|$index"
                        },
                        contentType = { _, _ -> "pending-assignment-row-lite" }
                    ) { index, assignment ->
                        PendingAssignmentRow(
                            index = index + 1,
                            assignment = assignment,
                            onDownloadRequested = { onDownloadRequested(assignment) },
                            onUploadRequested = {
                                if (assignment.submitLink.isNotEmpty()) {
                                    selectedAssignment = assignment
                                    launcher.launch("*/*")
                                }
                            }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    DisclaimerFooter(onOpenDisclaimer = onOpenDisclaimer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalAssignmentsScreen(
    assignments: List<Assignment>,
    loggedInStudentName: String?,
    onOpenDisclaimer: () -> Unit,
    onNavigateBack: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit = { _ -> },
    onReuploadRequested: (Assignment, Uri) -> Unit = { _, _ -> }
) {
    var historySearchQuery by remember { mutableStateOf("") }
    var historySearchFocused by remember { mutableStateOf(false) }
    val historySearchBorderColor = if (historySearchFocused) Cyprus else Cyprus.copy(alpha = 0.36f)
    val historySearchElevation = 2.dp
    val historySearchBorderWidth = 1.2.dp
    val filteredHistoryAssignments by remember(assignments, historySearchQuery) {
        derivedStateOf {
            if (historySearchQuery.isBlank()) {
                assignments
            } else {
                val query = historySearchQuery.trim()
                assignments.filter { assignment ->
                    assignment.courseTitle.contains(query, ignoreCase = true) ||
                        assignment.assignmentTitle.contains(query, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Assignment History",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (!loggedInStudentName.isNullOrBlank()) {
                            Text(
                                "Logged in: $loggedInStudentName",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Cyprus),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Sand
    ) { padding ->
        var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
        val focusManager = LocalFocusManager.current
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null && selectedAssignment != null) {
                onReuploadRequested(selectedAssignment!!, uri)
                selectedAssignment = null
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                },
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = historySearchElevation,
                                shape = RoundedCornerShape(50.dp),
                                ambientColor = Cyprus.copy(alpha = 0.10f),
                                spotColor = Cyprus.copy(alpha = 0.10f)
                            )
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFCFBF7),
                                        Color(0xFFF6F4EE)
                                    )
                                ),
                                shape = RoundedCornerShape(50.dp)
                            )
                            .border(
                                width = historySearchBorderWidth,
                                color = historySearchBorderColor,
                                shape = RoundedCornerShape(50.dp)
                            )
                            .padding(horizontal = 15.dp, vertical = 18.dp)
                    ) {
                        BasicTextField(
                            value = historySearchQuery,
                            onValueChange = { historySearchQuery = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = Color(0xFF222222),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(Color(0xFF222222)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { historySearchFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xB3222222),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (historySearchQuery.isBlank()) {
                                            Text(
                                                text = "Search by subject or assignment",
                                                color = Color(0xCC222222),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Start,
                                                maxLines = 1
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (filteredHistoryAssignments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (assignments.isEmpty()) "No assignment history" else "No matching assignments",
                            color = Cyprus.copy(alpha = 0.55f),
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                items(
                    items = filteredHistoryAssignments,
                    key = { assignment ->
                        "${assignment.courseTitle}|${assignment.assignmentTitle}|${assignment.deadline}|${assignment.status}"
                    }
                ) { assignment ->
                    val isOpenVal = remember(assignment.deadline) { assignment.isOpen() }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, shape = RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                assignment.courseTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0066CC)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                assignment.assignmentTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Deadline:", fontSize = 11.sp, color = Color.Gray)
                                    Text(assignment.deadline, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    val isNotSubmitted = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
                                    Text(if (isNotSubmitted) "Attempt:" else "Submitted:", fontSize = 11.sp, color = Color.Gray)
                                    val attemptColor = if (isNotSubmitted) Color(0xFFD32F2F) else Color.Black
                                    Text(
                                        if (isNotSubmitted) "Not Submitted" else assignment.submittedDate ?: "N/A",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = attemptColor
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Status:", fontSize = 11.sp, color = Color.Gray)
                                    val isNotSubmitted = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
                                    val statusColor = when {
                                        isNotSubmitted -> Color(0xFFD32F2F)
                                        isOpenVal -> Color(0xFF4CAF50)
                                        else -> Color(0xFFD32F2F)
                                    }
                                    Text(
                                        if (isNotSubmitted) "Closed" else assignment.getOpenClosedLabel(isOpenVal),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (!assignment.feedback.isNullOrEmpty()) {
                                Text(
                                    "Feedback: ${assignment.feedback}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666),
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Download stays available whenever a link exists; re-upload stays gated by open status.
                            val hasSubmitLink = assignment.submitLink.isNotEmpty()
                            val hasDownloadLink = assignment.downloadLink.isNotEmpty()

                            OutlinedButton(
                                onClick = {
                                    if (hasDownloadLink) {
                                        onDownloadRequested(assignment)
                                    }
                                },
                                enabled = hasDownloadLink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Cyprus)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download instructions",
                                        tint = if (hasDownloadLink) Cyprus else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (hasDownloadLink) "Download Instructions" else "Instructions Unavailable",
                                        fontSize = 12.sp,
                                        color = if (hasDownloadLink) Cyprus else Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isOpenVal && hasSubmitLink) {
                                Button(
                                    onClick = {
                                        selectedAssignment = assignment
                                        launcher.launch("*/*")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyprus),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Upload,
                                            contentDescription = "Change File",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Change File",
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                DisclaimerFooter(onOpenDisclaimer = onOpenDisclaimer)
            }
        }
    }
}

@Composable
fun AssignmentCard(assignment: Assignment, onDownload: () -> Unit, onSubmit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = assignment.courseTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Cyprus
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = assignment.assignmentTitle,
                fontSize = 15.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Sand)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Deadline: ",
                    color = Cyprus,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = assignment.deadline,
                    color = Color(0xFFD32F2F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Cyprus)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyprus)
                ) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PendingAssignmentRow(
    index: Int,
    assignment: Assignment,
    onDownloadRequested: () -> Unit,
    onUploadRequested: () -> Unit
) {
    val canDownload = assignment.downloadLink.isNotEmpty()
    val canUpload = assignment.submitLink.isNotEmpty()
    val isNotSubmittedClosed = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
    val statusText = if (isNotSubmittedClosed) "Not submitted • Closed" else "Pending"
    val statusColor = if (isNotSubmittedClosed) Color(0xFFD32F2F) else Color(0xFF4CAF50)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (index % 2 == 0) Color.White else Color(0xFFFBFCFF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = index.toString(),
                fontSize = 11.sp,
                color = Color(0xFF5B6775),
                modifier = Modifier.width(20.dp),
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = assignment.courseTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0066CC),
                    maxLines = 1
                )
                Text(
                    text = assignment.assignmentTitle,
                    fontSize = 11.sp,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1
                )
                Text(
                    text = "Due: ${assignment.deadline}",
                    fontSize = 10.sp,
                    color = Color(0xFF5B6775),
                    maxLines = 1
                )
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    maxLines = 1
                )
            }
            Text(
                text = if (canDownload) "Download" else "No file",
                fontSize = 11.sp,
                color = if (canDownload) Color(0xFF0066CC) else Color.Gray,
                modifier = Modifier.clickable(enabled = canDownload, onClick = onDownloadRequested)
            )
            Text(
                text = if (canUpload) "Upload" else "Closed",
                fontSize = 11.sp,
                color = if (canUpload) Cyprus else Color.Gray,
                modifier = Modifier.clickable(enabled = canUpload, onClick = onUploadRequested)
            )
        }
    }
}

@Composable
private fun StudentWelcomeCard(
    studentName: String?,
    profileBitmap: android.graphics.Bitmap?,
    statusMessage: String,
    attendanceInsightMessage: String?
) {
    val resolvedName = studentName?.takeIf { it.isNotBlank() } ?: "Student"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFEFF6FF), Color(0xFFF8FBFF))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Whats up",
                        fontSize = 13.sp,
                        color = Color(0xFF355D8C),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = resolvedName,
                        fontSize = 19.sp,
                        color = Color(0xFF15263D),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = statusMessage,
                        fontSize = 11.sp,
                        color = Color(0xFF4F6A8C)
                    )
                    if (!attendanceInsightMessage.isNullOrBlank()) {
                        Text(
                            text = attendanceInsightMessage,
                            fontSize = 11.sp,
                            color = Color(0xFF8F2D2D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color(0xFFD4E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap.asImageBitmap(),
                            contentDescription = "Student profile image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Default profile image",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun suggestDownloadFileName(assignment: Assignment): String {
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

private fun writeBytesToUri(context: Context, destinationUri: Uri, bytes: ByteArray): Boolean {
    return try {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
        if (outputStream == null) {
            Log.e("MainActivity", "writeBytesToUri failed: unable to open output stream")
            false
        } else {
            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            true
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "writeBytesToUri failed: ${e.javaClass.simpleName}")
        false
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
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

private fun uriToFile(context: Context, uri: Uri): File? {
    var tempFile: File? = null
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Log.e("MainActivity", "uriToFile failed: unable to open input stream")
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
        Log.e("MainActivity", "uriToFile failed: ${e.javaClass.simpleName}")
        null
    }
}
