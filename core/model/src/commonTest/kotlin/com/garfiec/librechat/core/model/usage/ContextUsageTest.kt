package com.garfiec.librechat.core.model.usage

import kotlin.test.Test
import kotlin.test.assertEquals

/** `usedTokens` ported from upstream `useTokenUsage` (client/src/hooks/Chat/useTokenUsage.ts, v0.8.8). */
class ContextUsageTest {

    private val breakdown = TokenBudgetBreakdown(
        maxContextTokens = 100_000,
        instructionTokens = 6_000,
        systemMessageTokens = 4_000,
        toolSchemaTokens = 2_000,
        summaryTokens = 1_000,
        messageTokens = 10_000,
    )

    @Test
    fun remaining_based_usage_wins_when_it_reads_higher() {
        val usage = ContextUsage(breakdown = breakdown, remainingContextTokens = 70_000)
        assertEquals(30_000, usage.usedTokens)
    }

    @Test
    fun remaining_based_usage_is_floored_at_the_breakdown_total() {
        // Remaining measured against a smaller instruction total than the breakdown publishes.
        val usage = ContextUsage(breakdown = breakdown, remainingContextTokens = 95_000)
        assertEquals(17_000, usage.usedTokens)
    }

    @Test
    fun the_fallback_does_not_add_the_system_and_tool_shares_twice() {
        assertEquals(17_000, ContextUsage(breakdown = breakdown).usedTokens)
    }

    @Test
    fun the_effective_instruction_total_replaces_the_breakdown_one() {
        val usage = ContextUsage(breakdown = breakdown, effectiveInstructionTokens = 8_000)
        assertEquals(19_000, usage.usedTokens)
    }

    @Test
    fun the_context_budget_is_the_denominator_when_present() {
        val usage = ContextUsage(breakdown = breakdown, contextBudget = 80_000, remainingContextTokens = 50_000)
        assertEquals(80_000, usage.windowTokens)
        assertEquals(30_000, usage.usedTokens)
        assertEquals(0.375f, usage.usedFraction)
    }

    @Test
    fun without_a_budget_the_window_is_the_raw_maximum() {
        val usage = ContextUsage(breakdown = breakdown, remainingContextTokens = 70_000)
        assertEquals(100_000, usage.windowTokens)
        assertEquals(0.3f, usage.usedFraction)
    }

    @Test
    fun output_and_tool_results_after_the_snapshot_are_added() {
        val usage = ContextUsage(
            breakdown = breakdown,
            remainingContextTokens = 70_000,
            completedOutputTokens = 1_500,
            retainedToolTokens = 500,
        )
        assertEquals(32_000, usage.usedTokens)
    }

    /** A persisted `metadata.contextUsage` blob as v0.8.8 writes it, unknown keys included. */
    @Test
    fun a_saved_snapshot_decodes_the_breakdown_extras() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val usage = json.decodeFromString(
            ContextUsage.serializer(),
            """{"breakdown":{"maxContextTokens":787000,"instructionTokens":15100,"messageTokens":68300,
               "toolTokenCounts":{"web_search":9500,"skill":946},"deferredToolNames":["x_mcp_y"],
               "toolMessageTokens":31600,"toolMessageTokenCounts":{"read_file":20000},"futureKey":1},
               "contextBudget":787000,"remainingContextTokens":703700,"completedOutputTokens":120,
               "retainedToolTokens":0,"cacheRead":82100,"cacheWrite":596,"model":"m","provider":"p"}""",
        )

        assertEquals(mapOf("web_search" to 9500, "skill" to 946), usage.breakdown.toolTokenCounts)
        assertEquals(listOf("x_mcp_y"), usage.breakdown.deferredToolNames)
        assertEquals(31600, usage.breakdown.toolMessageTokens)
        assertEquals(82100, usage.cacheRead)
        assertEquals(596, usage.cacheWrite)
        assertEquals(83520, usage.usedTokens)
    }

    @Test
    fun a_saved_usage_rollup_decodes_with_an_absent_cost() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val usage = json.decodeFromString(ResponseUsage.serializer(), """{"input":143,"output":91100,"cacheRead":5}""")

        assertEquals(143, usage.input)
        assertEquals(0, usage.cacheWrite)
        assertEquals(null, usage.cost)
    }

    @Test
    fun resumed_output_stands_in_for_completed_output() {
        val resumed = ContextUsage(breakdown = breakdown, remainingContextTokens = 70_000, resumedOutputTokens = 2_000)
        assertEquals(32_000, resumed.usedTokens)
        val both = resumed.copy(completedOutputTokens = 1_000)
        assertEquals(31_000, both.usedTokens)
    }
}
