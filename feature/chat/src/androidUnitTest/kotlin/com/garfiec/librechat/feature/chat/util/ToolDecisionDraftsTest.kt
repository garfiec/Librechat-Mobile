package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ToolDecisionDraftsTest {

    private val callIds = listOf("a", "b", "c")
    private val approve = ToolDecisionDraft(decision = ToolApprovalDecisions.APPROVE)

    private fun batch(vararg ids: String) = PendingActionPayload(
        type = PendingActionTypes.TOOL_APPROVAL,
        actionRequests = ids.map { ToolApprovalRequest(name = "tool", toolCallId = it) },
    )

    @Test
    fun `an edit or respond without its payload is not a decision yet`() {
        assertThat(ToolDecisionDraft(decision = ToolApprovalDecisions.EDIT).toResolution("a")).isNull()
        assertThat(
            ToolDecisionDraft(decision = ToolApprovalDecisions.EDIT, editedArguments = "[1, 2]").toResolution("a"),
        ).isNull()
        assertThat(
            ToolDecisionDraft(decision = ToolApprovalDecisions.RESPOND, responseText = "  ").toResolution("a"),
        ).isNull()
        assertThat(ToolDecisionDraft().toResolution("a")).isNull()
    }

    @Test
    fun `complete drafts carry their payload to the wire`() {
        val edited = ToolDecisionDraft(decision = ToolApprovalDecisions.EDIT, editedArguments = """{"q":"x"}""")
            .toResolution("a")
        assertThat(edited?.editedArguments?.get("q")).isEqualTo(JsonPrimitive("x"))

        val responded = ToolDecisionDraft(decision = ToolApprovalDecisions.RESPOND, responseText = " use cache ")
            .toResolution("b")
        assertThat(responded?.responseText).isEqualTo("use cache")
        assertThat(responded?.toolCallId).isEqualTo("b")
    }

    @Test
    fun `a batch resolves only once every call is complete`() {
        val payload = batch("a", "b")

        assertThat(toolBatchResolutions(payload, mapOf("a" to approve))).isNull()
        assertThat(
            toolBatchResolutions(
                payload,
                mapOf("a" to approve, "b" to ToolDecisionDraft(decision = ToolApprovalDecisions.RESPOND)),
            ),
        ).isNull()
        assertThat(toolBatchResolutions(payload, mapOf("a" to approve, "b" to approve))?.map { it.toolCallId })
            .containsExactly("a", "b")
            .inOrder()
        assertThat(toolBatchResolutions(batch(), emptyMap())).isNull()
    }

    @Test
    fun `next undecided searches forward from the current call and wraps`() {
        val drafts = mapOf("b" to approve)

        assertThat(nextUndecidedCallIndex(callIds, drafts, fromIndex = 0)).isEqualTo(2)
        assertThat(nextUndecidedCallIndex(callIds, drafts, fromIndex = 2)).isEqualTo(0)
    }

    @Test
    fun `an incomplete edit still counts as undecided`() {
        val drafts = mapOf(
            "a" to approve,
            "b" to ToolDecisionDraft(decision = ToolApprovalDecisions.EDIT, editedArguments = "not json"),
            "c" to approve,
        )

        assertThat(nextUndecidedCallIndex(callIds, drafts, fromIndex = 2)).isEqualTo(1)
        assertThat(nextUndecidedCallIndex(callIds, drafts + ("b" to approve), fromIndex = 2)).isNull()
    }
}
