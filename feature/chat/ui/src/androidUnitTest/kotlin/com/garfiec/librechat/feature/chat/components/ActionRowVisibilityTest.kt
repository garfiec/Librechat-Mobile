package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionRowVisibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var reading by mutableStateOf(false)
    private var shown = false

    private fun row(initially: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            shown = rememberActionRowShown(messageId = "reply-1", initially = initially, isReading = reading).value
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** The clock is driven by hand, so a write from the test is announced to the recomposer here. */
    private fun announceReading(value: Boolean) {
        reading = value
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun aReadOpensTheRowAndKeepsItOpenPastTheAutoHide() {
        row()
        assertThat(shown).isFalse()

        announceReading(true)
        composeRule.mainClock.advanceTimeBy(ACTION_ROW_AUTO_HIDE_MILLIS * 2)
        assertThat(shown).isTrue()

        announceReading(false)
        composeRule.mainClock.advanceTimeBy(ACTION_ROW_AUTO_HIDE_MILLIS - 1_000)
        assertThat(shown).isTrue()
        composeRule.mainClock.advanceTimeBy(2_000)
        assertThat(shown).isFalse()
    }

    @Test
    fun aRowOpenedWithoutAReadStillHidesItself() {
        row(initially = true)
        assertThat(shown).isTrue()

        composeRule.mainClock.advanceTimeBy(ACTION_ROW_AUTO_HIDE_MILLIS + 1_000)
        assertThat(shown).isFalse()
    }
}
