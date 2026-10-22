package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.MemoryArtifactData
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.FunctionCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.ContentGroup
import com.garfiec.librechat.feature.chat.util.groupContentParts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The activity-group header's verdict: the batch label's own status, plus a refused memory write
 * inside the block (upstream `outcome.ts` `getToolMeta`), so the header never claims success over
 * a memory card drawn as an error.
 */
class ActivityGroupVerdictTest {

    private fun tool(id: String, name: String, output: String?) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(id = id, name = name, output = output),
    )

    private fun label(status: String? = null) = MessageContentPart(
        type = ContentType.ACTIVITY_LABEL,
        activityLabel = "Saved a few things",
        status = status,
    )

    private fun memoryAttachment(toolCallId: String?, type: String) = Attachment(
        type = "memory",
        toolCallId = toolCallId,
        memory = MemoryArtifactData(key = "tone", value = "dry", type = type),
    )

    /** The one labeled activity group the parts form, as the message renders it. */
    private fun group(vararg parts: MessageContentPart): ContentGroup.Activity =
        groupContentParts(parts.toList()).single().groups.filterIsInstance<ContentGroup.Activity>().single()

    @Test
    fun `a refused set_memory fails a group whose label reports nothing`() {
        val group = group(
            tool("c1", "web_search", "results"),
            tool("c2", "set_memory", "Invalid key \"Tone\". Must be one of: tone"),
            label(),
        )

        assertThat(group.failed).isFalse()
        assertThat(activityGroupFailed(group, emptyList())).isTrue()
    }

    @Test
    fun `a refused delete_memory fails the group`() {
        val group = group(tool("c1", "delete_memory", "Failed to delete memory for key \"tone\""), label())

        assertThat(activityGroupFailed(group, emptyList())).isTrue()
    }

    @Test
    fun `successful memory writes leave the group successful`() {
        val group = group(
            tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"),
            tool("c2", "delete_memory", "Memory deleted."),
            label(),
        )

        assertThat(activityGroupFailed(group, emptyList())).isFalse()
    }

    @Test
    fun `a memory call with no output yet is not a failure`() {
        val group = group(tool("c1", "set_memory", null), tool("c2", "set_memory", ""), label())

        assertThat(activityGroupFailed(group, emptyList())).isFalse()
    }

    @Test
    fun `an MCP tool with memory in its name is not judged by the memory rule`() {
        val group = group(tool("c1", "memory_search_mcp_notes", "Invalid query"), label())

        assertThat(activityGroupFailed(group, emptyList())).isFalse()
    }

    @Test
    fun `an error memory artifact fails the group even beside success prose`() {
        val group = group(tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"), label())

        assertThat(activityGroupFailed(group, listOf(memoryAttachment("c1", "error")))).isTrue()
        assertThat(activityGroupFailed(group, listOf(memoryAttachment("c1", "update")))).isFalse()
    }

    @Test
    fun `another call's error artifact does not fail this group`() {
        val group = group(tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"), label())

        assertThat(activityGroupFailed(group, listOf(memoryAttachment("other", "error")))).isFalse()
    }

    @Test
    fun `a function-shaped memory call is judged by its function name and output`() {
        val call = MessageContentPart(
            type = ContentType.TOOL_CALL,
            toolCall = AgentToolCall(
                id = "c1",
                function = FunctionCall(name = "set_memory", output = "Memory storage exceeded. Cannot save new memories."),
            ),
        )
        val group = group(call, label())

        assertThat(activityGroupFailed(group, emptyList())).isTrue()
    }

    @Test
    fun `a label reporting failure or partial fails the group on its own`() {
        val ok = tool("c1", "web_search", "results")

        assertThat(activityGroupFailed(group(ok, label(status = "failed")), emptyList())).isTrue()
        assertThat(activityGroupFailed(group(ok, label(status = "partial")), emptyList())).isTrue()
        assertThat(activityGroupFailed(group(ok, label()), emptyList())).isFalse()
    }
}
