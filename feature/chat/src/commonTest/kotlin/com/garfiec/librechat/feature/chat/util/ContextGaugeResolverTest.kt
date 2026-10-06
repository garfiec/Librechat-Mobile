package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ported behaviour of upstream web's snapshot hydration and snapshot-less estimate (v0.8.8). */
class ContextGaugeResolverTest {

    private fun snapshotBlob(remaining: Int, completed: Int? = null): JsonObject = buildJsonObject {
        putJsonObject("breakdown") {
            put("maxContextTokens", 100_000)
            put("instructionTokens", 4_000)
            put("messageTokens", 6_000)
        }
        put("remainingContextTokens", remaining)
        completed?.let { put("completedOutputTokens", it) }
    }

    private fun message(
        id: String,
        parent: String? = null,
        user: Boolean = false,
        text: String = "",
        tokenCount: Int? = null,
        metadata: JsonObject? = null,
        content: List<MessageContentPart>? = null,
        quotes: List<String>? = null,
    ) = Message(
        messageId = id,
        conversationId = "c",
        parentMessageId = parent,
        isCreatedByUser = user,
        text = text,
        tokenCount = tokenCount,
        metadata = metadata,
        content = content,
        quotes = quotes,
    )

    private fun withSnapshot(remaining: Int, completed: Int? = null) =
        JsonObject(mapOf("contextUsage" to snapshotBlob(remaining, completed)))

    private fun summarized(tokens: Int) = JsonObject(mapOf("summaryUsedTokens" to JsonPrimitive(tokens)))

    // --- saved snapshot ---

    @Test
    fun the_deepest_snapshot_on_the_branch_wins() {
        val branch = listOf(
            message("u1", user = true),
            message("a1", "u1", metadata = withSnapshot(remaining = 90_000)),
            message("u2", "a1", user = true),
            message("a2", "u2", metadata = withSnapshot(remaining = 80_000, completed = 500)),
        )

        val usage = deepestPersistedSnapshot(branch)

        assertEquals(80_000, usage?.remainingContextTokens)
        assertEquals(20_500, usage?.usedTokens)
    }

    @Test
    fun a_tail_without_a_snapshot_falls_back_to_the_one_above_it() {
        val branch = listOf(
            message("u1", user = true),
            message("a1", "u1", metadata = withSnapshot(remaining = 90_000)),
            message("u2", "a1", user = true),
        )

        assertEquals(90_000, deepestPersistedSnapshot(branch)?.remainingContextTokens)
    }

    @Test
    fun the_walk_stops_at_a_summary_without_its_own_snapshot() {
        val branch = listOf(
            message("u1", user = true),
            message("a1", "u1", metadata = withSnapshot(remaining = 90_000)),
            message("u2", "a1", user = true),
            message("a2", "u2", metadata = summarized(3_000)),
        )

        assertNull(deepestPersistedSnapshot(branch))
    }

    @Test
    fun a_summarizing_response_with_its_own_snapshot_still_counts() {
        val metadata = JsonObject(withSnapshot(remaining = 70_000) + summarized(3_000))
        val branch = listOf(message("u1", user = true), message("a1", "u1", metadata = metadata))

        assertEquals(70_000, deepestPersistedSnapshot(branch)?.remainingContextTokens)
    }

    @Test
    fun a_malformed_blob_reads_as_absent() {
        val bad = JsonObject(mapOf("contextUsage" to buildJsonObject { put("breakdown", "nope") }))
        val branch = listOf(
            message("a1", metadata = withSnapshot(remaining = 90_000)),
            message("a2", "a1", metadata = bad),
        )

        assertEquals(90_000, deepestPersistedSnapshot(branch)?.remainingContextTokens)
    }

    @Test
    fun the_snapshot_at_an_anchor_is_read_from_that_message_only() {
        val branch = listOf(
            message("a1", metadata = withSnapshot(remaining = 90_000)),
            message("u2", "a1", user = true),
        )

        assertNull(persistedSnapshotAt(branch, "u2"))
        assertNull(persistedSnapshotAt(branch, "absent"))
        assertEquals(90_000, persistedSnapshotAt(branch, "a1")?.remainingContextTokens)
    }

    // --- estimate ---

    @Test
    fun stored_counts_are_summed_with_the_overhead() {
        val branch = listOf(
            message("u1", user = true, tokenCount = 100),
            message("a1", "u1", tokenCount = 400),
        )

        val usage = estimateContextUsage(branch, windowTokens = 10_000, overheadTokens = 1_000)

        assertEquals(1_500, usage?.usedTokens)
        assertEquals(10_000, usage?.windowTokens)
        assertEquals(500, usage?.breakdown?.messageTokens)
    }

    @Test
    fun a_message_without_a_count_estimates_from_its_characters() {
        // 10 chars -> round(2.5) = 3
        val branch = listOf(message("u1", user = true, text = "0123456789"))

        assertEquals(3, estimateContextUsage(branch, windowTokens = 10_000, overheadTokens = 0)?.usedTokens)
    }

    @Test
    fun structured_content_is_counted_over_text_and_reasoning_is_skipped() {
        val content = listOf(
            MessageContentPart(type = ContentType.THINK, think = "x".repeat(400)),
            MessageContentPart(type = ContentType.TEXT, text = "y".repeat(40)),
            MessageContentPart(
                type = ContentType.TOOL_CALL,
                toolCall = AgentToolCall(name = "web", args = JsonPrimitive("q=1"), output = "z".repeat(17)),
            ),
        )
        // 40 + (3 + 3 + 17) = 63 chars -> round(15.75) = 16
        val branch = listOf(message("a1", text = "ignored text", content = content))

        assertEquals(16, estimateContextUsage(branch, windowTokens = 10_000, overheadTokens = 0)?.usedTokens)
    }

    @Test
    fun a_quoted_user_turn_ignores_its_stored_count() {
        // "abcd" + "efghijkl" = 12 chars -> 3
        val branch = listOf(message("u1", user = true, text = "abcd", tokenCount = 900, quotes = listOf("efghijkl")))

        assertEquals(3, estimateContextUsage(branch, windowTokens = 10_000, overheadTokens = 0)?.usedTokens)
    }

    @Test
    fun a_summary_caps_the_walk_after_counting_the_summarizer_and_replaces_the_overhead() {
        val branch = listOf(
            message("u1", user = true, tokenCount = 5_000),
            message("a1", "u1", tokenCount = 300, metadata = summarized(2_000)),
            message("u2", "a1", user = true, tokenCount = 100),
        )

        val usage = estimateContextUsage(branch, windowTokens = 10_000, overheadTokens = 1_000)

        // baseline 2,000 + (100 + 300); the pre-summary 5,000 and the overhead are not added.
        assertEquals(2_400, usage?.usedTokens)
        assertEquals(2_000, usage?.breakdown?.summaryTokens)
    }

    @Test
    fun an_overflowing_branch_keeps_the_newest_messages_that_fit() {
        val branch = listOf(
            message("u1", user = true, tokenCount = 300),
            message("a1", "u1", tokenCount = 500),
            message("u2", "a1", user = true, tokenCount = 400),
            message("a2", "u2", tokenCount = 200),
        )

        // Budget 1,000 - 100 overhead = 900: keeps 200 + 400, then 500 does not fit, so stop.
        val usage = estimateContextUsage(branch, windowTokens = 1_000, overheadTokens = 100)

        assertEquals(600, usage?.breakdown?.messageTokens)
        assertEquals(700, usage?.usedTokens)
    }

    @Test
    fun without_a_window_there_is_no_estimate() {
        val branch = listOf(message("u1", user = true, tokenCount = 100))

        assertNull(estimateContextUsage(branch, windowTokens = null, overheadTokens = 0))
        assertNull(estimateContextUsage(branch, windowTokens = 0, overheadTokens = 0))
        assertNull(estimateContextUsage(emptyList(), windowTokens = 1_000, overheadTokens = 0))
    }
}
