package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SteerAndQueueColumnTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scroll = ScrollState(0)

    private fun setRows(count: Int, behind: Color = Color.White) {
        composeRule.setContent {
            Box(Modifier.width(200.dp).background(behind).testTag(REGION)) {
                SteerAndQueueColumn(scroll, canvas = { Color.White }) {
                    repeat(count) { Box(Modifier.fillMaxWidth().height(64.dp).background(Color.Red)) }
                }
            }
        }
    }

    /** The pixel at the region's centre x, [dpFromTop] down (negative: up from the bottom). */
    private fun pixel(dpFromTop: Float): Color {
        val pixels = composeRule.onNodeWithTag(REGION).captureToImage().toPixelMap()
        val density = composeRule.density.density
        val y = if (dpFromTop >= 0) (dpFromTop * density).toInt() else pixels.height - 1 + (dpFromTop * density).toInt()
        return pixels[pixels.width / 2, y]
    }

    /** Red channel minus green: 1 on the red rows, 0 on the white canvas. */
    private fun redness(dpFromTop: Float): Float = pixel(dpFromTop).let { it.red - it.green }

    @Test
    fun overflowFadesTheHiddenTopEdgeAtRest() {
        setRows(3)

        assertWithMessage("expected the top edge to fade toward the background").that(redness(1f)).isLessThan(0.5f)
        assertWithMessage("expected the bottom edge to stay the content colour").that(redness(-1f)).isGreaterThan(0.95f)
    }

    @Test
    fun scrolledToTheTopTheFadeMovesToTheBottom() {
        setRows(3)
        composeRule.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }

        assertWithMessage("expected the top edge to stay the content colour").that(redness(1f)).isGreaterThan(0.95f)
        assertWithMessage("expected the bottom edge to fade toward the background").that(redness(-1f)).isLessThan(0.5f)
    }

    @Test
    fun theFadeCoversWhatScrollsBehindTheRegion() {
        setRows(3, behind = Color.Blue)

        assertWithMessage("expected the top edge to fade to the canvas, not show what is behind it")
            .that(pixel(1f).green).isGreaterThan(0.9f)
    }

    @Test
    fun aRegionThatFitsDrawsNoFade() {
        setRows(2)

        assertWithMessage("top edge").that(redness(1f)).isGreaterThan(0.95f)
        assertWithMessage("bottom edge").that(redness(-1f)).isGreaterThan(0.95f)
    }

    private companion object {
        const val REGION = "region"
    }
}
