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
}
