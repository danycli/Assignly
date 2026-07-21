package com.danycli.assignmentchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.AttendanceDetail
import com.danycli.assignmentchecker.AttendanceSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    attendanceSummaryList: List<AttendanceSummary>,
    cachedAttendanceDetails: List<AttendanceDetail>,
    errorMessage: String? = null,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadDetail: suspend (String) -> List<AttendanceDetail>,
    onBack: () -> Unit
) {
    var selectedCourse by remember { mutableStateOf<AttendanceSummary?>(null) }
    var detailsList by remember { mutableStateOf<List<AttendanceDetail>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val totalLectures = attendanceSummaryList.sumOf { it.totalLectures }
    val totalPresent = attendanceSummaryList.sumOf { it.present }
    val overallPercentage = if (attendanceSummaryList.isNotEmpty()) {
        attendanceSummaryList.map { it.percentage }.average()
    } else {
        0.0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Attendance Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Overall summary header
                    item {
                        OverallAttendanceHeader(
                            overallPercentage = overallPercentage,
                            totalPresent = totalPresent,
                            totalLectures = totalLectures
                        )
                    }

                    if (errorMessage != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Feedback Required",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = errorMessage.removePrefix("FEEDBACK_REQUIRED:").trim(),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    } else if (attendanceSummaryList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No attendance records found.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val cleanRegex = "[^A-Z0-9]".toRegex()
                        items(attendanceSummaryList, key = { "${it.courseCode}_${it.courseName}" }) { summary ->
                            AttendanceCourseCard(
                                summary = summary,
                                onClick = {
                                    selectedCourse = summary
                                    
                                    val cleanSummaryCode = summary.courseCode.trim().uppercase().replace(cleanRegex, "")
                                    val cleanSummaryName = summary.courseName.trim().uppercase().replace(cleanRegex, "")
                                    val cached = cachedAttendanceDetails.filter { detail ->
                                        val cleanDetailCode = detail.courseCode.trim().uppercase().replace(cleanRegex, "")
                                        cleanDetailCode == cleanSummaryCode || 
                                        cleanDetailCode == cleanSummaryName ||
                                        (cleanSummaryCode.isNotEmpty() && (cleanDetailCode.contains(cleanSummaryCode) || cleanSummaryCode.contains(cleanDetailCode)))
                                    }

                                    if (cached.isNotEmpty()) {
                                        detailsList = cached
                                        isLoadingDetails = false
                                        detailsError = null
                                    } else {
                                        isLoadingDetails = true
                                        detailsError = null
                                        detailsList = emptyList()
                                        scope.launch {
                                            try {
                                                detailsList = onLoadDetail(if (summary.courseCode.isBlank()) summary.courseName else summary.courseCode)
                                            } catch (e: Exception) {
                                                detailsError = e.message ?: "Failed to load details"
                                            } finally {
                                                isLoadingDetails = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedCourse != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AttendanceDetailBottomSheet(
                course = selectedCourse!!,
                details = detailsList,
                isLoading = isLoadingDetails,
                error = detailsError,
                onClose = { selectedCourse = null }
            )
        }
    }
}

@Composable
fun OverallAttendanceHeader(
    overallPercentage: Double,
    totalPresent: Int,
    totalLectures: Int
) {
    val isDark = isSystemInDarkTheme()
    val accentColor = when {
        overallPercentage >= 75.0 -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        overallPercentage >= 60.0 -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
        else -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
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
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 
                        val p = (overallPercentage / 100).toFloat()
                        if (p.isNaN()) 0f else p
                    },
                    modifier = Modifier.fillMaxSize(),
                    color = accentColor,
                    strokeWidth = 8.dp,
                    trackColor = accentColor.copy(alpha = 0.15f),
                )
                Text(
                    text = String.format("%.0f%%", overallPercentage),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Aggregate Attendance",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$totalPresent Present / $totalLectures Total",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                val statusMessage = when {
                    overallPercentage >= 75.0 -> "Safe zone! Keep it up."
                    overallPercentage >= 60.0 -> "Warning! Close to falling below limit."
                    else -> "Danger! Below 75% requirement."
                }
                Text(
                    text = statusMessage,
                    fontSize = 12.sp,
                    color = if (overallPercentage < 75.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AttendanceCourseCard(
    summary: AttendanceSummary,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val statusColor = when {
        summary.percentage >= 75.0 -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        summary.percentage >= 60.0 -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
        else -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.courseCode,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary.courseName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.0f%%", summary.percentage),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    if (summary.percentage < 75.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Low Attendance",
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Short",
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { 
                    val p = (summary.percentage / 100.0).toFloat()
                    if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AttendanceStatBadges("Lectures: ${summary.totalLectures}", MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary)
                AttendanceStatBadges("Present: ${summary.present}", Color(0xFF2E7D32).copy(alpha = 0.1f), Color(0xFF2E7D32))
                AttendanceStatBadges("Absent: ${summary.absent}", Color(0xFFC62828).copy(alpha = 0.1f), Color(0xFFC62828))
            }
        }
    }
}

@Composable
fun AttendanceStatBadges(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun AttendanceDetailBottomSheet(
    course: AttendanceSummary,
    details: List<AttendanceDetail>,
    isLoading: Boolean,
    error: String?,
    onClose: () -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") } // "All", "Present", "Absent", "Leave"

    val filteredDetails = remember(details, statusFilter) {
        if (statusFilter == "All") {
            details
        } else {
            details.filter {
                when (statusFilter) {
                    "Present" -> isPresentStatus(it.status)
                    "Absent" -> isAbsentStatus(it.status)
                    "Leave" -> isLeaveStatus(it.status)
                    else -> false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        // Bottom sheet header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.courseCode,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = course.courseName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error loading attendance history:\n$error",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Status filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "Present", "Absent", "Leave")
                filters.forEach { filter ->
                    val isSelected = statusFilter == filter
                    val badgeColor = when (filter) {
                        "Present" -> Color(0xFF2E7D32)
                        "Absent" -> Color(0xFFC62828)
                        "Leave" -> Color(0xFFF57F17)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { statusFilter = filter },
                        label = { Text(filter, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = badgeColor,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (filteredDetails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No records match this filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDetails) { item ->
                        AttendanceDetailRow(item)
                    }
                }
            }
        }
    }
}

private fun isPresentStatus(status: String): Boolean {
    val s = status.trim().uppercase()
    return s == "P" || s == "PRESENT"
}

private fun isAbsentStatus(status: String): Boolean {
    val s = status.trim().uppercase()
    return s == "A" || s == "ABSENT"
}

private fun isLeaveStatus(status: String): Boolean {
    val s = status.trim().uppercase()
    return s == "L" || s == "LEAVE" || s.contains("LEAVE")
}

@Composable
fun AttendanceDetailRow(item: AttendanceDetail) {
    val isDark = isSystemInDarkTheme()
    val isPresent = isPresentStatus(item.status)
    val isAbsent = isAbsentStatus(item.status)
    val badgeBgColor = when {
        isPresent -> Color(0xFF2E7D32).copy(alpha = 0.12f)
        isAbsent -> Color(0xFFC62828).copy(alpha = 0.12f)
        else -> Color(0xFFF57F17).copy(alpha = 0.12f)
    }
    val badgeTextColor = when {
        isPresent -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        isAbsent -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
        else -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
    }
    
    val displayStatus = when {
        isPresent -> "Present"
        isAbsent -> "Absent"
        isLeaveStatus(item.status) -> "Leave"
        else -> item.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.date,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.remarks.takeIf { it.isNotBlank() } ?: "No topic details",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = displayStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = badgeTextColor
                )
            }
        }
    }
}
