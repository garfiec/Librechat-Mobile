package com.garfiec.librechat.core.model.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from upstream web's `Breakdown.spec.tsx` (v0.8.8): the same fixture, the same row math. */
class ContextBreakdownTest {

    /** Upstream's `snapshotView`: one tool per group, a deferred entry on both system and MCP. */
    private val snapshot = ContextUsage(
        effectiveInstructionTokens = 400,
        breakdown = TokenBudgetBreakdown(
            maxContextTokens = 2000,
            instructionTokens = 400,
            systemMessageTokens = 100,
            dynamicInstructionTokens = 20,
            toolSchemaTokens = 280,
            summaryTokens = 50,
            messageTokens = 550,
            toolTokenCounts = mapOf(
                "web_search" to 90,
                "deferred_tool" to 30,
                "server_mcp_tool" to 80,
                "server_mcp_lazy" to 40,
                "skill" to 25,
                "subagent" to 15,
            ),
            deferredToolNames = listOf("deferred_tool", "server_mcp_lazy"),
        ),
    )

    private fun build(usage: ContextUsage = snapshot, compaction: Boolean = false, reclaim: Int = 0) =
        buildContextBreakdown(usage, estimate = null, compactionAvailable = compaction, compactionReclaim = reclaim)

    private fun ContextBreakdownView.value(category: BreakdownCategory) =
        segments.first { it.category == category }.value

    @Test
    fun segments_stack_in_slot_order_with_deferred_beside_its_parent() {
        val view = build()

        assertEquals(1000, view.usedTokens)
        assertEquals(
            listOf(
                BreakdownCategory.MESSAGES, BreakdownCategory.SYSTEM, BreakdownCategory.TOOLS_SYSTEM,
                BreakdownCategory.TOOLS_SYSTEM_DEFERRED, BreakdownCategory.TOOLS_MCP,
                BreakdownCategory.TOOLS_MCP_DEFERRED, BreakdownCategory.SKILLS, BreakdownCategory.SUBAGENTS,
                BreakdownCategory.SUMMARY,
            ),
            view.segments.filter(view::showsRow).map { it.category },
        )
        assertEquals(550, view.value(BreakdownCategory.MESSAGES))
        assertEquals(120, view.value(BreakdownCategory.SYSTEM))
        assertEquals(90, view.value(BreakdownCategory.TOOLS_SYSTEM))
        assertEquals(30, view.value(BreakdownCategory.TOOLS_SYSTEM_DEFERRED))
        assertEquals(80, view.value(BreakdownCategory.TOOLS_MCP))
        assertEquals(40, view.value(BreakdownCategory.TOOLS_MCP_DEFERRED))
        assertEquals(25, view.value(BreakdownCategory.SKILLS))
        assertEquals(15, view.value(BreakdownCategory.SUBAGENTS))
        assertEquals(20, view.dynamicInstructionTokens)
        assertEquals(1000, view.freeTokens)
    }

    @Test
    fun a_category_that_contributes_nothing_has_no_row() {
        val noSkills = snapshot.copy(
            breakdown = snapshot.breakdown.copy(toolTokenCounts = snapshot.breakdown.toolTokenCounts!! - "skill"),
        )
        val view = build(noSkills)

        assertFalse(view.segments.filter(view::showsRow).any { it.category == BreakdownCategory.SKILLS })
    }

    @Test
    fun without_per_tool_counts_tool_schemas_are_one_row() {
        val view = build(snapshot.copy(breakdown = snapshot.breakdown.copy(toolTokenCounts = null)))

        assertEquals(280, view.value(BreakdownCategory.TOOLS))
        assertTrue(view.segments.none { it.category == BreakdownCategory.TOOLS_MCP })
    }

    @Test
    fun a_reported_tool_split_comes_out_of_the_messages_row() {
        val split = snapshot.copy(
            breakdown = snapshot.breakdown.copy(toolMessageTokens = 150, toolMessageTokenCounts = mapOf("read_file" to 100)),
        )
        val view = build(split)

        assertEquals(400, view.value(BreakdownCategory.MESSAGES))
        assertEquals(150, view.value(BreakdownCategory.TOOL_CALLS))
        assertEquals(listOf(ToolShare("read_file", 100)), view.toolBreakdown)
        assertEquals(ToolShare("read_file", 100), view.largestTool)
    }

    @Test
    fun retained_tool_results_widen_the_tool_call_row_within_used_tokens() {
        val retained = snapshot.copy(
            retainedToolTokens = 75,
            breakdown = snapshot.breakdown.copy(toolMessageTokens = 150),
        )
        val view = build(retained)

        assertEquals(1075, view.usedTokens)
        assertEquals(400, view.value(BreakdownCategory.MESSAGES))
        assertEquals(225, view.value(BreakdownCategory.TOOL_CALLS))
    }

    @Test
    fun a_reported_zero_tool_share_still_shows_its_row() {
        val view = build(snapshot.copy(breakdown = snapshot.breakdown.copy(toolMessageTokens = 0)))

        val toolCalls = view.segments.first { it.category == BreakdownCategory.TOOL_CALLS }
        assertTrue(view.showsRow(toolCalls))
    }

    @Test
    fun without_a_tool_split_the_messages_row_stays_whole() {
        val view = build()

        val toolCalls = view.segments.first { it.category == BreakdownCategory.TOOL_CALLS }
        assertFalse(view.showsRow(toolCalls))
        assertEquals(550, view.value(BreakdownCategory.MESSAGES))
    }

    @Test
    fun per_tool_results_are_clamped_to_the_tool_call_share_and_keep_known_zeroes() {
        val split = snapshot.copy(
            breakdown = snapshot.breakdown.copy(
                toolMessageTokens = 100,
                toolMessageTokenCounts = mapOf("big" to 90, "mid" to 40, "zero" to 0),
            ),
        )
        val view = build(split)

        assertEquals(listOf(ToolShare("big", 90), ToolShare("mid", 10), ToolShare("zero", 0)), view.toolBreakdown)
    }

    @Test
    fun cached_shares_are_carried_for_the_indented_subtotal() {
        val view = build(snapshot.copy(cacheRead = 30, cacheWrite = 0))

        assertEquals(30, view.cacheRead)
        assertEquals(0, view.cacheWrite)
    }

    /** Pressure reads the rounded percent every surface shows, so 79.5% is amber everywhere. */
    @Test
    fun pressure_warns_at_a_rounded_80_and_is_dangerous_at_95() {
        fun at(used: Int) = build(
            ContextUsage(breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, messageTokens = used)),
        ).pressure

        assertEquals(ContextPressure.NONE, at(794))
        assertEquals(ContextPressure.WARN, at(795))
        assertEquals(ContextPressure.WARN, at(944))
        assertEquals(ContextPressure.DANGER, at(945))
    }

    @Test
    fun the_compaction_insight_needs_compaction_and_something_to_free() {
        assertNull(build(compaction = false, reclaim = 300).compactionReclaim)
        assertNull(build(compaction = true, reclaim = 0).compactionReclaim)
        assertEquals(300, build(compaction = true, reclaim = 300).compactionReclaim)
    }

    @Test
    fun the_estimate_path_has_no_segments() {
        val estimate = EstimateDetail(
            input = 100, output = 300, estimated = 50, toolCallTokens = 40, overheadTokens = 200,
            summaryBaseline = 0, messageTokens = 450, messagesPruned = false,
        )
        val usage = ContextUsage(
            breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, instructionTokens = 200, messageTokens = 450),
        )

        val view = buildContextBreakdown(usage, estimate, compactionAvailable = false, compactionReclaim = 0)

        assertTrue(view.segments.isEmpty())
        assertEquals(estimate, view.estimate)
        assertEquals(650, view.usedTokens)
    }
}
