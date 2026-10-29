package com.garfiec.librechat.feature.chat.components.veil

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The temporary-chat veil's geometry and timing: what decides when each surface switches. */
class ModeVeilTest {

    @Test
    fun `an element is crossed only once the band's peak is past it`() {
        assertThat(veilCrossed(distance = 100f, radius = 124f, peakLag = 24f)).isFalse()
        assertThat(veilCrossed(distance = 100f, radius = 124.5f, peakLag = 24f)).isTrue()
        assertThat(veilCrossed(distance = 0f, radius = 0f, peakLag = 24f)).isFalse()
    }

    @Test
    fun `a growing band only ever adds crossed elements, so a reversal can't strand one`() {
        val distances = listOf(0f, 40f, 180f, 600f, 1400f)
        var previous = distances.map { false }
        for (r in 0..1600 step 20) {
            val now = distances.map { veilCrossed(it, r.toFloat(), 24f) }
            previous.zip(now).forEach { (before, after) -> if (before) assertThat(after).isTrue() }
            previous = now
        }
    }

    @Test
    fun `only a user toggle sweeps, and never under reduced motion`() {
        assertThat(veilShouldSweep(userToggle = true, reduceMotion = false)).isTrue()
        assertThat(veilShouldSweep(userToggle = false, reduceMotion = false)).isFalse()
        assertThat(veilShouldSweep(userToggle = true, reduceMotion = true)).isFalse()
    }

    @Test
    fun `a reversed sweep runs only for what is left of it`() {
        assertThat(veilSweepMillis(from = 0f, on = true)).isEqualTo(560)
        assertThat(veilSweepMillis(from = 1f, on = false)).isEqualTo(420)
        assertThat(veilSweepMillis(from = 0.5f, on = false)).isEqualTo(210)
        assertThat(veilSweepMillis(from = 0.5f, on = true)).isEqualTo(280)
        assertThat(veilSweepMillis(from = 0.98f, on = true)).isEqualTo(160)
    }

    @Test
    fun `turning on reaches the heading before the composer, turning off the composer first`() {
        val maxRadius = 1200f
        val heading = 300f
        val composer = 900f
        val onHeading = veilArrivalMillis(heading, maxRadius, 24f, on = true)!!
        val onComposer = veilArrivalMillis(composer, maxRadius, 24f, on = true)!!
        val offHeading = veilArrivalMillis(heading, maxRadius, 24f, on = false)!!
        val offComposer = veilArrivalMillis(composer, maxRadius, 24f, on = false)!!
        assertThat(onHeading).isLessThan(onComposer)
        assertThat(offComposer).isLessThan(offHeading)
        assertThat(onComposer).isAtMost(560)
        assertThat(offHeading).isAtMost(420)
    }

    @Test
    fun `a point the band never reaches has no arrival`() {
        assertThat(veilArrivalMillis(distance = 2000f, maxRadius = 1200f, peakLag = 24f, on = true)).isNull()
    }

    @Test
    fun `the band reaches the screen's farthest corner from the toggle`() {
        val bounds = Rect(0f, 0f, 400f, 800f)
        assertThat(farthestCornerDistance(Offset(400f, 0f), bounds)).isWithin(0.01f).of(894.43f)
        assertThat(farthestCornerDistance(Offset(200f, 400f), bounds)).isWithin(0.01f).of(447.21f)
    }

    @Test
    fun `the nudge rises, overshoots a little past rest and settles`() {
        assertThat(nudgeCurve(0f)).isEqualTo(0f)
        assertThat(nudgeCurve(1f)).isEqualTo(0f)
        assertThat(nudgeCurve(0.3f)).isWithin(0.01f).of(-1f)
        assertThat(nudgeCurve(0.7f)).isWithin(0.01f).of(0.25f)
    }

    @Test
    fun `the band is a share of the sweep, so a bigger screen gets a wider band`() {
        val phone = ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = 1f).apply {
            rootBounds = Rect(0f, 0f, 400f, 900f)
            reportOrigin(Offset(380f, 40f))
        }
        val unfolded = ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = 1f).apply {
            rootBounds = Rect(0f, 0f, 850f, 880f)
            reportOrigin(Offset(830f, 40f))
        }
        assertThat(phone.bandTail / phone.sweepDistance).isWithin(0.001f).of(0.125f)
        assertThat(unfolded.bandTail).isGreaterThan(phone.bandTail)
        unfolded.glass = true
        assertThat(unfolded.bandTail / unfolded.sweepDistance).isWithin(0.001f).of(0.17f)
    }

    @Test
    fun `a small screen keeps the band's dp floor`() {
        val tiny = ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = 1f).apply {
            rootBounds = Rect(0f, 0f, 200f, 300f)
            reportOrigin(Offset(180f, 20f))
        }
        assertThat(tiny.bandTail).isEqualTo(72f)
    }

    @Test
    fun `a surface switches as the band's peak, inside the band, passes it`() {
        val veil = ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = 1f).apply {
            rootBounds = Rect(0f, 0f, 400f, 900f)
            reportOrigin(Offset(380f, 40f))
        }
        listOf(false, true).forEach { glass ->
            veil.glass = glass
            assertThat(veil.peakLag)
                .isEqualTo(bandPeakLag(veil.bandTail, veil.bandLead, veil.bandPeakAt))
            assertThat(veil.peakLag).isGreaterThan(0f)
            assertThat(veil.peakLag).isLessThan(veil.bandTail)
        }
    }

    @Test
    fun `before anything is measured an element follows the flag`() {
        val off = ModeVeilState(initiallyOn = false, reduceMotion = false, pxPerDp = 1f)
        val on = ModeVeilState(initiallyOn = true, reduceMotion = false, pxPerDp = 1f)
        assertThat(off.isCrossed(Offset(10f, 10f))).isFalse()
        assertThat(on.isCrossed(Offset(10f, 10f))).isTrue()
        assertThat(on.isCrossed(Offset.Unspecified)).isTrue()
    }

    @Test
    fun `at rest on, the whole screen is behind the band`() {
        val on = ModeVeilState(initiallyOn = true, reduceMotion = false, pxPerDp = 1f)
        on.rootBounds = Rect(0f, 0f, 400f, 800f)
        on.reportOrigin(Offset(380f, 40f))
        listOf(Offset(0f, 800f), Offset(380f, 40f), Offset(200f, 700f)).forEach {
            assertThat(on.isCrossed(it)).isTrue()
        }
    }
}
