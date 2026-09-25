package com.garfiec.librechat.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The wire shape of a manual compaction (v0.8.8-rc3).
 *
 * Every field here is load-bearing and none of them is obvious from the call site: the turn is
 * regenerate-shaped with no user message, so `messageId` and `parentMessageId` are BOTH the branch
 * leaf — the summary's parent and the anchor the server compacts up to.
 *
 * `isRegenerate` is where the shape stops. It is a CLIENT-side value — it is what routes the send
 * down the regenerate path and keeps the leaf out of the early-abort un-send — and it must not
 * reach the wire beside `compact`: `getCompactionRejection` answers 400
 * `INVALID_COMPACTION_REQUEST` ("Compaction cannot be combined with an edit, regenerate, or
 * continue") for exactly that pair, so a compaction carrying it can never succeed against any
 * server. Upstream drops it in the same place, `createPayload`.
 */
class ChatPayloadCompactTest {

    private fun compactRequest() = ChatPayloadBuilder.build(
        text = "",
        conversationId = "conv-1",
        endpoint = "openAI",
        model = "gpt-4o",
        userMessageId = "leaf-9",
        parentMessageId = "leaf-9",
        isRegenerate = true,
        compact = true,
    )

    @Test
    fun `a compaction anchors both ids to the branch leaf`() {
        val request = compactRequest()
        assertThat(request.messageId).isEqualTo("leaf-9")
        assertThat(request.parentMessageId).isEqualTo("leaf-9")
        assertThat(request.text).isEmpty()
    }

    @Test
    fun `a compaction drops every flag the rejection checks, even one the caller asked for`() {
        val request = compactRequest()
        assertThat(request.compact).isTrue()
        // The caller passes isRegenerate = true — the local shape — and the builder drops it,
        // because `compact` plus ANY of isRegenerate / isContinued / editedContent /
        // responseMessageId is a 400 the user sees as a Compact button that does nothing.
        assertThat(request.isRegenerate).isFalse()
        assertThat(request.isContinued).isFalse()
        assertThat(request.responseMessageId).isNull()
        // Upstream sets overrideParentMessageId only for a REAL regenerate; a compaction leaves it
        // null, so the summary parents onto the leaf rather than replacing a sibling.
        assertThat(request.overrideParentMessageId).isNull()
    }

    @Test
    fun `an ordinary regenerate still carries the flag`() {
        val regenerate = ChatPayloadBuilder.build(
            text = "",
            conversationId = "conv-1",
            endpoint = "openAI",
            model = "gpt-4o",
            parentMessageId = "leaf-9",
            isRegenerate = true,
        )
        assertThat(regenerate.isRegenerate).isTrue()
        assertThat(regenerate.compact).isNull()
    }

    @Test
    fun `an ordinary turn omits the flag rather than sending false`() {
        // A server that does not know `compact` would answer a `false` as an ordinary turn; the
        // field is nullable so it never reaches the wire unless a compaction asked for it.
        val ordinary = ChatPayloadBuilder.build(
            text = "hello",
            conversationId = "conv-1",
            endpoint = "openAI",
            model = "gpt-4o",
            parentMessageId = "leaf-9",
        )
        assertThat(ordinary.compact).isNull()
    }
}
