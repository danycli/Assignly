package com.danycli.assignmentchecker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSettings: AppSettings,
    onBack: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onViewUploadQueue: () -> Unit = {}
) {
    var backgroundSyncEnabled by remember { mutableStateOf(initialSettings.backgroundSyncEnabled) }
    var selectedIntervalHours by remember { mutableStateOf(initialSettings.syncIntervalHours.coerceIn(1L, 24L)) }
    var backgroundUploadEnabled by remember { mutableStateOf(initialSettings.backgroundUploadEnabled) }
    var updateNotificationsEnabled by remember { mutableStateOf(initialSettings.updateNotificationsEnabled) }
    var uploadNotificationsEnabled by remember { mutableStateOf(initialSettings.uploadNotificationsEnabled) }
    var assignmentNotificationsEnabled by remember { mutableStateOf(initialSettings.assignmentNotificationsEnabled) }
    var downloadBehavior by remember { mutableStateOf(initialSettings.downloadBehavior) }
    var themeMode by remember { mutableStateOf(initialSettings.themeMode) }

    val intervalOptions = listOf(1L, 3L, 6L, 12L, 24L)
    fun buildSettings() = AppSettings(
        backgroundSyncEnabled = backgroundSyncEnabled,
        syncIntervalHours = selectedIntervalHours,
        backgroundUploadEnabled = backgroundUploadEnabled,
        updateNotificationsEnabled = updateNotificationsEnabled,
        uploadNotificationsEnabled = uploadNotificationsEnabled,
        assignmentNotificationsEnabled = assignmentNotificationsEnabled,
        downloadBehavior = downloadBehavior,
        themeMode = themeMode
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
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
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Background sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        SettingToggleRow(
                            title = "Enable periodic assignment sync",
                            subtitle = "Keeps local cache refreshed even when app is closed.",
                            checked = backgroundSyncEnabled,
                            onCheckedChange = {
                                backgroundSyncEnabled = it
                                onSaveSettings(buildSettings())
                            }
                        )
                        if (backgroundSyncEnabled) {
                            Text("Sync interval", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                intervalOptions.forEach { option ->
                                    AssistChip(
                                        onClick = {
                                            selectedIntervalHours = option
                                            onSaveSettings(buildSettings())
                                        },
                                        label = { Text("$option h") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selectedIntervalHours == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = if (selectedIntervalHours == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Uploads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        SettingToggleRow(
                            title = "Queue uploads in background",
                            subtitle = "Uploads continue with retry on network reconnect.",
                            checked = backgroundUploadEnabled,
                            onCheckedChange = {
                                backgroundUploadEnabled = it
                                onSaveSettings(buildSettings())
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onViewUploadQueue) {
                                Text("View upload queue", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        SettingToggleRow(
                            title = "Background update notifications",
                            subtitle = "Notify when a new app version is available.",
                            checked = updateNotificationsEnabled,
                            onCheckedChange = {
                                updateNotificationsEnabled = it
                                onSaveSettings(buildSettings())
                            }
                        )
                        SettingToggleRow(
                            title = "Upload status notifications",
                            subtitle = "Show progress and results for background uploads.",
                            checked = uploadNotificationsEnabled,
                            onCheckedChange = {
                                uploadNotificationsEnabled = it
                                onSaveSettings(buildSettings())
                            }
                        )
                        SettingToggleRow(
                            title = "Assignment notifications",
                            subtitle = "New assignments and deadline reminders.",
                            checked = assignmentNotificationsEnabled,
                            onCheckedChange = {
                                assignmentNotificationsEnabled = it
                                onSaveSettings(buildSettings())
                            }
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { option ->
                                val label = when (option) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                                AssistChip(
                                    onClick = {
                                        themeMode = option
                                        onSaveSettings(buildSettings())
                                    },
                                    label = { Text(label) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (themeMode == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = if (themeMode == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        Text(
                            "Theme affects the overall app colors and system bars.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DownloadBehavior.values().forEach { option ->
                                val label = when (option) {
                                    DownloadBehavior.ASK_EVERY_TIME -> "Ask every time"
                                    DownloadBehavior.AUTO_DOWNLOADS -> "Auto-save"
                                }
                                AssistChip(
                                    onClick = {
                                        downloadBehavior = option
                                        onSaveSettings(buildSettings())
                                    },
                                    label = { Text(label) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (downloadBehavior == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = if (downloadBehavior == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        Text(
                            "Auto-save stores files in your Downloads folder.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Performance mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "This build enables lighter list rows and deferred background work to reduce UI stutter while scrolling.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
