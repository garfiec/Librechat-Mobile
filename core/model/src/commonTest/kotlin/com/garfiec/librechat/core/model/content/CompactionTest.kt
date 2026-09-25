package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isCompactedLeaf` decides whether the Compact action is offered, so both directions cost
 * something: a false positive withholds it forever on a branch that is not compacted, and a false
 * negative offers a compaction of a compaction.
 */
class CompactionTest {

    private fun message(vararg parts: MessageContentPart) =
        Message(messageId = "m1", conversationId = "c1", content = parts.toList())

    private fun summary(
        text: String? = "a summary",
        summarizing: Boolean? = null,
        failed: Boolean? = null,
        boundary: SummaryBoundary? = SummaryBoundary(messageId = "m0", contentIndex = 0),
    ) = MessageContentPart(
        type = ContentType.SUMMARY,
        content = text?.let {
            buildJsonArray {
                add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(it)) })
            }
        },
        summarizing = summarizing,
        failed = failed,
        boundary = boundary,
    )

    @Test
    fun aFinishedCompactionIsALeaf() {
        assertTrue(Compaction.isCompactedLeaf(message(summary())))
    }

    @Test
    fun anOrdinaryReplyIsNot() {
        assertFalse(
            Compaction.isCompactedLeaf(
                message(MessageContentPart(type = ContentType.TEXT, text = "hello")),
            ),
        )
        // One non-summary part is enough to disqualify the whole message.
        assertFalse(
            Compaction.isCompactedLeaf(
                message(summary(), MessageContentPart(type = ContentType.TEXT, text = "hello")),
            ),
        )
    }

    @Test
    fun anInterruptedCompactionStaysRetryable() {
        // Still streaming, failed, or holding deltas with no boundary — none is finished, so the
        // action must remain available rather than reading as already done.
        assertFalse(Compaction.isCompactedLeaf(message(summary(summarizing = true))))
        assertFalse(Compaction.isCompactedLeaf(message(summary(failed = true))))
        assertFalse(Compaction.isCompactedLeaf(message(summary(boundary = null))))
        assertFalse(Compaction.isCompactedLeaf(message(summary(text = "   "))))
    }

    @Test
    fun oneUsablePartAmongSeveralIsEnough() {
        assertTrue(Compaction.isCompactedLeaf(message(summary(summarizing = true), summary())))
    }

    @Test
    fun emptyOrAbsentContentIsNotALeaf() {
        assertFalse(Compaction.isCompactedLeaf(message()))
        assertFalse(Compaction.isCompactedLeaf(null))
    }

    @Test
    fun assistantsEndpointsDoNotSupportCompaction() {
        // Their thread lives on the provider, so a summary in the local history compacts nothing.
        assertFalse(Compaction.supportsCompaction("assistants"))
        assertFalse(Compaction.supportsCompaction("azureAssistants"))
        assertFalse(Compaction.supportsCompaction(null))
        assertFalse(Compaction.supportsCompaction(""))
        assertTrue(Compaction.supportsCompaction("openAI"))
        assertTrue(Compaction.supportsCompaction("agents"))
    }

    @Test
    fun summaryTextReadsAllThreeShapes() {
        assertTrue(Compaction.summaryText(summary(text = "block form")) == "block form")
        assertTrue(
            Compaction.summaryText(
                MessageContentPart(type = ContentType.SUMMARY, content = JsonPrimitive("raw string")),
            ) == "raw string",
        )
        assertTrue(
            Compaction.summaryText(
                MessageContentPart(type = ContentType.SUMMARY, text = "legacy top-level"),
            ) == "legacy top-level",
        )
    }
}
