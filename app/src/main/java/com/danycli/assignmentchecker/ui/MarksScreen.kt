package com.danycli.assignmentchecker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.CourseMarks
import com.danycli.assignmentchecker.GpaSummary
import com.danycli.assignmentchecker.MarkItem
import com.danycli.assignmentchecker.MarksCategory
import com.danycli.assignmentchecker.isNetworkError
import kotlinx.coroutines.launch

private fun getShortCourseName(fullName: String): String {
    var name = fullName.trim()
    
    val prefixes = listOf("introduction to ", "intro to ", "fundamentals of ", "principles of ")
    for (prefix in prefixes) {
        if (name.lowercase().startsWith(prefix)) {
            name = name.substring(prefix.length).trim()
        }
    }
    
    val lower = name.lowercase()
    when {
        lower.contains("object oriented programming") -> return "OOP"
        lower.contains("data structures") -> return "Data Structures"
        lower.contains("database systems") || lower.contains("database management") -> return "Database Systems"
        lower.contains("digital logic") -> return "Digital Logic"
        lower.contains("calculus") -> return "Calculus"
        lower.contains("software engineering") -> return "Software Eng."
        lower.contains("computer architecture") -> return "Computer Arch."
        lower.contains("theory of automata") -> return "Automata Theory"
        lower.contains("probability and statistics") -> return "Prob & Stats"
        lower.contains("artificial intelligence") -> return "AI"
        lower.contains("machine learning") -> return "ML"
        lower.contains("computer networks") -> return "Networks"
        lower.contains("operating systems") -> return "Operating Systems"
        lower.contains("assembly language") -> return "Assembly"
        lower.contains("discrete structures") || lower.contains("discrete mathematics") -> return "Discrete Math"
        lower.contains("differential equations") -> return "Diff Equations"
        lower.contains("linear algebra") -> return "Linear Algebra"
        lower.contains("human computer interaction") -> return "HCI"
        lower.contains("web engineering") || lower.contains("web development") -> return "Web Eng."
        lower.contains("information security") || lower.contains("cyber security") -> return "Info Security"
    }

    name = name.replace("(?i)\\band\\b".toRegex(), "&")
    
    if (name.length > 20) {
        if (name.contains("&")) {
            val parts = name.split("&")
            if (parts[0].trim().length >= 8) {
                name = parts[0].trim()
            }
        }
        val words = name.split(" ")
        if (words.size > 3) {
            name = words.take(3).joinToString(" ")
        }
        if (name.length > 20) {
            name = name.take(18) + ".."
        }
    }
    
    return name.trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksScreen(
    courseList: List<String>,
    courseNames: Map<String, String>,
    courseMarksMap: Map<String, List<MarksCategory>>,
    isRefreshing: Boolean,
    onRefreshCourse: suspend (String) -> List<MarksCategory>,
    onBack: () -> Unit,
    gpaSummary: GpaSummary = GpaSummary(0.0, 0.0, emptyList())
) {
    var selectedCourseCode by remember { mutableStateOf(courseList.firstOrNull() ?: "") }
    var marksList by remember { mutableStateOf<List<MarksCategory>>(emptyList()) }
    var isLoadingMarks by remember { mutableStateOf(false) }
    var marksError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val showMessage = LocalShowMessage.current

    val semesterName = remember(selectedCourseCode, gpaSummary) {
        gpaSummary.semesters.find { semester ->
            semester.courses.any { it.courseCode.trim().uppercase() == selectedCourseCode.trim().uppercase() }
        }?.semesterName ?: gpaSummary.semesters.lastOrNull()?.semesterName ?: "Current Semester"
    }

    LaunchedEffect(selectedCourseCode, courseMarksMap) {
        if (selectedCourseCode.isNotEmpty()) {
            val cleanTarget = selectedCourseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
            val cached = courseMarksMap.entries.find { entry ->
                entry.key.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "") == cleanTarget
            }?.value
            if (cached != null) {
                marksList = cached
                marksError = null
            } else {
                isLoadingMarks = true
                marksError = null
                scope.launch {
                    try {
                        val fetched = onRefreshCourse(selectedCourseCode)
                        marksList = fetched
                    } catch (e: Exception) {
                        if (marksList.isEmpty()) {
                            marksError = if (e.message?.contains("Network unavailable", ignoreCase = true) == true) {
                                e.message!!
                            } else if (isNetworkError(e)) {
                                "Network unavailable. Please check your connection to load marks."
                            } else {
                                e.message ?: "Failed to load marks"
                            }
                        } else {
                            if (!isNetworkError(e)) {
                                showMessage(e.message ?: "Failed to load marks")
                            }
                        }
                    } finally {
                        isLoadingMarks = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Assessment Marks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
                isRefreshing = isRefreshing || isLoadingMarks,
                onRefresh = {
                    if (selectedCourseCode.isNotEmpty()) {
                        isLoadingMarks = true
                        scope.launch {
                            try {
                                val fetched = onRefreshCourse(selectedCourseCode)
                                marksList = fetched
                                marksError = null
                            } catch (e: Exception) {
                                if (marksList.isEmpty()) {
                                    marksError = if (e.message?.contains("Network unavailable", ignoreCase = true) == true) {
                                        e.message!!
                                    } else if (isNetworkError(e)) {
                                        "Network unavailable. Please check your connection to load marks."
                                    } else {
                                        e.message ?: "Failed to refresh marks"
                                    }
                                } else {
                                    if (!isNetworkError(e)) {
                                        showMessage(e.message ?: "Failed to refresh marks")
                                    }
                                }
                            } finally {
                                isLoadingMarks = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Subject Tabs
                    if (courseList.isNotEmpty()) {
                        item {
                            SubjectTabs(
                                courseList = courseList,
                                courseNames = courseNames,
                                selectedCourseCode = selectedCourseCode,
                                onCourseSelected = { selectedCourseCode = it }
                            )
                        }
                    }

                    // Hero Card
                    if (selectedCourseCode.isNotEmpty()) {
                        item {
                            val name = courseNames[selectedCourseCode] ?: "Course Details"
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                CourseHeroCard(
                                    courseName = name,
                                    courseCode = selectedCourseCode,
                                    semesterName = semesterName
                                )
                            }
                        }
                    }

                    // Content
                    if (marksError != null && marksList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = marksError ?: "An error occurred",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else if (marksList.isEmpty() && !isLoadingMarks) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No assessment marks recorded for this course yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = marksList,
                            key = { _, category -> "${selectedCourseCode}_${category.categoryName}" }
                        ) { _, category ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                MarksCategoryCard(
                                    category = category,
                                    isInitiallyExpanded = false
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
fun SubjectTabs(
    courseList: List<String>,
    courseNames: Map<String, String>,
    selectedCourseCode: String,
    onCourseSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(courseList) { code ->
            val isSelected = code == selectedCourseCode
            val courseName = courseNames[code] ?: code
            val displayName = getShortCourseName(courseName)
            
            val bgColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                label = "tabBg"
            )
            val contentColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabContent"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onCourseSelected(code) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun CourseHeroCard(
    courseName: String,
    courseCode: String,
    semesterName: String
) {
    val isDark = isSystemInDarkTheme()
    val gradientColors = if (isDark) {
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
    } else {
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
        ) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
                    .size(140.dp)
                    .alpha(if (isDark) 0.03f else 0.08f),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = courseName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = courseCode,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(
                        text = semesterName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MarksCategoryCard(
    category: MarksCategory,
    isInitiallyExpanded: Boolean
) {
    var expanded by remember { mutableStateOf(isInitiallyExpanded) }
    
    val categoryNameLower = category.categoryName.lowercase()
    val categoryIcon = when {
        categoryNameLower.contains("quiz") -> Icons.Outlined.AutoStories
        categoryNameLower.contains("assignment") || categoryNameLower.contains("homework") -> Icons.Outlined.Assignment
        categoryNameLower.contains("mid") || categoryNameLower.contains("sessional") -> Icons.Outlined.BarChart
        categoryNameLower.contains("final") || categoryNameLower.contains("exam") || categoryNameLower.contains("terminal") -> Icons.Outlined.EmojiEvents
        categoryNameLower.contains("lab") -> Icons.Outlined.Science
        categoryNameLower.contains("presentation") -> Icons.Outlined.PersonalVideo
        else -> Icons.Outlined.Assessment
    }

    val pct = category.averagePct
    val performanceColor = when {
        pct >= 75.0 -> MaterialTheme.colorScheme.primary
        pct >= 50.0 -> Color(0xFFD84315)
        else -> MaterialTheme.colorScheme.error
    }

    val rotationAngle by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotateIcon")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon in soft circular container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(performanceColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = category.categoryName,
                        tint = performanceColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Title and Progress Bar
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.categoryName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = if (category.totalMax > 0.0) {
                        (category.totalObtained / category.totalMax).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    AnimatedProgressBar(progress = progress, color = performanceColor, modifier = Modifier.fillMaxWidth())
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Marks, Badge, Chevron
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.1f", category.totalObtained)} / ${String.format("%.1f", category.totalMax)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(performanceColor.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format("%.0f%%", pct),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = performanceColor
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp).rotate(rotationAngle)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    category.items.forEachIndexed { idx, item ->
                        MarkItemCard(item = item)
                        if (idx < category.items.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkItemCard(item: MarkItem) {
    val pctVal = remember(item.percentage) {
        runCatching { item.percentage.replace("%", "").toDouble() }.getOrDefault(0.0)
    }
    val pillColor = when {
        pctVal >= 75.0 -> MaterialTheme.colorScheme.primary
        pctVal >= 50.0 -> Color(0xFFD84315)
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.date.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.date,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.1f", item.obtainedMarks)} / ${String.format("%.1f", item.totalMarks)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.percentage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progress else 0f,
        animationSpec = tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "progress"
    )

    Canvas(modifier = modifier.height(6.dp)) {
        val cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        val trackColor = color.copy(alpha = 0.12f)
        
        // Background track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        // Filled progress with soft gradient
        val progressWidth = size.width * animatedProgress
        if (progressWidth > 0) {
            val fillBrush = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.7f), color),
                startX = 0f,
                endX = progressWidth
            )
            drawRoundRect(
                brush = fillBrush,
                size = Size(width = progressWidth, height = size.height),
                cornerRadius = cornerRadius
            )
            
            // Glowing endpoint
            drawCircle(
                color = Color.White,
                radius = size.height * 0.4f,
                center = Offset(x = progressWidth - (size.height / 2), y = size.height / 2)
            )
        }
    }
}
