package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the hold behind `AdaptiveModalBottomSheet(interceptDismiss = true)` in Liquid Glass: while it
 * holds, a scrim tap or a drag down leaves the sheet up and asks once; while it doesn't, the sheet
 * slides away first and asks after, and slides back if the caller began holding meanwhile. A memory
 * draft with unsaved changes relies on the first and the last.
 *
 * Back is not covered here: iOS has no system back, so `PlatformBackHandler` never fires on it.
 * Composed on the FLAT tier, which needs no backdrop; the gestures are the same on every tier.
 */
@OptIn(ExperimentalTestApi::class)
class GlassFloatingSheetIosTest {

    private var dismissals = 0
    private var shown by mutableStateOf(true)

    /** Whether the caller holds the sheet; read live, so a test can flip it mid-slide. */
    private var holding by mutableStateOf(false)

    /** The caller's side: it closes the sheet only when the sheet is not holding. */
    private fun ComposeUiTest.setUp(intercept: Boolean) {
        holding = intercept
        setContent {
            CompositionLocalProvider(LocalGlassLevel provides GlassCapability.FLAT) {
                if (shown) {
                    GlassFloatingSheet(
                        onDismissRequest = {
                            dismissals++
                            if (!holding) shown = false
                        },
                        interceptDismiss = { holding },
                    ) {
                        Box(Modifier.fillMaxWidth().height(CONTENT_HEIGHT).testTag(CONTENT))
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    /** The top of the screen is scrim: the panel sits at the bottom. */
    private fun ComposeUiTest.tapScrim() {
        onRoot().performTouchInput { click(Offset(centerX, SCRIM_TAP_Y)) }
    }

    @Test
    fun aHeldSheetStaysUpOnAScrimTapAndAsksOnce() = runComposeUiTest {
        setUp(intercept = true)

        tapScrim()
        waitForIdle()

        assertEquals(1, dismissals)
        onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    @Test
    fun aHeldSheetSettlesBackFromADragDownAndAsksOnce() = runComposeUiTest {
        setUp(intercept = true)

        onNodeWithTag(CONTENT).performTouchInput { swipeDown() }
        waitForIdle()

        assertEquals(1, dismissals)
        onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    @Test
    fun aSheetThatDoesNotHoldSlidesAwayBeforeItAsks() = runComposeUiTest {
        setUp(intercept = false)
        mainClock.autoAdvance = false

        tapScrim()
        mainClock.advanceTimeByFrame()
        // Still sliding: the caller is told only once the sheet has gone.
        assertEquals(0, dismissals)

        mainClock.advanceTimeBy(HIDE_SETTLE_MILLIS)
        waitForIdle()
        assertEquals(1, dismissals)
        onNodeWithTag(CONTENT).assertDoesNotExist()
    }

    /**
     * The caller starts holding while the sheet slides away (a keystroke lands in a clean draft), so
     * it refuses to close: the sheet must come back and keep answering, not sit off screen behind a
     * scrim that takes every touch. A second scrim tap stands in for Back, which iOS doesn't have.
     */
    @Test
    fun aSheetThatStartsHoldingDuringItsSlideComesBackAndStillAsks() = runComposeUiTest {
        setUp(intercept = false)
        mainClock.autoAdvance = false

        tapScrim()
        mainClock.advanceTimeByFrame()
        holding = true

        mainClock.advanceTimeBy(HIDE_AND_SHOW_SETTLE_MILLIS)
        waitForIdle()
        assertEquals(1, dismissals)
        onNodeWithTag(CONTENT).assertIsDisplayed()

        tapScrim()
        mainClock.advanceTimeBy(HIDE_AND_SHOW_SETTLE_MILLIS)
        waitForIdle()
        // Held now, so it asks straight away and stays up.
        assertEquals(2, dismissals)
        onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    private companion object {
        const val CONTENT = "sheet_content"
        val CONTENT_HEIGHT = 240.dp
        const val SCRIM_TAP_Y = 8f

        /** Past the 220 ms hide tween. */
        const val HIDE_SETTLE_MILLIS = 600L

        /** Past the hide tween and the show spring that brings a refused sheet back. */
        const val HIDE_AND_SHOW_SETTLE_MILLIS = 2_000L
    }
}
