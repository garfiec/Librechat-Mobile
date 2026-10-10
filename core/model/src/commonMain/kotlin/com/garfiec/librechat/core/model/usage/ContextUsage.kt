package com.garfiec.librechat.core.model.usage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-model-call token budget breakdown (v0.8.7). Mirrors upstream
 * `TTokenBudgetBreakdown` — the inputs that make up a context window for one call.
 */
@Serializable
data class TokenBudgetBreakdown(
    val maxContextTokens: Int = 0,
    val instructionTokens: Int = 0,
    val systemMessageTokens: Int = 0,
    val dynamicInstructionTokens: Int = 0,
    val toolSchemaTokens: Int = 0,
    val summaryTokens: Int = 0,
    val toolCount: Int = 0,
    val messageCount: Int = 0,
    val messageTokens: Int = 0,
    val availableForMessages: Int = 0,
    /** Schema tokens per tool name; drives the System tools / MCP / Skills / Subagents split. */
    val toolTokenCounts: Map<String, Int>? = null,
    /** Tools loaded on demand rather than sent every call; shown hatched. */
    val deferredToolNames: List<String>? = null,
    /** The tool-call share of [messageTokens]. Absent on older snapshots, which keep one Messages row. */
    val toolMessageTokens: Int? = null,
    /** Result tokens per tool name, a subset of [toolMessageTokens]. */
    val toolMessageTokenCounts: Map<String, Int>? = null,
)

/**
 * Context-window usage snapshot for the current branch/config (v0.8.7). Carried
 * by the `on_context_usage` SSE event, returned by the context-projection
 * endpoint, and (v0.8.8) persisted on a response's `metadata.contextUsage`.
 * Mirrors upstream `TContextUsageEvent`.
 */
@Serializable
data class ContextUsage(
    val breakdown: TokenBudgetBreakdown = TokenBudgetBreakdown(),
    /** Usable budget this call: maxContextTokens minus output reserve. */
    val contextBudget: Int? = null,
    val effectiveInstructionTokens: Int? = null,
    val prePruneContextTokens: Int? = null,
    /** Tokens still free after instructions + pruned messages. */
    val remainingContextTokens: Int? = null,
    val calibrationRatio: Double? = null,
    val runId: String? = null,
    val agentId: String? = null,
    /**
     * Output of the response's final model call, which this pre-call snapshot predates. Only on
     * the persisted `metadata.contextUsage` blob, so a reloaded turn counts the same output the
     * live stream had produced.
     */
    val completedOutputTokens: Int? = null,
    /** Same delta as [completedOutputTokens], under the name the resume frame carries it. */
    val resumedOutputTokens: Int? = null,
    /** Tool results produced after the snapshot that stay in context for the next call. */
    val retainedToolTokens: Int? = null,
    /** The reconciled call's cached prompt share, part of the used context, not an addition. */
    val cacheRead: Int? = null,
    val cacheWrite: Int? = null,
) {
    /** The raw context window size from the breakdown. */
    val maxContextTokens: Int get() = breakdown.maxContextTokens

    /** The gauge denominator: the usable budget when the server reports one, else the raw window. */
    val windowTokens: Int get() = contextBudget ?: breakdown.maxContextTokens

    /**
     * Tokens consumed of the window (upstream `useTokenUsage`, v0.8.8): window minus the server's
     * [remainingContextTokens], floored at the breakdown's own instruction + summary + message
     * total, plus the output and tool results that landed after this pre-call snapshot.
     *
     * The floor exists because a remaining count measured against a smaller instruction total than
     * the snapshot publishes would put used below the shares the breakdown subtracts, hiding the
     * Messages row. The instruction total already includes the system-message and tool-schema
     * shares, so they are not added again.
     */
    val usedTokens: Int
        get() {
            val breakdownUsed = (effectiveInstructionTokens ?: breakdown.instructionTokens) +
                breakdown.summaryTokens +
                breakdown.messageTokens
            val baseUsed = remainingContextTokens
                ?.let { maxOf(windowTokens - it, breakdownUsed) }
                ?: breakdownUsed
            val postSnapshot = (completedOutputTokens ?: resumedOutputTokens ?: 0).coerceAtLeast(0) +
                (retainedToolTokens ?: 0).coerceAtLeast(0)
            return baseUsed.coerceAtLeast(0) + postSnapshot
        }

    /** Fraction of the window used, clamped to 0..1. Zero when the window is unknown. */
    val usedFraction: Float
        get() = if (windowTokens > 0) {
            (usedTokens.toFloat() / windowTokens.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Provider-reported usage for a single completed model call (v0.8.7
 * `on_token_usage`). Mirrors upstream `TTokenUsageEvent` (snake_case wire keys).
 */
@Serializable
data class TokenUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val model: String? = null,
    val provider: String? = null,
    /**
     * Names a non-primary usage bucket when present — upstream emits `summarization`,
     * `subagent`, `sequential`, `activity-label`, `activity-phase` and `reasoning-label`.
     * Absent on the turn's own model call. Every bucket counts toward the breakdown's Totals and
     * cost, as on web; none of them describes the context window.
     *
     * Deliberately a plain `String?` and never an enum: a bucket upstream adds later must decode.
     */
    @SerialName("usage_type") val usageType: String? = null,
    @SerialName("input_token_details") val inputTokenDetails: InputTokenDetails? = null,
    /** With [seq], identifies one model call, so a call replayed on resume is counted once. */
    val runId: String? = null,
    val seq: Int? = null,
    /** Authoritative USD cost of this call; present only when `interface.contextCost` is on. */
    val cost: Double? = null,
)

/** Prompt-cache traffic of one model call. */
@Serializable
data class InputTokenDetails(
    @SerialName("cache_creation") val cacheCreation: Int? = null,
    @SerialName("cache_read") val cacheRead: Int? = null,
)

/**
 * Per-response usage rollup persisted on `metadata.usage` (v0.8.8), in display units: input
 * excludes cache, output includes repaired completion. Mirrors upstream `TResponseUsage`.
 */
@Serializable
data class ResponseUsage(
    val input: Int = 0,
    val output: Int = 0,
    val cacheWrite: Int = 0,
    val cacheRead: Int = 0,
    /** Present only when `interface.contextCost` was on when the reply was saved. */
    val cost: Double? = null,
)

/** Per-model context window + (optional) pricing. Mirrors upstream `TModelTokenomics`. */
@Serializable
data class ModelTokenomics(
    val context: Int? = null,
    val prompt: Double? = null,
    val completion: Double? = null,
    val cacheWrite: Double? = null,
    val cacheRead: Double? = null,
)
