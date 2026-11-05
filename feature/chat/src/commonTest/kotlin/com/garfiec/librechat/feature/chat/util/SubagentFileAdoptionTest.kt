package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SubagentFileAdoptionTest {

    private fun file(id: String, callId: String?, agentId: String? = null) =
        Attachment(fileId = id, filename = "$id.png", toolCallId = callId, agentId = agentId)

    @Test
    fun an_unclaimed_tool_file_goes_under_the_only_subagent_call() {
        val adopted = adoptUnclaimedSubagentFiles(
            listOf(file("f1", "child_1")),
            claimedIds = setOf("call_sub"),
            subagents = listOf(SubagentOwner("call_sub", agentId = null)),
        )

        assertEquals("call_sub", adopted.single().toolCallId)
    }

    @Test
    fun a_code_outputs_download_fallback_without_a_file_id_is_adopted_too() {
        val fallback =
            Attachment(filename = "chart.png", filepath = "/api/files/code/download/s/f", toolCallId = "child")
        val owners = listOf(SubagentOwner("call_sub", null))

        val adopted = adoptUnclaimedSubagentFiles(listOf(fallback), setOf("call_sub"), owners)

        assertEquals("call_sub", adopted.single().toolCallId)
    }

    @Test
    fun a_claimed_file_stays_with_its_own_call() {
        val attachments = listOf(file("f1", "child_1"), file("f2", "call_sub"))

        val adopted = adoptUnclaimedSubagentFiles(
            attachments,
            claimedIds = setOf("call_sub", "child_1"),
            subagents = listOf(SubagentOwner("call_sub", null)),
        )

        assertSame(attachments, adopted)
    }

    @Test
    fun with_several_subagents_the_agent_that_stamped_the_file_wins_else_the_last() {
        val subagents = listOf(SubagentOwner("sub_a", "agent_a"), SubagentOwner("sub_b", "agent_b"))

        val adopted = adoptUnclaimedSubagentFiles(
            listOf(file("f1", "child", agentId = "agent_a"), file("f2", "child2", agentId = "unknown"), file("f3", "child3")),
            claimedIds = setOf("sub_a", "sub_b"),
            subagents = subagents,
        )

        assertEquals(listOf("sub_a", "sub_b", "sub_b"), adopted.map { it.toolCallId })
    }

    @Test
    fun no_subagent_call_keeps_todays_behaviour_and_metadata_never_moves() {
        val attachments = listOf(file("f1", "orphan"))
        assertSame(attachments, adoptUnclaimedSubagentFiles(attachments, setOf("call_x"), emptyList()))

        val noFileId = listOf(Attachment(filename = "memory", toolCallId = "orphan"))
        assertSame(noFileId, adoptUnclaimedSubagentFiles(noFileId, emptySet(), listOf(SubagentOwner("sub", null))))
        val upload = listOf(file("f1", callId = null))
        assertSame(upload, adoptUnclaimedSubagentFiles(upload, emptySet(), listOf(SubagentOwner("sub", null))))
    }

    @Test
    fun a_saved_message_routes_by_the_agent_each_subagent_ran_not_always_to_the_last() {
        fun subagent(id: String, agent: String) = com.garfiec.librechat.core.model.content.MessageContentPart(
            type = com.garfiec.librechat.core.model.ContentType.TOOL_CALL,
            toolCall = com.garfiec.librechat.core.model.content.AgentToolCall(
                id = id,
                name = "subagent",
                subagentIdentity = com.garfiec.librechat.core.model.content.SubagentIdentity(subagentAgentId = agent),
            ),
        )

        val adopted = listOf(file("f1", "child", agentId = "agent_a"))
            .withSubagentFilesAdopted(listOf(subagent("sub_a", "agent_a"), subagent("sub_b", "agent_b")))

        assertEquals("sub_a", adopted.single().toolCallId)
    }
}
