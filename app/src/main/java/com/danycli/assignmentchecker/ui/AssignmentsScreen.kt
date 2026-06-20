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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.foundation.layout.aspectRatio
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
fun DashboardScreen(
    loggedInStudentName: String?,
    loggedInStudentPhoto: ByteArray?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToGrades: () -> Unit = {},
    onNavigateToMarks: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToFee: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToAssignments: () -> Unit = {},
    lastSyncedMs: Long = 0L,
    studentProfile: StudentProfile? = null,
    enrolledCourses: List<EnrolledCourse> = emptyList(),
    enrolledCoursesSemester: String = "",
    gpaSummary: GpaSummary = GpaSummary(0.0, 0.0, emptyList()),
    attendanceSummaryList: List<AttendanceSummary> = emptyList()
) {
    val context = LocalContext.current

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
                            "Assignly Portal",
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dashboard_screen"),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Student Identity Card
                item {
                    StudentIdentityCard(
                        profile = studentProfile,
                        loggedInStudentName = loggedInStudentName,
                        profileBitmap = profileBitmap,
                        enrolledCoursesSemester = enrolledCoursesSemester,
                        onNavigateToProfile = onNavigateToProfile
                    )
                }

                // 2. Academic Snapshot Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val cgpaStr = if (gpaSummary.cgpa > 0.0) String.format(Locale.US, "%.2f", gpaSummary.cgpa) else "N/A"
                        
                        val avgAttendance = if (attendanceSummaryList.isNotEmpty()) {
                            attendanceSummaryList.map { it.percentage }.average()
                        } else {
                            0.0
                        }
                        val attendanceStr = if (attendanceSummaryList.isNotEmpty()) {
                            String.format(Locale.US, "%.0f%%", avgAttendance)
                        } else {
                            "N/A"
                        }
                        
                        val coursesStr = if (enrolledCourses.isNotEmpty()) "${enrolledCourses.size} Courses" else "N/A"
                        
                        val totalCredits = enrolledCourses.sumOf { parseCreditHoursValue(it.creditHours) }
                        val creditsStr = if (enrolledCourses.isNotEmpty()) formatCreditHours(totalCredits) else "N/A"

                        SnapshotCard("CGPA", cgpaStr, Modifier.weight(1f))
                        SnapshotCard("Attendance", attendanceStr, Modifier.weight(1f))
                        SnapshotCard("Registered", coursesStr, Modifier.weight(1f))
                        SnapshotCard("Credits", creditsStr, Modifier.weight(1f))
                    }
                }



                // 4. Academic Services Shortcuts
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Academic Services",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShortcutCard("Timetable", Icons.Default.CalendarMonth, onNavigateToTimetable, Modifier.weight(1f))
                            ShortcutCard("Attendance", Icons.Default.Analytics, onNavigateToAttendance, Modifier.weight(1f))
                            ShortcutCard("Grades", Icons.Default.School, onNavigateToGrades, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShortcutCard("Marks", Icons.Default.Description, onNavigateToMarks, Modifier.weight(1f))
                            ShortcutCard("Courses", Icons.Default.MenuBook, onNavigateToCourses, Modifier.weight(1f))
                            ShortcutCard("Fee", Icons.Default.Payments, onNavigateToFee, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShortcutCard("Assignments", Icons.Default.Assignment, onNavigateToAssignments, Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 5. Footer (Last synced + disclaimer)
                item {
                    val relativeSyncTime = remember(lastSyncedMs, isRefreshing) {
                        if (lastSyncedMs <= 0L) "Never" 
                        else android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastSyncedMs, 
                            System.currentTimeMillis(), 
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Last synced: $relativeSyncTime",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Data synced from COMSATS Student Information System.",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    assignments: List<Assignment>,
    historicalAssignments: List<Assignment>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit,
    onUploadRequested: (Assignment, Uri) -> Unit,
    onNavigateBack: () -> Unit,
    onViewTotal: () -> Unit,
    onViewSubmitted: () -> Unit,
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Assignments",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
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
                // 1. Assignment Summary Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, shape = RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                "Assignments Summary",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val pendingCount = assignments.count { it.status == AssignmentStatus.PENDING }
                                val submittedCount = countSuccessfulSubmissions(historicalAssignments)
                                val totalCount = assignments.size + historicalAssignments.size

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onViewTotal() }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text("$totalCount", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text("$pendingCount", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (isSystemInDarkTheme()) Color(0xFFFBC02D) else Color(0xFFD4AF37))
                                    Text("Pending", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onViewSubmitted() }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text("$submittedCount", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32))
                                    Text("Submitted", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Active background tasks
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

                // 2. Search and Filters Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clean Search Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (searchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
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
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isBlank()) {
                                                Text(
                                                    text = "Search by subject or assignment",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                }
                            )
                        }

                        // Filter Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PendingDueFilter.values().forEach { option ->
                                val label = when (option) {
                                    PendingDueFilter.ALL -> "Any Due"
                                    PendingDueFilter.TODAY -> "Due Today"
                                    PendingDueFilter.NEXT_3_DAYS -> "Next 3 Days"
                                    PendingDueFilter.NEXT_7_DAYS -> "Next 7 Days"
                                }
                                val selected = dueFilter == option
                                FilterChip(
                                    selected = selected,
                                    onClick = { dueFilter = option },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Assignments list items
                if (filteredAssignments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (assignments.isEmpty()) "No pending assignments" else "No assignments match filters",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
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

                // 4. Footer (Last synced + disclaimer)
                item {
                    val relativeSyncTime = remember(lastSyncedMs, isRefreshing) {
                        if (lastSyncedMs <= 0L) "Never" 
                        else android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastSyncedMs, 
                            System.currentTimeMillis(), 
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Last synced: $relativeSyncTime",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Data synced from COMSATS Student Information System.",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
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

data class RecentActivity(
    val title: String,
    val description: String,
    val relativeTime: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val epochMs: Long
)

@Composable
fun StudentIdentityCard(
    profile: StudentProfile?,
    loggedInStudentName: String?,
    profileBitmap: Bitmap?,
    enrolledCoursesSemester: String,
    onNavigateToProfile: () -> Unit
) {
    val resolvedName = profile?.name?.takeIf { it.isNotBlank() } ?: loggedInStudentName?.takeIf { it.isNotBlank() } ?: "Student"
    val resolvedReg = profile?.regNumber?.takeIf { it.isNotBlank() } ?: "CIIT/SP25-BCS-136/ATD"
    val resolvedProgram = profile?.program?.takeIf { it.isNotBlank() } ?: "BS Computer Science"
    val resolvedSection = profile?.section?.takeIf { it.isNotBlank() } ?: "Section C"
    val resolvedCampus = profile?.campus?.takeIf { it.isNotBlank() } ?: "Abbottabad Campus"
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(20.dp))
            .clickable { onNavigateToProfile() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isDark) {
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                        } else {
                            listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), MaterialTheme.colorScheme.surface)
                        }
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Photo
                Box(
                    modifier = Modifier
                        .size(80.dp)
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

                // Student Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = resolvedName,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = resolvedReg,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$resolvedProgram • $resolvedSection",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = resolvedCampus,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Chips Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasScholarship = profile?.scholarshipStatus?.let { status ->
                            val s = status.trim().lowercase()
                            s.isNotBlank() && s != "none" && s != "no" && s != "n/a" && 
                            !s.contains("no scholarship") && !s.contains("no active") &&
                            (s.contains("yes") || s.contains("active") || s.contains("holder") || s.contains("eligible") || s.contains("approved"))
                        } == true
                        
                        if (hasScholarship) {
                            Surface(
                                shape = RoundedCornerShape(50.dp),
                                color = if (isDark) Color(0xFF004D40) else Color(0xFFE0F2F1),
                                modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                            ) {
                                Text(
                                    text = "Scholarship Holder",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF80CBC4) else Color(0xFF004D40),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        val semesterLabel = enrolledCoursesSemester.ifBlank { "Current Semester" }
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                        ) {
                            Text(
                                text = semesterLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1.45f)
            .shadow(2.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SnapshotCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(2.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QuickAccessRow(
    onNavigateToProfile: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToAssignments: () -> Unit,
    onNavigateToFee: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Quick Actions",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val items = listOf(
                Triple("Profile", Icons.Default.Person, onNavigateToProfile),
                Triple("Timetable", Icons.Default.CalendarMonth, onNavigateToTimetable),
                Triple("Assignments", Icons.Default.Assignment, onNavigateToAssignments),
                Triple("Fee", Icons.Default.Payments, onNavigateToFee)
            )
            items.forEach { (label, icon, action) ->
                Surface(
                    onClick = action,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .shadow(1.dp, shape = RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
