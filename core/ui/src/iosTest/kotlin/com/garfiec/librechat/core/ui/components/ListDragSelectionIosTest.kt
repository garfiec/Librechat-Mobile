package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
 * Pins [listDragSelection]'s gesture inside a horizontal swipe (the drawer) and a vertical one (a
 * sheet): which drags it claims, which it leaves to the parents or to a child that consumed first,
 * `holdOnly`, excluded controls, and the haptics of each.
 */
@OptIn(ExperimentalTestApi::class)
class ListDragSelectionIosTest {

    private val haptics = RecordingHaptics()
    private val clicks = mutableListOf<Int>()
    private val selected = mutableListOf<Int>()
    private var swiped = 0f
    private var sheetDragged = 0f
    private var listScrolled = 0f
    private var avatarDragged = 0f
    private var excludedClicks = 0
    private var slop = 0f
    private var holdMillis = 0L

    /** [scrollInside]: the rows' own vertical scroll sits inside the modifier, as a LazyColumn's does. */
    private fun ComposeUiTest.setUp(holdOnly: Boolean = false, scrollInside: Boolean = false) {
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                slop = LocalViewConfiguration.current.touchSlop
                holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis
                val state = rememberListDragSelection()
                // The drawer's swipe around every list; a sheet's drag around the hold-only ones.
                val sheetState = rememberDraggableState { sheetDragged += it }
                Box(
                    Modifier.fillMaxSize()
                        .draggable(rememberDraggableState { swiped += it }, Orientation.Horizontal)
                        .then(if (holdOnly) Modifier.draggable(sheetState, Orientation.Vertical) else Modifier),
                ) {
                    val listState = rememberDraggableState { listScrolled += it }
                    Column(
                        Modifier.width(300.dp)
                            .listDragSelection(state, holdOnly)
                            .then(if (scrollInside) Modifier.draggable(listState, Orientation.Vertical) else Modifier),
                    ) {
                        repeat(ROWS) { i ->
                            Box(
                                Modifier.fillMaxWidth().height(56.dp).testTag("row$i")
                                    .listDragTarget(state) { selected += i }
                                    .clickable { clicks += i },
                            ) {
                                when (i) {
                                    // The account avatar: its own vertical swipe.
                                    0 -> Box(
                                        Modifier.size(40.dp).align(Alignment.CenterEnd).testTag(AVATAR)
                                            .pointerInput(Unit) {
                                                detectVerticalDragGestures { change, dy ->
                                                    change.consume()
                                                    avatarDragged += dy
                                                }
                                            },
                                    )
                                    // An edit button inside the row.
                                    1 -> Box(
                                        Modifier.size(40.dp).align(Alignment.CenterEnd).testTag(EXCLUDED)
                                            .dragSelectExclude(state)
                                            .clickable { excludedClicks++ },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.rowDelta(from: Int, to: Int): Offset {
        fun center(i: Int) = onNodeWithTag("row$i").fetchSemanticsNode().boundsInRoot.center
        return center(to) - center(from)
    }

    @Test
    fun tapIsLeftToTheRowsClick() = runComposeUiTest {
        setUp()
        onNodeWithTag("row2").performClick()
        waitForIdle()
        assertEquals(listOf(2), clicks)
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun verticalDragClaimsAndSelectsTheRowItEndsOn() = runComposeUiTest {
        setUp()
        val toRow2 = rowDelta(3, 2)
        val toRow1 = rowDelta(3, 1)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            moveTo(center + toRow2)
            moveTo(center + toRow1)
            up()
        }
        waitForIdle()
        assertEquals(listOf(1), selected)
        assertEquals(emptyList(), clicks)
        assertEquals(0f, swiped)
        // No tick for the touched row: the highlight was already there as its pressed state.
        assertEquals(
            listOf(HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.Confirm),
            haptics.performed,
        )
    }

    @Test
    fun releaseOffEveryRowSelectsNothing() = runComposeUiTest {
        setUp()
        onNodeWithTag("row3").performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveBy(Offset(0f, 300 * density))
            up()
        }
        waitForIdle()
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), clicks)
    }

    @Test
    fun horizontalDragIsLeftToTheSwipe() = runComposeUiTest {
        setUp()
        onNodeWithTag("row2").performTouchInput {
            down(center)
            moveBy(Offset(slop * 2, 0f))
            moveBy(Offset(slop * 2, 0f))
            up()
        }
        waitForIdle()
        assertTrue(swiped > 0f)
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), clicks)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun diagonalDragTakesTheAxisPastSlopFirst() = runComposeUiTest {
        setUp()
        // More sideways than vertical: the swipe's, even though it is past slop vertically too.
        onNodeWithTag("row2").performTouchInput {
            down(center)
            moveBy(Offset(slop * 3, slop * 2))
            moveBy(Offset(slop * 2, 0f))
            up()
        }
        waitForIdle()
        assertTrue(swiped > 0f)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun moveUnderSlopOnEachAxisStillWaitsForTheHold() = runComposeUiTest {
        setUp()
        // Past slop in distance but on neither axis: not a drag yet, so the hold takes it.
        onNodeWithTag("row2").performTouchInput {
            down(center)
            moveBy(Offset(slop * 0.8f, slop * 0.8f))
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0f, 0.5f))
            up()
        }
        waitForIdle()
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed.take(1))
    }

    @Test
    fun childThatConsumesFirstKeepsItsDrag() = runComposeUiTest {
        setUp()
        val down2 = rowDelta(0, 2)
        onNodeWithTag(AVATAR, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveBy(down2)
            up()
        }
        waitForIdle()
        assertTrue(avatarDragged > 0f)
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun holdTakesTheGestureWithAHaptic() = runComposeUiTest {
        setUp()
        val toRow1 = rowDelta(3, 1)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveTo(center + toRow1)
            up()
        }
        waitForIdle()
        assertEquals(listOf(1), selected)
        assertEquals(emptyList(), clicks)
        assertEquals(
            listOf(HapticFeedbackType.LongPress, HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.Confirm),
            haptics.performed,
        )
    }

    @Test
    fun holdThenSidewaysStillDragSelects() = runComposeUiTest {
        setUp()
        onNodeWithTag("row2").performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(slop * 3, 0f))
            moveBy(Offset(slop * 3, 0f))
            up()
        }
        waitForIdle()
        assertEquals(0f, swiped)
        assertEquals(listOf(2), selected)
    }

    @Test
    fun holdReleasedWithoutMovingIsTheRowsClick() = runComposeUiTest {
        setUp()
        onNodeWithTag("row2").performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0.5f, 0f))
            up()
        }
        waitForIdle()
        assertEquals(listOf(2), clicks)
        assertEquals(emptyList(), selected)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun holdOnlyLeavesADragBeforeTheHoldToTheSheet() = runComposeUiTest {
        setUp(holdOnly = true)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            moveBy(Offset(0f, -slop * 2))
            moveBy(Offset(0f, -slop * 2))
            up()
        }
        waitForIdle()
        assertTrue(sheetDragged < 0f)
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), clicks)
        assertEquals(emptyList(), haptics.performed)
    }

    @Test
    fun holdOnlyLeavesADragBeforeTheHoldToTheListsOwnScroll() = runComposeUiTest {
        setUp(holdOnly = true, scrollInside = true)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            moveBy(Offset(0f, -slop * 2))
            moveBy(Offset(0f, -slop * 2))
            up()
        }
        waitForIdle()
        assertTrue(listScrolled < 0f)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun holdOnlyAfterTheHoldTheListsOwnScrollGetsNothing() = runComposeUiTest {
        setUp(holdOnly = true, scrollInside = true)
        val toRow1 = rowDelta(3, 1)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0f, -slop * 2))
            moveTo(center + toRow1)
            up()
        }
        waitForIdle()
        assertEquals(0f, listScrolled)
        assertEquals(0f, sheetDragged)
        assertEquals(listOf(1), selected)
    }

    @Test
    fun holdOnlyAfterTheHoldTheSheetGetsNothing() = runComposeUiTest {
        setUp(holdOnly = true)
        val toRow1 = rowDelta(3, 1)
        onNodeWithTag("row3").performTouchInput {
            down(center)
            advanceEventTime(holdMillis + 100)
            moveBy(Offset(0f, -slop * 2))
            moveTo(center + toRow1)
            up()
        }
        waitForIdle()
        assertEquals(0f, sheetDragged)
        assertEquals(listOf(1), selected)
        assertEquals(HapticFeedbackType.LongPress, haptics.performed.first())
    }

    @Test
    fun pressOnAnExcludedControlIsTheControlsAlone() = runComposeUiTest {
        setUp(holdOnly = true)
        val toRow3 = rowDelta(1, 3)
        onNodeWithTag(EXCLUDED, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, slop * 2))
            moveBy(toRow3)
            up()
        }
        waitForIdle()
        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), haptics.performed)
        // Nothing claimed it, so the sheet behind took the drag.
        assertTrue(sheetDragged > 0f)
    }

    @Test
    fun tapOnAnExcludedControlClicksOnlyTheControl() = runComposeUiTest {
        setUp()
        onNodeWithTag(EXCLUDED, useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, excludedClicks)
        assertEquals(emptyList(), clicks)
        assertEquals(emptyList(), haptics.performed)
    }

    private companion object {
        const val ROWS = 4
        const val AVATAR = "avatar"
        const val EXCLUDED = "excluded"
    }
}
