package com.danycli.assignmentchecker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.TimetableLecture
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    lectures: List<TimetableLecture>,
    timetableError: String?,
    onBack: () -> Unit
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(10000L)
        }
    }

    val currentDayOfWeek = remember { 
        LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } 
    }
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    
    var selectedDay by remember { 
        mutableStateOf(if (daysOfWeek.contains(currentDayOfWeek)) currentDayOfWeek else "Monday") 
    }
    
    val pagerState = rememberPagerState(
        initialPage = daysOfWeek.indexOf(selectedDay).coerceAtLeast(0),
        pageCount = { daysOfWeek.size }
    )
    
    LaunchedEffect(pagerState.currentPage) {
        selectedDay = daysOfWeek[pagerState.currentPage]
    }
    
    LaunchedEffect(selectedDay) {
        val index = daysOfWeek.indexOf(selectedDay)
        if (index != -1 && pagerState.currentPage != index) {
            if (abs(pagerState.currentPage - index) > 1) {
                pagerState.scrollToPage(index)
            } else {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    val groupedLectures = remember(lectures) {
        lectures.groupBy { it.day.lowercase() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Timetable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedDay,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${groupedLectures[selectedDay.lowercase()]?.size ?: 0} Classes Today",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!timetableError.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = timetableError, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                    }
                }
            }

            // STATIC DAY SELECTOR (Pill style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                daysOfWeek.forEach { day ->
                    val isSelected = selectedDay == day
                    val shortDay = day.take(3)
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedDay = day },
                        label = { Text(shortDay, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageDay = daysOfWeek[page]
                val pageLectures = groupedLectures[pageDay.lowercase()] ?: emptyList()
                
                if (pageLectures.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No classes scheduled", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Enjoy your free day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // NEXT CLASS SECTION
                        if (pageDay == currentDayOfWeek) {
                            val nextClass = pageLectures.firstOrNull { isClassUpcomingOrOngoing(it.startTime, it.endTime, currentTime) }
                            if (nextClass != null) {
                                item {
                                    NextClassWidget(nextClass, currentTime)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                        
                        items(
                            items = pageLectures,
                            key = { it.id }
                        ) { lecture ->
                            CompactTimetableCard(
                                lecture = lecture, 
                                isToday = pageDay == currentDayOfWeek,
                                currentTime = currentTime
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NextClassWidget(lecture: TimetableLecture, currentTime: LocalTime) {
    val isOngoing = isClassOngoing(lecture.startTime, lecture.endTime, currentTime)
    val isUpcoming = isClassUpcoming(lecture.startTime, currentTime)
    val labelText = if (isOngoing) "Ongoing Class" else "Next Class"
    val countdownText = if (isOngoing) {
        getRemainingTimeText(lecture.endTime, currentTime)
    } else if (isUpcoming) {
        getUpcomingTimeText(lecture.startTime, currentTime)
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                if (countdownText != null) {
                    Text(
                        text = countdownText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lecture.courseName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${lecture.startTime} - ${lecture.endTime}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = lecture.room,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
            if (isOngoing) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = getClassProgress(lecture.startTime, lecture.endTime, currentTime)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CompactTimetableCard(
    lecture: TimetableLecture,
    isToday: Boolean,
    currentTime: LocalTime
) {
    val courseColor = remember(lecture.courseName) { getCourseColor(lecture.courseName) }
    val isOngoing = if (isToday) isClassOngoing(lecture.startTime, lecture.endTime, currentTime) else false
    val isUpcoming = if (isToday) isClassUpcoming(lecture.startTime, currentTime) else false
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOngoing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(courseColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lecture.courseName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lecture.sessionType,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 14.dp) // align with text
                    )
                }
                if (isOngoing) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Now",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        getRemainingTimeText(lecture.endTime, currentTime)?.let { remainingText ->
                            Text(
                                text = remainingText,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isUpcoming) {
                    getUpcomingTimeText(lecture.startTime, currentTime)?.let { upcomingText ->
                        Text(
                            text = upcomingText,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "${lecture.startTime} - ${lecture.endTime}",
                fontSize = 13.sp,
                color = if (isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp)
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = lecture.room,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp)
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = lecture.instructor,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp)
            )

            if (isOngoing) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = getClassProgress(lecture.startTime, lecture.endTime, currentTime)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

fun getCourseColor(courseName: String): Color {
    val predefinedColors = mapOf(
        "database" to Color(0xFF1976D2),
        "data structure" to Color(0xFF388E3C),
        "software engineering" to Color(0xFF7B1FA2),
        "calculus" to Color(0xFFF57C00),
        "programming" to Color(0xFF0288D1),
        "physics" to Color(0xFFD32F2F),
        "math" to Color(0xFFF57C00)
    )
    
    val lowerName = courseName.lowercase()
    for ((key, color) in predefinedColors) {
        if (lowerName.contains(key)) return color
    }
    
    val hue = abs(courseName.hashCode() % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.8f)
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun isClassOngoing(startTimeStr: String, endTimeStr: String, currentTime: LocalTime): Boolean {
    try {
        val start = LocalTime.parse(startTimeStr.uppercase().trim(), timeFormatter)
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        
        return (currentTime.isAfter(start) || currentTime.equals(start)) && currentTime.isBefore(end)
    } catch (e: Exception) {
        return false
    }
}

fun isClassUpcoming(startTimeStr: String, currentTime: LocalTime): Boolean {
    try {
        val start = LocalTime.parse(startTimeStr.uppercase().trim(), timeFormatter)
        return currentTime.isBefore(start)
    } catch (e: Exception) {
        return false
    }
}

fun isClassUpcomingOrOngoing(startTimeStr: String, endTimeStr: String, currentTime: LocalTime): Boolean {
    try {
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        return currentTime.isBefore(end)
    } catch (e: Exception) {
        return false
    }
}

fun formatMinutesDuration(minutes: Long): String {
    if (minutes < 60) {
        return "$minutes min"
    }
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
}

fun getRemainingTimeText(endTimeStr: String, currentTime: LocalTime): String? {
    try {
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        val diff = java.time.temporal.ChronoUnit.MINUTES.between(currentTime, end)
        if (diff <= 0) return null
        return "Ends in ${formatMinutesDuration(diff)}"
    } catch (e: Exception) {
        return null
    }
}

fun getUpcomingTimeText(startTimeStr: String, currentTime: LocalTime): String? {
    try {
        val start = LocalTime.parse(startTimeStr.uppercase().trim(), timeFormatter)
        val diff = java.time.temporal.ChronoUnit.MINUTES.between(currentTime, start)
        if (diff <= 0) return null
        return "Starts in ${formatMinutesDuration(diff)}"
    } catch (e: Exception) {
        return null
    }
}

fun getClassProgress(startTimeStr: String, endTimeStr: String, currentTime: LocalTime): Float {
    try {
        val start = LocalTime.parse(startTimeStr.uppercase().trim(), timeFormatter)
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        val totalMinutes = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
        if (totalMinutes <= 0) return 0f
        val elapsedMinutes = java.time.temporal.ChronoUnit.MINUTES.between(start, currentTime)
        return (elapsedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
    } catch (e: Exception) {
        return 0f
    }
}
