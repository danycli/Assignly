package com.danycli.assignmentchecker.ui

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.DownloadQueueStatus
import com.danycli.assignmentchecker.PhysicalDownloadedFile
import com.danycli.assignmentchecker.renameLocalFile
import com.danycli.assignmentchecker.QueuedDownload
import com.danycli.assignmentchecker.deleteLocalFile
import com.danycli.assignmentchecker.getLocalDownloadedFiles
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    activeDownloads: List<QueuedDownload>,
    onRemoveDownload: (QueuedDownload) -> Unit,
    onClearFinished: () -> Unit,
    onRetryDownload: (QueuedDownload) -> Unit,
    onOpenDownload: (QueuedDownload) -> Unit,
    onOpenPhysicalFile: (PhysicalDownloadedFile) -> Unit,
    onSolveCaptcha: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var physicalFiles by remember { mutableStateOf<List<PhysicalDownloadedFile>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    val refreshFiles = {
        isRefreshing = true
        physicalFiles = getLocalDownloadedFiles(context)
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    val successfulCount = activeDownloads.count { it.status == DownloadQueueStatus.SUCCESS }
    LaunchedEffect(successfulCount) {
        refreshFiles()
    }

    val activeQueue = activeDownloads.filter {
        it.status == DownloadQueueStatus.RUNNING ||
        it.status == DownloadQueueStatus.QUEUED ||
        it.status == DownloadQueueStatus.FAILED
    }.sortedByDescending { it.createdAtEpochMs }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Assignly Downloads", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    val hasFinished = activeDownloads.any { it.status == DownloadQueueStatus.SUCCESS || it.status == DownloadQueueStatus.FAILED }
                    if (hasFinished) {
                        IconButton(onClick = { onClearFinished() }) {
                            Icon(
                                imageVector = Icons.Outlined.ClearAll,
                                contentDescription = "Clear Queue History",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (activeQueue.isEmpty() && physicalFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "No Downloads Found",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Files you download will appear here permanently, directly from the Assignly Downloads folder on your device.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeQueue.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Downloading (${activeQueue.size})", icon = Icons.Outlined.CloudDownload)
                        }
                        items(activeQueue) { download ->
                            ActiveDownloadStatusCard(
                                download = download,
                                onDismiss = { onRemoveDownload(download) },
                                onSolveCaptcha = onSolveCaptcha
                            )
                        }
                    }

                    if (activeQueue.isNotEmpty() && physicalFiles.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        }
                    }

                    if (physicalFiles.isNotEmpty()) {
                        item {
                            SectionHeader(title = "On Device (${physicalFiles.size})", icon = Icons.Outlined.LibraryBooks)
                        }
                        itemsIndexed(physicalFiles, key = { _, file -> file.uri }) { _, file ->
                            PhysicalDownloadedFileCard(
                                    file = file,
                                    onClick = { onOpenPhysicalFile(file) },
                                    onDelete = {
                                        deleteLocalFile(context, file.uri)
                                        refreshFiles()
                                    },
                                    onRename = { newName ->
                                        renameLocalFile(context, file.uri, newName)
                                        refreshFiles()
                                    },
                                    onShare = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(file.uri))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share File"))
                                    }
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PhysicalDownloadedFileCard(
    file: PhysicalDownloadedFile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onShare: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        val extension = file.fileName.substringAfterLast('.', "")
        val baseName = if (extension.isNotEmpty() && file.fileName.contains(".")) file.fileName.substringBeforeLast('.') else file.fileName
        var newName by remember { mutableStateOf(baseName) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val finalName = if (extension.isNotEmpty() && !newName.endsWith(".$extension")) "$newName.$extension" else newName
                        onRename(finalName)
                    }
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 6.dp else 2.dp, label = "card_elevation")
    
    val isDark = isSystemInDarkTheme()
    
    val isPdf = file.fileName.lowercase().endsWith(".pdf")
    val isPpt = file.fileName.lowercase().endsWith(".ppt") || file.fileName.lowercase().endsWith(".pptx")
    val isWord = file.fileName.lowercase().endsWith(".doc") || file.fileName.lowercase().endsWith(".docx")
    
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

    val formatSize = remember(file.sizeBytes) {
        val kb = file.sizeBytes / 1024.0
        if (kb > 1024) String.format("%.1f MB", kb / 1024.0) else String.format("%.1f KB", kb)
    }
    
    val relativeTime = remember(file.lastModifiedMs) {
        DateUtils.getRelativeTimeSpanString(
            file.lastModifiedMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, shape = RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
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
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formatSize,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(
                        text = relativeTime,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Row {
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Rename File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete File",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    download: QueuedDownload,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isFailed = download.status == DownloadQueueStatus.FAILED

    val backgroundColor = if (isFailed) {
        if (isDark) Color(0xFF401D24) else Color(0xFFFFEBEE)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isFailed) {
        if (isDark) Color(0xFFCF6679) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(0.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFailed) contentColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFailed) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", tint = contentColor)
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            if (download.status == DownloadQueueStatus.RUNNING) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Text(text = "Downloading...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            } else if (download.status == DownloadQueueStatus.QUEUED) {
                Text(text = "Waiting in queue...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (isFailed) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                        Text(text = "Download Failed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
                    }
                    if (!download.lastError.isNullOrBlank()) {
                        Text(text = download.lastError, fontSize = 11.sp, color = contentColor.copy(alpha = 0.8f), lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}
