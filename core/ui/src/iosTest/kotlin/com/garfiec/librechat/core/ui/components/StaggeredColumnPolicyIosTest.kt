package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the Material menu's row column: it measures like M3's `Column` (as wide as its widest row,
 * rows stacked, each at its own width), and its layers move only the rows' drawing, by the stagger.
 */
@OptIn(ExperimentalTestApi::class)
class StaggeredColumnPolicyIosTest {

    private var progress by mutableFloatStateOf(1f)
    private var opensDown by mutableStateOf(true)

    @Composable
    private fun StaggeredRows(modifier: Modifier = Modifier, rows: @Composable () -> Unit = defaultRows) {
        val shiftPx = with(LocalDensity.current) { SHIFT.toPx() }
        Layout(
            content = rows,
            modifier = modifier.testTag(COLUMN),
            measurePolicy = StaggeredColumnPolicy(
                rowProgress = { progress },
                rowShiftPx = shiftPx,
                opensDown = { opensDown },
            ),
        )
    }

    private val defaultRows: @Composable () -> Unit = {
        Box(Modifier.size(120.dp, 40.dp).testTag("row0"))
        Box(Modifier.size(200.dp, 48.dp).testTag("row1"))
        Box(Modifier.size(80.dp, 32.dp).testTag("row2"))
    }

    private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun columnIsAsWideAsItsWidestRowAndAsTallAsAllOfThem() = runComposeUiTest {
        setContent { StaggeredRows() }
        val column = bounds(COLUMN)
        assertDp(200.dp, column.width)
        assertDp(120.dp, column.height)
    }

    @Test
    fun rowsStackInOrderAtTheirOwnWidth() = runComposeUiTest {
        setContent { StaggeredRows() }
        val top = bounds(COLUMN).top
        assertDp(top, bounds("row0").top)
        assertDp(top + 40.dp, bounds("row1").top)
        assertDp(top + 88.dp, bounds("row2").top)
        assertDp(120.dp, bounds("row0").width)
        assertDp(80.dp, bounds("row2").width)
    }

    @Test
    fun aFixedWidthStretchesTheColumnButNotItsRows() = runComposeUiTest {
        setContent { StaggeredRows(Modifier.width(300.dp)) }
        assertDp(300.dp, bounds(COLUMN).width)
        assertDp(120.dp, bounds("row0").width)
    }

    @Test
    fun aNarrowMaxWidthCapsTheColumn() = runComposeUiTest {
        setContent { StaggeredRows(Modifier.widthIn(max = 150.dp)) }
        assertDp(150.dp, bounds(COLUMN).width)
        assertDp(120.dp, bounds("row0").width)
        assertDp(150.dp, bounds("row1").width)
    }

    @Test
    fun maxIntrinsicWidthIsTheWidestRow() = runComposeUiTest {
        // The full-width row would make the column as wide as the 300dp parent, but intrinsically it
        // asks for nothing, so the column (and with it that row) is as wide as the widest sized row.
        setContent {
            Box(Modifier.width(300.dp)) {
                StaggeredRows(Modifier.width(IntrinsicSize.Max)) {
                    Box(Modifier.size(120.dp, 40.dp).testTag("row0"))
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("row1"))
                }
            }
        }
        assertDp(120.dp, bounds(COLUMN).width)
        assertDp(120.dp, bounds("row1").width)
    }

    @Test
    fun minIntrinsicWidthIsTheWidestRow() = runComposeUiTest {
        setContent {
            Box(Modifier.width(300.dp)) {
                StaggeredRows(Modifier.width(IntrinsicSize.Min)) {
                    Box(Modifier.size(120.dp, 40.dp).testTag("row0"))
                    Box(Modifier.size(90.dp, 40.dp).testTag("row1"))
                }
            }
        }
        assertDp(120.dp, bounds(COLUMN).width)
    }

    @Test
    fun intrinsicHeightIsAllTheRows() = runComposeUiTest {
        setContent { StaggeredRows(Modifier.height(IntrinsicSize.Max)) }
        assertDp(120.dp, bounds(COLUMN).height)
    }

    @Test
    fun aRowThatHasArrivedSitsWhereItWasLaidOut() = runComposeUiTest {
        progress = 1f
        setContent { StaggeredRows() }
        assertRect(laidOutRow1(), bounds("row1"))
    }

    @Test
    fun aRowYetToArriveStartsShrunkAndShiftedAgainstTheOpening() = runComposeUiTest {
        setContent { StaggeredRows() }
        val rest = laidOutRow1()

        // The reported bounds take the layer's transform to the row's origin but keep its unscaled
        // size, so the shrink shows as an origin pulled in toward the row's centre.
        progress = 0f
        opensDown = true
        waitForIdle()
        val down = bounds("row1")
        assertTrue(down.left > rest.left, "shrunk toward its centre: $down vs $rest")
        assertTrue(down.top < rest.top, "above its place: $down vs $rest")

        opensDown = false
        waitForIdle()
        val up = bounds("row1")
        assertTrue(up.top > rest.top, "below its place: $up vs $rest")
        assertDp(down.left, up.left)
        assertDp(SHIFT * 2, up.top - down.top)
    }

    private fun ComposeUiTest.laidOutRow1(): DpRect {
        val top = bounds(COLUMN).top + 40.dp
        val left = bounds(COLUMN).left
        return DpRect(left, top, left + 200.dp, top + 48.dp)
    }

    private fun assertDp(expected: Dp, actual: Dp) {
        assertTrue(abs(expected.value - actual.value) < TOLERANCE, "expected $expected, was $actual")
    }

    private fun assertRect(expected: DpRect, actual: DpRect) {
        assertDp(expected.left, actual.left)
        assertDp(expected.top, actual.top)
        assertDp(expected.right, actual.right)
        assertDp(expected.bottom, actual.bottom)
        assertEquals(expected.width.value, actual.width.value, TOLERANCE)
    }

    private companion object {
        const val COLUMN = "column"
        const val TOLERANCE = 0.6f
        val SHIFT = 12.dp
    }
}
