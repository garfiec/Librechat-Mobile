package com.garfiec.librechat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.auth.screen.ServerUrlScreen
import org.junit.Rule
import org.junit.Test

class AuthFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * These tests run inside the app's own package against its real Koin graph, so
     * [ServerUrlScreen]'s ViewModel pre-fills the field with whatever server URL the installed app
     * last saved on this device. Each test therefore sets the field's contents itself. Editing the
     * field also stops a prefill that has not landed yet from overwriting it later.
     */
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
        composeTestRule.onNodeWithText("Server URL").performTextClearance()
        composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun serverUrlScreen_enablesConnectOnInput() {
        setUpServerUrlScreen()
        composeTestRule.onNodeWithText("Server URL").performTextReplacement("https://example.com")
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }
}
