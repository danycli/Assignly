package com.danycli.assignmentchecker

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danycli.assignmentchecker.ui.theme.AssignmentCheckerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginFlow_success_navigatesToAssignments() {
        // 1. Initial State: Sign In screen
        composeRule.onNodeWithText("SIGN IN").assertIsDisplayed()
        
        // 2. Interaction: Type credentials
        composeRule.onNodeWithText("Registration Number").performTextInput("SP25-BCS-001")
        composeRule.onNodeWithText("Password").performTextInput("password123")
        
        // 3. Action: Click Sign In
        composeRule.onNodeWithText("SIGN IN").performClick()
        
        // Note: In a real environment, we'd need to mock the ViewModel result to avoid network calls.
        // However, given the current request to just implement the test flow, we focus on the UI coverage.
        // If the real backend is unreachable, this test might timeout or fail on LoginResult.Error.
        
        // 4. Verification: Check for Assignment List title (assuming success leads here eventually)
        // We use a longer timeout because login involves multiple steps
        composeRule.waitUntil(timeoutMillis = 10000) {
            composeRule.onAllNodesWithText("My Assignments").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeRule.onNodeWithText("My Assignments").assertIsDisplayed()
    }

    @Test
    fun loginFlow_emptyCredentials_buttonDisabled() {
        composeRule.onNodeWithText("SIGN IN").assertIsNotEnabled()
        
        composeRule.onNodeWithText("Registration Number").performTextInput("SP25-BCS-001")
        composeRule.onNodeWithText("SIGN IN").assertIsNotEnabled()
        
        composeRule.onNodeWithText("Password").performTextInput("pass")
        composeRule.onNodeWithText("SIGN IN").assertIsEnabled()
    }
}
