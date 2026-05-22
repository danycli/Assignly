package com.danycli.assignmentchecker

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.metrics.performance.JankStats
import com.danycli.assignmentchecker.ui.*
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import com.danycli.assignmentchecker.ui.theme.Cyprus
import com.danycli.assignmentchecker.ui.theme.Sand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark") {
            jankStats = JankStats.createAndTrack(window) { frameData ->
                if (BuildConfig.DEBUG && frameData.isJank) {
                    val durationMs = frameData.frameDurationUiNanos / 1_000_000.0
                    Log.w("JankStats", "Jank frame: ${String.format(Locale.US, "%.2f", durationMs)}ms")
                }
            }
        }
        setContent {
            AppEntry()
        }
    }

    override fun onDestroy() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
        super.onDestroy()
    }
}

@Composable
private fun AppEntry() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    var showSplash by remember { mutableStateOf(true) }
    var startupCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }
    var appSettings by remember { mutableStateOf(AppSettingsStore.get(context)) }

    LaunchedEffect(Unit) {
        val splashStart = System.currentTimeMillis()
        startupCredentials = withContext(Dispatchers.IO) {
            CredentialsStore.get(context)
        }
        val elapsedMs = System.currentTimeMillis() - splashStart
        val remainingMs = 1_000L - elapsedMs
        if (remainingMs > 0) {
            delay(remainingMs)
        }
        showSplash = false
    }

    AssignmentCheckerTheme(themeMode = appSettings.themeMode) {
        if (showSplash) {
            AppSplashScreen()
        } else {
            MainScreen(
                viewModel = viewModel,
                initialCredentials = startupCredentials,
                hasPerformedInitialCredentialCheck = true,
                appSettings = appSettings,
                onSettingsChange = { appSettings = it }
            )
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    initialCredentials: Pair<String, String>? = null,
    hasPerformedInitialCredentialCheck: Boolean = false,
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(initialCredentials != null) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var historicalAssignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var loggedInStudentName by remember { mutableStateOf<String?>(null) }
    var loggedInStudentPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var lastSyncedMs by remember { mutableStateOf(0L) }
    var welcomeStatusMessage by remember { mutableStateOf("Your professors are still thinking how to annoy you in a brutal way possible.") }
    var attendanceInsightMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf<ScreenType>(ScreenType.PENDING) }
    var loadingTargetScreen by remember { mutableStateOf(ScreenType.PENDING) }
    var historyShowSubmittedOnly by remember { mutableStateOf(false) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var instructionFilesDialog by remember { mutableStateOf<InstructionFileDialogState?>(null) }
    var selectedInstructionFile by remember { mutableStateOf<InstructionFile?>(null) }
    var pendingCaptchaCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var pendingExitConfirmation by remember { mutableStateOf(false) }
    var isPendingRefreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var activeUploads by remember { mutableStateOf<List<QueuedUpload>>(emptyList()) }
    var activeDownloads by remember { mutableStateOf<List<QueuedDownload>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            activeUploads = withContext(Dispatchers.IO) {
                UploadQueueStore.getAll(context)
            }
            activeDownloads = withContext(Dispatchers.IO) {
                DownloadQueueStore.getAll(context)
            }
            delay(2000)
        }
    }

    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val pageForUi = when {
        !isLoggedIn -> AppPage.LOGIN
        currentScreen == ScreenType.SETTINGS -> AppPage.SETTINGS
        currentScreen == ScreenType.HISTORICAL -> AppPage.HISTORICAL
        else -> AppPage.PENDING
    }

    fun loadInitialStateFromCache() {
        val cached = AssignmentCacheStore.loadSnapshot(context)
        if (cached != null) {
            assignments = cached.pendingAssignments
            historicalAssignments = cached.historicalAssignments
            loggedInStudentName = cached.studentName
            lastSyncedMs = cached.cachedAtEpochMs
            welcomeStatusMessage = generateWelcomeStatusMessage(
                pendingCount = cached.pendingAssignments.size,
                submittedCount = countSuccessfulSubmissions(cached.historicalAssignments),
                previousMessage = welcomeStatusMessage
            )
        }
    }

    suspend fun refreshAssignmentsState() {
        val previousSnapshot = AssignmentCacheStore.loadSnapshot(context)
        val dashboardData = viewModel.loadDashboardData()
        
        // Only update if we actually got data back from the network
        if (dashboardData.pendingAssignments.isNotEmpty() || dashboardData.historicalAssignments.isNotEmpty()) {
            assignments = dashboardData.pendingAssignments
            historicalAssignments = dashboardData.historicalAssignments
            loggedInStudentName = viewModel.getCurrentStudentName()
            loggedInStudentPhoto = dashboardData.profilePhoto
            welcomeStatusMessage = generateWelcomeStatusMessage(
                pendingCount = dashboardData.pendingAssignments.size,
                submittedCount = countSuccessfulSubmissions(dashboardData.historicalAssignments),
                previousMessage = welcomeStatusMessage
            )
            attendanceInsightMessage = generateAttendanceSarcasmMessage(dashboardData.weakestAttendanceInsight)
            AssignmentCacheStore.saveSnapshot(
                context = context,
                pendingAssignments = dashboardData.pendingAssignments,
                historicalAssignments = dashboardData.historicalAssignments,
                studentName = viewModel.getCurrentStudentName()
            )
            lastSyncedMs = System.currentTimeMillis()
        }
        if (appSettings.assignmentNotificationsEnabled) {
            val newAssignments = detectNewAssignments(
                previous = previousSnapshot,
                pending = dashboardData.pendingAssignments,
                historical = dashboardData.historicalAssignments
            )
            val notifiedKeys = AssignmentNotificationStore.getNotifiedKeys(context)
            val unseenAssignments = newAssignments.filter {
                assignmentNotificationKey(it) !in notifiedKeys
            }
            if (unseenAssignments.isNotEmpty()) {
                AssignmentNotificationManager.notifyNewAssignments(context, unseenAssignments)
                AssignmentNotificationStore.markNotified(
                    context,
                    unseenAssignments.map { assignmentNotificationKey(it) }
                )
            }
            AssignmentNotificationManager.scheduleDeadlineReminders(
                context = context,
                assignments = dashboardData.pendingAssignments
            )
        }
    }

    suspend fun checkForAppUpdateIfNeeded() {
        val localVersionCode = com.danycli.assignmentchecker.BuildConfig.VERSION_CODE
        val remoteInfo = withContext(Dispatchers.IO) { fetchAppUpdateInfo() } ?: return
        if (remoteInfo.latestVersionCode <= localVersionCode) {
            updateDialogInfo = null
            return
        }
        if (!UpdateNotificationStore.shouldNotifyToday(context)) return
        updateDialogInfo = remoteInfo
        UpdateNotificationStore.markNotifiedToday(context, remoteInfo.latestVersionCode)
    }

    LaunchedEffect(appSettings) {
        BackgroundSyncScheduler.applySettings(context, appSettings)
        UploadQueueStore.clearFinished(context)
    }

    LaunchedEffect(pendingExitConfirmation) {
        if (pendingExitConfirmation) {
            delay(2_000)
            pendingExitConfirmation = false
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        while (true) {
            delay(UPDATE_CHECK_INTERVAL_MS)
            checkForAppUpdateIfNeeded()
        }
    }

    BackHandler(enabled = isLoggedIn && !showCaptchaDialog) {
        when (pageForUi) {
            AppPage.SETTINGS -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
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
        saveCredentialsOnSuccess: Boolean,
        isAutoLogin: Boolean = false
    ) {
        val normalizedUser = usernameInput.trim().uppercase()
        suspend fun performLoginRequest(): LoginResult {
            viewModel.syncWebViewSession(context)
            return try {
                withTimeout(45_000) {
                    retryIo { viewModel.login(normalizedUser, passwordInput) }
                }
            } catch (e: TimeoutCancellationException) {
                LoginResult.Error("Login timed out. Please try again.")
            } catch (e: IOException) {
                LoginResult.Error("Network error. Please try again.")
            }
        }
        suspend fun isCaptchaStillRequiredInBackground(): Boolean {
            viewModel.syncWebViewSession(context)
            return try {
                withTimeout(10_000) {
                    retryIo(maxAttempts = 2, initialDelayMs = 250) {
                        viewModel.isSecurityVerificationStillRequired()
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
                        CredentialsStore.save(context, normalizedUser, passwordInput)
                    }
                    scope.launch {
                        checkForAppUpdateIfNeeded()
                    }
                } catch (e: PortalSystemException) {
                    Log.e("MainActivity", "Portal system error: ${e.message}")
                    if (!isAutoLogin) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error fetching assignments: ${e.message}", e)
                    if (!isAutoLogin) {
                        Toast.makeText(context, "Error loading assignments: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            is LoginResult.InvalidCredentials -> {
                pendingCaptchaCredentials = null
                showCaptchaDialog = false
                if (isAutoLogin) {
                    isLoggedIn = false // Session actually invalid
                    CredentialsStore.clear(context)
                }
                Toast.makeText(context, "Authentication failed. Check your ID/password.", Toast.LENGTH_LONG).show()
            }
            is LoginResult.CaptchaRequired -> {
                pendingCaptchaCredentials = normalizedUser to passwordInput
                showCaptchaDialog = true
                Toast.makeText(context, "Security check required. Complete verification in-app, then continue.", Toast.LENGTH_LONG).show()
            }
            is LoginResult.Error -> {
                if (!isAutoLogin) {
                    val cached = AssignmentCacheStore.loadSnapshot(context)
                    if (cached != null) {
                        assignments = cached.pendingAssignments
                        historicalAssignments = cached.historicalAssignments
                        loggedInStudentName = cached.studentName
                        welcomeStatusMessage = generateWelcomeStatusMessage(
                            pendingCount = cached.pendingAssignments.size,
                            submittedCount = countSuccessfulSubmissions(cached.historicalAssignments),
                            previousMessage = welcomeStatusMessage
                        )
                        attendanceInsightMessage = null
                        isLoggedIn = true
                        currentScreen = ScreenType.PENDING
                        val errorMsg = mapLoginErrorToMessage(result.message)
                        Toast.makeText(
                            context,
                            "$errorMsg\nShowing cached assignments.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, mapLoginErrorToMessage(result.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val instructionDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: android.net.Uri? ->
        val file = selectedInstructionFile
        if (uri != null && file != null) {
            loadingTargetScreen = currentScreen
            isLoading = true
            scope.launch {
                var downloadTimeout = false
                val result = try {
                    withTimeout(90_000) {
                        when (val downloadResult = viewModel.downloadAssignment(file.downloadLink)) {
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
            CredentialsStore.get(context)
        }
        if (savedCreds != null) {
            loadInitialStateFromCache()
            isLoggedIn = true // Assume logged in if we have creds, to show offline-first data
            isLoading = assignments.isEmpty() && historicalAssignments.isEmpty()
            isPendingRefreshing = true // Show refresh indicator during background sync

            val (savedUser, savedPass) = savedCreds
            scope.launch {
                try {
                    attemptPortalLogin(
                        usernameInput = savedUser,
                        passwordInput = savedPass,
                        saveCredentialsOnSuccess = false,
                        isAutoLogin = true
                    )
                } finally {
                    isLoading = false
                    isPendingRefreshing = false
                }
            }
        } else if (hasPerformedInitialCredentialCheck) {
            isLoading = false
        }
    }

    val showUploadQueueDialog = remember { mutableStateOf(false) }
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
                                    try {
                                        attemptPortalLogin(
                                            usernameInput = user,
                                            passwordInput = pass,
                                            saveCredentialsOnSuccess = true,
                                            isAutoLogin = false
                                        )
                                    } finally {
                                        isLoading = false
                                    }
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
                                            val msg = if (e is PortalSystemException) e.message else "Refresh failed. Please try again."
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                                CredentialsStore.clear(context)
                                AssignmentCacheStore.clear(context)
                            },
                            onDownloadRequested = { assignment ->
                                loadingTargetScreen = ScreenType.PENDING
                                isLoading = true
                                scope.launch {
                                    val result = try {
                                        withTimeout(45_000) {
                                            viewModel.fetchInstructionFiles(assignment.downloadLink)
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
                                if (appSettings.backgroundUploadEnabled) {
                                    // Enqueue background upload
                                    UploadWorkScheduler.enqueue(context, assignment.assignmentTitle, assignment.submitLink, uri)
                                    Toast.makeText(context, "Upload queued for background processing", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                } else {
                                    uploadJob?.cancel()
                                    uploadJob = scope.launch {
                                        var uploadTimeout = false
                                        val result = try {
                                            withTimeout(90_000) {
                                                withContext(Dispatchers.IO) {
                                                    val file = uriToFile(context, uri)
                                                    if (file != null) {
                                                        retryIo { viewModel.uploadAssignment(assignment.submitLink, file) }
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
                                }
                            },

                            onViewTotal = {
                                historyShowSubmittedOnly = false
                                currentScreen = ScreenType.HISTORICAL
                                scope.launch {
                                    try {
                                        val (fetched, photoBytes) = viewModel.loadHistoricalAndProfile()
                                        if (fetched.isNotEmpty()) {
                                            historicalAssignments = fetched
                                            loggedInStudentPhoto = photoBytes
                                            welcomeStatusMessage = generateWelcomeStatusMessage(
                                                pendingCount = assignments.size,
                                                submittedCount = countSuccessfulSubmissions(fetched),
                                                previousMessage = welcomeStatusMessage
                                            )
                                        }
                                    } catch (e: PortalSystemException) {
                                        Log.e("MainActivity", "Portal system error on history fetch: ${e.message}")
                                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "General error on history fetch: ${e.message}")
                                    }
                                }
                            },
                            onViewSubmitted = {
                                historyShowSubmittedOnly = true
                                currentScreen = ScreenType.HISTORICAL
                                scope.launch {
                                    try {
                                        val (fetched, photoBytes) = viewModel.loadHistoricalAndProfile()
                                        if (fetched.isNotEmpty()) {
                                            historicalAssignments = fetched
                                            loggedInStudentPhoto = photoBytes
                                            welcomeStatusMessage = generateWelcomeStatusMessage(
                                                pendingCount = assignments.size,
                                                submittedCount = countSuccessfulSubmissions(fetched),
                                                previousMessage = welcomeStatusMessage
                                            )
                                        }
                                    } catch (e: PortalSystemException) {
                                        Log.e("MainActivity", "Portal system error on history fetch: ${e.message}")
                                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "General error on history fetch: ${e.message}")
                                    }
                                }
                            },
                            onOpenSettings = {
                                currentScreen = ScreenType.SETTINGS
                            },
                            activeUploads = activeUploads,
                            onDismissUpload = { upload ->
                                UploadQueueStore.remove(context, upload.id)
                                activeUploads = activeUploads.filter { it.id != upload.id }
                            },
                            activeDownloads = activeDownloads,
                            onDismissDownload = { download ->
                                DownloadQueueStore.remove(context, download.id)
                                activeDownloads = activeDownloads.filter { it.id != download.id }
                            },
                            lastSyncedMs = lastSyncedMs
                        )
                    }
                    AppPage.SETTINGS -> {
                        SettingsScreen(
                            initialSettings = appSettings,
                            onBack = { currentScreen = ScreenType.PENDING },
                            onSaveSettings = { newSettings ->
                                AppSettingsStore.save(context, newSettings)
                                BackgroundSyncScheduler.applySettings(context, newSettings)
                                onSettingsChange(newSettings)
                                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                            },
                            onViewUploadQueue = { showUploadQueueDialog.value = true }
                        )
                    }
                    AppPage.HISTORICAL -> {
                        HistoricalAssignmentsScreen(
                            assignments = if (historyShowSubmittedOnly) {
                                historicalAssignments.filter { assignment ->
                                    assignment.status == AssignmentStatus.SUBMITTED || assignment.status == AssignmentStatus.GRADED
                                }
                            } else {
                                historicalAssignments
                            },
                            loggedInStudentName = loggedInStudentName,
                            title = if (historyShowSubmittedOnly) "Submitted Assignments" else "Assignment History",
                            emptyStateText = if (historyShowSubmittedOnly) "No submitted assignments" else "No assignment history",
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
                                            viewModel.fetchInstructionFiles(assignment.downloadLink)
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
                                if (appSettings.backgroundUploadEnabled) {
                                    UploadWorkScheduler.enqueue(context, assignment.assignmentTitle, assignment.submitLink, uri)
                                    Toast.makeText(context, "Re-upload queued for background processing", Toast.LENGTH_SHORT).show()
                                    isLoading = false
                                } else {
                                    uploadJob?.cancel()
                                    uploadJob = scope.launch {
                                        var uploadTimeout = false
                                        val result = try {
                                            withTimeout(90_000) {
                                                withContext(Dispatchers.IO) {
                                                    val file = uriToFile(context, uri)
                                                    if (file != null) {
                                                        retryIo { viewModel.uploadAssignment(assignment.submitLink, file) }
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
                            },
                            activeUploads = activeUploads,
                            onDismissUpload = { upload ->
                                UploadQueueStore.remove(context, upload.id)
                                activeUploads = activeUploads.filter { it.id != upload.id }
                            },
                            activeDownloads = activeDownloads,
                            onDismissDownload = { download ->
                                DownloadQueueStore.remove(context, download.id)
                                activeDownloads = activeDownloads.filter { it.id != download.id }
                            },
                            lastSyncedMs = lastSyncedMs
                        )
                    }
                }
            }

            if (showUploadQueueDialog.value) {
                val queuedUploads = UploadQueueStore.getAll(context)
                AlertDialog(
                    onDismissRequest = { showUploadQueueDialog.value = false },
                    title = { Text("Upload queue", fontWeight = FontWeight.Bold, color = Cyprus) },
                    text = {
                        if (queuedUploads.isEmpty()) {
                            Text("No queued uploads")
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                queuedUploads.forEach { q ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(q.assignmentTitle, fontSize = 14.sp)
                                            Text(q.fileUri, fontSize = 11.sp, color = Color.Gray)
                                            if (!q.lastError.isNullOrBlank()) {
                                                Text(q.lastError, fontSize = 11.sp, color = Color(0xFFD32F2F))
                                            }
                                        }
                                        Text(q.status.name)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showUploadQueueDialog.value = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            if (isLoading && pageForUi != AppPage.LOGIN) {
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
            viewModel = viewModel,
            onDismiss = { showCaptchaDialog = false },
            onCaptchaSolved = {
                showCaptchaDialog = false
                val creds = pendingCaptchaCredentials
                if (creds != null) {
                    loadingTargetScreen = ScreenType.PENDING
                    isLoading = true
                    scope.launch {
                        try {
                            attemptPortalLogin(
                                usernameInput = creds.first,
                                passwordInput = creds.second,
                                saveCredentialsOnSuccess = true,
                                isAutoLogin = false
                            )
                        } finally {
                            isLoading = false
                        }
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
                        text = "A newer version (${updateInfo.displayLabel}) is available.",
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
                                        DownloadWorkScheduler.enqueue(context, file.fileName, file.downloadLink)
                                        Toast.makeText(context, "Download started in background", Toast.LENGTH_SHORT).show()
                                        selectedInstructionFile = null
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
