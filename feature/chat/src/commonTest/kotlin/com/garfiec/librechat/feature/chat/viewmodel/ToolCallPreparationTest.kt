package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.components.preparingToolLabel
import com.garfiec.librechat.feature.chat.viewmodel.delegate.preparedBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A live call's preparing state (upstream `isToolCallPreparing`, #16619). */
class ToolCallPreparationTest {

    private val call = ActiveToolCall(id = "c1", name = "web_search")

    private fun prepared(vararg ids: String) = StreamEvent.ToolCallPreparation(ids.toList(), dispatched = false)
    private fun dispatched(vararg ids: String) = StreamEvent.ToolCallPreparation(ids.toList(), dispatched = true)

    @Test
    fun a_marker_makes_the_call_preparing_until_it_is_dispatched() {
        val preparing = listOf(call).preparedBy(prepared("c1")).single()
        assertTrue(preparing.isPreparing)
        assertFalse(listOf(preparing).preparedBy(dispatched("c1")).single().isPreparing)
    }

    @Test
    fun a_late_marker_never_reopens_a_dispatched_call() {
        val handedOff = listOf(call).preparedBy(dispatched("c1")).preparedBy(prepared("c1")).single()
        assertFalse(handedOff.isPreparing)
    }

    @Test
    fun a_result_or_a_terminal_status_outranks_the_marker() {
        val preparing = call.copy(preparationStarted = true)
        assertFalse(preparing.copy(isComplete = true).isPreparing)
        assertFalse(preparing.copy(output = "x").isPreparing)
        assertFalse(preparing.copy(closedStatus = RunStepStatus.CANCELLED).isPreparing)
    }

    @Test
    fun a_marker_for_another_call_changes_nothing() {
        val list = listOf(call)
        assertSame(list, list.preparedBy(prepared("other")))
    }

    @Test
    fun the_label_names_the_tool_but_not_an_integration() {
        assertEquals("web_search", preparingToolLabel(call.copy(preparationStarted = true)))
        assertNull(preparingToolLabel(call))
        assertNull(preparingToolLabel(call.copy(name = "search_mcp_github", preparationStarted = true)))
        assertNull(preparingToolLabel(call.copy(name = "getPet_action_petstore", preparationStarted = true)))
    }
}
