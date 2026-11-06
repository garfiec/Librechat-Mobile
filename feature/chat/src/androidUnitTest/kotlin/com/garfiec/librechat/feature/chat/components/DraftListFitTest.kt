package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The attachment list keeps below the top bar (which draws over it and takes its taps) and asks for
 * the keyboard down when that leaves less than its header and one row, measured at the font size in
 * effect.
 */
class DraftListFitTest {

    private val bar = 80.dp
    private val composer = 128.dp
    private val type = Typography()

    /** The panel's minimum with the M3 type scale, at [fontScale]. */
    private fun minHeightAt(fontScale: Float): Dp = Density(density = 1f, fontScale = fontScale)
        .draftListMinHeight(header = type.titleSmall, name = type.bodyMedium, subtitle = type.bodySmall)

    private val minHeight = minHeightAt(1f)

    @Test
    fun aPhoneWithRoomKeepsTheKeyboard() {
        // 780dp phone, keyboard down, a plain composer.
        assertThat(draftListNeedsRoom(height = 780.dp, bottom = composer, barBottom = bar, minHeight = minHeight)).isFalse()
    }

    @Test
    fun theHeaderAndOneRowBelowTheBarFit() {
        // 300dp above the keyboard and a 128dp composer leave 92dp below the bar: the header and a row.
        assertThat(draftListNeedsRoom(height = 300.dp, bottom = composer, barBottom = bar, minHeight = minHeight)).isFalse()
    }

    @Test
    fun lessThanTheHeaderAndARowAsksForTheKeyboardDown() {
        assertThat(draftListNeedsRoom(height = 299.dp, bottom = composer, barBottom = bar, minHeight = minHeight)).isTrue()
    }

    @Test
    fun aTallComposerWithTheKeyboardUpAsksForTheKeyboardDown() {
        // 780dp phone with a 300dp keyboard, three queued messages and the workspace card.
        assertThat(draftListNeedsRoom(height = 480.dp, bottom = 374.dp, barBottom = bar, minHeight = minHeight)).isTrue()
    }

    @Test
    fun aLandscapePhoneWithTheKeyboardUpAsksForTheKeyboardDown() {
        assertThat(draftListNeedsRoom(height = 211.dp, bottom = composer, barBottom = bar, minHeight = minHeight)).isTrue()
    }

    @Test
    fun beforeTheScreenIsMeasuredTheListFits() {
        assertThat(draftListNeedsRoom(height = 0.dp, bottom = 0.dp, barBottom = bar, minHeight = minHeight)).isFalse()
    }

    @Test
    fun theMinimumIsTheHeaderARowAndThePanelsPadding() {
        // 12 + a 20dp titleSmall line + 4, a 52dp row, 4.
        assertThat(minHeight).isEqualTo(92.dp)
    }

    @Test
    fun atTwiceTheFontTheMinimumGrowsWithTheLines() {
        val large = minHeightAt(2f)
        // The header line and the row's two lines outgrow the 52dp row.
        assertThat(large).isGreaterThan(120.dp)
        // A 640dp phone at 2x font, keyboard up (380dp left), an 88dp bar and a 190dp composer with
        // a queued message leave 98dp: enough at 1x, a sliver of the first row at 2x.
        assertThat(draftListNeedsRoom(height = 380.dp, bottom = 194.dp, barBottom = 88.dp, minHeight = minHeight)).isFalse()
        assertThat(draftListNeedsRoom(height = 380.dp, bottom = 194.dp, barBottom = 88.dp, minHeight = large)).isTrue()
    }
}
