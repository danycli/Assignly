package com.danycli.assignmentchecker

import android.os.Bundle
import android.util.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.metrics.performance.JankStats
import com.danycli.assignmentchecker.ui.*
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale

data class BatchDownloadReport(
    val successCount: Int,
    val totalCount: Int,
    val failedFiles: List<Pair<String, String>>,
    val zipSavedName: String?,
    val isSaved: Boolean
)

class MainActivity : FragmentActivity() {
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
    var showNotificationDialog by remember { mutableStateOf(false) }

    // Permission launcher for initial prompt
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        NotificationPromptStore.markInitialPromptShown(context)
        // If denied, we'll handle it via the daily re-prompt
    }

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

    // Handle notification prompt logic after splash
    LaunchedEffect(showSplash) {
        if (showSplash) return@LaunchedEffect

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationPromptStore.hasShownInitialPrompt(context)) {
                // First launch: request permission directly
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect
            }
        }

        // Track daily opens and check for re-prompt
        val dailyCount = NotificationPromptStore.incrementDailyOpen(context)
        if (dailyCount >= 3 &&
            !NotificationGate.areNotificationsEnabled(context) &&
            !NotificationPromptStore.hasDailyPromptBeenShown(context)
        ) {
            showNotificationDialog = true
        }
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

        // Notification re-prompt dialog
        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = {
                    showNotificationDialog = false
                    NotificationPromptStore.markDailyPromptShown(context)
                },
                icon = {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Stay Updated",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Enable notifications to get instant alerts for new assignments, deadline reminders, and important updates from your portal.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showNotificationDialog = false
                            NotificationPromptStore.markDailyPromptShown(context)
                            // Open app notification settings
                            val intent = android.content.Intent().apply {
                                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Turn On", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotificationDialog = false
                            NotificationPromptStore.markDailyPromptShown(context)
                        }
                    ) {
                        Text("Not Now")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var timetableLectures by remember { mutableStateOf<List<TimetableLecture>>(emptyList()) }
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
    var selectedChallanForDownload by remember { mutableStateOf<FeeChallan?>(null) }
    var selectedCourse by remember { mutableStateOf<EnrolledCourse?>(null) }
    var selectedCourseFile by remember { mutableStateOf<CourseFile?>(null) }
    var pendingDownloadBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingDownloadFileName by remember { mutableStateOf<String?>(null) }
    var downloadAllProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var downloadAllCurrentFile by remember { mutableStateOf("") }
    var pendingZipBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingZipFileName by remember { mutableStateOf<String?>(null) }
    var downloadAllJob by remember { mutableStateOf<Job?>(null) }
    var pendingBatchReport by remember { mutableStateOf<BatchDownloadReport?>(null) }
    var batchReportToShow by remember { mutableStateOf<BatchDownloadReport?>(null) }
    var pendingCaptchaCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var pendingExitConfirmation by remember { mutableStateOf(false) }
    var isPendingRefreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var activeUploads by remember { mutableStateOf<List<QueuedUpload>>(emptyList()) }
    var activeDownloads by remember { mutableStateOf<List<QueuedDownload>>(emptyList()) }
    var showTimetableModal by remember { mutableStateOf(false) }
    var timetableError by remember { mutableStateOf<String?>(null) }
    var attendanceSummaryList by remember { mutableStateOf<List<AttendanceSummary>>(emptyList()) }
    var cachedAttendanceDetails by remember { mutableStateOf<List<AttendanceDetail>>(emptyList()) }
    var isAttendanceRefreshing by remember { mutableStateOf(false) }
    var gpaSummary by remember { mutableStateOf(GpaSummary(0.0, 0.0, emptyList())) }
    var isGradesRefreshing by remember { mutableStateOf(false) }
    var courseMarksMap by remember { mutableStateOf<Map<String, List<MarksCategory>>>(emptyMap()) }
    var isMarksRefreshing by remember { mutableStateOf(false) }
    var studentProfile by remember { mutableStateOf<StudentProfile?>(null) }
    var isProfileRefreshing by remember { mutableStateOf(false) }
    var feeSnapshot by remember { mutableStateOf<FeeSnapshot?>(null) }
    var isFeeRefreshing by remember { mutableStateOf(false) }
    var enrolledCourses by remember { mutableStateOf<List<EnrolledCourse>>(emptyList()) }
    var enrolledCoursesSemester by remember { mutableStateOf("") }
    var isEnrolledCoursesRefreshing by remember { mutableStateOf(false) }

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
    val snackbarHostState = remember { SnackbarHostState() }
    fun showAppMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    var isAppLocked by remember {
        mutableStateOf(initialCredentials != null && appSettings.biometricLockEnabled)
    }

    fun showBiometricPrompt() {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity == null) {
            showAppMessage("Unable to authenticate: Invalid activity context.")
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showAppMessage(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAppLocked = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showAppMessage("Biometric authentication failed.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Assignly")
            .setSubtitle("Use your biometric credential or device lock to unlock")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("MainActivity", "Biometric authentication error", e)
            showAppMessage("Biometric authentication error: ${e.message}")
        }
    }

    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            showBiometricPrompt()
        }
    }



    fun openDownloadedFile(fileUriString: String) {
        try {
            val uri = Uri.parse(fileUriString)
            val mimeType = if (uri.scheme == "content") {
                context.contentResolver.getType(uri)
            } else {
                guessMimeType(fileUriString.substringAfterLast('/'))
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open file: $fileUriString", e)
            try {
                val uri = Uri.parse(fileUriString)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                showAppMessage("No application found to open this file.")
            }
        }
    }
    val pageForUi = when {
        !isLoggedIn -> AppPage.LOGIN
        currentScreen == ScreenType.SETTINGS -> AppPage.SETTINGS
        currentScreen == ScreenType.DOWNLOADS -> AppPage.DOWNLOADS
        currentScreen == ScreenType.HISTORICAL -> AppPage.HISTORICAL
        currentScreen == ScreenType.ATTENDANCE -> AppPage.ATTENDANCE
        currentScreen == ScreenType.GRADES -> AppPage.GRADES
        currentScreen == ScreenType.MARKS -> AppPage.MARKS
        currentScreen == ScreenType.PROFILE -> AppPage.PROFILE
        currentScreen == ScreenType.FEE -> AppPage.FEE
        currentScreen == ScreenType.ENROLLED_COURSES -> AppPage.ENROLLED_COURSES
        currentScreen == ScreenType.ASSIGNMENTS -> AppPage.ASSIGNMENTS
        currentScreen == ScreenType.TIMETABLE -> AppPage.TIMETABLE
        currentScreen == ScreenType.CHANGE_PASSWORD -> AppPage.CHANGE_PASSWORD
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
        val cachedTimetable = TimetableCacheStore.loadSnapshot(context)
        if (cachedTimetable != null) {
            timetableLectures = cachedTimetable.lectures
        }
        val cachedAttendance = AttendanceCacheStore.loadSnapshot(context)
        if (cachedAttendance != null) {
            attendanceSummaryList = cachedAttendance.summary
            cachedAttendanceDetails = cachedAttendance.details
        }
        val cachedGrades = GradesCacheStore.loadSnapshot(context)
        if (cachedGrades != null) {
            gpaSummary = cachedGrades.summary
        }
        val cachedMarks = MarksCacheStore.loadSnapshot(context)
        if (cachedMarks != null) {
            courseMarksMap = cachedMarks.courseMarksList.associate { it.courseCode to it.categories }
        }
        val cachedProfile = ProfileCacheStore.loadSnapshot(context)
        if (cachedProfile != null) {
            studentProfile = cachedProfile.profile
        }
        val cachedPhoto = ProfileCacheStore.loadPhoto(context)
        if (cachedPhoto != null) {
            loggedInStudentPhoto = cachedPhoto
        }
        val cachedFee = FeeCacheStore.loadSnapshot(context)
        if (cachedFee != null) {
            feeSnapshot = cachedFee
        }
        val cachedEnrolledCourses = EnrolledCoursesCacheStore.loadSnapshot(context)
        if (cachedEnrolledCourses != null) {
            enrolledCourses = cachedEnrolledCourses.courses
            enrolledCoursesSemester = cachedEnrolledCourses.semesterName
        }

        // Reschedule class reminders from cache on startup
        TimetableNotificationManager.scheduleClassReminders(context)
    }

    val clearAllUserSessionData = {
        isLoggedIn = false
        assignments = emptyList()
        historicalAssignments = emptyList()
        timetableLectures = emptyList()
        attendanceSummaryList = emptyList()
        cachedAttendanceDetails = emptyList()
        gpaSummary = GpaSummary(0.0, 0.0, emptyList())
        courseMarksMap = emptyMap()
        loggedInStudentName = null
        loggedInStudentPhoto = null
        studentProfile = null
        feeSnapshot = null
        enrolledCourses = emptyList()
        enrolledCoursesSemester = ""
        welcomeStatusMessage = "Your professors are still thinking how to annoy you in a brutal way possible."
        attendanceInsightMessage = null
        currentScreen = ScreenType.PENDING

        CredentialsStore.clear(context)
        AssignmentCacheStore.clear(context)
        ProfileCacheStore.clear(context)
        FeeCacheStore.clear(context)
        TimetableCacheStore.clear(context)
        AttendanceCacheStore.clear(context)
        GradesCacheStore.clear(context)
        MarksCacheStore.clear(context)
        EnrolledCoursesCacheStore.clear(context)
        CourseFilesCacheStore.clear(context)
        AssignmentNotificationStore.clear(context)

        runCatching {
            TimetableNotificationManager.cancelAllClassReminders(context)
        }
    }

    suspend fun attemptPortalLoginSilent(usernameInput: String, passwordInput: String): LoginResult {
        val normalizedUser = usernameInput.trim().uppercase()
        viewModel.syncWebViewSession(context)
        val result = try {
            withTimeout(35_000) {
                retryIo { viewModel.login(normalizedUser, passwordInput) }
            }
        } catch (e: Exception) {
            LoginResult.Error("Silent login failed: ${e.message}")
        }
        if (result is LoginResult.Success) {
            isLoggedIn = true
        }
        return result
    }

    suspend fun runDownloadWithRetry(downloadLink: String): DownloadResult {
        var result = viewModel.downloadAssignment(downloadLink)
        val isSessionExpired = result is DownloadResult.Rejected && (
            result.reason.contains("Session expired", ignoreCase = true)
            || result.reason.contains("sign in again", ignoreCase = true)
            || result.reason.contains("Not authenticated", ignoreCase = true)
        )
        if (isSessionExpired) {
            val savedCreds = withContext(Dispatchers.IO) {
                CredentialsStore.get(context)
            }
            if (savedCreds != null) {
                val (savedUser, savedPass) = savedCreds
                val loginResult = attemptPortalLoginSilent(savedUser, savedPass)
                if (loginResult is LoginResult.Success) {
                    result = viewModel.downloadAssignment(downloadLink)
                }
            }
        }
        return result
    }

    suspend fun <T> runWithAutoRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            val isSessionExpired = e is PortalSystemException && (
                e.message?.contains("Session expired", ignoreCase = true) == true
                || e.message?.contains("Not authenticated", ignoreCase = true) == true
            )
            if (isSessionExpired) {
                val savedCreds = withContext(Dispatchers.IO) {
                    CredentialsStore.get(context)
                }
                if (savedCreds != null) {
                    val (savedUser, savedPass) = savedCreds
                    val loginResult = attemptPortalLoginSilent(savedUser, savedPass)
                    if (loginResult is LoginResult.Success) {
                        block()
                    } else {
                        throw e
                    }
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun syncAttendanceAndDetails(resolvedCodes: Map<String, String>? = null): List<AttendanceSummary> {
        val fetchedAttendance = runWithAutoRetry { viewModel.loadAttendanceSummary(resolvedCodes) }
        if (fetchedAttendance.isNotEmpty()) {
            attendanceSummaryList = fetchedAttendance
            
            // Pre-fetch all course details sequentially to avoid ASP.NET session state race conditions
            val fetchedDetails = mutableListOf<AttendanceDetail>()
            for (course in fetchedAttendance) {
                runCatching {
                    val target = if (course.courseCode.isBlank()) course.courseName else course.courseCode
                    val details = runWithAutoRetry { viewModel.loadAttendanceDetail(target) }
                    fetchedDetails.addAll(details)
                }.onFailure { err ->
                    Log.e("MainActivity", "Failed to pre-fetch attendance detail for ${course.courseName}: ${err.message}", err)
                }
            }
            
            val currentCached = AttendanceCacheStore.loadSnapshot(context)
            val currentDetails = currentCached?.details ?: emptyList()
            
            val updatedCourseCodes = fetchedAttendance.map { it.courseCode.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            val updatedCourseNames = fetchedAttendance.map { it.courseName.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "") }.toSet()
            
            val otherDetails = currentDetails.filter { cachedDetail ->
                val cleanCode = cachedDetail.courseCode.trim().uppercase()
                val cleanName = cachedDetail.courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
                cleanCode !in updatedCourseCodes && cleanName !in updatedCourseNames
            }
            
            val mergedDetails = otherDetails + fetchedDetails
            AttendanceCacheStore.saveSnapshot(context, fetchedAttendance, mergedDetails)
            cachedAttendanceDetails = mergedDetails
            return fetchedAttendance
        }
        return emptyList()
    }

    suspend fun syncMarks(courseCodes: List<String>, courseNamesMap: Map<String, String>) {
        if (courseCodes.isEmpty()) return
        val currentCached = MarksCacheStore.loadSnapshot(context)
        val currentMarksList = currentCached?.courseMarksList ?: emptyList()
        val currentMarksMap = currentMarksList.associateBy { it.courseCode.trim().uppercase() }

        val fetchedMarks = mutableListOf<CourseMarks>()
        for (code in courseCodes) {
            val cleanCode = code.trim().uppercase()
            val cachedCourse = currentMarksMap[cleanCode]

            runCatching {
                val categories = runWithAutoRetry { viewModel.loadMarks(code) }
                val name = courseNamesMap[code] ?: ""
                if (categories.isEmpty() && cachedCourse != null && cachedCourse.categories.isNotEmpty()) {
                    Log.w("MainActivity", "Fetched empty marks categories for $code, but cache has data. Retaining cached marks.")
                    fetchedMarks.add(cachedCourse)
                } else {
                    fetchedMarks.add(CourseMarks(code, name, categories))
                }
            }.onFailure { err ->
                Log.e("MainActivity", "Failed to pre-fetch marks for $code: ${err.message}", err)
                if (cachedCourse != null) {
                    Log.i("MainActivity", "Retaining cached marks for $code due to fetch failure.")
                    fetchedMarks.add(cachedCourse)
                }
            }
        }
        if (fetchedMarks.isNotEmpty()) {
            if (appSettings.marksNotificationsEnabled) {
                val changes = detectMarksChanges(currentMarksList, fetchedMarks)
                if (changes.isNotEmpty()) {
                    MarksNotificationManager.notifyMarksChanges(context, changes)
                }
            }

            val updatedCourseCodes = courseCodes.map { it.trim().uppercase() }.toSet()
            val otherMarks = currentMarksList.filter { cachedMarks ->
                cachedMarks.courseCode.trim().uppercase() !in updatedCourseCodes
            }
            
            val mergedMarks = otherMarks + fetchedMarks
            MarksCacheStore.saveSnapshot(context, mergedMarks)
            courseMarksMap = mergedMarks.associate { it.courseCode to it.categories }
        }
    }

    suspend fun refreshAssignmentsState() = coroutineScope {
        val previousSnapshot = AssignmentCacheStore.loadSnapshot(context)

        // Trigger concurrent parallel fetches for all independent dashboard sections
        val dashboardDeferred = async { runWithAutoRetry { viewModel.loadDashboardData() } }
        val profileDeferred = async { runCatching { runWithAutoRetry { viewModel.loadProfile() } }.getOrNull() }
        val photoDeferred = async { runCatching { runWithAutoRetry { viewModel.loadPhotoBytes() } }.getOrNull() }
        val timetableDeferred = async { runCatching { runWithAutoRetry { viewModel.loadTimetable() } }.getOrNull() }
        val gradesDeferred = async { runCatching { runWithAutoRetry { viewModel.loadGrades() } }.getOrNull() }
        val feeDeferred = async { runCatching { runWithAutoRetry { viewModel.loadFeeDetails() } }.getOrNull() }
        val enrolledDeferred = async { runCatching { runWithAutoRetry { viewModel.loadEnrolledCourses() } }.getOrNull() }

        // Await background execution outcomes
        val dashboardData = dashboardDeferred.await()
        val fetchedProfile = profileDeferred.await()
        val fetchedPhoto = photoDeferred.await()
        val fetchedTimetable = timetableDeferred.await()
        val fetchedGrades = gradesDeferred.await()
        val fetchedFee = feeDeferred.await()
        val fetchedEnrolled = enrolledDeferred.await()

        if (dashboardData != null && (dashboardData.pendingAssignments.isNotEmpty() || dashboardData.historicalAssignments.isNotEmpty())) {
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
            dashboardData.profilePhoto?.let { ProfileCacheStore.savePhoto(context, it) }
            
            AssignmentCacheStore.saveSnapshot(
                context = context,
                pendingAssignments = dashboardData.pendingAssignments,
                historicalAssignments = dashboardData.historicalAssignments,
                studentName = viewModel.getCurrentStudentName()
            )
            lastSyncedMs = System.currentTimeMillis()
        }

        if (fetchedProfile != null) {
            studentProfile = fetchedProfile
            ProfileCacheStore.saveSnapshot(context, fetchedProfile)
        }
        if (fetchedPhoto != null) {
            loggedInStudentPhoto = fetchedPhoto
            ProfileCacheStore.savePhoto(context, fetchedPhoto)
        }

        timetableError = null
        if (fetchedTimetable != null && fetchedTimetable.isNotEmpty()) {
            timetableLectures = fetchedTimetable
            TimetableCacheStore.saveSnapshot(context, fetchedTimetable)
            TimetableNotificationManager.scheduleClassReminders(context)
        }

        if (fetchedGrades != null && fetchedGrades.semesters.isNotEmpty()) {
            gpaSummary = fetchedGrades
            GradesCacheStore.saveSnapshot(context, fetchedGrades)
        }

        if (fetchedFee != null) {
            feeSnapshot = fetchedFee
            FeeCacheStore.saveSnapshot(context, fetchedFee)
        }

        if (fetchedEnrolled != null) {
            enrolledCourses = fetchedEnrolled.courses
            enrolledCoursesSemester = fetchedEnrolled.semesterName
            EnrolledCoursesCacheStore.saveSnapshot(context, fetchedEnrolled)
            
            fetchedEnrolled.courses.forEach { course ->
                launch {
                    try {
                        val files = viewModel.loadCourseFiles(course.courseCode, course.courseTitle)
                        CourseFilesCacheStore.saveSnapshot(context, course.courseCode, files)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to sync course files for ${course.courseCode} on startup", e)
                    }
                }
            }
        }

        if (dashboardData != null && appSettings.assignmentNotificationsEnabled) {
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
                pending = dashboardData.pendingAssignments,
                historical = dashboardData.historicalAssignments
            )
        }

        // Attendance & Marks sync (depend on grades/attendance lists)
        val activeAttendance = try {
            val resolvedCodes = (fetchedGrades ?: GradesCacheStore.loadSnapshot(context)?.summary)?.semesters?.flatMap { it.courses }
                ?.associate { it.courseName.lowercase().replace("\\s+|-|•|–".toRegex(), "") to it.courseCode }
            syncAttendanceAndDetails(resolvedCodes)
        } catch (e: Exception) {
            Log.e("MainActivity", "Attendance summary fetch failed: ${e.message}")
            emptyList()
        }

        try {
            val courses = activeAttendance.map { it.courseCode }.ifEmpty { attendanceSummaryList.map { it.courseCode } }
            val courseNamesMap = activeAttendance.associate { it.courseCode to it.courseName }.ifEmpty { attendanceSummaryList.associate { it.courseCode to it.courseName } }
            syncMarks(courses, courseNamesMap)
        } catch (e: Exception) {
            Log.e("MainActivity", "Marks sync failed: ${e.message}")
        }
    }

    suspend fun checkForAppUpdateIfNeeded() {
        val localVersionCode = com.danycli.assignmentchecker.BuildConfig.VERSION_CODE
        val remoteInfo = withContext(Dispatchers.IO) { fetchAppUpdateInfo(context, force = false) } ?: return
        if (remoteInfo.latestVersionCode <= localVersionCode) {
            updateDialogInfo = null
            return
        }
        // Always show the in-app dialog when an update is available,
        // regardless of whether the background notification already fired today.
        updateDialogInfo = remoteInfo
    }

    LaunchedEffect(appSettings) {
        BackgroundSyncScheduler.applySettings(context, appSettings)
        UploadQueueStore.clearFinished(context)
        TimetableNotificationManager.scheduleClassReminders(context)
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
            AppPage.CHANGE_PASSWORD -> {
                currentScreen = ScreenType.SETTINGS
                pendingExitConfirmation = false
            }
            AppPage.HISTORICAL -> {
                currentScreen = ScreenType.ASSIGNMENTS
                pendingExitConfirmation = false
            }

            AppPage.DOWNLOADS -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.ATTENDANCE -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.GRADES -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.MARKS -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.PROFILE -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.FEE -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.ENROLLED_COURSES -> {
                if (selectedCourse != null) {
                    selectedCourse = null
                } else {
                    currentScreen = ScreenType.PENDING
                    pendingExitConfirmation = false
                }
            }
            AppPage.ASSIGNMENTS -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.TIMETABLE -> {
                currentScreen = ScreenType.PENDING
                pendingExitConfirmation = false
            }
            AppPage.PENDING -> {
                if (pendingExitConfirmation) {
                    (context as? FragmentActivity)?.finish()
                } else {
                    pendingExitConfirmation = true
                    showAppMessage("Press back again to exit")
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
                isLoggedIn = true
                if (saveCredentialsOnSuccess) {
                    CredentialsStore.save(context, normalizedUser, passwordInput)
                }
                
                val settings = AppSettingsStore.get(context)
                if (settings.rememberRegistrationNumber) {
                    RegistrationHistoryStore.saveRegistration(context, normalizedUser)
                }

                scope.launch {
                    checkForAppUpdateIfNeeded()
                }
                
                // Fetch data in background so the user is not blocked on the login screen
                scope.launch {
                    isPendingRefreshing = true
                    try {
                        refreshAssignmentsState()
                    } catch (e: PortalSystemException) {
                        Log.e("MainActivity", "Portal system error on initial data fetch: ${e.message}")
                        if (!isAutoLogin) {
                            showAppMessage(e.message ?: "An error occurred.")
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error fetching initial assignments: ${e.message}", e)
                        if (!isAutoLogin) {
                            showAppMessage("Error loading assignments: ${e.message}")
                        }
                    } finally {
                        isPendingRefreshing = false
                    }
                }
            }
            is LoginResult.InvalidCredentials -> {
                pendingCaptchaCredentials = null
                showCaptchaDialog = false
                if (isAutoLogin) {
                    clearAllUserSessionData()
                }
                showAppMessage("Authentication failed. Check your ID/password.")
            }
            is LoginResult.CaptchaRequired -> {
                pendingCaptchaCredentials = normalizedUser to passwordInput
                showCaptchaDialog = true
                showAppMessage("Security check required. Complete verification in-app, then continue.")
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
                        showAppMessage("$errorMsg\nShowing cached assignments.")
                    } else {
                        showAppMessage(mapLoginErrorToMessage(result.message))
                    }
                } else {
                    showAppMessage("Offline mode. Showing cached data.")
                }
            }
        }
    }

    val instructionDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: android.net.Uri? ->
        val file = selectedInstructionFile
        if (uri != null && file != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to persist URI permission for $uri", e)
            }
            loadingTargetScreen = ScreenType.DOWNLOADS
            isLoading = true
            scope.launch {
                var downloadTimeout = false
                val result = try {
                    withTimeout(90_000) {
                        when (val downloadResult = runDownloadWithRetry(file.downloadLink)) {
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
                        result is DownloadResult.Success -> {
                            val actualName = getFileNameFromUri(context, uri) ?: result.fileName
                            val dl = QueuedDownload(
                                id = java.util.UUID.randomUUID().toString(),
                                fileName = actualName,
                                downloadLink = file.downloadLink,
                                status = DownloadQueueStatus.SUCCESS,
                                lastError = null,
                                createdAtEpochMs = System.currentTimeMillis(),
                                fileUri = uri.toString()
                            )
                            DownloadQueueStore.upsert(context, dl)
                            activeDownloads = DownloadQueueStore.getAll(context)
                            "Instruction file downloaded: $actualName"
                        }
                        result is DownloadResult.NetworkError -> "Network unavailable. Download could not start."
                        result is DownloadResult.Rejected -> result.reason
                        result is DownloadResult.Error -> result.message
                        else -> "Download failed."
                    }
                    showAppMessage(msg)
                }
                isLoading = false
            }
        }
        selectedInstructionFile = null
    }

    val challanDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: android.net.Uri? ->
        val challan = selectedChallanForDownload
        if (uri != null && challan != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to persist URI permission for $uri", e)
            }
            loadingTargetScreen = ScreenType.DOWNLOADS
            isLoading = true
            scope.launch {
                var downloadTimeout = false
                val result = try {
                    withTimeout(90_000) {
                        when (val downloadResult = runDownloadWithRetry(challan.downloadLink)) {
                            is DownloadResult.Success -> {
                                if (writeBytesToUri(context, uri, downloadResult.bytes)) {
                                    downloadResult
                                } else {
                                    DownloadResult.Error("Could not save downloaded challan.")
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
                        result is DownloadResult.Success -> {
                            val actualName = getFileNameFromUri(context, uri) ?: result.fileName
                            val dl = QueuedDownload(
                                id = java.util.UUID.randomUUID().toString(),
                                fileName = actualName,
                                downloadLink = challan.downloadLink,
                                status = DownloadQueueStatus.SUCCESS,
                                lastError = null,
                                createdAtEpochMs = System.currentTimeMillis(),
                                fileUri = uri.toString()
                            )
                            DownloadQueueStore.upsert(context, dl)
                            activeDownloads = DownloadQueueStore.getAll(context)
                            "Challan downloaded successfully: $actualName"
                        }
                        result is DownloadResult.NetworkError -> "Network unavailable. Download could not start."
                        result is DownloadResult.Rejected -> result.reason
                        result is DownloadResult.Error -> result.message
                        else -> "Download failed."
                    }
                    showAppMessage(msg)
                }
                isLoading = false
            }
        }
        selectedChallanForDownload = null
    }

    val courseFileDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: android.net.Uri? ->
        val bytes = pendingDownloadBytes
        val pFileName = pendingDownloadFileName
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to persist URI permission for $uri", e)
            }
            loadingTargetScreen = ScreenType.DOWNLOADS
            isLoading = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    writeBytesToUri(context, uri, bytes)
                }
                withContext(Dispatchers.Main) {
                    val actualName = getFileNameFromUri(context, uri) ?: pFileName ?: "document"
                    val msg = if (success) {
                        val dl = QueuedDownload(
                            id = java.util.UUID.randomUUID().toString(),
                            fileName = actualName,
                            downloadLink = selectedCourseFile?.downloadLink ?: "",
                            status = DownloadQueueStatus.SUCCESS,
                            lastError = null,
                            createdAtEpochMs = System.currentTimeMillis(),
                            fileUri = uri.toString()
                        )
                        DownloadQueueStore.upsert(context, dl)
                        activeDownloads = DownloadQueueStore.getAll(context)
                        "File downloaded successfully: $actualName"
                    } else {
                        "Could not save downloaded file."
                    }
                    showAppMessage(msg)
                }
                isLoading = false
            }
        }
        pendingDownloadBytes = null
        pendingDownloadFileName = null
        selectedCourseFile = null
    }

    val zipDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: android.net.Uri? ->
        val bytes = pendingZipBytes
        val pFileName = pendingZipFileName
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to persist URI permission for $uri", e)
            }
            loadingTargetScreen = ScreenType.DOWNLOADS
            isLoading = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    writeBytesToUri(context, uri, bytes)
                }
                withContext(Dispatchers.Main) {
                    val actualName = getFileNameFromUri(context, uri) ?: pFileName ?: "archive.zip"
                    if (success) {
                        val dl = QueuedDownload(
                            id = java.util.UUID.randomUUID().toString(),
                            fileName = actualName,
                            downloadLink = "",
                            status = DownloadQueueStatus.SUCCESS,
                            lastError = null,
                            createdAtEpochMs = System.currentTimeMillis(),
                            fileUri = uri.toString()
                        )
                        DownloadQueueStore.upsert(context, dl)
                        activeDownloads = DownloadQueueStore.getAll(context)
                        
                        val finalReport = pendingBatchReport?.copy(
                            zipSavedName = actualName,
                            isSaved = true
                        ) ?: BatchDownloadReport(0, 0, emptyList(), actualName, true)
                        
                        if (finalReport.failedFiles.isEmpty()) {
                            showAppMessage("All files saved successfully to $actualName")
                        } else {
                            batchReportToShow = finalReport
                        }
                    } else {
                        val finalReport = pendingBatchReport?.copy(
                            zipSavedName = null,
                            isSaved = false
                        )
                        batchReportToShow = finalReport
                        showAppMessage("Could not save zip archive.")
                    }
                }
                isLoading = false
            }
        } else {
            val finalReport = pendingBatchReport?.copy(
                zipSavedName = null,
                isSaved = false
            )
            if (finalReport != null && finalReport.failedFiles.isNotEmpty()) {
                batchReportToShow = finalReport
            }
        }
        pendingBatchReport = null
        pendingZipBytes = null
        pendingZipFileName = null
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
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isAppLocked) {
                BackHandler(enabled = true) {
                    (context as? androidx.fragment.app.FragmentActivity)?.finish()
                }
                AppLockScreen(onUnlockClick = { showBiometricPrompt() })
            } else {
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
                        DashboardScreen(
                            loggedInStudentName = loggedInStudentName,
                            loggedInStudentPhoto = loggedInStudentPhoto,
                            isRefreshing = isPendingRefreshing,
                            onRefresh = {
                                if (isPendingRefreshing) return@DashboardScreen
                                loadingTargetScreen = ScreenType.PENDING
                                isPendingRefreshing = true
                                isLoading = true
                                scope.launch {
                                    runCatching {
                                        withTimeout(25_000) {
                                            refreshAssignmentsState()
                                        }
                                    }.onFailure { e ->
                                        Log.e("MainActivity", "Refresh failed: ${e.message}", e)
                                        val msg = when (e) {
                                            is TimeoutCancellationException -> "Refresh timed out. Portal is unreachable."
                                            is PortalSystemException -> (e.message ?: "Refresh failed. Please try again.")
                                            else -> "Refresh failed. Please try again."
                                        }
                                        showAppMessage(msg)
                                    }
                                    isPendingRefreshing = false
                                    isLoading = false
                                }
                            },
                            onLogout = {
                                AssignmentNotificationManager.cancelAllReminders(context)
                                scope.coroutineContext.cancelChildren()
                                uploadJob?.cancel()
                                uploadJob = null
                                isPendingRefreshing = false
                                isLoading = false
                                isLoggedIn = false
                                clearAllUserSessionData()
                            },
                            onOpenSettings = {
                                currentScreen = ScreenType.SETTINGS
                            },
                            onNavigateToTimetable = { currentScreen = ScreenType.TIMETABLE },
                            onNavigateToAttendance = { currentScreen = ScreenType.ATTENDANCE },
                            onNavigateToGrades = { currentScreen = ScreenType.GRADES },
                            onNavigateToMarks = { currentScreen = ScreenType.MARKS },
                            onNavigateToProfile = { currentScreen = ScreenType.PROFILE },
                            onNavigateToFee = { currentScreen = ScreenType.FEE },
                            onNavigateToCourses = { currentScreen = ScreenType.ENROLLED_COURSES },
                            onNavigateToAssignments = { currentScreen = ScreenType.ASSIGNMENTS },
                            onNavigateToDownloads = { currentScreen = ScreenType.DOWNLOADS },
                            lastSyncedMs = lastSyncedMs,
                            studentProfile = studentProfile,
                            enrolledCourses = enrolledCourses,
                            enrolledCoursesSemester = enrolledCoursesSemester,
                            gpaSummary = gpaSummary,
                            attendanceSummaryList = attendanceSummaryList
                        )
                    }
                    AppPage.TIMETABLE -> {
                        TimetableScreen(
                            lectures = timetableLectures,
                            timetableError = timetableError,
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
                        )
                    }
                    AppPage.ASSIGNMENTS -> {
                        AssignmentsScreen(
                            assignments = assignments,
                            historicalAssignments = historicalAssignments,
                            isRefreshing = isPendingRefreshing,
                            onRefresh = {
                                if (isPendingRefreshing) return@AssignmentsScreen
                                loadingTargetScreen = ScreenType.ASSIGNMENTS
                                isPendingRefreshing = true
                                isLoading = true
                                scope.launch {
                                    runCatching {
                                        withTimeout(25_000) {
                                            refreshAssignmentsState()
                                        }
                                    }.onFailure { e ->
                                        Log.e("MainActivity", "Refresh failed: ${e.message}", e)
                                        val msg = when (e) {
                                            is TimeoutCancellationException -> "Refresh timed out. Portal is unreachable."
                                            is PortalSystemException -> (e.message ?: "Refresh failed. Please try again.")
                                            else -> "Refresh failed. Please try again."
                                        }
                                        showAppMessage(msg)
                                    }
                                    isPendingRefreshing = false
                                    isLoading = false
                                }
                            },
                            onDownloadRequested = { assignment ->
                                loadingTargetScreen = ScreenType.INSTRUCTION_FILES
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
                                            showAppMessage(msg)
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            onUploadRequested = { assignment, uri ->
                                loadingTargetScreen = ScreenType.UPLOADING
                                isLoading = true
                                if (appSettings.backgroundUploadEnabled) {
                                    UploadWorkScheduler.enqueue(context, assignment.assignmentTitle, assignment.submitLink, uri)
                                    showAppMessage("Upload queued for background processing")
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
                                                showAppMessage("Uploaded Successfully")
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
                                                showAppMessage(msg)
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
                            onNavigateBack = {
                                currentScreen = ScreenType.PENDING
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
                                        showAppMessage(e.message ?: "An error occurred.")
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
                                        showAppMessage(e.message ?: "An error occurred.")
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "General error on history fetch: ${e.message}")
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
                    AppPage.DOWNLOADS -> {
                        DownloadsScreen(
                            activeDownloads = activeDownloads,
                            onRemoveDownload = { download ->
                                DownloadQueueStore.remove(context, download.id)
                                activeDownloads = activeDownloads.filter { it.id != download.id }
                            },
                            onClearFinished = {
                                DownloadQueueStore.clearFinished(context)
                                activeDownloads = DownloadQueueStore.getAll(context)
                            },
                            onRetryDownload = { download ->
                                DownloadQueueStore.remove(context, download.id)
                                DownloadWorkScheduler.enqueue(context, download.fileName, download.downloadLink)
                                activeDownloads = DownloadQueueStore.getAll(context)
                            },
                            onOpenDownload = { download ->
                                if (!download.fileUri.isNullOrEmpty()) {
                                    openDownloadedFile(download.fileUri)
                                }
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
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
                                showAppMessage("Settings saved successfully")
                            },
                            onViewUploadQueue = { showUploadQueueDialog.value = true },
                            onChangePassword = { currentScreen = ScreenType.CHANGE_PASSWORD },
                            onOpenDisclaimer = { uriHandler.openUri(DISCLAIMER_URL) },
                            onShowMessage = { showAppMessage(it) },
                            onCheckForUpdates = { onResult ->
                                scope.launch {
                                    val localVersionCode = com.danycli.assignmentchecker.BuildConfig.VERSION_CODE
                                    val remoteInfo = withContext(Dispatchers.IO) { fetchAppUpdateInfo(context, force = true) }
                                    if (remoteInfo == null) {
                                        onResult(UpdateCheckResult.ERROR)
                                    } else if (remoteInfo.latestVersionCode > localVersionCode) {
                                        updateDialogInfo = remoteInfo
                                        onResult(UpdateCheckResult.UPDATE_AVAILABLE)
                                    } else {
                                        updateDialogInfo = null
                                        onResult(UpdateCheckResult.UP_TO_DATE)
                                    }
                                }
                            }
                        )
                    }
                    AppPage.CHANGE_PASSWORD -> {
                        ChangePasswordScreen(
                            onBack = { currentScreen = ScreenType.SETTINGS },
                            fetchRules = { viewModel.fetchPasswordRules() },
                            onSubmit = { current, new, confirm -> viewModel.changePassword(current, new, confirm) },
                            onPasswordUpdated = { newPassword ->
                                 val username = CredentialsStore.get(context)?.first ?: ""
                                 CredentialsStore.save(context, username, newPassword)
                                showAppMessage("Password updated successfully")
                                currentScreen = ScreenType.SETTINGS
                            }
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
                                currentScreen = ScreenType.ASSIGNMENTS
                            },
                            onDownloadRequested = { assignment ->
                                loadingTargetScreen = ScreenType.INSTRUCTION_FILES
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
                                            showAppMessage(msg)
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            onReuploadRequested = { assignment, uri ->
                                loadingTargetScreen = ScreenType.UPLOADING
                                isLoading = true
                                if (appSettings.backgroundUploadEnabled) {
                                    UploadWorkScheduler.enqueue(context, assignment.assignmentTitle, assignment.submitLink, uri)
                                    showAppMessage("Re-upload queued for background processing")
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
                                                showAppMessage("Re-uploaded Successfully")
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
                                                showAppMessage(msg)
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
                    AppPage.ATTENDANCE -> {
                        AttendanceScreen(
                            attendanceSummaryList = attendanceSummaryList,
                            cachedAttendanceDetails = cachedAttendanceDetails,
                            isRefreshing = isAttendanceRefreshing,
                            onRefresh = {
                                if (isAttendanceRefreshing) return@AttendanceScreen
                                isAttendanceRefreshing = true
                                scope.launch {
                                    runCatching {
                                        val gradesSnapshot = GradesCacheStore.loadSnapshot(context)
                                        val resolvedCodes = gradesSnapshot?.summary?.semesters?.flatMap { it.courses }
                                            ?.associate { it.courseName.lowercase().replace("\\s+|-|•|–".toRegex(), "") to it.courseCode }
                                        syncAttendanceAndDetails(resolvedCodes)
                                    }.onFailure { e ->
                                        Log.e("MainActivity", "Attendance refresh failed: ${e.message}", e)
                                        if (!isNetworkError(e)) {
                                            val msg = if (e is PortalSystemException) (e.message ?: "Refresh failed. Please try again.") else "Refresh failed. Please try again."
                                            showAppMessage(msg)
                                        }
                                    }
                                    isAttendanceRefreshing = false
                                }
                            },
                            onLoadDetail = { courseCode ->
                                try {
                                    runWithAutoRetry { viewModel.loadAttendanceDetail(courseCode) }.also { details ->
                                        if (details.isNotEmpty()) {
                                            val currentCached = AttendanceCacheStore.loadSnapshot(context)
                                            val currentSummary = currentCached?.summary ?: attendanceSummaryList
                                            val otherDetails = (currentCached?.details ?: emptyList()).filter { it.courseCode != courseCode }
                                            val mergedDetails = otherDetails + details
                                            AttendanceCacheStore.saveSnapshot(context, currentSummary, mergedDetails)
                                            cachedAttendanceDetails = mergedDetails
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to fetch attendance details online, loading from cache", e)
                                    val currentCached = AttendanceCacheStore.loadSnapshot(context)
                                    val cleanTarget = courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
                                    val cachedDetails = currentCached?.details?.filter {
                                        val cleanCode = it.courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
                                        cleanCode == cleanTarget
                                    } ?: emptyList()
                                    
                                    if (cachedDetails.isEmpty()) {
                                        throw e
                                    }
                                    cachedDetails
                                }
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
                        )
                    }
                    AppPage.GRADES -> {
                        GradesScreen(
                            gpaSummary = gpaSummary,
                            isRefreshing = isGradesRefreshing,
                            onRefresh = {
                                if (isGradesRefreshing) return@GradesScreen
                                isGradesRefreshing = true
                                scope.launch {
                                    runCatching {
                                        val fetched = runWithAutoRetry { viewModel.loadGrades() }
                                        if (fetched.semesters.isNotEmpty()) {
                                            gpaSummary = fetched
                                            GradesCacheStore.saveSnapshot(context, fetched)
                                        }
                                    }.onFailure { e ->
                                        Log.e("MainActivity", "Grades refresh failed: ${e.message}", e)
                                        if (!isNetworkError(e)) {
                                            val msg = if (e is PortalSystemException) (e.message ?: "Refresh failed. Please try again.") else "Refresh failed. Please try again."
                                            showAppMessage(msg)
                                        }
                                    }
                                    isGradesRefreshing = false
                                }
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
                        )
                    }
                    AppPage.MARKS -> {
                        val courses = attendanceSummaryList.map { it.courseCode }
                        val courseNamesMap = attendanceSummaryList.associate { it.courseCode to it.courseName }
                        MarksScreen(
                            courseList = courses,
                            courseNames = courseNamesMap,
                            courseMarksMap = courseMarksMap,
                            isRefreshing = isMarksRefreshing,
                            onRefreshCourse = { courseCode ->
                                try {
                                    runWithAutoRetry { viewModel.loadMarks(courseCode) }.also { categories ->
                                        if (categories.isNotEmpty()) {
                                            val updatedMap = courseMarksMap + (courseCode to categories)
                                            courseMarksMap = updatedMap
                                            val currentCached = MarksCacheStore.loadSnapshot(context)
                                            val snapshotList = updatedMap.map { entry ->
                                                val cName = courseNamesMap[entry.key]
                                                    ?: currentCached?.courseMarksList?.find { it.courseCode == entry.key }?.courseName
                                                    ?: ""
                                                CourseMarks(entry.key, cName, entry.value)
                                            }
                                            MarksCacheStore.saveSnapshot(context, snapshotList)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to fetch marks online, loading from cache", e)
                                    val currentCached = MarksCacheStore.loadSnapshot(context)
                                    val cleanTarget = courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
                                    val cachedMarks = currentCached?.courseMarksList?.find {
                                        val cleanCode = it.courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
                                        cleanCode == cleanTarget
                                    }?.categories ?: emptyList()

                                     if (cachedMarks.isEmpty()) {
                                         if (isNetworkError(e)) {
                                             throw Exception("Network unavailable. Please check your connection to load marks.")
                                         }
                                         throw e
                                     }
                                     
                                     val updatedMap = courseMarksMap + (courseCode to cachedMarks)
                                     courseMarksMap = updatedMap
                                     cachedMarks
                                }
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            },
                            gpaSummary = gpaSummary
                        )
                    }
                    AppPage.PROFILE -> {
                        ProfileScreen(
                            profile = studentProfile,
                            photoBytes = loggedInStudentPhoto,
                            isRefreshing = isProfileRefreshing,
                            completedSemesters = countCompletedSemesters(gpaSummary.semesters),
                            onRefreshProfile = {
                                isProfileRefreshing = true
                                val result = try {
                                    val prof = runWithAutoRetry { viewModel.loadProfile() }
                                    val photo = runWithAutoRetry { viewModel.loadPhotoBytes() }
                                    studentProfile = prof
                                    ProfileCacheStore.saveSnapshot(context, prof)
                                    if (photo != null) {
                                        loggedInStudentPhoto = photo
                                        ProfileCacheStore.savePhoto(context, photo)
                                    }
                                    Pair(prof, photo)
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to refresh profile online", e)
                                    throw e
                                } finally {
                                    isProfileRefreshing = false
                                }
                                result
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
                        )
                    }
                    AppPage.FEE -> {
                        FeeScreen(
                            feeSnapshot = feeSnapshot,
                            isRefreshing = isFeeRefreshing,
                            onRefreshFee = {
                                isFeeRefreshing = true
                                val result = try {
                                    val snapshot = runWithAutoRetry { viewModel.loadFeeDetails() }
                                    feeSnapshot = snapshot
                                    FeeCacheStore.saveSnapshot(context, snapshot)
                                    snapshot
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to refresh fee online", e)
                                    throw e
                                } finally {
                                    isFeeRefreshing = false
                                }
                                result
                            },
                            onDownloadChallan = { challan ->
                                selectedChallanForDownload = challan
                                val defaultName = "FeeChallan_${challan.semester.replace(" ", "_")}.pdf"
                                challanDownloadLauncher.launch(defaultName)
                            },
                            onBack = {
                                currentScreen = ScreenType.PENDING
                            }
                        )
                    }
                    AppPage.ENROLLED_COURSES -> {
                        val course = selectedCourse
                        if (course != null) {
                            CourseDetailScreen(
                                course = course,
                                allAssignments = assignments + historicalAssignments,
                                viewModel = viewModel,
                                onDownloadFile = { file ->
                                    selectedCourseFile = file
                                    loadingTargetScreen = ScreenType.DOWNLOADS
                                    isLoading = true
                                    scope.launch {
                                        var downloadTimeout = false
                                        val result = try {
                                            withTimeout(90_000) {
                                                runDownloadWithRetry(file.downloadLink)
                                            }
                                        } catch (e: TimeoutCancellationException) {
                                            downloadTimeout = true
                                            DownloadResult.Error("Download timed out.")
                                        } catch (e: IOException) {
                                            DownloadResult.NetworkError
                                        }

                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            when (result) {
                                                is DownloadResult.Success -> {
                                                    pendingDownloadBytes = result.bytes
                                                    pendingDownloadFileName = result.fileName
                                                    val sanitizedName = result.fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                                    courseFileDownloadLauncher.launch(sanitizedName)
                                                }
                                                is DownloadResult.NetworkError -> {
                                                    showAppMessage("Network unavailable. Download could not start.")
                                                }
                                                is DownloadResult.Rejected -> {
                                                    showAppMessage(result.reason)
                                                }
                                                is DownloadResult.Error -> {
                                                    showAppMessage(result.message)
                                                }
                                            }
                                        }
                                    }
                                },
                                onDownloadAllFiles = { files ->
                                    val job = scope.launch {
                                        var successCount = 0
                                        var zipBytesResult: ByteArray? = null
                                        val baos = ByteArrayOutputStream()
                                        val failedFiles = mutableListOf<Pair<String, String>>()
                                        
                                        try {
                                            ZipOutputStream(baos).use { zos ->
                                                files.forEachIndexed { index, file ->
                                                    downloadAllCurrentFile = file.title
                                                    downloadAllProgress = (index + 1) to files.size
                                                    
                                                    val downloadResult = try {
                                                        withTimeout(45_000) {
                                                            runDownloadWithRetry(file.downloadLink)
                                                        }
                                                    } catch (e: TimeoutCancellationException) {
                                                        Log.e("MainActivity", "Failed to download during batch: ${file.title}", e)
                                                        DownloadResult.Error("Download timed out (45s)")
                                                    } catch (e: Exception) {
                                                        Log.e("MainActivity", "Failed to download during batch: ${file.title}", e)
                                                        DownloadResult.Error(e.message ?: "Connection error")
                                                    }
                                                    
                                                    when (downloadResult) {
                                                        is DownloadResult.Success -> {
                                                            val name = downloadResult.fileName.ifBlank {
                                                                val cleanTitle = file.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                                                                "$cleanTitle.pdf"
                                                            }
                                                            val entry = ZipEntry(name)
                                                            zos.putNextEntry(entry)
                                                            zos.write(downloadResult.bytes)
                                                            zos.closeEntry()
                                                            successCount++
                                                        }
                                                        is DownloadResult.Rejected -> {
                                                            failedFiles.add(file.title to (downloadResult.reason.ifBlank { "Rejected by server" }))
                                                        }
                                                        is DownloadResult.NetworkError -> {
                                                            failedFiles.add(file.title to "Network unavailable")
                                                        }
                                                        is DownloadResult.Error -> {
                                                            failedFiles.add(file.title to (downloadResult.message.ifBlank { "Unknown error" }))
                                                        }
                                                    }
                                                }
                                            }
                                            zipBytesResult = baos.toByteArray()
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error generating zip archive", e)
                                        } finally {
                                            downloadAllProgress = null
                                            downloadAllCurrentFile = ""
                                            downloadAllJob = null
                                        }
                                        
                                        val report = BatchDownloadReport(
                                            successCount = successCount,
                                            totalCount = files.size,
                                            failedFiles = failedFiles,
                                            zipSavedName = null,
                                            isSaved = false
                                        )
                                        
                                        if (zipBytesResult != null && successCount > 0) {
                                            pendingBatchReport = report
                                            pendingZipBytes = zipBytesResult
                                            val defaultName = "${course.courseCode.replace(" ", "_")}_Course_Files.zip"
                                            pendingZipFileName = defaultName
                                            zipDownloadLauncher.launch(defaultName)
                                        } else {
                                            batchReportToShow = report
                                        }
                                    }
                                    downloadAllJob = job
                                },
                                onBack = {
                                    selectedCourse = null
                                }
                            )
                        } else {
                            EnrolledCoursesScreen(
                                courses = enrolledCourses,
                                semesterName = enrolledCoursesSemester,
                                isRefreshing = isEnrolledCoursesRefreshing,
                                onRefresh = {
                                    isEnrolledCoursesRefreshing = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                for (page in listOf("EnrolledCourses.aspx", "StudentRegistration.aspx", "Summary.aspx")) {
                                                    val html = viewModel.fetchPageHtmlDebug(page)
                                                    val file = java.io.File(context.filesDir, "debug_$page.html")
                                                    file.writeText(html)
                                                    Log.d("MainActivity", "Dumped $page to ${file.absolutePath}, length: ${html.length}")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Failed to dump pages", e)
                                            }
                                        }
                                        try {
                                            val data = runWithAutoRetry { viewModel.loadEnrolledCourses() }
                                            enrolledCourses = data.courses
                                            enrolledCoursesSemester = data.semesterName
                                            EnrolledCoursesCacheStore.saveSnapshot(context, data)
                                            
                                            data.courses.forEach { course ->
                                                scope.launch {
                                                    try {
                                                        val files = viewModel.loadCourseFiles(course.courseCode, course.courseTitle)
                                                        CourseFilesCacheStore.saveSnapshot(context, course.courseCode, files)
                                                    } catch (e: Exception) {
                                                        Log.e("MainActivity", "Failed to sync course files for ${course.courseCode} on manual refresh", e)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Failed to fetch enrolled courses", e)
                                            val msg = when {
                                                isNetworkError(e) -> "Network unavailable. Please check your connection."
                                                e.message?.contains("No enrolled courses found for the current semester") == true -> "No enrolled courses found for the current semester."
                                                else -> e.message ?: "Failed to fetch enrolled courses"
                                            }
                                            showAppMessage(msg)
                                        } finally {
                                            isEnrolledCoursesRefreshing = false
                                        }
                                    }
                                },
                                onCourseClick = { clickedCourse ->
                                    selectedCourse = clickedCourse
                                },
                                onBack = {
                                    currentScreen = ScreenType.PENDING
                                }
                            )
                        }
                    }
                }
            }
            }

            if (showUploadQueueDialog.value) {
                val queuedUploads = UploadQueueStore.getAll(context)
                AlertDialog(
                    onDismissRequest = { showUploadQueueDialog.value = false },
                    title = { Text("Upload queue", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    text = {
                        if (queuedUploads.isEmpty()) {
                            Text("No queued uploads", color = MaterialTheme.colorScheme.onSurface)
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
                                            Text(q.assignmentTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(q.fileUri, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (!q.lastError.isNullOrBlank()) {
                                                Text(q.lastError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Text(q.status.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showUploadQueueDialog.value = false }) {
                            Text("Close", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }

            if (isLoading && pageForUi != AppPage.LOGIN) {
                val targetScreen = if (loadingTargetScreen == ScreenType.DOWNLOADS ||
                                       loadingTargetScreen == ScreenType.INSTRUCTION_FILES ||
                                       loadingTargetScreen == ScreenType.UPLOADING) {
                    loadingTargetScreen
                } else {
                    currentScreen
                }
                val loadingMessage = when (targetScreen) {
                    ScreenType.PENDING -> "Loading dashboard..."
                    ScreenType.ASSIGNMENTS -> "Loading assignments..."
                    ScreenType.HISTORICAL -> "Loading historical assignments..."
                    ScreenType.DOWNLOADS -> "Downloading file..."
                    ScreenType.INSTRUCTION_FILES -> "Loading instruction files..."
                    ScreenType.UPLOADING -> "Uploading assignment..."
                    ScreenType.ATTENDANCE -> "Loading attendance..."
                    ScreenType.GRADES -> "Loading grades..."
                    ScreenType.MARKS -> "Loading marks..."
                    ScreenType.PROFILE -> "Loading profile..."
                    ScreenType.FEE -> "Loading fee details..."
                    ScreenType.ENROLLED_COURSES -> "Loading courses..."
                    ScreenType.TIMETABLE -> "Loading timetable..."
                    ScreenType.CHANGE_PASSWORD -> "Changing password..."
                    else -> "Loading..."
                }
                LoadingStatusOverlay(message = loadingMessage)
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { updateDialogInfo = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close update dialog",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "A newer version (${updateInfo.displayLabel}) is available.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { uriHandler.openUri(updateInfo.releaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Update now", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
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
                    color = MaterialTheme.colorScheme.primary
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    dialogState.files.forEach { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (appSettings.downloadBehavior == DownloadBehavior.AUTO_DOWNLOADS) {
                                            DownloadWorkScheduler.enqueue(context, file.fileName, file.downloadLink)
                                             showAppMessage("Download started in background")
                                        } else {
                                            selectedInstructionFile = file
                                            instructionDownloadLauncher.launch(file.fileName)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download file",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { instructionFilesDialog = null }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    val progressState = downloadAllProgress
    if (progressState != null) {
        Dialog(
            onDismissRequest = {
                downloadAllJob?.cancel()
                downloadAllProgress = null
                downloadAllJob = null
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Downloading Course Files",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    CircularProgressIndicator(
                        progress = { progressState.first.toFloat() / progressState.second.toFloat() },
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Text(
                        text = "Downloading ${progressState.first} of ${progressState.second}...",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (downloadAllCurrentFile.isNotBlank()) {
                        Text(
                            text = downloadAllCurrentFile,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            downloadAllJob?.cancel()
                            downloadAllProgress = null
                            downloadAllJob = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val reportState = batchReportToShow
    if (reportState != null) {
        AlertDialog(
            onDismissRequest = { batchReportToShow = null },
            icon = {
                Icon(
                    imageVector = if (reportState.successCount == reportState.totalCount) {
                        Icons.Default.CheckCircle
                    } else if (reportState.successCount > 0) {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.Error
                    },
                    contentDescription = "Report Icon",
                    tint = if (reportState.successCount == reportState.totalCount) {
                        Color(0xFF00E676)
                    } else if (reportState.successCount > 0) {
                        Color(0xFFFFB300)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Batch Download Report",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (reportState.isSaved) {
                            "Successfully saved to archive:"
                        } else {
                            "Status of download attempt:"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (reportState.isSaved && reportState.zipSavedName != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Zip",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = reportState.zipSavedName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "Downloaded: ${reportState.successCount} of ${reportState.totalCount} files.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (reportState.failedFiles.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        
                        Text(
                            text = "Failed Files Details:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            reportState.failedFiles.forEach { (title, reason) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Reason: $reason",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { batchReportToShow = null }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
