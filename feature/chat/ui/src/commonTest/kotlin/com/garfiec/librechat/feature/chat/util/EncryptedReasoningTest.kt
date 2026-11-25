package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.components.countPartOccurrences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A provider's encrypted reasoning (a long base64 run in a THINK part, v0.8.9-rc1 #17100) is kept
 * out of everything that treats reasoning as prose: copy, find-in-page and the editor.
 */
class EncryptedReasoningTest {

    private val payload = "QUJD".repeat(64) // 256 chars of base64, no break

    private fun think(text: String) = MessageContentPart(type = ContentType.THINK, think = text)

    @Test
    fun copyKeepsOnlyTheReadableReasoning() {
        val message = Message(
            messageId = "m",
            conversationId = "c",
            content = listOf(
                think("Checking the policy.\n$payload"),
                MessageContentPart(type = ContentType.TEXT, text = "Done."),
            ),
        )

        val copied = serializeMessageForClipboard(message)

        assertFalse(payload in copied)
        assertTrue("Checking the policy." in copied)
    }

    @Test
    fun payloadOnlyReasoningCopiesNothing() {
        val message = Message(
            messageId = "m",
            conversationId = "c",
            content = listOf(think(payload), MessageContentPart(type = ContentType.TEXT, text = "Done.")),
        )

        assertEquals("Done.", serializeMessageForClipboard(message))
    }

    @Test
    fun findInPageCountsWhatTheCardShows() {
        assertEquals(0, countPartOccurrences(think("Plain. $payload"), "QUJD"))
        assertEquals(1, countPartOccurrences(think("Plain. $payload"), "Plain"))
    }

    @Test
    fun reasoningCarryingAPayloadIsNotEditable() {
        assertFalse(isEditablePart(think("Checking.\n$payload")))
        assertFalse(isEditablePart(think(payload)))
        assertTrue(isEditablePart(think("Checking the policy.")))
    }
}
