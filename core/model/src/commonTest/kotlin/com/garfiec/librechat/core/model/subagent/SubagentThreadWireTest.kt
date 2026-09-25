package com.garfiec.librechat.core.model.subagent

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.RunStepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class SubagentThreadWireTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun decodes_the_parent_index() {
        val body = """
            {
              "parentConversationId": "convo-1",
              "children": [
                {
                  "threadId": "child-1", "parentMessageId": "msg-1",
                  "parentToolCallId": "call_abc", "subagentType": "researcher",
                  "subagentKind": "agent", "agentId": "agent_x", "title": "Research",
                  "origin": "tool", "status": "completed", "latestTaskId": "task-2",
                  "tasks": [{"taskId": "task-2", "status": "completed"},
                            {"taskId": "task-1", "status": "failed"}],
                  "tasksTruncated": false
                }
              ],
              "childrenTruncated": false
            }
        """.trimIndent()

        val index = json.decodeFromString<SubagentIndex>(body)
        val child = index.children.single()

        assertEquals("child-1", child.threadId)
        assertEquals("call_abc", child.parentToolCallId)
        assertEquals(SubagentOrigin.TOOL, child.origin)
        assertEquals(2, child.tasks.size)
        assertTrue(SubagentThreadStatus.isTerminal(child.status))
    }

    @Test
    fun an_event_spawned_child_carries_no_parent_tool_call() {
        // The reason these routes are worth porting at all: an event-origin child hangs off no
        // tool call, so neither `on_subagent_update` nor the persisted trace can reach it.
        val child = json.decodeFromString<SubagentSummary>(
            """
            {"threadId": "child-2", "parentMessageId": "msg-1", "subagentType": "watcher",
             "title": "Inbox watcher", "origin": "event", "actorId": "actor-9",
             "status": "running", "tasks": [], "tasksTruncated": false}
            """.trimIndent(),
        )

        assertEquals(SubagentOrigin.EVENT, child.origin)
        assertNull(child.parentToolCallId)
        assertTrue(!SubagentThreadStatus.isTerminal(child.status))
    }

    @Test
    fun decodes_a_thread_view_with_its_turns() {
        val body = """
            {
              "threadId": "child-1", "parentConversationId": "convo-1",
              "parentMessageId": "msg-1", "parentToolCallId": "call_abc",
              "subagentType": "researcher", "subagentKind": "agent", "depth": 1,
              "title": "Research", "status": "completed",
              "activity": [{"type": "writing", "text": "Found three sources."}],
              "activityTruncated": false,
              "turns": [
                {
                  "taskId": "task-1",
                  "trigger": {"kind": "parent_dispatch", "summary": "Look into it"},
                  "status": "completed",
                  "activity": [{"type": "reasoning", "text": "Considering"}],
                  "activityTruncated": false,
                  "messages": [
                    {"messageId": "task-1:user", "parentMessageId": null,
                     "role": "user", "text": "Look into it"}
                  ]
                }
              ],
              "messages": [
                {"messageId": "task-1:assistant", "parentMessageId": "task-1:user",
                 "role": "assistant", "text": "Found three sources."}
              ],
              "historyTruncated": false,
              "nextCursor": "task-0:assistant"
            }
        """.trimIndent()

        val view = json.decodeFromString<SubagentThreadView>(body)

        assertEquals(1, view.depth)
        assertEquals("task-1", view.turns?.single()?.taskId)
        assertEquals(SubagentTriggerKind.PARENT_DISPATCH, view.turns?.single()?.trigger?.kind)
        // The branch root's parent is an explicit null on the wire.
        assertNull(view.turns?.single()?.messages?.single()?.parentMessageId)
        assertEquals("task-0:assistant", view.nextCursor)
    }

    @Test
    fun an_unknown_activity_type_degrades_one_row_instead_of_failing_the_view() {
        // The union's arms share nothing but `type`, so a future arm carries fields nothing here
        // can lay out. It still has to DECODE — losing the whole thread over one row is worse.
        val view = json.decodeFromString<SubagentThreadView>(
            """
            {"threadId": "c", "activity": [
               {"type": "writing", "text": "kept"},
               {"type": "some_future_arm", "somethingNew": 1}
             ], "activityTruncated": false, "messages": [], "historyTruncated": false}
            """.trimIndent(),
        )

        assertEquals(2, view.activity.size)
        // …and is dropped at render time rather than drawn as an empty row.
        assertEquals(1, view.activity.toContentParts().size)
    }

    @Test
    fun activity_maps_onto_the_parts_the_app_already_renders() {
        val activity = listOf(
            SubagentActivityItem(type = SubagentActivityType.WRITING, text = "wrote"),
            SubagentActivityItem(type = SubagentActivityType.REASONING, text = "thought"),
            SubagentActivityItem(
                type = SubagentActivityType.ACTIVITY_LABEL,
                label = "Searching",
                labelType = "phase",
                activityStartIndex = 0,
                activityEndIndex = 2,
                status = "ok",
            ),
            SubagentActivityItem(
                type = SubagentActivityType.TOOL,
                toolCallId = "call_1",
                name = "web_search",
                input = """{"q":"kotlin"}""",
                output = "three results",
                status = SubagentToolStatus.COMPLETED,
            ),
        )

        val parts = activity.toContentParts()

        assertEquals(ContentType.TEXT, parts[0].type)
        assertEquals("wrote", parts[0].text)
        assertEquals(ContentType.THINK, parts[1].type)
        assertEquals("thought", parts[1].think)
        assertEquals(ContentType.ACTIVITY_LABEL, parts[2].type)
        assertEquals("Searching", parts[2].activityLabel)
        assertEquals("phase", parts[2].activityLabelType)
        assertEquals(ContentType.TOOL_CALL, parts[3].type)
        assertEquals("web_search", parts[3].toolCall?.name)
        assertEquals("three results", parts[3].toolCall?.output)
    }

    /**
     * A child's tool call carries the same terminal verdict the live path stamps as
     * `runStepStatus`. Without it a FAILED call renders exactly like one that succeeded, which is
     * the only difference a reader of the trace is looking for.
     */
    @Test
    fun a_projected_tool_call_carries_its_terminal_verdict() {
        fun verdictFor(status: String?) = listOf(
            SubagentActivityItem(
                type = SubagentActivityType.TOOL,
                toolCallId = "call_1",
                name = "t",
                status = status,
            ),
        ).toContentParts().single().toolCall?.runStepStatus

        assertEquals(RunStepStatus.FAILED, verdictFor(SubagentToolStatus.FAILED))
        assertEquals(RunStepStatus.CANCELLED, verdictFor(SubagentToolStatus.CANCELLED))
        assertEquals(RunStepStatus.COMPLETED, verdictFor(SubagentToolStatus.COMPLETED))
        // `running` is not terminal and has no counterpart; the card falls back to its heuristic.
        assertNull(verdictFor(SubagentToolStatus.RUNNING))
        assertNull(verdictFor(null))
    }

    @Test
    fun a_projected_tool_input_survives_being_a_bounded_string() {
        // A live tool call carries structured JSON args; the view projects a bounded STRING. Read
        // as an object it would fail to decode something that was never one.
        val parts = listOf(
            SubagentActivityItem(
                type = SubagentActivityType.TOOL,
                toolCallId = "call_1",
                name = "t",
                input = "not json at all…",
            ),
        ).toContentParts()

        assertEquals("not json at all…", parts.single().toolCall?.args?.toString()?.trim('"'))
    }

    @Test
    fun reasoning_with_no_retained_text_still_renders_a_block() {
        // Absent on projections persisted before reasoning was retained; upstream shows a marker.
        val parts = listOf(SubagentActivityItem(type = SubagentActivityType.REASONING))
            .toContentParts()

        assertEquals(ContentType.THINK, parts.single().type)
        assertEquals("", parts.single().think)
    }

    @Test
    fun truncation_is_reported_once_for_the_whole_activity() {
        assertTrue(
            listOf(
                SubagentActivityItem(type = SubagentActivityType.WRITING, text = "a"),
                SubagentActivityItem(
                    type = SubagentActivityType.TOOL,
                    toolCallId = "c",
                    outputTruncated = true,
                ),
            ).hasTruncatedContent,
        )
        assertTrue(
            !listOf(SubagentActivityItem(type = SubagentActivityType.WRITING, text = "a"))
                .hasTruncatedContent,
        )
    }

    @Test
    fun control_receipts_decode_without_being_modelled() {
        // Read-only: modelling the receipt type invites wiring the control route that makes one.
        val view = json.decodeFromString<SubagentThreadView>(
            """
            {"threadId": "c", "activity": [], "activityTruncated": false, "messages": [],
             "historyTruncated": false,
             "controlReceipts": [{"invocationId": "i", "action": "cancel", "status": "applied",
                                  "createdAt": "x", "updatedAt": "y"}],
             "controlReceiptsTruncated": false}
            """.trimIndent(),
        )

        assertTrue(view.controlReceipts != null)
    }
}
