package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.request.NO_PARENT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.8.9-rc1's `validateChatMessageIds` 400s (`INVALID_MESSAGE_ID`) any turn id that is present but
 * not 1-256 characters of `[A-Za-z0-9_:.-]`. An empty string counts as present.
 */
class ChatPayloadMessageIdTest {

    /** Upstream `isValidMessageId`, by hand rather than as a Regex. */
    private fun isValidMessageId(id: String, maxLength: Int = 256): Boolean =
        id.isNotEmpty() && id.length <= maxLength &&
            id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == ':' || it == '.' || it == '-' }

    @Test
    fun blankIdsAreSentAsAbsent() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = "c-1",
            endpoint = "agents",
            model = "agent_1",
            userMessageId = "",
            parentMessageId = " ",
            overrideParentMessageId = "",
            responseMessageId = "",
        )

        assertNull(req.messageId)
        assertEquals(NO_PARENT, req.parentMessageId)
        assertNull(req.overrideParentMessageId)
        assertNull(req.responseMessageId)
    }

    @Test
    fun theIdsMobileMintsPassTheServerRule() {
        val minted = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b"
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = "c-1",
            endpoint = "agents",
            model = "agent_1",
            userMessageId = minted,
            // The server's own synthesized abort response id: the request id plus `_`.
            responseMessageId = "${minted}_",
        )

        assertTrue(isValidMessageId(req.messageId!!))
        assertTrue(isValidMessageId(req.parentMessageId!!))
        assertTrue(isValidMessageId(req.responseMessageId!!, maxLength = 257))
    }
}
