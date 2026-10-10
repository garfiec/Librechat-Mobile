package com.garfiec.librechat.core.model.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The v0.8.8-rc4 simple view: steps, roles, and when a turn's cost may be shown. Mirrors upstream
 * `buildTraceModel` (`client/src/components/Chat/Trace/model.ts`).
 */
class TraceStepsTest {

    private var clock = 0

    /** Records stamped in the order they are declared, one second apart. */
    private fun rec(
        id: String,
        kind: String = TraceRecordKind.SPAN,
        parentId: String? = null,
        role: String? = null,
        name: String = id,
        cost: Double? = null,
        status: String = TraceStatus.OK,
        origin: String? = null,
        tools: List<String>? = null,
        agentId: String? = null,
        messageId: String = "msg-1",
    ) = TraceRecord(
        id = id,
        messageId = messageId,
        parentId = parentId,
        kind = kind,
        role = role,
        name = name,
        cost = cost,
        status = status,
        origin = origin,
        tools = tools,
        agentId = agentId,
        startTime = "2026-09-24T10:00:${(clock++).toString().padStart(2, '0')}Z",
    )

    /** A two-step agent run: model → tool, model → reply. Wrappers are spans the simple view hides. */
    private fun twoStepRun(): List<TraceRecord> {
        clock = 0
        return listOf(
            rec("run", role = TraceRole.RUN),
            rec("agent", parentId = "run", role = TraceRole.AGENT, agentId = "agent_1"),
            rec("wrap1", parentId = "agent", role = TraceRole.PLUMBING),
            rec("gen1", kind = TraceRecordKind.GENERATION, parentId = "wrap1", role = TraceRole.MODEL, cost = 0.25),
            rec("tool1", kind = TraceRecordKind.TOOL, parentId = "agent", name = "web_search"),
            rec("wrap2", parentId = "agent", role = TraceRole.PLUMBING),
            rec("gen2", kind = TraceRecordKind.GENERATION, parentId = "wrap2", role = TraceRole.MODEL, cost = 0.5),
        )
    }

    @Test
    fun each_model_call_starts_a_step_and_the_tools_after_it_join_it() {
        val turn = groupTraceRecords(twoStepRun()).single()

        assertEquals(2, turn.steps.size)
        val (first, second) = turn.steps
        assertEquals(1, first.index)
        assertEquals("gen1", first.generationId)
        assertEquals(listOf("gen1", "tool1"), first.rows.map { it.record.id })
        assertEquals(mapOf("web_search" to 1), first.toolNames)
        assertEquals(0.25, first.cost)
        assertEquals("agent_1", first.agentId)
        assertEquals(listOf("gen2"), second.rows.map { it.record.id })
        // The full view is untouched: every span as recorded.
        assertEquals(7, turn.rows.size)
    }

    /**
     * A failed wrapper is listed, so it is a step root; the model call under it rolls up to no
     * anchor and is a root too. The spend walk from both roots reaches the model call twice, and
     * without a per-step dedupe its price is counted twice (upstream `groupSteps`' `counted` set).
     */
    @Test
    fun a_price_under_a_failed_root_wrapper_is_counted_once() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("wrap", role = TraceRole.PLUMBING, status = TraceStatus.ERROR),
                rec("gen", kind = TraceRecordKind.GENERATION, parentId = "wrap", role = TraceRole.MODEL, cost = 0.25),
            ),
        ).single()

        val step = turn.steps.single()
        assertEquals(0.25, step.cost)
        assertEquals(listOf("wrap", "gen"), step.rows.map { it.record.id })
    }

    @Test
    fun a_turn_with_nothing_but_wrappers_lists_them_as_one_step() {
        clock = 0
        val turn = groupTraceRecords(listOf(rec("run", role = TraceRole.RUN))).single()

        assertEquals(listOf("run"), turn.steps.single().rows.map { it.record.id })
    }

    @Test
    fun a_failed_wrapper_stays_visible_in_its_step() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, role = TraceRole.MODEL),
                rec("boom", parentId = "gen", status = TraceStatus.ERROR),
            ),
        ).single()

        assertEquals(listOf("gen" to 0, "boom" to 1), turn.steps.single().rows.map { it.record.id to it.depth })
        assertEquals(1, turn.steps.single().errorCount)
    }

    @Test
    fun a_title_run_is_its_own_step_and_sorts_after_a_run_step_at_the_same_time() {
        clock = 0
        val gen = rec("gen", kind = TraceRecordKind.GENERATION, role = TraceRole.MODEL)
        val title = rec("title", kind = TraceRecordKind.GENERATION, origin = TraceOrigin.TITLE)
            .copy(startTime = gen.startTime)
        val steps = groupTraceRecords(listOf(title, gen)).single().steps

        assertEquals(listOf(false, true), steps.map { it.isTitle })
        assertEquals(listOf(1, 1), steps.map { it.index })
    }

    @Test
    fun a_label_call_describes_a_step_but_never_starts_one() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, role = TraceRole.MODEL),
                rec("label", kind = TraceRecordKind.GENERATION, role = TraceRole.STEP_LABEL, cost = 0.01),
            ),
        ).single()

        assertEquals(1, turn.steps.size)
        assertEquals(1, turn.summary.generationCount)
        assertEquals(1, turn.summary.labelCount)
        // Label calls are spend: the unpriced response call still withholds the total.
        assertNull(turn.summary.cost)
    }

    @Test
    fun a_tool_round_that_recorded_no_tools_counts_the_names_it_carries() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, role = TraceRole.MODEL),
                rec("round", role = TraceRole.TOOLS, tools = listOf("search", "search", "fetch")),
            ),
        ).single()

        val step = turn.steps.single()
        assertEquals("round", step.roundId)
        assertEquals(mapOf("search" to 2, "fetch" to 1), step.toolNames)
        assertEquals(3, turn.summary.toolCallCount)
        assertEquals(3, summarizeTraceTurns(listOf(turn)).toolCallCount)
    }

    @Test
    fun a_round_that_recorded_its_tools_counts_those_rather_than_its_names() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, role = TraceRole.MODEL),
                rec("round", role = TraceRole.TOOLS, tools = listOf("search")),
                rec("t", kind = TraceRecordKind.TOOL, parentId = "round", name = "search"),
            ),
        ).single()

        assertEquals(mapOf("search" to 1), turn.steps.single().toolNames)
        assertEquals(1, turn.summary.toolCallCount)
    }

    @Test
    fun a_turn_cut_from_its_parent_is_split_and_shows_no_cost() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, parentId = "unloaded", cost = 0.5),
            ),
        ).single()

        assertTrue(turn.split)
        assertNull(turn.summary.cost)
    }

    @Test
    fun a_title_record_cut_from_its_parent_does_not_split_the_response() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("gen", kind = TraceRecordKind.GENERATION, cost = 0.5),
                rec("title", kind = TraceRecordKind.GENERATION, parentId = "gone", origin = TraceOrigin.TITLE, cost = 0.1),
            ),
        ).single()

        assertFalse(turn.split)
        assertEquals(0.6, turn.summary.cost!!, 1e-9)
    }

    @Test
    fun the_oldest_loaded_turn_shows_no_cost_while_older_pages_remain() {
        clock = 0
        val older = rec("old", kind = TraceRecordKind.GENERATION, cost = 0.5, messageId = "msg-old")
        val newer = rec("new", kind = TraceRecordKind.GENERATION, cost = 0.25, messageId = "msg-new")

        val paged = groupTraceRecords(listOf(older, newer), hasOlder = true)
        assertEquals(listOf("msg-new", "msg-old"), paged.map { it.messageId })
        assertEquals(0.25, paged[0].summary.cost)
        assertNull(paged[1].summary.cost)

        val whole = groupTraceRecords(listOf(older, newer), hasOlder = false)
        assertEquals(0.5, whole[1].summary.cost)
        // The overall figure is over everything loaded, as upstream's summary is.
        assertEquals(0.75, summarizeTraceTurns(paged).cost)
    }

    /**
     * A turn with no timestamp sorts last, so taking the last turn as "the oldest" withholds its
     * cost and shows the real oldest turn's partial sum as though it were whole. Neither turn is
     * known to be complete while older pages remain.
     */
    @Test
    fun a_turn_with_no_start_is_not_taken_for_the_oldest_and_shows_no_cost_while_paging() {
        clock = 0
        val oldest = rec("old", kind = TraceRecordKind.GENERATION, cost = 0.5, messageId = "msg-old")
        val newer = rec("new", kind = TraceRecordKind.GENERATION, cost = 0.25, messageId = "msg-new")
        val undated = rec("nodate", kind = TraceRecordKind.GENERATION, cost = 0.1, messageId = "msg-undated")
            .copy(startTime = "")

        val paged = groupTraceRecords(listOf(oldest, newer, undated), hasOlder = true).associateBy { it.messageId }
        assertEquals(0.25, paged.getValue("msg-new").summary.cost)
        assertNull(paged.getValue("msg-old").summary.cost)
        assertNull(paged.getValue("msg-undated").summary.cost)

        val whole = groupTraceRecords(listOf(oldest, newer, undated), hasOlder = false).associateBy { it.messageId }
        assertEquals(0.5, whole.getValue("msg-old").summary.cost)
        assertEquals(0.1, whole.getValue("msg-undated").summary.cost)
    }

    @Test
    fun a_tool_whose_model_call_is_on_an_older_page_leads_its_own_step() {
        clock = 0
        val turn = groupTraceRecords(
            listOf(
                rec("tool", kind = TraceRecordKind.TOOL, parentId = "graph-unloaded", name = "fetch"),
                rec("wrap", parentId = "graph-unloaded", role = TraceRole.PLUMBING),
                rec("gen", kind = TraceRecordKind.GENERATION, parentId = "wrap", role = TraceRole.MODEL),
            ),
        ).single()

        assertEquals(listOf(listOf("tool"), listOf("gen")), turn.steps.map { step -> step.rows.map { it.record.id } })
    }

    @Test
    fun the_new_record_fields_decode_and_default_to_absent() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            TraceRecord.serializer(),
            """{"id":"r","kind":"span","role":"tools","agentId":"a","tools":["x"],"origin":"title","future":1}""",
        )
        assertEquals(TraceRole.TOOLS, decoded.role)
        assertEquals(listOf("x"), decoded.tools)
        assertEquals(TraceOrigin.TITLE, decoded.origin)
        assertNull(json.decodeFromString(TraceRecord.serializer(), """{"id":"r"}""").role)

        val detail = json.decodeFromString(
            TraceRecordDetail.serializer(),
            """{"record":{"id":"r"},"contentAvailable":true,
               "prompt":{"messages":[{"role":"user","text":{"value":"hi","truncated":false}}],"total":3,"omitted":2},
               "reply":{"role":"assistant","toolCalls":[{"name":"search"}]}}""",
        )
        assertEquals(2, detail.prompt?.omitted)
        assertEquals("search", detail.reply?.toolCalls?.single()?.name)
    }
}
