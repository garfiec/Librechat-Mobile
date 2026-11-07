package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpChipSignInMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<SettingChip>()
    private val cancels = mutableListOf<String>()
    private val startsOver = mutableListOf<String>()

    private fun line(status: McpDisplayStatus) {
        val chip = SettingChip.Mcp(McpServerDisplayData("github", "GitHub", null, status, requiresOAuth = true), SettingChipState.ON)
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

    private fun mcp(name: String, label: String, status: McpDisplayStatus, signInBusy: Boolean = false) = SettingChip.Mcp(
        McpServerDisplayData(name, label, null, status, requiresOAuth = true, signInBusy = signInBusy),
        SettingChipState.ON,
    )

    /** A line whose chips the test changes afterwards, as a status reload or a deselect would. */
    private fun liveLine(initial: List<SettingChip>): MutableState<List<SettingChip>> {
        val chips = mutableStateOf(initial)
        composeRule.setContent {
            LibreChatTheme {
                ComposerSettingsLine(
                    chips = SettingChips(chips.value),
                    onChipTap = { taps += it },
                    onCancelSignIn = { cancels += it },
                    onStartSignInOver = { startsOver += it },
                )
            }
        }
        return chips
    }

    @Test
    fun aMenuASettledStatusTookDownNeverReopensUntapped() {
        val chips = liveLine(listOf(mcp("drive", "Drive", McpDisplayStatus.CONNECTING)))
        composeRule.onNodeWithText("Drive").performClick()
        composeRule.onNodeWithText("Cancel sign-in").assertExists()

        // A reload while the menu is up: the flow finished elsewhere, and later needs sign-in again.
        chips.value = listOf(mcp("drive", "Drive", McpDisplayStatus.NEEDS_SIGN_IN))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()

        // The user's sign-in tap writes the chip connecting at once: no menu over that sign-in.
        chips.value = listOf(mcp("drive", "Drive", McpDisplayStatus.CONNECTING))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()
    }

    @Test
    fun anOpenMenuStaysWithItsChipWhenAChipAheadLeaves() {
        val chips = liveLine(
            listOf(mcp("github", "GitHub", McpDisplayStatus.CONNECTING), mcp("drive", "Drive", McpDisplayStatus.CONNECTING)),
        )
        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.onNodeWithText("Cancel sign-in").assertExists()

        // GitHub is deselected in the "+" sheet while its menu is up; Drive moves into its place.
        chips.value = listOf(mcp("drive", "Drive", McpDisplayStatus.CONNECTING))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()
    }

    @Test
    fun aConnectingChipWhoseReconnectIsStillOutOffersNoMenu() {
        // The chip's own sign-in tap is still reconnecting: Cancel sign-in and Start over would be ignored.
        liveLine(listOf(mcp("github", "GitHub", McpDisplayStatus.CONNECTING, signInBusy = true)))
        composeRule.onNodeWithText("· connecting").assertExists()

        composeRule.onNodeWithText("GitHub").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel sign-in").assertDoesNotExist()
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
