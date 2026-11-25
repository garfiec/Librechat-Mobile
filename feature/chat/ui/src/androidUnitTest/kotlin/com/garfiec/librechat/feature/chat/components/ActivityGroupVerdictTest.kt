package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.MemoryArtifactData
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.FunctionCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.ContentGroup
import com.garfiec.librechat.feature.chat.util.groupContentParts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The activity-group header's verdict (upstream `outcome.ts`, #16949): each call is judged as its
 * card judges it (a refused memory write, error output, a failed run step, an attached-workspace
 * command's exit status), and the header reads failed only when every call failed or the label
 * says `failed`. Mixed outcomes are a count, not a red header.
 */
class ActivityGroupVerdictTest {

    private fun tool(
        id: String,
        name: String,
        output: String?,
        status: RunStepStatus? = null,
        executor: String? = null,
    ) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(id = id, name = name, output = output, runStepStatus = status, executor = executor),
    )

    private fun failed(group: ContentGroup.Activity, attachments: List<Attachment> = emptyList()) =
        activityGroupOutcome(group, attachments).headerFailed

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
    fun `a refused set_memory beside a successful call is counted, not a failed header`() {
        val group = group(
            tool("c1", "web_search", "results"),
            tool("c2", "set_memory", "Invalid key \"Tone\". Must be one of: tone"),
            label(),
        )

        val outcome = activityGroupOutcome(group, emptyList())
        assertThat(outcome.failed).isEqualTo(1)
        assertThat(outcome.total).isEqualTo(2)
        // One of two failed: a count beside an ordinary header, not a failed header.
        assertThat(outcome.headerFailed).isFalse()
    }

    @Test
    fun `a refused delete_memory fails the group`() {
        val group = group(tool("c1", "delete_memory", "Failed to delete memory for key \"tone\""), label())

        assertThat(failed(group)).isTrue()
    }

    @Test
    fun `successful memory writes leave the group successful`() {
        val group = group(
            tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"),
            tool("c2", "delete_memory", "Memory deleted."),
            label(),
        )

        assertThat(failed(group)).isFalse()
    }

    @Test
    fun `a memory call with no output yet is not a failure`() {
        val group = group(tool("c1", "set_memory", null), tool("c2", "set_memory", ""), label())

        assertThat(failed(group)).isFalse()
    }

    @Test
    fun `an MCP tool with memory in its name is not judged by the memory rule`() {
        val group = group(tool("c1", "memory_search_mcp_notes", "Invalid query"), label())

        assertThat(failed(group)).isFalse()
    }

    @Test
    fun `an error memory artifact fails the group even beside success prose`() {
        val group = group(tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"), label())

        assertThat(failed(group, listOf(memoryAttachment("c1", "error")))).isTrue()
        assertThat(failed(group, listOf(memoryAttachment("c1", "update")))).isFalse()
    }

    @Test
    fun `another call's error artifact does not fail this group`() {
        val group = group(tool("c1", "set_memory", "Memory set for key \"tone\" (4 tokens)"), label())

        assertThat(failed(group, listOf(memoryAttachment("other", "error")))).isFalse()
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

        assertThat(failed(group)).isTrue()
    }

    @Test
    fun `a failed label fails the group, a partial one does not`() {
        val ok = tool("c1", "web_search", "results")

        assertThat(failed(group(ok, label(status = "failed")))).isTrue()
        assertThat(failed(group(ok, label(status = "partial")))).isFalse()
        assertThat(failed(group(ok, label()))).isFalse()
    }

    @Test
    fun `a partial label shows the warning only when the calls cannot say what went wrong`() {
        val ok = tool("c1", "web_search", "results")
        val bad = tool("c2", "web_search", "", status = RunStepStatus.FAILED)

        assertThat(activityGroupOutcome(group(ok, label(status = "partial")), emptyList()).showsPartialMarker).isTrue()
        assertThat(activityGroupOutcome(group(ok, bad, label(status = "partial")), emptyList()).showsPartialMarker)
            .isFalse()
        assertThat(activityGroupOutcome(group(bad, label(status = "partial")), emptyList()).showsPartialMarker).isTrue()
    }

    @Test
    fun `every call failing fails the header`() {
        val group = group(
            tool("c1", "web_search", "", status = RunStepStatus.FAILED),
            tool("c2", "fetch", "Error: tool call failed: timeout"),
            label(),
        )

        val outcome = activityGroupOutcome(group, emptyList())
        assertThat(outcome.failed).isEqualTo(2)
        assertThat(outcome.headerFailed).isTrue()
    }

    @Test
    fun `a tool the user's interrupt cancelled counts as cancelled, not failed`() {
        val interrupted = "Error: $INTERRUPTED_TOOL_RESULT\n Please fix your mistakes."
        val group = group(
            tool("c1", "web_search", interrupted),
            tool("c2", "fetch", "ok"),
            label(),
        )

        val outcome = activityGroupOutcome(group, emptyList())
        assertThat(outcome.cancelled).isEqualTo(1)
        assertThat(outcome.failed).isEqualTo(0)
        assertThat(outcome.headerFailed).isFalse()
    }

    @Test
    fun `a cancelled step outranks failure-shaped output and is counted apart`() {
        val group = group(
            tool("c1", "fetch", "Error: tool call failed: stopped", status = RunStepStatus.CANCELLED),
            tool("c2", "fetch", "ok"),
            label(),
        )

        val outcome = activityGroupOutcome(group, emptyList())
        assertThat(outcome.cancelled).isEqualTo(1)
        assertThat(outcome.failed).isEqualTo(0)
        assertThat(outcome.headerFailed).isFalse()
    }

    @Test
    fun `an attached-workspace command that exits non-zero fails, a sandbox one with the same text does not`() {
        val text = "stdout:\nnope\n[exit code: 1]"
        val attached = tool("c1", "bash_tool", text, executor = "attached_workspace")
        val sandbox = tool("c2", "bash_tool", text)

        assertThat(failed(group(attached, label()))).isTrue()
        assertThat(failed(group(sandbox, label()))).isFalse()
        assertThat(failed(group(tool("c3", "bash_tool", "stdout:\nok\n[exit code: 0]", executor = "attached_workspace"), label())))
            .isFalse()
    }
}
