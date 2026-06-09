package com.danycli.assignmentchecker.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    onViewTotal: () -> Unit,
    onViewSubmitted: () -> Unit,
    onOpenSettings: () -> Unit,
    timetableLectures: List<TimetableLecture> = emptyList(),
    onNavigateToTimetable: () -> Unit = {},
    activeUploads: List<QueuedUpload> = emptyList(),
    onDismissUpload: (QueuedUpload) -> Unit = {},
    activeDownloads: List<QueuedDownload> = emptyList(),
    onDismissDownload: (QueuedDownload) -> Unit = {},
    lastSyncedMs: Long = 0L
) {
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
    var dueFilter by remember { mutableStateOf(PendingDueFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val filteredAssignments by produceState(
        initialValue = assignments,
        assignments,
        dueFilter,
        searchQuery
    ) {
        value = withContext(Dispatchers.Default) {
            val normalizedQuery = searchQuery.trim().lowercase()
            assignments.filter { assignment ->
                val matchesQuery = normalizedQuery.isBlank() ||
                    assignment.courseTitle.contains(normalizedQuery, ignoreCase = true) ||
                    assignment.assignmentTitle.contains(normalizedQuery, ignoreCase = true)
                val matchesDue = matchesDueFilter(assignment, dueFilter)
                matchesQuery && matchesDue
            }
        }
    }

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
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "My Assignments",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!loggedInStudentName.isNullOrBlank()) {
                            Text(
                                "Logged in: $loggedInStudentName",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("assignment_list"),
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

                item {
                    OutlinedButton(
                        onClick = onNavigateToTimetable,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("View Timetable", fontWeight = FontWeight.Bold)
                    }
                }

                if (activeUploads.isNotEmpty()) {
                    items(
                        items = activeUploads,
                        key = { "upload-${it.id}" }
                    ) { upload ->
                        ActiveUploadStatusCard(
                            upload = upload,
                            onDismiss = { onDismissUpload(upload) }
                        )
                    }
                }

                if (activeDownloads.isNotEmpty()) {
                    items(
                        items = activeDownloads,
                        key = { "download-${it.id}" }
                    ) { download ->
                        ActiveDownloadStatusCard(
                            download = download,
                            onDismiss = { onDismissDownload(download) }
                        )
                    }
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
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    item {
                        val relativeSyncTime = remember(lastSyncedMs, isRefreshing) {
                            if (lastSyncedMs <= 0L) "Never" 
                            else android.text.format.DateUtils.getRelativeTimeSpanString(
                                lastSyncedMs, 
                                System.currentTimeMillis(), 
                                android.text.format.DateUtils.MINUTE_IN_MILLIS
                            ).toString()
                        }
                        val isStale = lastSyncedMs > 0 && (System.currentTimeMillis() - lastSyncedMs > 24 * 3600 * 1000L)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isStale) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Stale data",
                                    tint = if (isSystemInDarkTheme()) Color(0xFFFBC02D) else Color(0xFFFBC02D),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "Last updated: $relativeSyncTime",
                                fontSize = 10.sp,
                                color = if (isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontWeight = if (isStale) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Assignment Summary Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, shape = RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
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
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val pendingAssignmentsCount = assignments.count { it.status == AssignmentStatus.PENDING }
                                    val submittedAssignmentsCount = countSuccessfulSubmissions(historicalAssignments)
                                    val totalAssignmentsCount = assignments.size + historicalAssignments.size

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onViewTotal() }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text("$totalAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                        Text("Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("$pendingAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color(0xFFFFE082) else Color(0xFFFFD700))
                                        Text("Pending", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onViewSubmitted() }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text("$submittedAssignmentsCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF4CAF50))
                                        Text("Submitted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Search Bar
                                val isDark = isSystemInDarkTheme()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(
                                            elevation = 2.dp,
                                            shape = RoundedCornerShape(50.dp),
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        )
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(50.dp)
                                        )
                                        .border(
                                            width = 1.2.dp,
                                            color = if (searchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.36f),
                                            shape = RoundedCornerShape(50.dp)
                                        )
                                        .padding(horizontal = 15.dp, vertical = 18.dp)
                                ) {
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Start
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { searchFocused = it.isFocused },
                                        decorationBox = { innerTextField ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Search",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (searchQuery.isBlank()) {
                                                        Text(
                                                            text = "Search by subject or assignment",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

                                Text("Due date filter", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PendingDueFilter.values().forEach { option ->
                                        val label = when (option) {
                                            PendingDueFilter.ALL -> "Any"
                                            PendingDueFilter.TODAY -> "Today"
                                            PendingDueFilter.NEXT_3_DAYS -> "Next 3 days"
                                            PendingDueFilter.NEXT_7_DAYS -> "Next 7 days"
                                        }
                                        AssistChip(
                                            onClick = { dueFilter = option },
                                            label = { Text(label) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (dueFilter == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = if (dueFilter == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        if (filteredAssignments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (assignments.isEmpty()) "No pending assignments" else "No assignments match filters",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Text(
                                text = "Pending Assignments",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    itemsIndexed(
                        items = filteredAssignments,
                        key = { index, assignment ->
                            "${assignment.courseTitle}|${assignment.assignmentTitle}|${assignment.deadline}|${assignment.status}|$index"
                        },
                        contentType = { _, _ -> "pending-assignment-row-lite" }
                    ) { index, assignment ->
                        PendingAssignmentRow(
                            index = index + 1,
                            assignment = assignment,
                            searchQuery = searchQuery,
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PendingAssignmentRow(
    index: Int,
    assignment: Assignment,
    searchQuery: String = "",
    onDownloadRequested: () -> Unit,
    onUploadRequested: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val canDownload = assignment.downloadLink.isNotEmpty()
    val canUpload = assignment.submitLink.isNotEmpty()
    val isNotSubmittedClosed = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
    val statusText = if (isNotSubmittedClosed) "Not submitted • Closed" else "Pending"
    val isDark = isSystemInDarkTheme()
    val statusColor = if (isNotSubmittedClosed) MaterialTheme.colorScheme.error else if (isDark) Color(0xFFA5D6A7) else Color(0xFF4CAF50)
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = "Show assignment actions"
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HighlightedText(
                        text = assignment.courseTitle,
                        query = searchQuery,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF4FC3F7) else Color(0xFF0066CC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HighlightedText(
                        text = assignment.assignmentTitle,
                        query = searchQuery,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Due: ${assignment.deadline}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = if (canDownload) (if (isDark) Color(0xFF4FC3F7) else Color(0xFF0066CC)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable(enabled = canDownload, onClick = onDownloadRequested)
                )
                Text(
                    text = if (canUpload) "Upload" else "Closed",
                    fontSize = 11.sp,
                    color = if (canUpload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable(enabled = canUpload, onClick = onUploadRequested)
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy title") },
                onClick = {
                    menuExpanded = false
                    clipboardManager.setText(AnnotatedString(assignment.assignmentTitle))
                    Toast.makeText(context, "Title copied", Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("Open course portal") },
                onClick = {
                    menuExpanded = false
                    uriHandler.openUri(BuildConfig.PORTAL_BASE_URL + "/CoursePortal.aspx")
                }
            )
            DropdownMenuItem(
                text = { Text(if (canDownload) "Download instructions" else "Instructions unavailable") },
                enabled = canDownload,
                onClick = {
                    menuExpanded = false
                    onDownloadRequested()
                }
            )
            DropdownMenuItem(
                text = { Text(if (canUpload) "Upload submission" else "Submission closed") },
                enabled = canUpload,
                onClick = {
                    menuExpanded = false
                    onUploadRequested()
                }
            )
        }
    }
}

@Composable
fun StudentWelcomeCard(
    studentName: String?,
    profileBitmap: Bitmap?,
    statusMessage: String,
    attendanceInsightMessage: String?
) {
    val resolvedName = studentName?.takeIf { it.isNotBlank() } ?: "Student"
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
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
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = resolvedName,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = statusMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!attendanceInsightMessage.isNullOrBlank()) {
                        Text(
                            text = attendanceInsightMessage,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
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
                            painter = painterResource(id = com.danycli.assignmentchecker.R.drawable.ic_launcher_foreground),
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
