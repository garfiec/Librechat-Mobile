package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.usage.InputTokenDetails
import com.garfiec.librechat.core.model.usage.ResponseUsage
import com.garfiec.librechat.core.model.usage.TokenUsage
import com.garfiec.librechat.core.model.usage.UsageAmount
import com.garfiec.librechat.core.model.usage.showsAllBranchesCost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from upstream web (v0.8.8): `normalizeUsageUnits`, `readPersistedUsage`, the pending fold. */
class UsageTotalsTest {

    private fun message(id: String, usage: ResponseUsage? = null) = Message(
        messageId = id,
        conversationId = "c",
        metadata = usage?.let { JsonObject(mapOf("usage" to Json.encodeToJsonElement(ResponseUsage.serializer(), it))) },
    )

    // --- normalization ---

    @Test
    fun a_cache_subset_provider_reports_input_net_of_cache() {
        val units = TokenUsage(
            inputTokens = 1000,
            outputTokens = 200,
            provider = "anthropic",
            inputTokenDetails = InputTokenDetails(cacheCreation = 100, cacheRead = 600),
        ).normalizedUnits()

        assertEquals(300, units.input)
        assertEquals(600, units.cacheRead)
        assertEquals(100, units.cacheWrite)
        assertEquals(200, units.output)
    }

    @Test
    fun an_additive_provider_keeps_input_whole() {
        val units = TokenUsage(
            inputTokens = 1000,
            outputTokens = 200,
            provider = "bedrock",
            inputTokenDetails = InputTokenDetails(cacheRead = 600),
        ).normalizedUnits()

        assertEquals(1000, units.input)
        assertEquals(600, units.cacheRead)
    }

    @Test
    fun output_includes_completion_the_provider_left_out_of_output_tokens() {
        val units = TokenUsage(inputTokens = 100, outputTokens = 50, totalTokens = 200, provider = "openAI").normalizedUnits()

        assertEquals(100, units.output)
    }

    @Test
    fun a_call_without_a_cost_makes_the_cost_unknown() {
        assertFalse(TokenUsage(inputTokens = 1).normalizedUnits().costKnown)
        assertTrue(TokenUsage(inputTokens = 1, cost = 0.01).normalizedUnits().costKnown)
    }

    // --- folding ---

    @Test
    fun a_call_seen_twice_is_counted_once_and_subagent_calls_are_tracked_apart() {
        val call = TokenUsage(inputTokens = 100, outputTokens = 10, runId = "r", seq = 1)
        val sub = TokenUsage(inputTokens = 50, outputTokens = 5, runId = "r", seq = 2, usageType = "subagent")

        val pending = PendingUsage.EMPTY.fold(call).fold(call).fold(sub).fold(sub)

        assertEquals(150, pending.usage.input)
        assertEquals(50, pending.subagent.input)
    }

    // --- persisted and attribution ---

    @Test
    fun saved_usage_sums_over_the_scope_and_a_reply_without_a_cost_breaks_coverage() {
        val branch = listOf(
            message("a1", ResponseUsage(input = 100, output = 20, cost = 0.5)),
            message("a2", ResponseUsage(input = 50, output = 10)),
        )

        val usage = usageOver(branch, branch, PendingUsage.EMPTY, isStreaming = false)

        assertEquals(150, usage.input)
        assertEquals(30, usage.output)
        assertFalse(usage.costKnown)
    }

    private val pending = PendingUsage.EMPTY.fold(TokenUsage(inputTokens = 900, runId = "r", seq = 1))

    @Test
    fun pending_counts_while_streaming() {
        val branch = listOf(message("a1", ResponseUsage(input = 100)))

        assertEquals(1000, usageOver(branch, branch, pending, isStreaming = true).input)
    }

    @Test
    fun pending_stops_counting_once_its_reply_carries_its_own_usage() {
        val anchored = pending.copy(anchorResponseId = "a2")
        val withoutUsage = listOf(message("a1", ResponseUsage(input = 100)), message("a2"))
        val withUsage = listOf(message("a1", ResponseUsage(input = 100)), message("a2", ResponseUsage(input = 900)))

        assertEquals(1000, usageOver(withoutUsage, withoutUsage, anchored, isStreaming = false).input)
        assertEquals(1000, usageOver(withUsage, withUsage, anchored, isStreaming = false).input)
    }

    @Test
    fun a_reply_not_yet_loaded_still_counts_and_one_off_the_branch_counts_only_in_the_total() {
        val anchored = pending.copy(anchorResponseId = "a2")
        val branch = listOf(message("a1", ResponseUsage(input = 100)))

        // The comparison reload gap: the reply isn't in the conversation yet.
        assertEquals(1000, usageOver(branch, branch, anchored, isStreaming = false).input)
        // Regenerated away from: it is in the conversation, just not on this branch.
        val all = branch + message("a2")
        assertEquals(100, usageOver(branch, all, anchored, isStreaming = false).input)
        assertEquals(1000, usageOver(all, all, anchored, isStreaming = false).input)
    }

    @Test
    fun the_all_branches_cost_shows_only_when_larger_and_fully_covered() {
        val branch = UsageAmount(cost = 1.0)

        assertFalse(showsAllBranchesCost(branch, UsageAmount(cost = 1.0)))
        assertTrue(showsAllBranchesCost(branch, UsageAmount(cost = 1.5)))
        assertFalse(showsAllBranchesCost(branch, UsageAmount(cost = 1.5, costKnown = false)))
    }
}
