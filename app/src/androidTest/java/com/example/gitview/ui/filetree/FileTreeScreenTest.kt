package com.example.gitview.ui.filetree

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FileTreeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun placeholder_compiles() {
        composeTestRule.setContent {
            androidx.compose.material3.Text("FileTree test")
        }

        composeTestRule.onNodeWithText("FileTree test").assertIsDisplayed()
    }
}
