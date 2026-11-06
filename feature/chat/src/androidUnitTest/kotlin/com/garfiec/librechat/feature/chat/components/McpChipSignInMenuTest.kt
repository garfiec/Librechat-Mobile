package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An MCP chip whose OAuth sign-in is pending reads "· connecting" and opens a menu with "Cancel
 * sign-in" and "Start over"; its tap never reaches the screen's chip tap (which would turn it off).
 * A chip that needs sign-in still hands its tap to the screen. On Robolectric, with the unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpChipSignInMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<SettingChip>()
    private val cancels = mutableListOf<String>()
    private val startsOver = mutableListOf<String>()

    private fun line(status: McpDisplayStatus) {
        val chip = SettingChip.Mcp("github", "GitHub", SettingChipState.ON, status, requiresOAuth = true)
        composeRule.setContent {
            LibreChatTheme {
                ComposerSettingsLine(
                    chips = SettingChips(listOf(chip)),
                    onChipTap = { taps += it },
                    onCancelSignIn = { cancels += it },
                    onStartSignInOver = { startsOver += it },
                )
            }
        }
    }

    @Test
    fun aConnectingChipOffersCancelSignIn() {
        line(McpDisplayStatus.CONNECTING)
        composeRule.onNodeWithText("· connecting").assertExists()

        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.onNodeWithText("Cancel sign-in").performClick()
        composeRule.waitForIdle()

        assertThat(cancels).containsExactly("github")
        assertThat(startsOver).isEmpty()
        assertThat(taps).isEmpty()
        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()
    }

    @Test
    fun aConnectingChipOffersStartOver() {
        line(McpDisplayStatus.CONNECTING)

        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.onNodeWithText("Start over").performClick()
        composeRule.waitForIdle()

        assertThat(startsOver).containsExactly("github")
        assertThat(cancels).isEmpty()
        assertThat(taps).isEmpty()
    }

    @Test
    fun aChipThatNeedsSignInHandsItsTapToTheScreen() {
        line(McpDisplayStatus.NEEDS_SIGN_IN)
        composeRule.onNodeWithText("· sign in").assertExists()

        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.waitForIdle()

        assertThat(taps.map { (it as SettingChip.Mcp).name }).containsExactly("github")
        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()
    }
}
