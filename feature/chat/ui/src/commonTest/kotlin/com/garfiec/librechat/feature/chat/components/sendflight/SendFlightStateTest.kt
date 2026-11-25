package com.garfiec.librechat.feature.chat.components.sendflight

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
    private fun user(id: String, text: String) = Message(messageId = id, conversationId = "c", text = text, isCreatedByUser = true)

    /** A state whose thread ends with [tail], and a send of "hello" being animated. */
    private fun sending(tail: Message? = user("old", "earlier")): SendFlightState = SendFlightState().apply {
        observeTail(tail, slot = "s0")
        start("hello")
    }

    @Test
    fun theSentRowIsANewUserTailWithTheSameWords() {
        val sent = user("new", "  hello there \n")
        assertTrue(isSentRow("hello there", knownTailId = "old", tail = sent))
        assertFalse(isSentRow("hello there", knownTailId = "new", tail = sent), "the row that was already the tail")
        assertFalse(isSentRow("hello", knownTailId = "old", tail = sent), "other words")
        assertFalse(isSentRow("hello there", knownTailId = "old", tail = sent.copy(isCreatedByUser = false)))
        assertFalse(isSentRow("hello there", knownTailId = "old", tail = null))
    }

    @Test
    fun aSendWithNoTextIsNotAnimated() {
        val state = SendFlightState()
        state.start(" \n ")
        assertNull(state.flight)
    }

    @Test
    fun theNewTailWithTheSentWordsIsClaimed() {
        val state = sending()
        state.observeTail(user("new", "hello"), slot = "s1")
        assertEquals("new", state.flight?.messageId)
    }

    @Test
    fun theRowThatWasAlreadyTheTailIsNotClaimed() {
        val state = sending(tail = user("old", "hello"))
        state.observeTail(user("old", "hello"), slot = "s0")
        assertNull(state.flight?.messageId)
    }

    @Test
    fun aRowLaterThanTheClaimWindowEndsTheAnimation() {
        val state = SendFlightState()
        state.start("hello", capturedAt = TimeSource.Monotonic.markNow() - 1.seconds)
        state.observeTail(user("new", "hello"), slot = "s1")
        assertNull(state.flight)
        assertFalse(state.holdsReply)
        assertEquals(1f, state.messageFraction("new"))
    }

    @Test
    fun theServerCopyInTheSameSlotTakesOverTheClaim() {
        val state = sending()
        state.observeTail(user("optimistic", "hello"), slot = "s1")
        state.observeTail(user("server", "hello"), slot = "s1")
        assertEquals("server", state.flight?.messageId)
        state.observeTail(user("elsewhere", "hello"), slot = "s2")
        assertEquals("server", state.flight?.messageId, "another slot is another message")
    }

    @Test
    fun theSentTurnStartsHiddenAndShowsWhenTheAnimationEnds() {
        val state = sending()
        assertEquals(0f, state.replyHeaderFraction(), "the reply never shows ahead of the message")
        state.observeTail(user("new", "hello"), slot = "s1")
        assertTrue(state.holdsReply)
        assertEquals(0f, state.nameFraction("new"))
        assertEquals(0f, state.messageFraction("new"))
        assertEquals(0f, state.replyHeaderFraction())
        assertEquals(0f, state.replyBodyFraction())
        assertEquals(1f, state.nameFraction("old"), "earlier rows are untouched")
        assertEquals(1f, state.messageFraction("old"))

        state.end(assertNotNull(state.flight))
        assertFalse(state.holdsReply)
        assertEquals(1f, state.nameFraction("new"))
        assertEquals(1f, state.messageFraction("new"))
        assertEquals(1f, state.replyHeaderFraction())
        assertEquals(1f, state.replyBodyFraction())
    }

    @Test
    fun theReplyIsLetInWhenItsStepStarts() {
        val state = sending()
        state.observeTail(user("new", "hello"), slot = "s1")
        assertNotNull(state.flight).replyBodyShown = true
        assertFalse(state.holdsReply)
    }

    @Test
    fun endingAnEarlierSendLeavesTheCurrentOne() {
        val state = sending()
        val earlier = assertNotNull(state.flight)
        state.start("again")
        state.end(earlier)
        assertEquals("again", state.flight?.text)
    }
}
