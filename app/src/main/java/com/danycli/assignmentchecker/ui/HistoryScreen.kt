package com.danycli.assignmentchecker.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoricalAssignmentsScreen(
    assignments: List<Assignment>,
    loggedInStudentName: String?,
    title: String = "Assignment History",
    emptyStateText: String = "No assignment history",
    onOpenDisclaimer: () -> Unit,
    onNavigateBack: () -> Unit,
    onDownloadRequested: (Assignment) -> Unit = { _ -> },
    onReuploadRequested: (Assignment, Uri) -> Unit = { _, _ -> },
    activeUploads: List<QueuedUpload> = emptyList(),
    onDismissUpload: (QueuedUpload) -> Unit = {},
    activeDownloads: List<QueuedDownload> = emptyList(),
    onDismissDownload: (QueuedDownload) -> Unit = {},
    lastSyncedMs: Long = 0L
) {
    var historySearchQuery by remember { mutableStateOf("") }
    var historySearchFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val historySearchBorderColor = if (historySearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
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
                            title,
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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

            item {
                val relativeSyncTime = remember(lastSyncedMs) {
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
                            tint = Color(0xFFFBC02D),
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
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            if (assignments.isEmpty()) emptyStateText else "No matching assignments",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
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
                    var menuExpanded by remember { mutableStateOf(false) }
                    val clipboardManager = LocalClipboardManager.current
                    val uriHandler = LocalUriHandler.current
                    val context = LocalContext.current
                    val hasSubmitLink = assignment.submitLink.isNotEmpty()
                    val hasDownloadLink = assignment.downloadLink.isNotEmpty()
                    val isDark = isSystemInDarkTheme()

                    Box {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { menuExpanded = true },
                                    onLongClickLabel = "Show assignment actions"
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                HighlightedText(
                                    text = assignment.courseTitle,
                                    query = historySearchQuery,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF4FC3F7) else Color(0xFF0066CC)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                HighlightedText(
                                    text = assignment.assignmentTitle,
                                    query = historySearchQuery,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Deadline:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(assignment.deadline, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Column {
                                        val isNotSubmitted = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
                                        Text(if (isNotSubmitted) "Attempt:" else "Submitted:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val attemptColor = if (isNotSubmitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                                        Text("Status:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val isNotSubmitted = assignment.status == AssignmentStatus.NOT_SUBMITTED_CLOSED
                                        val statusColor = when {
                                            isNotSubmitted -> MaterialTheme.colorScheme.error
                                            isOpenVal -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF4CAF50)
                                            else -> MaterialTheme.colorScheme.error
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

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
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download instructions",
                                            tint = if (hasDownloadLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (hasDownloadLink) "Download Instructions" else "Instructions Unavailable",
                                            fontSize = 12.sp,
                                            color = if (hasDownloadLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Change File",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
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
                                text = { Text(if (hasDownloadLink) "Download instructions" else "Instructions unavailable") },
                                enabled = hasDownloadLink,
                                onClick = {
                                    menuExpanded = false
                                    onDownloadRequested(assignment)
                                }
                            )
                            if (isOpenVal && hasSubmitLink) {
                                DropdownMenuItem(
                                    text = { Text("Change submission") },
                                    onClick = {
                                        menuExpanded = false
                                        selectedAssignment = assignment
                                        launcher.launch("*/*")
                                    }
                                )
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
