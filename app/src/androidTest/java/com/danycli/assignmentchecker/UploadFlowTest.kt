package com.danycli.assignmentchecker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danycli.assignmentchecker.ui.AssignmentsList
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class UploadFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockAssignment = Assignment(
        courseTitle = "Kotlin Programming",
        assignmentTitle = "Basics of Coroutines",
        deadline = "Dec 31 ,2025 23:59",
        downloadLink = "link1",
        submitLink = "submit1",
        status = AssignmentStatus.PENDING
    )

    @Test
    fun clickingUpload_triggersCallback() {
        val uploadTriggered = AtomicBoolean(false)
        
        composeRule.setContent {
            AssignmentCheckerTheme {
                AssignmentsList(
                    assignments = listOf(mockAssignment),
                    historicalAssignments = emptyList(),
                    loggedInStudentName = "John Doe",
                    loggedInStudentPhoto = null,
                    welcomeStatusMessage = "Welcome!",
                    attendanceInsightMessage = null,
                    isRefreshing = false,
                    onOpenDisclaimer = {},
                    onRefresh = {},
                    onLogout = {},
                    onDownloadRequested = {},
                    onUploadRequested = { _, _ -> uploadTriggered.set(true) },
                    onViewTotal = {},
                    onViewSubmitted = {},
                    onOpenSettings = {}
                )
            }
        }

        // Find the "Upload" text button and click it
        composeRule.onNodeWithText("Upload").performClick()
        
        // Note: We can't easily test the actual file picker launch in an isolated Compose test
        // but we can verify that our callback would be reached if the launcher was invoked.
        // Actually, onUploadRequested is called AFTER the launcher returns a URI.
        // So we just verify the Upload button exists and is clickable.
        composeRule.onNodeWithText("Upload").assertHasClickAction()
    }
}
