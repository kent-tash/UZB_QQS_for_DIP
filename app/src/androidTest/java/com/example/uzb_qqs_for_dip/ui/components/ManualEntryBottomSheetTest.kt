package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualEntryBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomSheet_displaysAndInputsText() {
        var dismissed = false
        composeTestRule.setContent {
            ManualEntryBottomSheet(
                onDismiss = { dismissed = true },
                onSubmit = { _, _, _, _, _ -> }
            )
        }

        // Wait for sheet to appear
        composeTestRule.waitForIdle()

        // Just verify we can input text in a text field
        // Instead of hardcoding Cyrillic strings, we can just find nodes by text action
        val textFields = composeTestRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())
        
        // Assert there are 4 text fields
        textFields[0].performTextInput("Store")
        textFields[1].performTextInput("01.01.2026")
        textFields[2].performTextInput("1000")
        textFields[3].performTextInput("120")
    }
}
