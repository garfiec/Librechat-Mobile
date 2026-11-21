package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.feature.chat.util.READING_LINE
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scroll sync on two real lazy lists: the panes share a turn layout, and only the parallel
 * reply (item 1) differs in height, so where the lead pane is through that reply decides where the
 * other one must be.
 */
@RunWith(AndroidJUnit4::class)
class ComparisonScrollSyncInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var enabled by mutableStateOf(true)
    private lateinit var primary: LazyListState
    private lateinit var secondary: LazyListState

    private val primarySizes = listOf(600.dp, 300.dp, 600.dp, 1200.dp)
    private val secondarySizes = listOf(600.dp, 1500.dp, 600.dp, 1200.dp)

    private fun setPanes() {
        composeRule.setContent {
            primary = rememberLazyListState()
            secondary = rememberLazyListState()
            val sync = rememberComparisonScrollSync(primary, secondary, enabled = enabled, isStreaming = false)
            Row(Modifier.size(width = 400.dp, height = 500.dp)) {
                Pane(primary, primarySizes, Modifier.weight(1f).comparisonSyncLead(sync, ComparisonPane.A).testTag("A"))
                Pane(secondary, secondarySizes, Modifier.weight(1f).comparisonSyncLead(sync, ComparisonPane.B).testTag("B"))
            }
        }
        composeRule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Pane(state: LazyListState, sizes: List<Dp>, modifier: Modifier) {
        LazyColumn(state = state, modifier = modifier.fillMaxHeight()) {
            itemsIndexed(sizes) { _, size -> Box(Modifier.fillMaxWidth().height(size)) }
        }
    }

    /** Fraction of the way through item [index] the given pane's reading line sits. */
    private fun fractionAtReadingLine(state: LazyListState, index: Int): Float {
        val info = state.layoutInfo
        val line = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) * READING_LINE
        val item = info.visibleItemsInfo.first { it.index == index }
        return (line - item.offset) / item.size
    }

    @Test
    fun scrollingTheLeadBringsTheOtherPaneLevelThroughItsTallerReply() {
        setPanes()
        // Pane A leads until another is touched; its reply's top at the viewport top puts the
        // reading line about 125dp into the 300dp reply.
        composeRule.runOnUiThread { runBlocking { primary.scrollToItem(1, 0) } }
        composeRule.waitForIdle()

        val leadFraction = fractionAtReadingLine(primary, 1)
        val followerFraction = fractionAtReadingLine(secondary, 1)
        assertTrue("lead's reading line is not in its reply ($leadFraction)", leadFraction in 0f..1f)
        assertEquals(leadFraction, followerFraction, 0.02f)
    }

    @Test
    fun theOtherPaneLeadsOnceTouched() {
        setPanes()
        composeRule.onNodeWithTag("B").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 10f - 1200f) }
        composeRule.waitForIdle()

        val leadFraction = fractionAtReadingLine(secondary, 1)
        val followerFraction = fractionAtReadingLine(primary, 1)
        assertEquals(leadFraction, followerFraction, 0.02f)
    }

    @Test
    fun unlinkedPanesScrollAlone() {
        setPanes()
        composeRule.runOnUiThread { enabled = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("A").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 10f - 700f) }
        composeRule.waitForIdle()

        assertEquals(0, secondary.firstVisibleItemIndex)
        assertEquals(0, secondary.firstVisibleItemScrollOffset)
    }

    @Test
    fun relinkingAlignsToThePaneScrolledWhileUnlinked() {
        setPanes()
        // B leads while linked; then A is the one read while unlinked.
        composeRule.onNodeWithTag("B").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 10f - 300f) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { enabled = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("A").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 60f) }
        composeRule.runOnUiThread { runBlocking { primary.scrollToItem(1, 0) } }
        composeRule.waitForIdle()
        val primaryBefore = primary.firstVisibleItemIndex to primary.firstVisibleItemScrollOffset

        composeRule.runOnUiThread { enabled = true }
        composeRule.waitForIdle()

        assertEquals(primaryBefore, primary.firstVisibleItemIndex to primary.firstVisibleItemScrollOffset)
        assertEquals(fractionAtReadingLine(primary, 1), fractionAtReadingLine(secondary, 1), 0.02f)
    }

    @Test
    fun theLinkIsAButtonNamedForItsState() {
        var linked by mutableStateOf(true)
        composeRule.setContent {
            ComparisonDualPane(
                primaryModelSelector = {},
                secondaryModelSelector = {},
                primaryContent = {},
                secondaryContent = {},
                bottomInset = 0.dp,
                syncScroll = linked,
                onSyncScrollChange = { linked = it },
                modifier = Modifier.size(width = 800.dp, height = 600.dp),
            )
        }
        composeRule.onNodeWithContentDescription("Panes scroll together. Unlink").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Panes scroll separately. Link").assertExists()
    }
}
