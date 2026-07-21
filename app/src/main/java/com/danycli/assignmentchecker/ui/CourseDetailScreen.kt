package com.danycli.assignmentchecker.ui

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.Assignment
import com.danycli.assignmentchecker.AssignmentStatus
import com.danycli.assignmentchecker.CourseFile
import com.danycli.assignmentchecker.EnrolledCourse
import com.danycli.assignmentchecker.MainViewModel
import com.danycli.assignmentchecker.CourseFilesCacheStore
import com.danycli.assignmentchecker.isDeviceOnline
import com.danycli.assignmentchecker.SecurityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: EnrolledCourse,
    allAssignments: List<Assignment>,
    viewModel: MainViewModel,
    onDownloadFile: (CourseFile) -> Unit,
    onDownloadAllFiles: (List<CourseFile>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = LocalShowMessage.current
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Overview", "Course Contents")
    val tabIcons = listOf(Icons.Outlined.Info, Icons.Outlined.LibraryBooks)
    
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabTitles.size }
    )
    
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }
    
    var courseFiles by remember { mutableStateOf<List<CourseFile>>(emptyList()) }
    var isFilesRefreshing by remember { mutableStateOf(false) }
    var filesQuery by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(false) }

    val courseAssignments = remember(allAssignments, course) {
        allAssignments.filter {
            it.courseTitle.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                .contains(course.courseTitle.lowercase().replace("\\s+|-|•|–".toRegex(), "")) ||
            course.courseTitle.lowercase().replace("\\s+|-|•|–".toRegex(), "")
                .contains(it.courseTitle.lowercase().replace("\\s+|-|•|–".toRegex(), ""))
        }
    }
    
    val totalAssignmentsCount = courseAssignments.size
    val pendingAssignmentsCount = courseAssignments.count { it.status == AssignmentStatus.PENDING }
    val completedAssignmentsCount = courseAssignments.count { it.status == AssignmentStatus.SUBMITTED || it.status == AssignmentStatus.GRADED }

    LaunchedEffect(course) {
        val cached = CourseFilesCacheStore.loadSnapshot(context, course.courseCode)
        courseFiles = cached
        isOffline = !isDeviceOnline(context)
    }

    DisposableEffect(course) {
        val cleanKey = course.courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
        val prefs = SecurityUtils.getSecurePrefs(context, "secure_course_files_cache")
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == cleanKey) {
                val updated = CourseFilesCacheStore.loadSnapshot(context, course.courseCode)
                courseFiles = updated
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(course.courseCode, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PremiumCourseHeroCard(course = course)
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        PremiumStatisticsOverview(
                            materialsCount = courseFiles.size,
                            totalAssignments = totalAssignmentsCount,
                            pendingAssignments = pendingAssignmentsCount,
                            completedWork = completedAssignmentsCount
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        PremiumSegmentedControl(
                            items = tabTitles,
                            icons = tabIcons,
                            selectedIndex = selectedTab,
                            onItemSelection = { index ->
                                selectedTab = index
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> OverviewTabPremiumContent(course = course)
                    1 -> CourseContentsPremiumContent(
                        files = courseFiles,
                        isRefreshing = isFilesRefreshing,
                        searchQuery = filesQuery,
                        isOffline = isOffline,
                        onSearchQueryChange = { filesQuery = it },
                        onDownload = onDownloadFile,
                        onDownloadAll = { onDownloadAllFiles(courseFiles) },
                        onRefresh = {
                            isFilesRefreshing = true
                            scope.launch {
                                try {
                                    val fetchedFiles = viewModel.loadCourseFiles(course.courseCode, course.courseTitle)
                                    courseFiles = fetchedFiles
                                    isOffline = false
                                    CourseFilesCacheStore.saveSnapshot(context, course.courseCode, fetchedFiles)
                                } catch (e: Exception) {
                                    Log.e("CourseDetailScreen", "Failed to refresh files online", e)
                                    isOffline = true
                                    val cached = CourseFilesCacheStore.loadSnapshot(context, course.courseCode)
                                    courseFiles = cached
                                    val msg = if (cached.isNotEmpty()) {
                                        "Offline: Showing cached course contents."
                                    } else {
                                        e.message ?: "Failed to refresh files"
                                    }
                                    showMessage(msg)
                                } finally {
                                    isFilesRefreshing = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumCourseHeroCard(course: EnrolledCourse) {
    val isDark = isSystemInDarkTheme()
    val gradientColors = if (isDark) {
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
    } else {
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface
        )
    }

    val courseIcon = getCourseIcon(course.courseTitle)

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
                .background(Brush.horizontalGradient(gradientColors))
        ) {
            // Faded watermark subject icon
            Icon(
                imageVector = courseIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-12).dp)
                    .size(150.dp)
                    .rotate(-10f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = course.courseTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = course.courseCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (course.instructorName.isNotBlank() && course.instructorName != "N/A") {
                        HeroGlassChip(icon = Icons.Outlined.Person, text = course.instructorName)
                    }
                    if (course.section.isNotBlank()) {
                        HeroGlassChip(icon = Icons.Outlined.School, text = course.section)
                    }
                }
            }
        }
    }
}

@Composable
fun HeroGlassChip(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PremiumStatisticsOverview(
    materialsCount: Int,
    totalAssignments: Int,
    pendingAssignments: Int,
    completedWork: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    // Colors
    val isDark = isSystemInDarkTheme()
    val pendingColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00) // Warm Orange
    val completedColor = if (isDark) Color(0xFF81C784) else Color(0xFF388E3C) // Soft Green
    val materialsColor = if (isDark) Color(0xFF4DB6AC) else Color(0xFF00796B) // Teal
    val tasksColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2) // Blue

    val stats = listOf(
        PremiumStatItem("Materials", materialsCount.toString(), Icons.Outlined.Folder, materialsColor),
        PremiumStatItem("Tasks", totalAssignments.toString(), Icons.Outlined.Assignment, tasksColor),
        PremiumStatItem("Pending", pendingAssignments.toString(), Icons.Outlined.PendingActions, pendingColor),
        PremiumStatItem("Completed", completedWork.toString(), Icons.Outlined.TaskAlt, completedColor)
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stats.forEachIndexed { index, stat ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(300, delayMillis = index * 100)) + slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = tween(300, delayMillis = index * 100, easing = FastOutSlowInEasing)
                )
            ) {
                PremiumStatCard(stat)
            }
        }
    }
}

private data class PremiumStatItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun PremiumStatCard(stat: PremiumStatItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 6.dp else 2.dp, label = "stat_elevation")
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "stat_scale")

    Card(
        modifier = Modifier
            .width(105.dp)
            .shadow(elevation, shape = RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = {}),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            stat.color.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(stat.color.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = stat.color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stat.value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stat.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PremiumSegmentedControl(
    items: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onItemSelection: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onItemSelection(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icons[index],
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun OverviewTabPremiumContent(course: EnrolledCourse) {
    val details = listOf(
        Triple(Icons.Outlined.Book, "Course Title", course.courseTitle),
        Triple(Icons.Outlined.Tag, "Course Code", course.courseCode),
        Triple(Icons.Outlined.Person, "Faculty Instructor", course.instructorName.ifBlank { "N/A" }),
        Triple(Icons.Outlined.School, "Class Section", course.section.ifBlank { "N/A" }),
        Triple(Icons.Outlined.Schedule, "Credit Hours", course.creditHours.ifBlank { "N/A" })
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(details) { _, detail ->
            PremiumInfoRowCard(icon = detail.first, title = detail.second, value = detail.third)
        }
    }
}

@Composable
fun PremiumInfoRowCard(icon: ImageVector, title: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseContentsPremiumContent(
    files: List<CourseFile>,
    isRefreshing: Boolean,
    searchQuery: String,
    isOffline: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onDownload: (CourseFile) -> Unit,
    onDownloadAll: () -> Unit,
    onRefresh: () -> Unit
) {
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) {
            files
        } else {
            val q = searchQuery.trim().lowercase()
            files.filter {
                it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
            }
        }
    }
    
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
            // Premium Search field
            item {
                CourseDetailPremiumSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange
                )
            }

            // Premium Download All Button
            if (filteredFiles.isNotEmpty()) {
                item {
                    PremiumDownloadAllButton(isOffline = isOffline, onClick = onDownloadAll)
                }
            }
            
            if (filteredFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching files found." else "No course materials uploaded yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                itemsIndexed(filteredFiles, key = { _, file -> file.downloadLink }) { _, file ->
                    PremiumCourseFileCard(file = file, isOffline = isOffline, onDownload = { onDownload(file) })
                }
            }
        }
    }
}

@Composable
fun PremiumCourseFileCard(file: CourseFile, isOffline: Boolean, onDownload: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 6.dp else 2.dp, label = "file_card_elevation")

    // File type icon
    val isPdf = file.title.lowercase().contains(".pdf") || file.description.lowercase().contains("pdf")
    val isPpt = file.title.lowercase().contains(".ppt") || file.title.lowercase().contains(".pptx")
    val isWord = file.title.lowercase().contains(".doc") || file.title.lowercase().contains(".docx")
    
    val fileIcon = when {
        isPdf -> Icons.Outlined.PictureAsPdf
        isPpt -> Icons.Outlined.Slideshow
        isWord -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }

    val iconColor = when {
        isPdf -> Color(0xFFE53935)
        isPpt -> Color(0xFFFB8C00)
        isWord -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, shape = RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = { if(!isOffline) onDownload() }),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (file.uploadDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = file.uploadDate,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Download Action
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isOffline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Outlined.Lock else Icons.Outlined.Download,
                    contentDescription = if (isOffline) "Downloads locked" else "Download file",
                    tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PremiumDownloadAllButton(isOffline: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 6.dp else 2.dp, label = "btn_elevation")
    
    val bgGradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (isOffline) 0.dp else elevation, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = !isOffline,
                onClick = onClick
            ),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = if (isOffline) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOffline) SolidColor(MaterialTheme.colorScheme.surfaceVariant) else bgGradient),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Outlined.Lock else Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (isOffline) "Download All (Locked Offline)" else "Download All Files",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun CourseDetailPremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val shadowSize by animateDpAsState(if (isFocused) 6.dp else 2.dp, label = "search_shadow")
    val containerColor = if (isFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(shadowSize, CircleShape)
            .background(containerColor, CircleShape)
            .height(56.dp),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search lecture notes or files...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}
