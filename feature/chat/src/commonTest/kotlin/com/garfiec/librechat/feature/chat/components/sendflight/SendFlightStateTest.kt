package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.ui.geometry.Rect
import com.garfiec.librechat.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SendFlightStateTest {
    private val rect = Rect(0f, 0f, 100f, 40f)
    private fun user(id: String, text: String) = Message(messageId = id, conversationId = "c", text = text, isCreatedByUser = true)

    /** A state whose thread ends with [tail], and a flight of "hello" in the air. */
    private fun flying(tail: Message? = user("old", "earlier")): SendFlightState = SendFlightState().apply {
        observeTail(tail, slot = "s0")
        start("hello", rect, rect)
    }

    @Test
    fun theNewTailWithTheSentWordsIsClaimed() {
        val state = flying()
        state.observeTail(user("new", "hello"), slot = "s1")
        assertEquals("new", state.flight?.messageId)
    }

    @Test
    fun theRowThatWasAlreadyTheTailIsNotClaimed() {
        val state = flying(tail = user("old", "hello"))
        state.observeTail(user("old", "hello"), slot = "s0")
        assertNull(state.flight?.messageId)
    }

    @Test
    fun aRowLaterThanTheClaimWindowEndsTheFlight() {
        val state = SendFlightState()
        state.start("hello", rect, rect, capturedAt = TimeSource.Monotonic.markNow() - 1.seconds)
        state.observeTail(user("new", "hello"), slot = "s1")
        assertNull(state.flight)
        assertFalse(state.holdsReply)
    }

    @Test
    fun theServerCopyInTheSameSlotTakesOverTheClaim() {
        val state = flying()
        state.observeTail(user("optimistic", "hello"), slot = "s1")
        state.observeTail(user("server", "hello"), slot = "s1")
        assertEquals("server", state.flight?.messageId)
        state.observeTail(user("elsewhere", "hello"), slot = "s2")
        assertEquals("server", state.flight?.messageId, "another slot is another message")
    }

    @Test
    fun onlyTheClaimedRowIsHiddenAndOnlyWhileItFlies() {
        val state = flying()
        assertEquals(1f, state.revealFraction(), "nothing is held back before the row is claimed")
        state.observeTail(user("new", "hello"), slot = "s1")
        assertTrue(state.holdsReply)
        assertEquals(0f, state.revealFraction(), "the reply waits until the message lands")
        assertEquals(0f, state.bodyFraction("new"))
        assertEquals(0f, state.headerFraction("new"), "the sender line comes in with the drop")
        assertEquals(1f, state.bodyFraction("old"))
        assertEquals(1f, state.headerFraction("old"))

        state.end(assertNotNull(state.flight))
        assertFalse(state.holdsReply)
        assertEquals(1f, state.revealFraction())
        assertEquals(1f, state.bodyFraction("new"))
        assertEquals(1f, state.headerFraction("new"))
    }

    @Test
    fun endingAnEarlierFlightLeavesTheCurrentOne() {
        val state = flying()
        val earlier = assertNotNull(state.flight)
        state.start("again", rect, rect)
        state.end(earlier)
        assertEquals("again", state.flight?.text)
    }
}
