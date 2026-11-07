package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownModel
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.viewmodel.CompactNudge
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextRingNudgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var snoozes = 0
    private var compacts = 0

    private val details = ContextGaugeDetails(
        model = ContextBreakdownModel(
            usage = ContextUsage(
                breakdown = TokenBudgetBreakdown(
                    maxContextTokens = 128_000,
                    instructionTokens = 4_000,
                    messageTokens = 114_000,
                    messageCount = 24,
                ),
            ),
            isEstimate = false,
            totals = ContextUsageTotals(),
            costEnabled = false,
            currency = null,
            isCompacting = false,
            compactionAvailable = true,
        ),
        sections = ContextDetailPreset.STANDARD.sections,
        nudge = CompactNudge(band = 1, percent = 92),
    )

    private fun ring() {
        composeRule.setContent {
            LibreChatTheme {
                ComposerSettingsLine(
                    chips = SettingChips.EMPTY,
                    onChipTap = {},
                    ring = ContextRingState(details, onCompact = { compacts++ }, onSnoozeCompact = { snoozes++ }),
                )
            }
        }
    }

    @Test
    fun aDueSuggestionStillLeadsToTheBreakdownWithoutSnoozing() {
        ring()
        composeRule.onNodeWithContentDescription("Context used", substring = true).performClick()
        composeRule.onNodeWithText("Not now").assertExists()

        composeRule.onNodeWithText("See usage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Compact this conversation?").assertDoesNotExist()
        composeRule.onNodeWithText("Context window").assertExists()
        assertThat(snoozes).isEqualTo(0)
        assertThat(compacts).isEqualTo(0)
    }

    @Test
    fun notNowStillSnoozes() {
        ring()
        composeRule.onNodeWithContentDescription("Context used", substring = true).performClick()

        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitForIdle()

        assertThat(snoozes).isEqualTo(1)
        composeRule.onNodeWithText("Context window").assertDoesNotExist()
    }
}
