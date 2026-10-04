package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [menuDragAnchor]'s gesture: what a tap, a hold and a drag on the opener do, selecting a row,
 * and cancelling far from the menu. The menu is stood in for by a column that registers itself as
 * the panel, as the real menu does while open.
 */
@OptIn(ExperimentalTestApi::class)
class MenuDragSelectionIosTest {

    private val haptics = RecordingHaptics()
    private var opens = 0
    private var cancels = 0
    private var clicks = 0
    private val selected = mutableListOf<Int>()
    private var slop = 0f
    private var holdMillis = 0L

    private fun ComposeUiTest.setUp(opensOnDrag: Boolean = true) {
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                slop = LocalViewConfiguration.current.touchSlop
                holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis
                val state = rememberMenuDragSelection()
                Column(Modifier.fillMaxSize()) {
                    // As in AdaptiveTopBar: the anchor wraps the opener's own clickable button.
                    Box(
                        Modifier.menuDragAnchor(
                            state,
                            opensOnDrag = opensOnDrag,
                            onOpen = { opens++ },
                            onCancel = { cancels++ },
                        ),
                    ) {
                        Box(Modifier.size(48.dp).testTag(ANCHOR).clickable { clicks++ })
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.width(200.dp).onPlaced { state.panel = it }) {
                        repeat(ROWS) { i ->
                            Box(
                                Modifier.fillMaxWidth().height(40.dp).testTag("row$i")
                                    .menuDragTarget(state) { selected += i },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    /** [tag]'s center, relative to the anchor's top-left — the coordinates `performTouchInput` on it uses. */
    private fun ComposeUiTest.fromAnchor(tag: String, offset: Offset = Offset.Zero): Offset {
        val anchor = onNodeWithTag(ANCHOR).fetchSemanticsNode().boundsInRoot.topLeft
        return onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center + offset - anchor
    }

    @Test
    fun tapIsLeftToTheOpenersClick() = runComposeUiTest {
        setUp()
        onNodeWithTag(ANCHOR).performClick()
        assertEquals(0, opens)
        assertEquals(1, clicks)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun holdOpensWithAHapticAndAStillReleaseStillClicks() = runComposeUiTest {
        setUp()
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0.5f, 0f))
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(1, clicks)
        assertEquals(0, cancels)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun dragOpensAtOnceAndCancelsTheClick() = runComposeUiTest {
        setUp()
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(0, clicks)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun diagonalMovePastSlopClaimsTheDrag() = runComposeUiTest {
        setUp()
        // Under slop on each axis, past it in distance: the menu measures distance.
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(slop * 0.8f, slop * 0.8f))
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(0, clicks)
    }

    @Test
    fun withoutOpensOnDragADragIsLeftAlone() = runComposeUiTest {
        setUp(opensOnDrag = false)
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0f, 1f))
            up()
        }
        waitForIdle()
        assertEquals(0, opens)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun withoutOpensOnDragAHoldStillOpens() = runComposeUiTest {
        setUp(opensOnDrag = false)
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0.5f, 0f))
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun dragOntoARowAndReleaseSelectsIt() = runComposeUiTest {
        setUp()
        val row1 = fromAnchor("row1")
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveTo(row1)
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(listOf(1), selected)
        assertEquals(0, cancels)
        assertEquals(0, clicks)
        assertEquals(
            listOf(HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.Confirm),
            haptics.performed,
        )
    }

    @Test
    fun holdThenDragOntoARowSelectsIt() = runComposeUiTest {
        setUp()
        val row2 = fromAnchor("row2")
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveTo(row2)
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(listOf(2), selected)
        assertEquals(0, clicks)
        assertEquals(
            listOf(HapticFeedbackType.LongPress, HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.Confirm),
            haptics.performed,
        )
    }

    @Test
    fun releaseFarFromTheMenuCancels() = runComposeUiTest {
        setUp()
        val far = fromAnchor("row${ROWS - 1}", Offset(0f, FAR_BELOW_DP * density.density))
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveTo(far)
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(1, cancels)
        assertEquals(emptyList(), selected)
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.performed)
    }

    @Test
    fun releaseNearTheMenuButOffEveryRowDoesNothing() = runComposeUiTest {
        setUp()
        // 20dp to the right of the panel: closer than the cancel distance.
        val beside = fromAnchor("row1", Offset((100 + 20) * density.density, 0f))
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveTo(beside)
            up()
        }
        waitForIdle()
        assertEquals(1, opens)
        assertEquals(0, cancels)
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun comingBackFromFarDisarmsTheCancel() = runComposeUiTest {
        setUp()
        val far = fromAnchor("row${ROWS - 1}", Offset(0f, FAR_BELOW_DP * density.density))
        val row0 = fromAnchor("row0")
        onNodeWithTag(ANCHOR).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveTo(far)
            moveTo(row0)
            up()
        }
        waitForIdle()
        assertEquals(0, cancels)
        assertEquals(listOf(0), selected)
        assertTrue(HapticFeedbackType.GestureThresholdActivate in haptics.performed)
    }

    private companion object {
        const val ANCHOR = "anchor"
        const val ROWS = 3
        const val FAR_BELOW_DP = 200f
    }
}
