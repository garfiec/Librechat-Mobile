package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.platform.BarSide
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.VerticalBars
import com.garfiec.librechat.core.ui.platform.WindowFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rail-mode heading stands in for a top bar: it is the screen's heading and covers what scrolls beneath it. */
@OptIn(ExperimentalTestApi::class)
class RailHeadingIosTest {

    private val spec = AdaptiveTopBarSpec(navigation = null, title = BarTitle("Settings"))

    private val barSpec = AdaptiveTopBarSpec(
        navigation = BarNavigation(BarIcons.Back, "Back", onClick = {}),
        title = BarTitle("Settings"),
        actions = listOf(BarAction.Icon(id = "share", icon = BarIcons.Share, label = "Share", onClick = {})),
    )

    private val duoBars = WindowFeatures(verticalBars = VerticalBars(BarSide.RIGHT, 84.dp, 82.dp))

    @Test
    fun theTitleIsAHeading() = runComposeUiTest {
        setContent { RailHeading(spec, RailSlot(parent = null), MaterialBarStyle(), belowBar = null) }

        val title = onNodeWithText("Settings").fetchSemanticsNode()
        assertTrue(SemanticsProperties.Heading in title.config)
    }

    @Test
    fun aTapOnTheHeadingDoesNotReachTheContentBeneathIt() = runComposeUiTest {
        var taps = 0
        setContent {
            Box(Modifier.size(320.dp, 400.dp)) {
                Box(Modifier.matchParentSize().clickable { taps++ })
                RailHeading(spec, RailSlot(parent = null), MaterialBarStyle(), belowBar = null, Modifier.fillMaxWidth().testTag(HEADING))
            }
        }

        // The empty part of the heading, right of the short title.
        onNodeWithTag(HEADING).performTouchInput { click(Offset(width - 8f, height / 2f)) }

        assertEquals(0, taps)
    }

    @Test
    fun aScaffoldOnVerticalBarsHandsItsBarToTheRail() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalWindowFeatures provides duoBars) { Screen() } }

        assertBarControlsShown()
        assertTrue(titleIsHeading())
    }

    @Test
    fun withoutVerticalBarsTheScaffoldDrawsAHorizontalBar() = runComposeUiTest {
        setContent { Screen() }

        assertBarControlsShown()
        assertFalse(titleIsHeading())
    }

    @Test
    fun aPaneOffsetByABackSwipeGetsItsRailBackOnceItSettles() = runComposeUiTest {
        // The page a back swipe reveals in the content pane starts offset toward the start edge by its layer alone.
        var offset by mutableStateOf(-96f)
        setContent {
            CompositionLocalProvider(LocalWindowFeatures provides duoBars) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(PANE_SHARE)
                            .graphicsLayer { translationX = offset.dp.toPx() },
                    ) { Screen() }
                }
            }
        }
        assertFalse(titleIsHeading())

        runOnIdle { offset = 0f }

        assertBarControlsShown()
        assertTrue(titleIsHeading())
    }

    @Composable
    private fun Screen() {
        AdaptiveScaffold(Modifier.fillMaxSize(), topBar = { AdaptiveTopBar(barSpec) }) {}
    }

    private fun ComposeUiTest.assertBarControlsShown() {
        onNodeWithContentDescription("Back").assertExists()
        onNodeWithContentDescription("Share").assertExists()
    }

    private fun ComposeUiTest.titleIsHeading(): Boolean =
        SemanticsProperties.Heading in onNodeWithText("Settings").fetchSemanticsNode().config

    private companion object {
        const val HEADING = "heading"
        const val PANE_SHARE = 0.66f
    }
}
