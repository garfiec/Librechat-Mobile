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
        // Under BOTH with Totals on, each block carries its cost and the separate section goes.
        assertNull(v.cost)
        assertEquals(1.0, v.totalsCost)
        assertEquals(AllBranchesUsage(totals.total, 1.5), v.allBranches)
    }

    @Test
    fun totals_off_keeps_the_separate_cost_section_with_all_branches() {
        val sections = ContextDetailPreset.DETAILED.sections.copy(totals = false)
        val v = visibleBreakdown(view, sections, totals, costEnabled = true)

        assertEquals(CostRows(1.0, primaryIsWholeConversation = false, allBranches = 1.5), v.cost)
        assertNull(v.lastTurn)
        assertNull(v.allBranches)
        assertNull(v.totalsCost)
    }

    @Test
    fun whole_conversation_scope_keeps_one_cost_line_and_no_header_costs() {
        val sections = ContextDetailPreset.DETAILED.sections.copy(cost = ContextCostScope.WHOLE_CONVERSATION)
        val withTurn = totals.copy(lastTurn = UsageAmount(input = 10, output = 5, cost = 0.1))
        val v = visibleBreakdown(view, sections, withTurn, costEnabled = true)

        assertEquals(CostRows(1.5, primaryIsWholeConversation = true, allBranches = null), v.cost)
        assertNull(v.totalsCost)
        assertEquals(LastTurnUsage(withTurn.lastTurn, inProgress = false, cost = null), v.lastTurn)
        assertEquals(AllBranchesUsage(totals.total, null), v.allBranches)
    }

    @Test
    fun last_turn_carries_its_known_cost_and_states() {
        val turn = UsageAmount(input = 10, output = 5, cost = 0.04)
        assertEquals(
            LastTurnUsage(turn, inProgress = false, cost = 0.04),
            visible(ContextDetailPreset.DETAILED, totals = totals.copy(lastTurn = turn)).lastTurn,
        )
        // Saved without a cost: the tokens show, the cost doesn't.
        val unpriced = turn.copy(costKnown = false)
        assertEquals(
            LastTurnUsage(unpriced, inProgress = false, cost = null),
            visible(ContextDetailPreset.DETAILED, totals = totals.copy(lastTurn = unpriced)).lastTurn,
        )
        // A turn in progress before any usage event: waiting.
        assertEquals(
            LastTurnUsage(null, inProgress = true, cost = null),
            visible(ContextDetailPreset.DETAILED, totals = ContextUsageTotals(turnInProgress = true)).lastTurn,
        )
        // A settled branch whose tail reply has no usage: not available.
        assertEquals(LastTurnUsage(null, inProgress = false, cost = null), visible(ContextDetailPreset.DETAILED).lastTurn)
        // Nothing to report on yet, and Totals hidden: no block.
        assertNull(visible(ContextDetailPreset.DETAILED, totals = ContextUsageTotals()).lastTurn)
        assertNull(visible(ContextDetailPreset.STANDARD, totals = totals.copy(lastTurn = turn)).lastTurn)
    }

    @Test
    fun all_branches_shows_when_a_token_bucket_differs_or_a_known_cost_is_higher() {
        val branch = UsageAmount(input = 100, output = 50, cost = 1.0)
        assertFalse(showsAllBranches(branch, branch))
        assertTrue(showsAllBranches(branch, branch.copy(cacheRead = 1)))
        assertTrue(showsAllBranches(branch, branch.copy(cost = 1.5)))
        // A higher total cost alone needs both sides priced.
        assertFalse(showsAllBranches(branch.copy(costKnown = false), branch.copy(cost = 1.5)))
        assertFalse(showsAllBranches(branch, branch.copy(cost = 1.5, costKnown = false)))
        // Tokens differ: shown whatever the cost coverage.
        assertTrue(showsAllBranches(branch.copy(costKnown = false), branch.copy(output = 51, costKnown = false)))
        val same = totals.copy(total = totals.branch)
        assertNull(visible(ContextDetailPreset.DETAILED, totals = same).allBranches)
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
