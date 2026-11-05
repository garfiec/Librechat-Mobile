package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.content.SubagentAttachmentCall
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Slice 2 of subagent file delivery: which files are asked about, and where the answers land. */
class StoredSubagentHintsTest {

    private fun subagent(id: String, omitted: Boolean = true, hints: List<SubagentAttachmentCall>? = null, agentId: String? = null) =
        MessageContentPart(
            type = ContentType.TOOL_CALL,
            agentId = agentId,
            toolCall = AgentToolCall(
                id = id,
                stepId = "step-$id",
                name = "subagent",
                subagentContentOmitted = if (omitted) true else null,
                subagentContentParts = if (omitted) 3 else null,
                subagentAttachmentCalls = hints,
            ),
        )

    private fun file(id: String, callId: String, agentId: String? = null) =
        Attachment(fileId = id, filename = "$id.csv", toolCallId = callId, agentId = agentId)

    private fun message(vararg parts: MessageContentPart, attachments: List<Attachment>) =
        Message(messageId = "m1", conversationId = "c1", content = parts.toList(), attachments = attachments)

    @Test
    fun only_files_no_call_or_hint_claims_are_asked_about() {
        val message = message(
            subagent("sub_a", hints = listOf(SubagentAttachmentCall("known_child"))),
            attachments = listOf(
                file("f1", "known_child"),
                file("f2", "late_child", agentId = "agent_x"),
                file("f3", "sub_a"),
                Attachment(toolCallId = "metadata_only"),
            ),
        )

        assertEquals(listOf(SubagentAttachmentFile("late_child", agentId = "agent_x")), subagentFilesInDoubt(message))
    }

    @Test
    fun a_message_without_a_previewed_subagent_asks_nothing() {
        val message = message(subagent("sub_a", omitted = false), attachments = listOf(file("f1", "late_child")))
        assertTrue(subagentFilesInDoubt(message).isEmpty())
    }

    @Test
    fun an_answer_is_added_to_the_matching_call_only() {
        val nodes = listOf(
            MessageNode(
                message = message(subagent("sub_a"), subagent("sub_b"), attachments = emptyList()),
                children = emptyList(),
                siblingIndex = 0,
                siblingCount = 1,
            ),
        )
        val answer = mapOf(
            "m1" to listOf(
                SubagentAttachmentCallsEntry("sub_b", stepId = "step-sub_b", calls = listOf(SubagentAttachmentCall("late_child"))),
            ),
        )

        val hinted = nodes.withStoredSubagentHints(answer).single().message.content!!

        assertEquals(null, hinted[0].toolCall?.subagentAttachmentCalls)
        assertEquals(listOf(SubagentAttachmentCall("late_child")), hinted[1].toolCall?.subagentAttachmentCalls)
        // The new hint is what makes the file claimed, so slice 1 no longer guesses it.
        assertTrue("late_child" in outputToolCallIds(hinted))
    }

    @Test
    fun an_answer_for_another_identity_or_nothing_new_returns_the_same_list() {
        val nodes = listOf(
            MessageNode(
                message = message(subagent("sub_a", hints = listOf(SubagentAttachmentCall("c"))), attachments = emptyList()),
                children = emptyList(),
                siblingIndex = 0,
                siblingCount = 1,
            ),
        )
        val otherStep = SubagentAttachmentCallsEntry("sub_a", calls = listOf(SubagentAttachmentCall("c")))
        val known =
            SubagentAttachmentCallsEntry("sub_a", stepId = "step-sub_a", calls = listOf(SubagentAttachmentCall("c")))
        assertSame(nodes, nodes.withStoredSubagentHints(mapOf("m1" to listOf(otherStep))))
        assertSame(nodes, nodes.withStoredSubagentHints(mapOf("m1" to listOf(known))))
    }
}
