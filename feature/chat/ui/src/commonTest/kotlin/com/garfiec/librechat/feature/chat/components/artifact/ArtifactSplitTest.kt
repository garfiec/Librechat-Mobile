package com.garfiec.librechat.feature.chat.components.artifact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArtifactSplitTest {
    // A 1024-wide window less a 24 gutter; panes at least 320, detents within 16, overdrag 96.
    private val available = 1000f
    private val minPane = 320f

    private fun release(paneWidth: Float) =
        splitRelease(paneWidth, available, minPane, snap = 16f, overdrag = 96f)

    @Test
    fun paneWidth_keepsBothPanesAtTheirMinimum() {
        assertEquals(500f, splitPaneWidth(available, 0.5f, minPane))
        assertEquals(320f, splitPaneWidth(available, 0.1f, minPane))
        assertEquals(680f, splitPaneWidth(available, 0.9f, minPane))
    }

    @Test
    fun paneWidth_inAWindowTooNarrowForBoth_keepsTheArtifactsMinimum() {
        assertEquals(320f, splitPaneWidth(600f, 0.5f, minPane))
    }

    @Test
    fun release_nearADetent_settlesOnIt() {
        assertEquals(SplitRelease.Settle(0.5f), release(510f))
        assertEquals(SplitRelease.Settle(2f / 3f), release(655f))
    }

    @Test
    fun release_awayFromDetents_staysWhereLetGo() {
        assertEquals(SplitRelease.Settle(0.42f), release(420f))
    }

    @Test
    fun release_aDetentOutsideTheMinimums_isSkipped() {
        // A third of 1000 is 333: inside the minimum, so it still applies.
        assertEquals(SplitRelease.Settle(1f / 3f), release(325f))
        // In a narrow split, a third would leave the artifact under its minimum: stays at the minimum.
        val narrow = splitRelease(320f, 700f, minPane, snap = 16f, overdrag = 96f)
        assertEquals(SplitRelease.Settle(320f / 700f), narrow)
    }

    @Test
    fun release_pastAMinimumButShortOfTheOverdrag_holdsAtTheMinimum() {
        // 1200 wide: detents at 400, 600 and 800, none near either minimum.
        fun wide(paneWidth: Float) = splitRelease(paneWidth, 1200f, minPane, snap = 16f, overdrag = 96f)
        assertEquals(SplitRelease.Settle(320f / 1200f), wide(240f))
        assertEquals(SplitRelease.Settle(880f / 1200f), wide(960f))
    }

    @Test
    fun release_overdraggedPastTheArtifactsMinimum_closes() {
        assertIs<SplitRelease.Close>(release(224f))
    }

    @Test
    fun release_overdraggedPastTheChatsMinimum_fillsTheWindow() {
        assertIs<SplitRelease.FullScreen>(release(776f))
    }

    @Test
    fun step_movesBetweenDetents_andStopsAtTheEnds() {
        assertEquals(2f / 3f, stepSplitDetent(0.5f, wider = true))
        assertEquals(1f / 3f, stepSplitDetent(0.5f, wider = false))
        assertEquals(0.5f, stepSplitDetent(0.42f, wider = true))
        assertEquals(1f / 3f, stepSplitDetent(0.42f, wider = false))
        assertEquals(2f / 3f, stepSplitDetent(2f / 3f, wider = true))
        assertEquals(1f / 3f, stepSplitDetent(1f / 3f, wider = false))
    }
}
