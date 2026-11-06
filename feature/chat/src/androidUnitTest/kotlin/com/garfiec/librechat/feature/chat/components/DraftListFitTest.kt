package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The attachment list keeps below the top bar (which draws over it and takes its taps) and asks for
 * the keyboard down when that leaves less than its header and one row. During a tool-approval pause
 * it sits on the collapsed panel rather than over it.
 */
class DraftListFitTest {

    private val bar = 80.dp
    private val composer = 128.dp

    @Test
    fun aPhoneWithRoomKeepsTheKeyboard() {
        // 780dp phone, keyboard down, a plain composer.
        assertThat(draftListNeedsRoom(height = 780.dp, bottom = composer, barBottom = bar)).isFalse()
    }

    @Test
    fun theHeaderAndOneRowBelowTheBarFit() {
        // 300dp above the keyboard and a 128dp composer leave 92dp below the bar: the header and a row.
        assertThat(draftListNeedsRoom(height = 300.dp, bottom = composer, barBottom = bar)).isFalse()
    }

    @Test
    fun lessThanTheHeaderAndARowAsksForTheKeyboardDown() {
        assertThat(draftListNeedsRoom(height = 299.dp, bottom = composer, barBottom = bar)).isTrue()
    }

    @Test
    fun aTallComposerWithTheKeyboardUpAsksForTheKeyboardDown() {
        // 780dp phone with a 300dp keyboard, three queued messages and the workspace card.
        assertThat(draftListNeedsRoom(height = 480.dp, bottom = 374.dp, barBottom = bar)).isTrue()
    }

    @Test
    fun aLandscapePhoneWithTheKeyboardUpAsksForTheKeyboardDown() {
        assertThat(draftListNeedsRoom(height = 211.dp, bottom = composer, barBottom = bar)).isTrue()
    }

    @Test
    fun beforeTheScreenIsMeasuredTheListFits() {
        assertThat(draftListNeedsRoom(height = 0.dp, bottom = 0.dp, barBottom = bar)).isFalse()
    }

    @Test
    fun theMinimumIsTheHeaderARowAndThePanelsPadding() {
        assertThat(DRAFT_LIST_MIN_HEIGHT).isEqualTo(92.dp)
    }

    @Test
    fun theListSitsOnTheComposerOrOnACollapsedApprovalPanel() {
        assertThat(draftListBottom(composer = composer, collapsedToolPanel = null)).isEqualTo(composer + DRAFT_LIST_GAP)
        assertThat(draftListBottom(composer = composer, collapsedToolPanel = 56.dp))
            .isEqualTo(composer + 56.dp + DRAFT_LIST_GAP)
    }
}
