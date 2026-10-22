package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the Material half of `AdaptiveModalBottomSheet(interceptDismiss = …)`: while it holds, back
 * (handled inside the sheet's own window) and a drag down leave the sheet up and ask the caller;
 * while it doesn't, back hides the sheet and then asks, and the sheet comes back if the caller
 * began holding meanwhile. A memory draft with unsaved changes relies on the hold. The Liquid Glass half is `GlassFloatingSheetIosTest`.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveModalBottomSheetInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissals by mutableIntStateOf(0)
    private var shown by mutableStateOf(true)

    /** Whether the caller holds the sheet; state, so a test can flip it while the sheet hides. */
    private var holding by mutableStateOf(false)

    /** The caller's side: it closes the sheet only when the sheet is not holding. */
    private fun setContent(intercept: Boolean) {
        holding = intercept
        composeRule.setContent {
            if (shown) {
                AdaptiveModalBottomSheet(
                    onDismissRequest = {
                        dismissals++
                        if (!holding) shown = false
                    },
                    interceptDismiss = holding,
                    skipPartiallyExpanded = true,
                ) {
                    Box(Modifier.fillMaxWidth().height(240.dp).testTag(CONTENT))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    @Test
    fun aHeldSheetStaysUpOnBackAndAsksOnce() {
        setContent(intercept = true)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    @Test
    fun aHeldSheetStaysUpOnADragDownAndAsks() {
        setContent(intercept = true)

        composeRule.onNodeWithTag(CONTENT).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        // The veto runs from the sheet state's confirmValueChange, which a settle may consult more
        // than once; what matters is that the caller was asked and the sheet is still up.
        assertTrue(dismissals >= 1)
        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    @Test
    fun aSheetThatDoesNotHoldHidesOnBackThenAsksOnce() {
        setContent(intercept = false)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
        composeRule.onNodeWithTag(CONTENT).assertDoesNotExist()
    }

    /**
     * The caller starts holding while M3 hides the sheet (a keystroke lands in a clean draft), so it
     * refuses to close when told: the sheet must come back up and keep answering Back.
     */
    @Test
    fun aSheetThatStartsHoldingWhileItHidesComesBackAndStillAsks() {
        setContent(intercept = false)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(CONTENT).performTouchInput { swipeDown() }
        holding = true
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(dismissals >= 1)
        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()

        val before = dismissals
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(before + 1, dismissals)
        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    private companion object {
        const val CONTENT = "sheet_content"

        /** Past the hide and the show that brings a refused sheet back. */
        const val SETTLE_MILLIS = 2_000L
    }
}
