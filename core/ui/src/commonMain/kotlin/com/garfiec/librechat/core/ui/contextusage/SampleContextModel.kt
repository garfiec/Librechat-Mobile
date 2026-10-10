package com.garfiec.librechat.core.ui.contextusage

import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.core.model.usage.UsageAmount

/**
 * A realistic reading for the Settings previews: an agent chat with tool calls, a skill and a warm
 * prompt cache, about 11% into a large window. Fixed numbers, so the preview only changes when the
 * user's choices do.
 */
val SampleContextModel = ContextBreakdownModel(
    usage = ContextUsage(
        effectiveInstructionTokens = 15_100,
        remainingContextTokens = 703_700,
        cacheRead = 82_100,
        cacheWrite = 596,
        breakdown = TokenBudgetBreakdown(
            maxContextTokens = 787_000,
            instructionTokens = 15_100,
            systemMessageTokens = 4_600,
            toolSchemaTokens = 10_500,
            messageTokens = 68_300,
            toolTokenCounts = mapOf("web_search" to 6_200, "execute_code" to 3_300, "skill" to 946),
            toolMessageTokens = 31_600,
            toolMessageTokenCounts = mapOf("web_search" to 22_400, "execute_code" to 9_200),
        ),
    ),
    isEstimate = false,
    totals = ContextUsageTotals(
        branch = UsageAmount(input = 143, output = 91_100, cacheRead = 1_300_000, cacheWrite = 134_100, cost = 2.60),
        total = UsageAmount(input = 210, output = 133_000, cacheRead = 1_900_000, cacheWrite = 190_000, cost = 3.89),
        compactionReclaim = 41_000,
    ),
    costEnabled = true,
    currency = null,
    isCompacting = false,
    compactionAvailable = true,
)
