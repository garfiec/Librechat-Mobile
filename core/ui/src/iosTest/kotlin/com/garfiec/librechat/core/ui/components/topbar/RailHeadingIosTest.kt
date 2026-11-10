package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rail-mode heading stands in for a top bar: it is the screen's heading and covers what scrolls beneath it. */
@OptIn(ExperimentalTestApi::class)
class RailHeadingIosTest {

    private val spec = AdaptiveTopBarSpec(navigation = null, title = BarTitle("Settings"))

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

    private companion object {
        const val HEADING = "heading"
    }
}
