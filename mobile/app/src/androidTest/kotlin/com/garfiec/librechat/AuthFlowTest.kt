package com.garfiec.librechat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.auth.screen.ServerUrlScreen
import org.junit.Rule
import org.junit.Test

class AuthFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setUpServerUrlScreen() {
        composeTestRule.setContent {
            LibreChatTheme {
                ServerUrlScreen(
                    onServerValidate = {},
                )
            }
        }
    }

    @Test
    fun serverUrlScreen_displaysCorrectly() {
        setUpServerUrlScreen()
        composeTestRule.onNodeWithText("Connect to LibreChat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun serverUrlScreen_connectButtonDisabledWhenEmpty() {
        setUpServerUrlScreen()
        composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun serverUrlScreen_enablesConnectOnInput() {
        setUpServerUrlScreen()
        composeTestRule.onNodeWithText("Server URL").performTextInput("https://example.com")
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }
}
