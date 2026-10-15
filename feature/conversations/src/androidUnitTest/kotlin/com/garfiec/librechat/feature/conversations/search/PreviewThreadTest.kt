package com.garfiec.librechat.feature.conversations.search

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewThreadTest {

    private fun message(id: String, parent: String?, at: String) =
        Message(messageId = id, conversationId = "c", parentMessageId = parent, createdAt = at)

    @Test
    fun `the thread runs from the first message through the target to the newest reply`() {
        val messages = listOf(
            message("a", NO_PARENT, "2026-01-01T00:00:00Z"),
            message("b", "a", "2026-01-01T00:01:00Z"),
            message("c", "b", "2026-01-01T00:02:00Z"),
            message("d", "c", "2026-01-01T00:03:00Z"),
        )

        assertThat(PreviewThread.of(messages, "b").map { it.messageId }).containsExactly("a", "b", "c", "d").inOrder()
    }

    @Test
    fun `below the target each step takes the newest reply`() {
        val messages = listOf(
            message("q", NO_PARENT, "2026-01-01T00:00:00Z"),
            message("old", "q", "2026-01-01T00:01:00Z"),
            message("new", "q", "2026-01-01T00:05:00Z"),
            message("under-old", "old", "2026-01-01T00:02:00Z"),
        )

        assertThat(PreviewThread.of(messages, "q").map { it.messageId }).containsExactly("q", "new").inOrder()
        // A target on an older branch keeps its own branch above and below.
        assertThat(PreviewThread.of(messages, "old").map { it.messageId }).containsExactly("q", "old", "under-old").inOrder()
    }

    @Test
    fun `a parent missing from the cache starts the thread`() {
        val messages = listOf(
            message("b", "not-cached", "2026-01-01T00:01:00Z"),
            message("c", "b", "2026-01-01T00:02:00Z"),
        )

        assertThat(PreviewThread.of(messages, "c").map { it.messageId }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `a missing target gives no thread`() {
        assertThat(PreviewThread.of(listOf(message("a", NO_PARENT, "2026-01-01T00:00:00Z")), "zzz")).isEmpty()
    }

    @Test
    fun `a parent cycle ends the walk instead of looping`() {
        val messages = listOf(
            message("a", "b", "2026-01-01T00:00:00Z"),
            message("b", "a", "2026-01-01T00:01:00Z"),
        )

        assertThat(PreviewThread.of(messages, "a").map { it.messageId }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `the latest thread is the one through the newest message`() {
        val messages = listOf(
            message("q", NO_PARENT, "2026-01-01T00:00:00Z"),
            message("x", "q", "2026-01-01T00:09:00Z"),
            message("y", "q", "2026-01-01T00:01:00Z"),
        )

        assertThat(PreviewThread.latest(messages).map { it.messageId }).containsExactly("q", "x").inOrder()
    }
}
