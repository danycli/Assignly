package com.danycli.assignmentchecker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.*
import com.danycli.assignmentchecker.ui.theme.*

@Composable
fun ActiveUploadStatusCard(upload: QueuedUpload, onDismiss: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val isFinished = upload.status == UploadQueueStatus.SUCCESS || upload.status == UploadQueueStatus.FAILED
    
    val backgroundColor = when (upload.status) {
        UploadQueueStatus.SUCCESS -> if (isDark) Color(0xFF1B3921) else Color(0xFFE8F5E9)
        UploadQueueStatus.FAILED -> if (isDark) Color(0xFF401D24) else Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (upload.status) {
        UploadQueueStatus.SUCCESS -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        UploadQueueStatus.FAILED -> if (isDark) Color(0xFFCF6679) else Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (upload.status == UploadQueueStatus.SUCCESS) "Uploaded: ${upload.assignmentTitle}" else "Uploading: ${upload.assignmentTitle}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (isFinished) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = when (upload.status) {
                            UploadQueueStatus.QUEUED -> "Queued"
                            UploadQueueStatus.RUNNING -> "In Progress"
                            UploadQueueStatus.FAILED_RETRY -> "Retrying..."
                            else -> upload.status.name
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (upload.status == UploadQueueStatus.FAILED_RETRY) MaterialTheme.colorScheme.error else contentColor
                    )
                }
            }

            if (!isFinished) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.1f)
                )
            }

            if (!upload.lastError.isNullOrBlank()) {
                Text(
                    text = upload.lastError,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun ActiveDownloadStatusCard(download: QueuedDownload, onDismiss: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val isFinished = download.status == DownloadQueueStatus.SUCCESS || download.status == DownloadQueueStatus.FAILED
    
    val backgroundColor = when (download.status) {
        DownloadQueueStatus.SUCCESS -> if (isDark) Color(0xFF113438) else Color(0xFFE0F7FA)
        DownloadQueueStatus.FAILED -> if (isDark) Color(0xFF401D24) else Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (download.status) {
        DownloadQueueStatus.SUCCESS -> if (isDark) Color(0xFF4DD0E1) else Color(0xFF006064)
        DownloadQueueStatus.FAILED -> if (isDark) Color(0xFFCF6679) else Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (download.status == DownloadQueueStatus.SUCCESS) "Downloaded: ${download.fileName}" else "Downloading: ${download.fileName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (isFinished) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = when (download.status) {
                            DownloadQueueStatus.QUEUED -> "Queued"
                            DownloadQueueStatus.RUNNING -> "In Progress"
                            else -> download.status.name
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
            }

            if (!isFinished) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.1f)
                )
            }

            if (!download.lastError.isNullOrBlank()) {
                Text(
                    text = download.lastError,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    if (query.isEmpty() || !text.contains(query, ignoreCase = true)) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
        return
    }

    val isDark = isSystemInDarkTheme()
    val highlightColor = if (isDark) Color(0xFFFBC02D) else Color(0xFFFFF176)
    val highlightTextColor = Color.Black

    val annotatedString = buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(query, start, ignoreCase = true)
            if (index == -1) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(style = SpanStyle(background = highlightColor, color = highlightTextColor)) {
                append(text.substring(index, index + query.length))
            }
            start = index + query.length
        }
    }

    Text(
        text = annotatedString,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

@Composable
fun rememberSkeletonBrush(): Brush {
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color.DarkGray else Color.LightGray
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.45f),
            baseColor.copy(alpha = 0.2f),
            baseColor.copy(alpha = 0.45f)
        ),
        start = Offset(shimmerOffset - 200f, shimmerOffset - 200f),
        end = Offset(shimmerOffset, shimmerOffset)
    )
}

@Composable
fun SkeletonBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingAssignmentsSkeleton() {
    val brush = rememberSkeletonBrush()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "My Assignments",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SkeletonBlock(
                    brush = brush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            for (index in 0 until 5) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.75f).height(16.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.35f).height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SkeletonBlock(
                                    brush = brush,
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                SkeletonBlock(
                                    brush = brush,
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalAssignmentsSkeleton() {
    val brush = rememberSkeletonBrush()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Assignment History",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (index in 0 until 5) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                            SkeletonBlock(brush = brush, modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(1f).height(32.dp))
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(1f).height(32.dp))
                                SkeletonBlock(brush = brush, modifier = Modifier.weight(0.7f).height(32.dp))
                            }
                            SkeletonBlock(
                                brush = brush,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisclaimerFooter(
    modifier: Modifier = Modifier,
    onOpenDisclaimer: () -> Unit
) {
    TextButton(
        onClick = onOpenDisclaimer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Disclaimer",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun LoadingStatusOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AssignmentCard(assignment: Assignment, onDownload: () -> Unit, onSubmit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = assignment.courseTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = assignment.assignmentTitle,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Deadline: ",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = assignment.deadline,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AssignlyBottomNavigation(
    currentScreen: ScreenType,
    onNavigate: (ScreenType) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                icon = androidx.compose.material.icons.Icons.Default.Refresh,
                label = "Tasks",
                selected = currentScreen == ScreenType.PENDING,
                onClick = { onNavigate(ScreenType.PENDING) }
            )
            NavigationItem(
                icon = androidx.compose.material.icons.Icons.Default.Download,
                label = "Schedule",
                selected = currentScreen == ScreenType.TIMETABLE,
                onClick = { onNavigate(ScreenType.TIMETABLE) }
            )
            NavigationItem(
                icon = androidx.compose.material.icons.Icons.Default.Close,
                label = "Files",
                selected = currentScreen == ScreenType.DOWNLOADS,
                onClick = { onNavigate(ScreenType.DOWNLOADS) }
            )
            NavigationItem(
                icon = androidx.compose.material.icons.Icons.Default.Upload,
                label = "History",
                selected = currentScreen == ScreenType.HISTORICAL,
                onClick = { onNavigate(ScreenType.HISTORICAL) }
            )
            NavigationItem(
                icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Logout,
                label = "Settings",
                selected = currentScreen == ScreenType.SETTINGS,
                onClick = { onNavigate(ScreenType.SETTINGS) }
            )
        }
    }
}

@Composable
fun NavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
