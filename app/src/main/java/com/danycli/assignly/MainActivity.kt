package com.danycli.assignmentchecker

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

private suspend fun loadAssignmentsAndProfile(repository: PortalRepository): Triple<List<Assignment>, List<Assignment>, ByteArray?> {
    return withContext(Dispatchers.IO) {
        val (pending, submitted) = repository.fetchAssignments()
        Triple(pending, submitted, repository.fetchCurrentStudentPhoto())
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

private data class InstructionFileDialogState(
    val assignment: Assignment,
    val files: List<InstructionFile>
)

enum class ScreenType {
    PENDING, HISTORICAL
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
    var currentScreen by remember { mutableStateOf<ScreenType>(ScreenType.PENDING) }
    var loadingTargetScreen by remember { mutableStateOf(ScreenType.PENDING) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var instructionFilesDialog by remember { mutableStateOf<InstructionFileDialogState?>(null) }
    var selectedInstructionFile by remember { mutableStateOf<InstructionFile?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                val result = try {
                    withTimeout(45_000) {
                        withContext(Dispatchers.IO) {
                            retryIo { repository.login(savedUser, savedPass) }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    LoginResult.Error("Login timed out. Please try again.")
                } catch (e: IOException) {
                    LoginResult.Error("Network error. Please try again.")
                }
                
                withContext(Dispatchers.Main) {
                    if (result is LoginResult.Success) {
                        try {
                            val (pending, submitted, photoBytes) = loadAssignmentsAndProfile(repository)
                            assignments = pending
                            historicalAssignments = submitted
                            loggedInStudentName = repository.getCurrentStudentName()
                            loggedInStudentPhoto = photoBytes
                            welcomeStatusMessage = generateWelcomeStatusMessage(
                                pendingCount = pending.size,
                                submittedCount = submitted.size,
                                previousMessage = welcomeStatusMessage
                            )
                            isLoggedIn = true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error fetching assignments: ${e.message}", e)
                        }
                    }
                    isLoading = false
                }
            }
        } else if (hasPerformedInitialCredentialCheck) {
            isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Sand) {
        if (isLoading) {
            when (loadingTargetScreen) {
                ScreenType.PENDING -> PendingAssignmentsSkeleton()
                ScreenType.HISTORICAL -> HistoricalAssignmentsSkeleton()
            }
        } else if (!isLoggedIn) {
            LoginScreen(
                isLoading = isLoading,
                onLogin = { user, pass ->
                    loadingTargetScreen = ScreenType.PENDING
                    isLoading = true
                    scope.launch {
                        val result = try {
                            withTimeout(45_000) {
                                withContext(Dispatchers.IO) {
                                    val normalizedUser = user.trim().uppercase()
                                    retryIo { repository.login(normalizedUser, pass) }
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            LoginResult.Error("Login timed out. Please try again.")
                        } catch (e: IOException) {
                            LoginResult.Error("Network error. Please try again.")
                        }
                        
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is LoginResult.Success -> {
                                    try {
                                        val (pending, submitted, photoBytes) = loadAssignmentsAndProfile(repository)
                                        assignments = pending
                                        historicalAssignments = submitted
                                        loggedInStudentName = repository.getCurrentStudentName()
                                        loggedInStudentPhoto = photoBytes
                                        welcomeStatusMessage = generateWelcomeStatusMessage(
                                            pendingCount = pending.size,
                                            submittedCount = submitted.size,
                                            previousMessage = welcomeStatusMessage
                                        )
                                        isLoggedIn = true
                                        // Save credentials for future auto-login
                                        context.saveCredentials(user.trim().uppercase(), pass)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error fetching assignments: ${e.message}", e)
                                        Toast.makeText(context, "Error loading assignments: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                is LoginResult.InvalidCredentials -> {
                                    Toast.makeText(context, "Authentication failed. Check your ID/password.", Toast.LENGTH_LONG).show()
                                }
                                is LoginResult.CaptchaRequired -> {
                                    Toast.makeText(context, "Security Verification (CAPTCHA) required. Please log in via browser first.", Toast.LENGTH_LONG).show()
                                }
                                is LoginResult.Error -> {
                                    Toast.makeText(context, mapLoginErrorToMessage(result.message), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        isLoading = false
                    }
                }
            )
        } else {
            when (currentScreen) {
                ScreenType.PENDING -> {
                    AssignmentsList(
                        assignments = assignments,
                        historicalAssignments = historicalAssignments,
                        loggedInStudentName = loggedInStudentName,
                        loggedInStudentPhoto = loggedInStudentPhoto,
                        welcomeStatusMessage = welcomeStatusMessage,
                        onRefresh = {
                            loadingTargetScreen = ScreenType.PENDING
                            isLoading = true
                            scope.launch {
                                val (pending, submitted, photoBytes) = loadAssignmentsAndProfile(repository)
                                assignments = pending
                                historicalAssignments = submitted
                                loggedInStudentName = repository.getCurrentStudentName()
                                loggedInStudentPhoto = photoBytes
                                welcomeStatusMessage = generateWelcomeStatusMessage(
                                    pendingCount = pending.size,
                                    submittedCount = submitted.size,
                                    previousMessage = welcomeStatusMessage
                                )
                                isLoading = false
                            }
                        },
                        onLogout = {
                            isLoggedIn = false
                            assignments = emptyList()
                            historicalAssignments = emptyList()
                            loggedInStudentName = null
                            loggedInStudentPhoto = null
                            welcomeStatusMessage = "Your professors are still thinking how to annoy you in a brutal way possible."
                            currentScreen = ScreenType.PENDING
                            // Clear saved credentials on logout
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
                                    // Refresh assignments after successful upload
                                    val (pending, submitted, photoBytes) = loadAssignmentsAndProfile(repository)
                                    assignments = pending
                                    historicalAssignments = submitted
                                    loggedInStudentName = repository.getCurrentStudentName()
                                    loggedInStudentPhoto = photoBytes
                                    welcomeStatusMessage = generateWelcomeStatusMessage(
                                        pendingCount = pending.size,
                                        submittedCount = submitted.size,
                                        previousMessage = welcomeStatusMessage
                                    )
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
                                    submittedCount = fetched.size,
                                    previousMessage = welcomeStatusMessage
                                )
                                isLoading = false
                            }
                        }
                    )
                }
                ScreenType.HISTORICAL -> {
                    HistoricalAssignmentsScreen(
                        assignments = historicalAssignments,
                        loggedInStudentName = loggedInStudentName,
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
                                    // Refresh assignments after successful re-upload
                                    val (pending, submitted, photoBytes) = loadAssignmentsAndProfile(repository)
                                    assignments = pending
                                    historicalAssignments = submitted
                                    loggedInStudentName = repository.getCurrentStudentName()
                                    loggedInStudentPhoto = photoBytes
                                    welcomeStatusMessage = generateWelcomeStatusMessage(
                                        pendingCount = pending.size,
                                        submittedCount = submitted.size,
                                        previousMessage = welcomeStatusMessage
                                    )
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
                                isLoading = false
                            }
                        }
                    )
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
                        "Submitted Assignments",
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
fun LoginScreen(isLoading: Boolean, onLogin: (String, String) -> Unit) {
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
fun AssignmentsList(
    assignments: List<Assignment>,
    historicalAssignments: List<Assignment>,
    loggedInStudentName: String?,
    loggedInStudentPhoto: ByteArray?,
    welcomeStatusMessage: String,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit,
    onUploadRequested: (Assignment, Uri) -> Unit,
    onViewHistorical: () -> Unit
) {
    val context = LocalContext.current
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
    val tableScrollState = rememberScrollState()

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StudentWelcomeCard(
                    studentName = loggedInStudentName,
                    profileBitmap = profileBitmap,
                    statusMessage = welcomeStatusMessage
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
                            "No assignments",
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
                                val closedNotSubmittedCount = assignments.count { it.status == AssignmentStatus.NOT_SUBMITTED_CLOSED }
                                val submittedAssignmentsCount = historicalAssignments.size
                                val totalAssignmentsCount = pendingAssignmentsCount + closedNotSubmittedCount + submittedAssignmentsCount

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tableScrollState)
                            .background(Cyprus)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(35.dp))
                        Text("Course Title", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(140.dp))
                        Text("Title", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(100.dp))
                        Text("Start-Date", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(95.dp))
                        Text("Deadline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(110.dp))
                        Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(80.dp))
                        Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(90.dp))
                        Text("Submit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(90.dp))
                    }
                }

                itemsIndexed(assignments) { index, assignment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tableScrollState)
                            .background(if (index % 2 == 0) Color.White else Color(0xFFF0F0F0))
                            .border(1.dp, Color.LightGray)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", fontSize = 12.sp, modifier = Modifier.width(35.dp))
                        Text(
                            assignment.courseTitle,
                            fontSize = 12.sp,
                            color = Color(0xFF0066CC),
                            modifier = Modifier.width(140.dp),
                            maxLines = 1
                        )
                        Text(
                            assignment.assignmentTitle,
                            fontSize = 12.sp,
                            modifier = Modifier.width(100.dp),
                            maxLines = 1
                        )
                        Text(
                            assignment.deadline.split(" ")[0],
                            fontSize = 12.sp,
                            modifier = Modifier.width(95.dp),
                            maxLines = 1
                        )
                        Text(
                            assignment.deadline,
                            fontSize = 11.sp,
                            modifier = Modifier.width(110.dp),
                            maxLines = 1
                        )
                        val isNotSubmittedClosed = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
                        val statusText = if (isNotSubmittedClosed) "Not submitted\nClosed" else "Pending"
                        val statusColor = if (isNotSubmittedClosed) Color(0xFFD32F2F) else Color(0xFF4CAF50)
                        Text(
                            statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.width(80.dp)
                        )
                        val canDownload = assignment.downloadLink.isNotEmpty()
                        Text(
                            if (canDownload) "Download" else "N/A",
                            fontSize = 11.sp,
                            color = if (canDownload) Color(0xFF0066CC) else Color.Gray,
                            modifier = Modifier
                                .width(90.dp)
                                .clickable(enabled = canDownload) {
                                    onDownloadRequested(assignment)
                                }
                        )
                        Text(
                            "Upload File",
                            fontSize = 11.sp,
                            color = Color(0xFF0066CC),
                            modifier = Modifier
                                .width(90.dp)
                                .clickable {
                                    if (assignment.submitLink.isNotEmpty()) {
                                        selectedAssignment = assignment
                                        launcher.launch("*/*")
                                    }
                                }
                        )
                    }
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
    onNavigateBack: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit = { _ -> },
    onReuploadRequested: (Assignment, Uri) -> Unit = { _, _ -> }
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Submitted Assignments",
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
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (assignments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No submitted assignments",
                            color = Cyprus.copy(alpha = 0.55f),
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                itemsIndexed(assignments) { index, assignment ->
                    val isOpenVal = assignment.isOpen()
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
                                    Text("Submitted:", fontSize = 11.sp, color = Color.Gray)
                                    Text(assignment.submittedDate ?: "N/A", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Status:", fontSize = 11.sp, color = Color.Gray)
                                    val statusColor = if (isOpenVal) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                                    Text(
                                        assignment.getOpenClosedLabel(isOpenVal),
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
private fun StudentWelcomeCard(
    studentName: String?,
    profileBitmap: android.graphics.Bitmap?,
    statusMessage: String
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
