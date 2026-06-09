package com.danycli.assignmentchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.TimetableLecture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableDebugScreen(
    lectures: List<TimetableLecture>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timetable Raw Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Parsed Classes: ${lectures.size}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (lectures.isEmpty()) {
                item {
                    Text("No classes parsed. Check Logcat for details.", color = MaterialTheme.colorScheme.error)
                }
            } else {
                items(lectures) { lecture ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Course: ${lecture.courseName}", fontWeight = FontWeight.Bold)
                            Text("Code: ${lecture.courseCode}")
                            Text("Day: ${lecture.day}")
                            Text("Time: ${lecture.startTime} - ${lecture.endTime}")
                            Text("Room: ${lecture.room}")
                            Text("Instructor: ${lecture.instructor}")

                            Text("Duration: ${lecture.duration}")
                            Text("Cr.Hrs: ${lecture.creditHours}")
                        }
                    }
                }
            }
        }
    }
}
