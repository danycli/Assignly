package com.danycli.assignmentchecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.TimetableLecture
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    lectures: List<TimetableLecture>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    lastSyncedMs: Long
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentDayOfWeek = remember { LocalDate.now().dayOfWeek.name.lowercase().capitalize() }
    var selectedDay by remember { mutableStateOf(if (lectures.any { it.day == currentDayOfWeek }) currentDayOfWeek else lectures.firstOrNull()?.day ?: "Monday") }
    
    val filteredLectures = remember(lectures, searchQuery, selectedDay) {
        lectures.filter { it.day == selectedDay }.filter {
            searchQuery.isBlank() ||
                    it.courseName.contains(searchQuery, ignoreCase = true) ||
                    it.instructor.contains(searchQuery, ignoreCase = true) ||
                    it.room.contains(searchQuery, ignoreCase = true)
        }
    }

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val sheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf<TimetableLecture?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Academic Timetable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    TodayScheduleCard(lectures)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search timetable...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            shape = RoundedCornerShape(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            singleLine = true
                        )

                        // Day Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            daysOfWeek.forEach { day ->
                                val isSelected = selectedDay == day
                                AssistChip(
                                    onClick = { selectedDay = day },
                                    label = { Text(day) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                                        labelColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        enabled = true
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                )
                            }
                        }
                    }
                }

                val liveClass = filteredLectures.firstOrNull { it.isCurrent() }
                val upcomingClasses = filteredLectures.filter { it.isUpcoming() }.sortedBy { it.startTime }
                val nextClass = if (liveClass == null) upcomingClasses.firstOrNull() else null

                if (nextClass != null && searchQuery.isBlank()) {
                    item {
                        NextClassWidget(nextClass)
                    }
                }

                if (filteredLectures.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No classes found for $selectedDay", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = if (selectedDay == currentDayOfWeek) "Today's Lectures" else "$selectedDay Lectures",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    
                    items(filteredLectures) { lecture ->
                        TimetableClassCard(
                            lecture = lecture,
                            onTap = { showDetailsSheet = lecture }
                        )
                    }
                }

                item {
                    val syncText = remember(lastSyncedMs) {
                        if (lastSyncedMs <= 0) "Never"
                        else android.text.format.DateUtils.getRelativeTimeSpanString(lastSyncedMs).toString()
                    }
                    Text(
                        text = "Last Updated: $syncText",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (showDetailsSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ClassDetailsContent(showDetailsSheet!!)
        }
    }
}

@Composable
fun TodayScheduleCard(lectures: List<TimetableLecture>) {
    val currentDay = remember { LocalDate.now().dayOfWeek.name.lowercase().capitalize() }
    val todayLectures = remember(lectures) { lectures.filter { it.day.equals(currentDay, ignoreCase = true) } }
    
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, shape = RoundedCornerShape(20.dp)),
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
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Today's Schedule",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                if (todayLectures.isNotEmpty()) {
                    Text(
                        text = "${todayLectures.size} Classes Today",
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    val liveOrNext = todayLectures.firstOrNull { it.isCurrent() } 
                        ?: todayLectures.filter { it.isUpcoming() }.minByOrNull { it.startTime }

                    if (liveOrNext != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isLive = liveOrNext.isCurrent()
                        Text(
                            text = if (isLive) "Currently In:" else "Next Class:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = liveOrNext.courseName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${liveOrNext.startTime} - ${liveOrNext.endTime}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Room, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Room ${liveOrNext.room}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        val countdownText = remember(liveOrNext) {
                            val now = LocalTime.now()
                            if (isLive) {
                                val end = runCatching { LocalTime.parse(liveOrNext.endTime, DateTimeFormatter.ofPattern("hh:mm a", Locale.US)) }.getOrNull()
                                val diff = end?.let { now.until(it, ChronoUnit.MINUTES) } ?: 0
                                "Ends in $diff minutes"
                            } else {
                                val start = runCatching { LocalTime.parse(liveOrNext.startTime, DateTimeFormatter.ofPattern("hh:mm a", Locale.US)) }.getOrNull()
                                val diff = start?.let { now.until(it, ChronoUnit.MINUTES) } ?: 0
                                if (diff < 60) "Starts in $diff minutes"
                                else "Starts in ${diff / 60}h ${diff % 60}m"
                            }
                        }
                        Text(
                            text = countdownText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = "No classes scheduled today",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enjoy your free day.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NextClassWidget(lecture: TimetableLecture) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Next Class", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(lecture.courseName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Room ${lecture.room} • ${lecture.startTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val countdown = remember(lecture) {
                val now = LocalTime.now()
                val start = runCatching { LocalTime.parse(lecture.startTime, DateTimeFormatter.ofPattern("hh:mm a", Locale.US)) }.getOrNull()
                val diff = start?.let { now.until(it, ChronoUnit.MINUTES) } ?: 0
                if (diff < 60) "$diff min" else "${diff / 60}h ${diff % 60}m"
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = "In $countdown",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TimetableClassCard(lecture: TimetableLecture, onTap: () -> Unit) {
    val isLive = remember(lecture) { lecture.isCurrent() }
    val borderColor = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val backgroundTint = if (isLive) Color(0xFF4CAF50).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundTint),
        border = BorderStroke(if (isLive) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = lecture.courseName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLive) {
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "LIVE NOW",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${lecture.startTime} - ${lecture.endTime}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Room:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lecture.room, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Instructor:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lecture.instructor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Duration:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lecture.duration, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ClassDetailsContent(lecture: TimetableLecture) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Class Details",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Course Name", lecture.courseName)
                DetailRow("Course Code", lecture.courseCode)
                DetailRow("Instructor", lecture.instructor)
                DetailRow("Room", lecture.room)
                DetailRow("Day", lecture.day)
                DetailRow("Timings", "${lecture.startTime} - ${lecture.endTime}")
                DetailRow("Duration", lecture.duration)
                DetailRow("Credit Hours", lecture.creditHours)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
