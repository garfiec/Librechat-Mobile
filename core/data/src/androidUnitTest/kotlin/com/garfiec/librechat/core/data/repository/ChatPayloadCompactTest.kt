package com.garfiec.librechat.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The wire shape of a manual compaction (v0.8.8-rc3).
 *
 * Every field here is load-bearing and none of them is obvious from the call site: the turn is
 * regenerate-shaped with no user message, so `messageId` and `parentMessageId` are BOTH the branch
 * leaf — the summary's parent and the anchor the server compacts up to — and `isRegenerate` rides
 * alongside `compact` because upstream's `ask` derives it as `isRegenerate || compact`.
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
    fun `a compaction is regenerate-shaped on the wire`() {
        val request = compactRequest()
        assertThat(request.compact).isTrue()
        assertThat(request.isRegenerate).isTrue()
        // Upstream sets overrideParentMessageId only for a REAL regenerate; a compaction leaves it
        // null, so the summary parents onto the leaf rather than replacing a sibling.
        assertThat(request.overrideParentMessageId).isNull()
        assertThat(request.isContinued).isFalse()
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
