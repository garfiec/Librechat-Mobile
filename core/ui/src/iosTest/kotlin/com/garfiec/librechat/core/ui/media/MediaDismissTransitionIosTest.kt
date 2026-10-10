package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.rememberCoilZoomState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the viewer's dismiss transition as the pager drives it: tracking a drag or a back gesture,
 * springing back, flying into the thumbnail (or fading without one), and calling `onDismissed`
 * exactly once, only when the flight has landed or the viewer leaves mid-flight.
 */
@OptIn(ExperimentalTestApi::class)
class MediaDismissTransitionIosTest {

    private var showViewer by mutableStateOf(true)
    private var showThumbnail by mutableStateOf(true)
    private var dismissed = 0
    private var px = 1f
    private lateinit var registry: MediaThumbnailRegistry
    private lateinit var transition: MediaDismissTransition
    private lateinit var page: MediaPageSource

    private fun ComposeUiTest.setUp(thumbnail: Boolean = true) {
        showThumbnail = thumbnail
        setContent {
            px = LocalDensity.current.density
            registry = remember { MediaThumbnailRegistry() }
            CompositionLocalProvider(LocalMediaThumbnails provides registry) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    if (showThumbnail) {
                        Box(Modifier.offset(20.dp, 20.dp).size(60.dp).mediaThumbnail(URL, RoundedCornerShape(8.dp)))
                    }
                    if (showViewer) {
                        transition = rememberMediaDismissTransition(registry) { dismissed++ }
                        val zoom = rememberCoilZoomState()
                        val source = remember { MediaPageSource(URL, zoom.zoomable) }
                        page = source
                        Box(Modifier.fillMaxSize().onPlaced { source.coordinates = it })
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
    }

    private fun ComposeUiTest.settleAnimations() {
        mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    @Test
    fun atRestNothingMoves() = runComposeUiTest {
        setUp()
        assertNull(transition.frame(page))
        assertEquals(1f, transition.scrimAlpha)
        assertEquals(1f, transition.chromeAlpha)
        assertFalse(transition.isDismissing)
    }

    @Test
    fun dragFadesTheScrimAndToolbarTheSameInAnyDirection() = runComposeUiTest {
        setUp()
        transition.track(page)
        transition.dragBy(Offset(0f, 40 * px))
        val down = transition.scrimAlpha to transition.chromeAlpha
        // Now 40 to the left of rest instead of 40 below: the same distance.
        transition.dragBy(Offset(-40 * px, -40 * px))
        val sideways = transition.scrimAlpha to transition.chromeAlpha
        assertTrue(down.first < 1f && down.second < 1f, "faded: $down")
        assertEquals(down.first, sideways.first, 0.001f)
        assertEquals(down.second, sideways.second, 0.001f)

        transition.dragBy(Offset(0f, 40 * px))
        assertTrue(transition.scrimAlpha < down.first, "fades further as the drag grows")
        assertNotNull(transition.frame(page))
    }

    @Test
    fun shortReleaseSpringsBack() = runComposeUiTest {
        setUp()
        transition.track(page)
        assertNotNull(registry.hidden, "the thumbnail hides while its image is lifted")
        transition.dragBy(Offset(0f, 20 * px))
        transition.release(Offset.Zero)
        assertFalse(transition.isDismissing)
        settleAnimations()
        assertNull(transition.frame(page))
        assertNull(registry.hidden)
        assertEquals(1f, transition.scrimAlpha)
        assertEquals(0, dismissed)
    }

    @Test
    fun farReleaseFliesIntoTheThumbnailThenDismisses() = runComposeUiTest {
        setUp()
        transition.track(page)
        transition.dragBy(Offset(0f, 200 * px))
        transition.release(Offset.Zero)
        assertTrue(transition.isDismissing)
        assertNotNull(registry.hidden)

        mainClock.advanceTimeBy(FRAME_MILLIS * 3)
        assertEquals(0, dismissed, "the viewer stays until the image lands")

        settleAnimations()
        assertEquals(1, dismissed)
        assertNull(registry.hidden)
    }

    @Test
    fun withoutAThumbnailTheImageFadesOutThenDismisses() = runComposeUiTest {
        setUp(thumbnail = false)
        transition.dismiss(page)
        assertTrue(transition.isDismissing)
        assertNull(registry.hidden)
        mainClock.advanceTimeBy(FRAME_MILLIS * 3)
        assertEquals(0, dismissed)
        settleAnimations()
        assertEquals(1, dismissed)
        assertEquals(0f, assertNotNull(transition.frame(page)).alpha, 0.01f)
    }

    @Test
    fun dismissWithNoPageDismissesAtOnce() = runComposeUiTest {
        setUp()
        transition.dismiss(null)
        assertEquals(1, dismissed)
    }

    @Test
    fun backGestureDimsTheScrimOnlyPartway() = runComposeUiTest {
        setUp()
        transition.track(page)
        transition.back(0.5f)
        assertEquals(0f, transition.chromeAlpha, 0.001f)
        transition.back(1f)
        assertEquals(MIN_BACK_SCRIM, transition.scrimAlpha, 0.001f)
    }

    @Test
    fun aFlightIsNotRestartedOrCancelledByFurtherInput() = runComposeUiTest {
        // A track() that got through would cancel the flight, which then never lands; a second
        // dismiss() would restart it. Either way the viewer would not be gone when this one lands.
        setUp()
        // Time an undisturbed flight first, then reopen the viewer (a fresh transition) for the real one.
        transition.dismiss(page)
        var frames = 0
        while (dismissed == 0 && frames < MAX_FLIGHT_FRAMES) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        assertEquals(1, dismissed, "a flight lands")
        showViewer = false
        mainClock.advanceTimeByFrame()
        showViewer = true
        mainClock.advanceTimeByFrame()

        transition.dismiss(page)
        repeat(frames / 2) { mainClock.advanceTimeByFrame() }
        transition.track(page)
        transition.dismiss(page)
        repeat(frames - frames / 2 + 2) { mainClock.advanceTimeByFrame() }
        assertEquals(2, dismissed, "the second flight lands on time")
    }

    @Test
    fun leavingMidFlightStillDismissesOnce() = runComposeUiTest {
        setUp()
        transition.dismiss(page)
        mainClock.advanceTimeBy(FRAME_MILLIS * 3)
        showViewer = false
        mainClock.advanceTimeByFrame()
        assertEquals(1, dismissed)
        assertNull(registry.hidden)
        settleAnimations()
        assertEquals(1, dismissed)
    }

    @Test
    fun leavingAfterLandingDoesNotDismissAgain() = runComposeUiTest {
        setUp()
        transition.dismiss(page)
        settleAnimations()
        assertEquals(1, dismissed)
        showViewer = false
        mainClock.advanceTimeByFrame()
        assertEquals(1, dismissed)
    }

    @Test
    fun leavingAtRestDoesNotDismiss() = runComposeUiTest {
        setUp()
        showViewer = false
        mainClock.advanceTimeByFrame()
        assertEquals(0, dismissed)
    }

    private companion object {
        const val URL = "https://example.com/image.png"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 3_000L
        const val MAX_FLIGHT_FRAMES = 300

        /** The scrim at the end of a back gesture (MIN_BACK_SCRIM in MediaDismissTransition). */
        const val MIN_BACK_SCRIM = 0.6f
    }
}
