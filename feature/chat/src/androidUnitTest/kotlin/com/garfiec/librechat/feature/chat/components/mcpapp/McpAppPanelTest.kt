package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h480dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class McpAppPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longBody = (1..40).joinToString("\n") { "  \"line$it\": \"value $it\"," }

    @Test
    fun toolApprovalScrollsToItsArgumentsAndOptOutAtLargeFont() {
        val action = McpAppAction.ToolCall(1, "trip-budget", "adjust_budget", "{ \"ARGS-TOP\": 1,\n$longBody\n}")
        composeRule.setContent {
            LibreChatTheme { McpAppActionDialog(action, onApprove = {}, onCancel = {}, onStop = {}) }
        }

        val optOut = composeRule.onNodeWithText("Don't ask again for adjust_budget", substring = true)
        optOut.assertIsNotDisplayed()
        optOut.performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("ARGS-TOP", substring = true).assertIsNotDisplayed()
        composeRule.onNode(hasScrollAction() and hasAnyChild(hasText("ARGS-TOP", substring = true))).performScrollTo()
        composeRule.onNodeWithText("ARGS-TOP", substring = true).assertIsDisplayed()
    }

    @Test
    fun legacyMessageScrollsToItsTextAtLargeFont() {
        composeRule.setContent {
            LibreChatTheme { LegacyUiMessageDialog(text = "MSG-TOP\n$longBody", onAnswer = {}) }
        }

        composeRule.onNodeWithText("MSG-TOP", substring = true).assertIsNotDisplayed()
        composeRule.onNode(hasScrollAction() and hasAnyChild(hasText("MSG-TOP", substring = true))).performScrollTo()
        composeRule.onNodeWithText("MSG-TOP", substring = true).assertIsDisplayed()
    }
}
