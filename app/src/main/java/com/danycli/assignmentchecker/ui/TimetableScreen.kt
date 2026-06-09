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
fun TimetableBottomSheetContent(
    lectures: List<TimetableLecture>,
    timetableError: String?,
    onClose: () -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
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
                    text = "📅 $selectedDay",
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
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Planner")
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
                        Text("🎉", fontSize = 48.sp)
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
                        val nextClass = pageLectures.firstOrNull { isClassUpcomingOrOngoing(it.startTime, it.endTime) }
                        if (nextClass != null) {
                            item {
                                NextClassWidget(nextClass)
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
                            isToday = pageDay == currentDayOfWeek
                        )
                    }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Next Class",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
        }
    }
}

@Composable
fun CompactTimetableCard(lecture: TimetableLecture, isToday: Boolean) {
    val courseColor = remember(lecture.courseName) { getCourseColor(lecture.courseName) }
    val isOngoing = remember(lecture, isToday) { if (isToday) isClassOngoing(lecture.startTime, lecture.endTime) else false }
    
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
                    Text(
                        text = "Now",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
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

fun isClassOngoing(startTimeStr: String, endTimeStr: String): Boolean {
    try {
        val start = LocalTime.parse(startTimeStr.uppercase().trim(), timeFormatter)
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        val now = LocalTime.now()
        
        return now.isAfter(start) && now.isBefore(end)
    } catch (e: Exception) {
        return false
    }
}

fun isClassUpcomingOrOngoing(startTimeStr: String, endTimeStr: String): Boolean {
    try {
        val end = LocalTime.parse(endTimeStr.uppercase().trim(), timeFormatter)
        val now = LocalTime.now()
        
        return now.isBefore(end)
    } catch (e: Exception) {
        return false
    }
}
