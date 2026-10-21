package com.garfiec.librechat.feature.chat.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScrollSyncMappingTest {

    private val viewportHeight = 1000f
    private val padding = 20f

    /**
     * A list of items with [sizes], scrolled [scroll] px from its top, laid out as LazyColumn does:
     * the viewport runs from -padding, item 0 sits at 0 when unscrolled, and only items
     * intersecting the viewport are reported.
     */
    private fun viewport(sizes: List<Float>, scroll: Float, measured: Boolean = true): SyncViewport {
        val start = -padding
        val end = viewportHeight - padding
        var top = -scroll
        val items = sizes.mapIndexed { i, size -> SyncItem(i, top, size).also { top += size } }
            .filter { it.end > start && it.offset < end }
        val first = items.first().index
        val last = items.last().index
        return if (measured) {
            SyncViewport(
                start,
                end,
                afterContentPadding = padding,
                items = items,
                totalItemsCount = sizes.size,
                heightAbove = sizes.take(first).sum(),
                heightBelow = sizes.drop(last + 1).sum(),
            )
        } else {
            SyncViewport(start, end, afterContentPadding = padding, items = items, totalItemsCount = sizes.size)
        }
    }

    private fun maxScroll(sizes: List<Float>) = maxOf(0f, sizes.sum() + 2 * padding - viewportHeight)

    /** Applies [followerMove] until it settles, resolving jumps as LazyColumn's scrollToItem would. */
    private fun settle(
        masterSizes: List<Float>,
        masterScroll: Float,
        followerSizes: List<Float>,
        start: Float,
        masterDirection: Int = 0,
    ): Float {
        var scroll = start
        val master = viewport(masterSizes, masterScroll)
        repeat(4) {
            when (val move = followerMove(master, viewport(followerSizes, scroll), masterDirection)) {
                is FollowerMove.By -> scroll += move.px
                is FollowerMove.ToItem -> scroll = followerSizes.take(move.index).sum() +
                    move.scrollOffsetPx.coerceAtMost(1_000_000)
                FollowerMove.None -> return scroll.coerceIn(0f, maxScroll(followerSizes))
            }
            scroll = scroll.coerceIn(0f, maxScroll(followerSizes))
        }
        return scroll
    }

    @Test
    fun syncpointRampsFromTopThroughTheReadingLineToEnd() {
        val line = READING_LINE
        val above = line * viewportHeight
        val below = viewportHeight - above
        assertEquals(0f, syncpoint(0f, null, viewportHeight), 0.001f)
        assertEquals(line / 2, syncpoint(above / 2, null, viewportHeight), 0.001f)
        assertEquals(line, syncpoint(above, null, viewportHeight), 0.001f)
        assertEquals(line, syncpoint(null, null, viewportHeight), 0.001f)
        assertEquals(line + (1 - line) / 2, syncpoint(null, below / 2, viewportHeight), 0.001f)
        assertEquals(1f, syncpoint(null, 0f, viewportHeight), 0.001f)
        assertEquals(line, syncpoint(null, below, viewportHeight), 0.001f)
    }

    @Test
    fun identicalLayoutsNeedNoMove() {
        val sizes = listOf(300f, 800f, 200f, 900f)
        val v = viewport(sizes, 400f)
        assertEquals(FollowerMove.None, followerMove(v, v))
    }

    @Test
    fun aTallerFollowerReplyMovesFasterInProportion() {
        // A shared 1500px turn, then the parallel reply: 300px in the master, 2000px in the follower.
        val master = listOf(1500f, 300f, 400f, 3000f)
        val follower = listOf(1500f, 2000f, 400f, 3000f)
        // Master's reading line 1/3 and 2/3 of the way through its reply.
        val a = settle(master, 1500f + 100f - READING_LINE * viewportHeight + padding, follower, 0f)
        val b = settle(master, 1500f + 200f - READING_LINE * viewportHeight + padding, follower, 0f)
        val masterStep = 100f
        val followerStep = b - a
        assertEquals(2000f / 300f, followerStep / masterStep, 0.05f)
    }

    @Test
    fun bothTopsAndBothEndsAlign() {
        val master = listOf(200f, 600f, 200f, 4000f)
        val follower = listOf(200f, 1500f, 200f, 800f)
        assertEquals(0f, settle(master, 0f, follower, 700f), 1f)
        assertEquals(maxScroll(follower), settle(master, maxScroll(master), follower, 0f), 1f)
    }

    @Test
    fun anOffscreenFollowerItemIsJumpedToThenRefined() {
        val master = listOf(500f, 500f, 500f, 500f, 500f, 500f)
        val follower = listOf(500f, 500f, 500f, 500f, 500f, 500f)
        val move = followerMove(viewport(master, 1500f), viewport(follower, 0f))
        assertIs<FollowerMove.ToItem>(move)
        assertEquals(1500f, settle(master, 1500f, follower, 0f), 1f)
    }

    @Test
    fun aMasterPointPastTheFollowersItemsMapsToItsEnd() {
        // Streaming tails differ in item count: the master's tool card has no follower counterpart.
        val master = listOf(400f, 300f, 300f)
        val follower = listOf(400f, 500f)
        assertEquals(maxScroll(follower), settle(master, maxScroll(master), follower, 0f), 1f)
    }

    @Test
    fun aZeroHeightItemDoesNotBreakTheMapping() {
        val sizes = listOf(400f, 0f, 2000f)
        assertEquals(500f, settle(sizes, 500f, sizes, 0f), 1f)
    }

    @Test
    fun theFollowerNeverMovesBackwardAsTheMasterScrollsForward() {
        // Without the direction guard, a follower region under half the master's creeps back in the end ramp.
        val random = Random(7)
        repeat(30) {
            val count = random.nextInt(2, 8)
            val master = List(count) { random.nextInt(50, 2500).toFloat() }
            val follower = master.mapIndexed { i, size -> if (i % 2 == 1) random.nextInt(50, 2500).toFloat() else size }
            var previous = 0f
            var scroll = 0f
            while (scroll <= maxScroll(master)) {
                val position = settle(master, scroll, follower, previous, masterDirection = 1)
                assertTrue(position >= previous - 1f, "moved back from $previous to $position at master $scroll")
                previous = position
                scroll += 37f
            }
        }
    }

    @Test
    fun aShortFirstMessageScrollingOffDoesNotJumpTheOtherPane() {
        // A one-line prompt, then replies of 982px and 1691px.
        val lead = listOf(136f, 982f, 136f, 1456f, 136f, 1172f)
        val follower = listOf(136f, 1691f, 136f, 2002f, 136f, 1882f)
        var previous = 0f
        var scroll = 0f
        while (scroll <= 600f) {
            val position = settle(lead, scroll, follower, previous)
            // Faster than the reply ratio would allow, at any step, is a jump.
            assertTrue(position - previous <= 4f * 6f, "jumped ${position - previous}px at lead $scroll")
            previous = position
            scroll += 4f
        }
    }

    @Test
    fun unmeasuredHeightsAboveReadAsFarFromTheTop() {
        val sizes = listOf(136f, 2000f, 2000f)
        assertEquals(null, viewport(sizes, 300f, measured = false).distanceFromTop())
        assertEquals(300f, viewport(sizes, 300f).distanceFromTop())
    }

    @Test
    fun aJumpLandsItsTargetOnScreenEvenWhenTheFollowersCopyIsShorter() {
        // The lead's reply is far taller than the follower's; the follower sits at its top.
        val lead = listOf(136f, 6000f, 136f, 3000f)
        val follower = listOf(136f, 400f, 136f, 3000f)
        val leadView = viewport(lead, 136f + 5000f)
        val jump = followerMove(leadView, viewport(follower, 3000f + 272f + 400f))
        assertIs<FollowerMove.ToItem>(jump)
        // Unmeasured, it lands the item at the top; a guess from the lead's 6000px copy would put
        // the follower's 400px one far above the viewport, and the next pass would jump again.
        val landed = follower.take(jump.index).sum() + jump.scrollOffsetPx
        val next = followerMove(leadView, viewport(follower, landed))
        assertTrue(next !is FollowerMove.ToItem, "jumped again: $next")
    }

    @Test
    fun bothLastLinesRestTogetherAboveTheReadingRoom() {
        val room = 500f
        fun view(sizes: List<Float>, scroll: Float): SyncViewport {
            val plain = viewport(sizes, scroll)
            return plain.copy(afterContentPadding = padding + room, extraEnd = room)
        }

        // Where scroll-to-bottom rests: the end, less the room.
        fun rest(sizes: List<Float>) = sizes.sum() + 2 * padding - viewportHeight
        val lead = listOf(300f, 2400f, 300f, 1800f)
        val follower = listOf(300f, 1400f, 300f, 900f)

        val move = followerMove(view(lead, rest(lead)), view(follower, rest(follower)))

        assertEquals(FollowerMove.None, move)
    }
}
