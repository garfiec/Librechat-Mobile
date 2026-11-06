package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The attachment list keeps room for its header and a row: below the top bar while it fits there,
 * under the bar's scrim when it doesn't, and with the keyboard down when even that is too little.
 */
class DraftListFitTest {

    private val statusBar = 24.dp
    private val bar = 80.dp

    @Test
    fun aPhoneWithRoomKeepsTheListBelowTheTopBar() {
        // 780dp phone, keyboard down, a plain composer.
        val fit = draftListFit(height = 780.dp, bottom = 128.dp, barBottom = bar, statusBarTop = statusBar)

        assertThat(fit).isEqualTo(DraftListFit(top = bar, needsRoom = false))
    }

    @Test
    fun aListThatWouldBeASliverBelowTheBarGoesUnderItsScrim() {
        // 300dp above the keyboard and a 128dp composer leave 92dp below the bar, 148dp below the status bar.
        val fit = draftListFit(height = 300.dp, bottom = 128.dp, barBottom = bar, statusBarTop = statusBar)

        assertThat(fit).isEqualTo(DraftListFit(top = statusBar, needsRoom = false))
    }

    @Test
    fun aTallComposerWithTheKeyboardUpAsksForTheKeyboardDown() {
        // 780dp phone with a 300dp keyboard, three queued messages and the workspace card: about
        // 30dp would be left below the bar, and under 100dp even below the status bar.
        val fit = draftListFit(height = 480.dp, bottom = 374.dp, barBottom = bar, statusBarTop = statusBar)

        assertThat(fit).isEqualTo(DraftListFit(top = statusBar, needsRoom = true))
    }

    @Test
    fun aLandscapePhoneWithTheKeyboardUpAsksForTheKeyboardDown() {
        val fit = draftListFit(height = 211.dp, bottom = 128.dp, barBottom = bar, statusBarTop = statusBar)

        assertThat(fit.needsRoom).isTrue()
    }

    @Test
    fun beforeTheScreenIsMeasuredTheListTakesTheBarsInset() {
        val fit = draftListFit(height = 0.dp, bottom = 0.dp, barBottom = bar, statusBarTop = statusBar)

        assertThat(fit).isEqualTo(DraftListFit(top = bar, needsRoom = false))
    }

    @Test
    fun theMinimumIsTheHeaderAndOneRow() {
        assertThat(DRAFT_LIST_MIN_HEIGHT).isEqualTo(100.dp)
        val exact = draftListFit(
            height = bar + DRAFT_LIST_MIN_HEIGHT + 128.dp,
            bottom = 128.dp,
            barBottom = bar,
            statusBarTop = statusBar,
        )
        assertThat(exact.top).isEqualTo(bar)
    }
}
