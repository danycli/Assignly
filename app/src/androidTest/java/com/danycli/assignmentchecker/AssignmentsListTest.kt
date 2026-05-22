package com.danycli.assignmentchecker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danycli.assignmentchecker.ui.AssignmentsList
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssignmentsListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockAssignments = listOf(
        Assignment(
            courseTitle = "Kotlin Programming",
            assignmentTitle = "Basics of Coroutines",
            deadline = "Dec 31 ,2025 23:59",
            downloadLink = "link1",
            submitLink = "submit1",
            status = AssignmentStatus.PENDING
        ),
        Assignment(
            courseTitle = "Android Development",
            assignmentTitle = "Compose UI Layouts",
            deadline = "Jan 01 ,2020 10:00", // Closed
            downloadLink = "link2",
            submitLink = "submit2",
            status = AssignmentStatus.PENDING
        )
    )

    @Test
    fun assignmentsList_rendersCorrectly() {
        startAssignmentsList()

        composeRule.onNodeWithText("Kotlin Programming").assertIsDisplayed()
        composeRule.onNodeWithText("Basics of Coroutines").assertIsDisplayed()
        composeRule.onNodeWithText("Android Development").assertIsDisplayed()
        composeRule.onNodeWithText("Compose UI Layouts").assertIsDisplayed()
    }

    @Test
    fun searchFilter_worksCorrectly() {
        startAssignmentsList()

        // Initially both are visible
        composeRule.onNodeWithText("Kotlin Programming").assertExists()
        composeRule.onNodeWithText("Android Development").assertExists()

        // Type "Kotlin" in search
        composeRule.onNodeWithText("Search by subject or assignment").performTextInput("Kotlin")

        composeRule.onNodeWithText("Kotlin Programming").assertIsDisplayed()
        composeRule.onNodeWithText("Android Development").assertDoesNotExist()
    }

    @Test
    fun statusFilter_removed() {
        startAssignmentsList()
        // Status filters like "Open" and "Closed" should not exist anymore
        composeRule.onNodeWithText("Open").assertDoesNotExist()
        composeRule.onNodeWithText("Closed").assertDoesNotExist()
    }

    @Test
    fun assignmentLongPress_showsActions() {
        startAssignmentsList()

        // Long press the first assignment
        composeRule.onNodeWithText("Basics of Coroutines").performTouchInput { longClick() }

        // Verify actions exist
        composeRule.onNodeWithText("Copy title").assertIsDisplayed()
        composeRule.onNodeWithText("Open course portal").assertIsDisplayed()
        composeRule.onNodeWithText("Download instructions").assertIsDisplayed()
    }

    private fun startAssignmentsList() {
        composeRule.setContent {
            AssignmentCheckerTheme {
                AssignmentsList(
                    assignments = mockAssignments,
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
                    onUploadRequested = { _, _ -> },
                    onViewTotal = {},
                    onViewSubmitted = {},
                    onOpenSettings = {}
                )
            }
        }
    }
}
