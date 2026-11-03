package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests the in-memory final-frame merge — used by every finalize (and the only display driver
 * for temporary chats, which never touch Room). The load-bearing property is monotonicity via
 * [mergedOver]: applying a possibly-skeletal server frame over a richer local copy must
 * gap-fill, never downgrade.
 */
class TemporaryChatMergeTest {

    private fun message(id: String, parentId: String? = null, text: String = "t-$id", isUser: Boolean = false) =
        Message(
            messageId = id,
            conversationId = "conv-1",
            parentMessageId = parentId,
            text = text,
            isCreatedByUser = isUser,
        )

    @Test
    fun `replaces optimistic user message by id and appends the response`() {
        val existing = listOf(message("u1", text = "hi", isUser = true))
        // Final event returns the canonical request (same id) + a new response.
        val finalMessages = listOf(
            message("u1", text = "hi", isUser = true),
            message("a1", parentId = "u1", text = "hello there"),
        )

        val merged = mergeFinalMessagesInMemory(existing, finalMessages)

        assertThat(merged.map { it.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(merged.last().text).isEqualTo("hello there")
    }

    @Test
    fun `updates an existing message in place without reordering`() {
        val existing = listOf(message("u1", isUser = true), message("a1", text = "partial"))
        val finalMessages = listOf(message("a1", text = "complete"))

        val merged = mergeFinalMessagesInMemory(existing, finalMessages)

        assertThat(merged.map { it.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(merged.first { it.messageId == "a1" }.text).isEqualTo("complete")
    }

    @Test
    fun `preserves prior turns and appends new ones`() {
        val existing = listOf(message("u1", isUser = true), message("a1"))
        val finalMessages = listOf(message("u2", isUser = true), message("a2"))

        val merged = mergeFinalMessagesInMemory(existing, finalMessages)

        assertThat(merged.map { it.messageId }).containsExactly("u1", "a1", "u2", "a2").inOrder()
    }

    // ---- mergedOver: the monotonic field rules ----

    /**
     * The finding this rule exists for: an aborted frame's requestMessage is skeletal
     * (id/parent/text/quotes only). Folding it over the optimistic user message must keep the
     * local attachments, files, sender, and createdAt instead of stripping them.
     */
    @Test
    fun `a skeletal incoming request keeps the local rich fields`() {
        val optimistic = message("u1", text = "hi", isUser = true).copy(
            attachments = listOf(Attachment(fileId = "f1")),
            files = listOf(FileReference(fileId = "f1")),
            sender = "User",
            createdAt = "2026-07-20T00:00:00Z",
        )
        val skeletal = message("u1", text = "hi", isUser = true).copy(quotes = listOf("quoted"))

        val merged = skeletal.mergedOver(optimistic)

        assertThat(merged.attachments).isEqualTo(optimistic.attachments)
        assertThat(merged.files).isEqualTo(optimistic.files)
        assertThat(merged.sender).isEqualTo("User")
        assertThat(merged.createdAt).isEqualTo("2026-07-20T00:00:00Z")
        // Incoming information still wins where it exists.
        assertThat(merged.quotes).containsExactly("quoted")
    }

    @Test
    fun `non-blank incoming text wins and blank incoming text keeps local`() {
        val local = message("a1", text = "streamed partial")

        assertThat(message("a1", text = "server text").mergedOver(local).text)
            .isEqualTo("server text")
        assertThat(message("a1", text = "").mergedOver(local).text)
            .isEqualTo("streamed partial")
    }

    /**
     * error/unfinished are server truth about the turn's terminal state: an aborted frame's
     * `unfinished = true` must win over the local default even though `false` looks "richer".
     */
    @Test
    fun `terminal-state booleans always take the incoming value`() {
        val local = message("a1").copy(unfinished = false, error = true)
        val incoming = message("a1").copy(unfinished = true, error = false)

        val merged = incoming.mergedOver(local)

        assertThat(merged.unfinished).isTrue()
        assertThat(merged.error).isFalse()
    }

    @Test
    fun `incoming content wins over local content when present`() {
        val local = message("a1").copy(
            content = listOf(MessageContentPart(type = ContentType.TEXT, text = "old")),
        )
        val serverParts = listOf(MessageContentPart(type = ContentType.TEXT, text = "new"))

        assertThat(message("a1").copy(content = serverParts).mergedOver(local).content)
            .isEqualTo(serverParts)
        assertThat(message("a1").mergedOver(local).content).isEqualTo(local.content)
    }

    /** The full-list merge applies mergedOver for existing ids — not a wholesale replace. */
    @Test
    fun `merging a final frame gap-fills the existing message instead of replacing it`() {
        val optimistic = message("u1", text = "hi", isUser = true).copy(
            attachments = listOf(Attachment(fileId = "f1")),
        )
        val skeletal = message("u1", text = "hi", isUser = true)

        val merged = mergeFinalMessagesInMemory(listOf(optimistic), listOf(skeletal))

        assertThat(merged.single().attachments).isEqualTo(optimistic.attachments)
    }

    /**
     * A stopped turn's frame carries a skeletal request under the id the SERVER minted, not the
     * client's. It must fold over the optimistic copy in its slot, or Continue replays the skeleton
     * and loses the turn's reasoning choice, files and skills.
     */
    @Test
    fun `a request under a server-minted id folds over this turn's optimistic message in its slot`() {
        val override = ReasoningOverride.level("reasoning_effort", "medium")
        val root = message("a0", text = "earlier reply")
        val optimistic = message("client-u1", parentId = "a0", text = "think", isUser = true).copy(
            reasoningOverride = override,
            files = listOf(FileReference(fileId = "f1")),
            manualSkills = listOf("skill-1"),
        )
        val skeletal = message("server-u1", parentId = "a0", text = "think", isUser = true)
        val partial = message("a1", parentId = "server-u1", text = "partial").copy(unfinished = true)

        val merged = mergeFinalMessagesInMemory(listOf(root, optimistic), listOf(skeletal, partial), "client-u1")

        assertThat(merged.map { it.messageId }).containsExactly("a0", "server-u1", "a1").inOrder()
        val request = merged[1]
        assertThat(request.reasoningOverride).isEqualTo(override)
        assertThat(request.files).isEqualTo(optimistic.files)
        assertThat(request.manualSkills).containsExactly("skill-1")
        assertThat(request.parentMessageId).isEqualTo("a0")
    }

    @Test
    fun `a request that is not this turn's copy is appended, leaving the optimistic message alone`() {
        val optimistic = message("client-u1", text = "think", isUser = true)
        val other = message("server-u2", text = "something else", isUser = true)

        val merged = mergeFinalMessagesInMemory(listOf(optimistic), listOf(other), "client-u1")

        assertThat(merged.map { it.messageId }).containsExactly("client-u1", "server-u2").inOrder()
    }
}
