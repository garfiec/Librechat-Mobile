package com.garfiec.librechat.core.model.usage

/*
 * Figures behind the context breakdown's Totals, Cost and estimate rows. Computed in the chat
 * feature (from messages and live usage) and rendered by core:ui.
 */

/** Token counts in display units (input excludes cache) plus the cost, and whether every part had one. */
data class UsageAmount(
    val input: Int = 0,
    val output: Int = 0,
    val cacheWrite: Int = 0,
    val cacheRead: Int = 0,
    val cost: Double = 0.0,
    /** False once any summed part was saved without a cost: the total would under-report. */
    val costKnown: Boolean = true,
) {
    val tokens: Int get() = input + output + cacheRead + cacheWrite

    operator fun plus(other: UsageAmount) = UsageAmount(
        input = input + other.input,
        output = output + other.output,
        cacheWrite = cacheWrite + other.cacheWrite,
        cacheRead = cacheRead + other.cacheRead,
        cost = cost + other.cost,
        costKnown = costKnown && other.costKnown,
    )

    companion object {
        val EMPTY = UsageAmount()
    }
}

/**
 * The breakdown's figures that depend on the whole branch, derived by `ContextProjectionDelegate`
 * on every branch or usage change so the UI never walks messages itself.
 */
data class ContextUsageTotals(
    /** Provider usage along the displayed branch, the live reply included. */
    val branch: UsageAmount = UsageAmount.EMPTY,
    /** Provider usage across every branch of the conversation. */
    val total: UsageAmount = UsageAmount.EMPTY,
    /** Subagent calls this session, across branches (they aren't attributed to a reply). */
    val subagent: UsageAmount = UsageAmount.EMPTY,
    /** What summarizing now could free, for a snapshot reading; 0 otherwise. */
    val compactionReclaim: Int = 0,
    /** The estimate path's rows, when the reading is an estimate. */
    val estimate: EstimateDetail? = null,
) {
    val hasUsage: Boolean get() = branch.tokens > 0
}

/**
 * What a snapshot-less estimate is made of, for the breakdown's estimate rows (upstream
 * `sumBranch` + the estimate block of `useTokenUsage`).
 *
 * [input] and [output] are stored counts by author, [estimated] the char-based share of
 * messages that have none; when pruning applied ([messagesPruned]) those no longer describe what
 * is sent and only [messageTokens] does. [toolCallTokens] is the tool-call share of the messages
 * (a subset, never an addition). [overheadTokens] is the cached instruction overhead.
 */
data class EstimateDetail(
    val input: Int,
    val output: Int,
    val estimated: Int,
    val toolCallTokens: Int,
    val overheadTokens: Int,
    val summaryBaseline: Int,
    val messageTokens: Int,
    val messagesPruned: Boolean,
) {
    val usedTokens: Int get() = overheadTokens + summaryBaseline + messageTokens
}
