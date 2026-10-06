package com.garfiec.librechat.core.model.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextDetailTest {

    private val snapshot = ContextUsage(
        effectiveInstructionTokens = 400,
        cacheRead = 30,
        cacheWrite = 5,
        breakdown = TokenBudgetBreakdown(
            maxContextTokens = 2000,
            instructionTokens = 400,
            systemMessageTokens = 100,
            dynamicInstructionTokens = 20,
            toolSchemaTokens = 280,
            messageTokens = 550,
            toolTokenCounts = mapOf("web_search" to 90, "server_mcp_tool" to 80, "skill" to 25),
            toolMessageTokens = 150,
            toolMessageTokenCounts = mapOf("read_file" to 100),
        ),
    )
    private val view =
        buildContextBreakdown(snapshot, estimate = null, compactionAvailable = true, compactionReclaim = 300)
    private val totals = ContextUsageTotals(
        branch = UsageAmount(input = 100, output = 50, cost = 1.0),
        total = UsageAmount(input = 150, output = 70, cost = 1.5),
        subagent = UsageAmount(input = 10),
    )

    private fun visible(preset: ContextDetailPreset, costEnabled: Boolean = true, totals: ContextUsageTotals = this.totals) =
        visibleBreakdown(view, preset.sections, totals, costEnabled)

    @Test
    fun simple_shows_used_and_free_only() {
        val v = visible(ContextDetailPreset.SIMPLE)

        assertTrue(v.usedAndFreeOnly)
        assertTrue(v.rows.isEmpty())
        assertTrue(v.toolBreakdown.isEmpty())
        assertEquals(0, v.cacheRead)
        assertFalse(v.showWarning)
        assertNull(v.totals)
        assertNull(v.cost)
    }

    @Test
    fun standard_folds_every_tool_type_into_one_row_and_shows_the_whole_conversation_cost() {
        val v = visible(ContextDetailPreset.STANDARD)

        assertEquals(
            listOf(BreakdownCategory.MESSAGES, BreakdownCategory.TOOL_CALLS, BreakdownCategory.SYSTEM, BreakdownCategory.TOOLS_ALL),
            v.rows.map { it.category },
        )
        assertEquals(195, v.rows.first { it.category == BreakdownCategory.TOOLS_ALL }.value)
        assertTrue(v.toolBreakdown.isEmpty())
        assertEquals(0, v.cacheRead)
        assertNull(v.largestTool)
        assertNull(v.totals)
        assertEquals(CostRows(1.5, primaryIsWholeConversation = true, allBranches = null), v.cost)
    }

    @Test
    fun detailed_shows_everything() {
        val v = visible(ContextDetailPreset.DETAILED)

        assertTrue(BreakdownCategory.SKILLS in v.rows.map { it.category })
        assertEquals(listOf(ToolShare("read_file", 100)), v.toolBreakdown)
        assertEquals(30, v.cacheRead)
        assertEquals(5, v.cacheWrite)
        assertEquals(300, v.compactionReclaim)
        assertEquals(totals.branch, v.totals)
        assertEquals(10, v.subagentTokens)
        assertEquals(CostRows(1.0, primaryIsWholeConversation = false, allBranches = 1.5), v.cost)
    }

    @Test
    fun the_whole_conversation_cost_needs_every_part_known_and_the_server_to_report_cost() {
        val unknown = totals.copy(total = totals.total.copy(costKnown = false))

        assertNull(visible(ContextDetailPreset.STANDARD, totals = unknown).cost)
        assertNull(visible(ContextDetailPreset.STANDARD, costEnabled = false).cost)
        assertNull(visible(ContextDetailPreset.STANDARD, totals = ContextUsageTotals()).cost)
    }

    @Test
    fun the_warning_line_is_an_insight() {
        val full = buildContextBreakdown(
            ContextUsage(breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, messageTokens = 900)),
            estimate = null,
            compactionAvailable = false,
            compactionReclaim = 0,
        )

        assertFalse(visibleBreakdown(full, ContextDetailPreset.STANDARD.sections, totals, true).showWarning)
        assertTrue(visibleBreakdown(full, ContextDetailPreset.DETAILED.sections, totals, true).showWarning)
    }

    @Test
    fun an_estimate_has_no_rows_and_keeps_its_detail_unless_simple() {
        val estimate = EstimateDetail(100, 300, 50, 0, 200, 0, 450, messagesPruned = false)
        val estimated = buildContextBreakdown(
            ContextUsage(breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, instructionTokens = 200, messageTokens = 450)),
            estimate,
            compactionAvailable = false,
            compactionReclaim = 0,
        )

        val standard = visibleBreakdown(estimated, ContextDetailPreset.STANDARD.sections, totals, true)
        assertTrue(standard.rows.isEmpty())
        assertEquals(estimate, standard.estimate)
        assertNull(visibleBreakdown(estimated, ContextDetailPreset.SIMPLE.sections, totals, true).estimate)
    }

    @Test
    fun nudge_bands_follow_the_threshold() {
        assertNull(compactNudgeBand(99, threshold = 0))
        assertNull(compactNudgeBand(59, threshold = 60))
        assertEquals(0, compactNudgeBand(60, threshold = 60))
        assertNull(compactNudgeBand(69, threshold = 70))
        assertEquals(0, compactNudgeBand(70, threshold = 70))
        assertEquals(0, compactNudgeBand(79, threshold = 70))
        assertEquals(1, compactNudgeBand(80, threshold = 70))
        assertEquals(1, compactNudgeBand(94, threshold = 70))
        assertEquals(2, compactNudgeBand(95, threshold = 70))
        // At an 80 threshold the suggestion starts at band 1, so "Not now" still re-arms at 95.
        assertNull(compactNudgeBand(79, threshold = 80))
        assertEquals(1, compactNudgeBand(80, threshold = 80))
    }

    @Test
    fun presets_parse_with_standard_as_the_default() {
        assertEquals(ContextDetailPreset.DETAILED, ContextDetailPreset.fromString("DETAILED"))
        assertEquals(ContextDetailPreset.STANDARD, ContextDetailPreset.fromString(null))
        assertEquals(ContextDetailPreset.STANDARD, ContextDetailPreset.fromString("nonsense"))
    }
}
