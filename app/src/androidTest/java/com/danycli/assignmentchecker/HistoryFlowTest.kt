package com.danycli.assignmentchecker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danycli.assignmentchecker.ui.HistoricalAssignmentsScreen
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockHistory = listOf(
        Assignment(
            courseTitle = "Data Structures",
            assignmentTitle = "Binary Search Trees",
            deadline = "Oct 20 ,2023 23:59",
            downloadLink = "link1",
            submitLink = "",
            status = AssignmentStatus.GRADED,
            submittedDate = "Oct 19 ,2023 10:00",
            grade = "A"
        ),
        Assignment(
            courseTitle = "Database Systems",
            assignmentTitle = "SQL Queries",
            deadline = "Nov 15 ,2023 23:59",
            downloadLink = "link2",
            submitLink = "",
            status = AssignmentStatus.SUBMITTED,
            submittedDate = "Nov 14 ,2023 15:00"
        )
    )

    @Test
    fun historyScreen_rendersCorrectly() {
        startHistoryScreen()

        composeRule.onNodeWithText("Data Structures").assertIsDisplayed()
        composeRule.onNodeWithText("Binary Search Trees").assertIsDisplayed()
        composeRule.onNodeWithText("Database Systems").assertIsDisplayed()
        composeRule.onNodeWithText("SQL Queries").assertIsDisplayed()
    }

    @Test
    fun historySearch_filtersCorrectly() {
        startHistoryScreen()

        composeRule.onNodeWithText("Search by subject or assignment").performTextInput("SQL")

        composeRule.onNodeWithText("Database Systems").assertIsDisplayed()
        composeRule.onNodeWithText("Data Structures").assertDoesNotExist()
    }

    @Test
    fun historyLongPress_showsActions() {
        startHistoryScreen()

        // Long press a history item
        composeRule.onNodeWithText("Binary Search Trees").performTouchInput { longClick() }

        // Verify actions exist
        composeRule.onNodeWithText("Copy title").assertIsDisplayed()
        composeRule.onNodeWithText("Open course portal").assertIsDisplayed()
        composeRule.onNodeWithText("Download instructions").assertIsDisplayed()
        // Note: Change submission only appears if isOpenVal is true. 
        // In mock, deadline is Oct 20, 2023, so it's closed.
        // Let's adjust mock or just expect what's there.
    }

    private fun startHistoryScreen() {
        composeRule.setContent {
            AssignmentCheckerTheme {
                HistoricalAssignmentsScreen(
                    assignments = mockHistory,
                    loggedInStudentName = "John Doe",
                    onOpenDisclaimer = {},
                    onNavigateBack = {}
                )
            }
        }
    }
}
