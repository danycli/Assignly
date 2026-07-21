package com.danycli.assignmentchecker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.CourseGrade
import com.danycli.assignmentchecker.GpaSummary
import com.danycli.assignmentchecker.SemesterGrades
import com.danycli.assignmentchecker.isSemesterActiveOrIncomplete
import com.danycli.assignmentchecker.hasPublishedGrades
import com.danycli.assignmentchecker.countCompletedSemesters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    gpaSummary: GpaSummary,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var selectedSemester by remember { mutableStateOf<SemesterGrades?>(null) }
    val displaySemesters = remember(gpaSummary.semesters) {
        gpaSummary.semesters
    }

    if (selectedSemester != null) {
        val originalIndex = gpaSummary.semesters.indexOf(selectedSemester!!)
        val isActive = isSemesterActiveOrIncomplete(selectedSemester!!, originalIndex, gpaSummary.semesters.size)
        BackHandler {
            selectedSemester = null
        }
        SemesterTranscriptScreen(
            semester = selectedSemester!!,
            isActive = isActive,
            onBack = { selectedSemester = null }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Grades & CGPA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                        // 1. CGPA Snapshot Hero Card
                        item {
                            GpaHeroCard(gpaSummary = gpaSummary)
                        }

                        // 2. GPA Progression Timeline
                        val validSemesters = gpaSummary.semesters.filterIndexed { index, semester ->
                            !isSemesterActiveOrIncomplete(semester, index, gpaSummary.semesters.size)
                        }
                        if (validSemesters.size >= 2) {
                            item {
                                SemesterProgressionTimeline(semesters = validSemesters)
                            }
                        }

                        // 3. Semester Cards List
                        if (gpaSummary.semesters.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No result records found.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(displaySemesters) { displayIndex, semester ->
                                val originalIndex = gpaSummary.semesters.indexOf(semester)
                                val isActive = isSemesterActiveOrIncomplete(semester, originalIndex, gpaSummary.semesters.size)
                                SemesterSummaryCard(
                                    semester = semester,
                                    isActive = isActive,
                                    onClick = { selectedSemester = semester }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpaHeroCard(gpaSummary: GpaSummary) {
    val isDark = isSystemInDarkTheme()
    
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isLoaded = true }

    val cgpaColor = when {
        gpaSummary.cgpa >= 3.5 -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        gpaSummary.cgpa >= 3.0 -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0)
        gpaSummary.cgpa >= 2.0 -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
        else -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    }

    val semesters = gpaSummary.semesters
    val completedSemesters = semesters.filterIndexed { index, semester ->
        !isSemesterActiveOrIncomplete(semester, index, semesters.size)
    }
    val totalSemesters = countCompletedSemesters(semesters)
    val totalCourses = completedSemesters.sumOf { it.courses.size }

    val cgpaChange = if (completedSemesters.size >= 2) {
        val current = completedSemesters.last().cgpa
        val previous = completedSemesters[completedSemesters.size - 2].cgpa
        current - previous
    } else null

    val animatedCgpa by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLoaded) gpaSummary.cgpa.toFloat() else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "cgpa"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Decorative Elements
            val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
            val blobColor1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            val blobColor2 = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f)

            Canvas(modifier = Modifier.matchParentSize()) {
                // Gradient Blobs
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor1, Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.2f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.1f, size.height * 0.2f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor2, Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.8f),
                        radius = size.width * 0.6f
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.9f, size.height * 0.8f)
                )

                // Dotted Grid
                val dotSpacing = 24.dp.toPx()
                val dotRadius = 1.dp.toPx()
                for (x in 0..(size.width / dotSpacing).toInt()) {
                    for (y in 0..(size.height / dotSpacing).toInt()) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x * dotSpacing, y * dotSpacing)
                        )
                    }
                }
            }

            // Watermark
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-20).dp)
                    .size(160.dp)
                    .alpha(if (isDark) 0.03f else 0.04f),
                tint = MaterialTheme.colorScheme.onSurface
            )

            // Content
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Top Section: CGPA and Standing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CUMULATIVE GPA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (gpaSummary.cgpa > 0.0) String.format("%.2f", animatedCgpa) else "0.00",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = cgpaColor,
                                letterSpacing = (-1).sp
                            )
                            
                            // Glassmorphism Badge
                            val standing = gpaSummary.academicStanding ?: when {
                                gpaSummary.cgpa >= 3.5 -> "Excellent"
                                gpaSummary.cgpa >= 3.0 -> "Good"
                                gpaSummary.cgpa >= 2.0 -> "Satisfactory"
                                gpaSummary.cgpa > 0.0 -> "Warning"
                                else -> "N/A"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                cgpaColor.copy(alpha = 0.15f),
                                                cgpaColor.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = cgpaColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = cgpaColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = standing,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cgpaColor
                                )
                            }
                        }
                    }

                    // CGPA Change Trend Chip
                    if (cgpaChange != null) {
                        val isPositive = cgpaChange >= 0.0
                        val changeColor = if (isPositive) (if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)) else Color(0xFFE57373)
                        val icon = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.TrendingDown
                        val sign = if (isPositive) "+" else ""
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .shadow(elevation = 2.dp, shape = RoundedCornerShape(50), spotColor = changeColor.copy(alpha = 0.2f))
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, changeColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(changeColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = changeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = "$sign${String.format("%.2f", cgpaChange)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = changeColor
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                // Bottom Section: Three-column Stats Row using Mini Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Credits
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        title = "Credits",
                        value = "${gpaSummary.totalCreditHours.toInt()}",
                        icon = Icons.Default.MenuBook
                    )
                    
                    // Courses
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        title = "Courses",
                        value = "$totalCourses",
                        icon = Icons.Default.CheckCircle
                    )

                    // Semesters
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        title = "Semesters",
                        value = "$totalSemesters",
                        icon = Icons.Default.DateRange
                    )
                }
            }
        }
    }
}

@Composable
fun StatMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SemesterProgressionTimeline(
    semesters: List<SemesterGrades>,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GPA PROGRESSION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                itemsIndexed(semesters) { index, sem ->
                    // 1. Progression Arrow (if not first semester)
                    if (index > 0) {
                        val prevSem = semesters[index - 1]
                        val diff = sem.sgpa - prevSem.sgpa
                        val isPositive = diff >= 0.0
                        val changeColor = if (isPositive) {
                            if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        } else {
                            if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
                        }
                        val sign = if (isPositive) "+" else ""
                        val arrow = if (isPositive) "▲" else "▼"
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(changeColor.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = arrow,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                            Text(
                                text = "$sign${String.format("%.2f", diff)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                        }
                    }
                    
                    // 2. Semester Chip
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = sem.semesterName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "SGPA:",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format("%.2f", sem.sgpa),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
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

@Composable
fun SemesterSummaryCard(
    semester: SemesterGrades,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Name and Badge + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = semester.semesterName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Status Badge
                    if (isActive) {
                        val hasGrades = hasPublishedGrades(semester)
                        val statusText = if (hasGrades) "Pending" else "Active"
                        val badgeBgColor = if (hasGrades) {
                            if (isDark) Color(0xFF37474F) else Color(0xFFECEFF1)
                        } else {
                            if (isDark) Color(0xFFE65100).copy(alpha = 0.15f) else Color(0xFFFFE0B2)
                        }
                        val badgeTextColor = if (hasGrades) {
                            if (isDark) Color(0xFFCFD8DC) else Color(0xFF455A64)
                        } else {
                            if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBgColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFE8F5E9))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Completed",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Row 2: SGPA / CGPA values + Credits & Courses count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SGPA Display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SGPA",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (!isActive && semester.sgpa > 0.0) String.format("%.2f", semester.sgpa) else "N/A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // CGPA Display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CGPA",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (!isActive && semester.cgpa > 0.0) String.format("%.2f", semester.cgpa) else "N/A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Credits & Courses count
                val creditsText = if (semester.creditHours > 0.0) {
                    "${semester.creditHours.toInt()}"
                } else {
                    "${semester.courses.size * 3}"
                }
                Text(
                    text = "$creditsText Cr  •  ${semester.courses.size} Courses",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterTranscriptScreen(
    semester: SemesterGrades,
    isActive: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Semester Transcript", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = semester.semesterName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("SGPA", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (!isActive && semester.sgpa > 0.0) String.format("%.2f", semester.sgpa) else "N/A",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text("CGPA", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (!isActive && semester.cgpa > 0.0) String.format("%.2f", semester.cgpa) else "N/A",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text("Credits Earned", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val creditsVal = if (semester.creditHours > 0.0) semester.creditHours.toInt() else semester.courses.size * 3
                            Text("$creditsVal Credits", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Courses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${semester.courses.size} Courses", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Code", modifier = Modifier.weight(0.15f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Course Title", modifier = Modifier.weight(0.32f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cr.", modifier = Modifier.weight(0.08f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Text("Marks", modifier = Modifier.weight(0.11f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Text("Gr.", modifier = Modifier.weight(0.08f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Text("GP", modifier = Modifier.weight(0.11f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Text("Contrib.", modifier = Modifier.weight(0.15f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(semester.courses) { course ->
                            TranscriptRow(course = course, semesterTotalCredits = semester.creditHours)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptRow(course: CourseGrade, semesterTotalCredits: Double) {
    val isDark = isSystemInDarkTheme()
    val gradeColor = when (course.grade.uppercase().trim()) {
        "A+", "A", "A-" -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        "B+", "B", "B-" -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0)
        "C+", "C", "C-" -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        "D+", "D" -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
        "F" -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contribution = if (semesterTotalCredits > 0.0) {
        (course.creditHours * course.gradePoints) / semesterTotalCredits
    } else 0.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = course.courseCode,
            modifier = Modifier.weight(0.15f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = course.courseName,
            modifier = Modifier.weight(0.32f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
        
        Text(
            text = String.format("%.0f", course.creditHours),
            modifier = Modifier.weight(0.08f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = course.marks ?: "-",
            modifier = Modifier.weight(0.11f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        
        Box(
            modifier = Modifier.weight(0.08f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(gradeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = course.grade,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = gradeColor
                )
            }
        }
        
        Text(
            text = String.format("%.2f", course.gradePoints),
            modifier = Modifier.weight(0.11f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = String.format("%.2f", contribution),
            modifier = Modifier.weight(0.15f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
}
