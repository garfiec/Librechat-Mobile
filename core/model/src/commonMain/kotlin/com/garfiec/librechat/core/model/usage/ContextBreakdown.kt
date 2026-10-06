package com.garfiec.librechat.core.model.usage

import com.garfiec.librechat.core.common.ToolConstants

/*
 * The context breakdown's rows, computed from one gauge reading. Ported from upstream web at
 * v0.8.8 (client/src/components/Chat/Input/TokenUsage/Breakdown.tsx, plus `groupToolTokens` in
 * client/src/utils/tokens.ts).
 */

/** One categorical slice of the context window; its row and its meter segment share it. */
enum class BreakdownCategory(val slot: Int, val hatched: Boolean = false) {
    MESSAGES(slot = 1),
    TOOL_CALLS(slot = 2),
    SYSTEM(slot = 3),

    /** Tool schemas when the snapshot doesn't say which tools they belong to. */
    TOOLS(slot = 4),
    TOOLS_SYSTEM(slot = 4),
    TOOLS_SYSTEM_DEFERRED(slot = 4, hatched = true),
    TOOLS_MCP(slot = 5),
    TOOLS_MCP_DEFERRED(slot = 5, hatched = true),
    SKILLS(slot = 6),
    SUBAGENTS(slot = 7),
    SUMMARY(slot = 8),

    /** Every tool-schema category as one row, when tool types aren't shown separately. */
    TOOLS_ALL(slot = 4),
}

data class BreakdownSegment(val category: BreakdownCategory, val value: Int)

enum class ContextPressure { NONE, WARN, DANGER }

data class ToolShare(val name: String, val tokens: Int)

/**
 * Everything the breakdown renders for one reading. [segments] is empty on the estimate path,
 * which knows the total but not its composition; [estimate] carries that path's rows instead.
 */
data class ContextBreakdownView(
    val usedTokens: Int,
    val windowTokens: Int,
    /** 0–100, unrounded; the meter draws it. Pressure uses the rounded [percentOf] instead. */
    val percent: Float,
    val segments: List<BreakdownSegment>,
    /** The snapshot reports a tool-call share, so its row shows even at zero. */
    val toolCallsReported: Boolean,
    val dynamicInstructionTokens: Int,
    val cacheRead: Int,
    val cacheWrite: Int,
    val freeTokens: Int,
    /** Result tokens per tool, largest first, clamped to fit the tool-call share. */
    val toolBreakdown: List<ToolShare>,
    val pressure: ContextPressure,
    val largestTool: ToolShare?,
    /** What summarizing now could free; null when compaction isn't available or would free nothing. */
    val compactionReclaim: Int?,
    val estimate: EstimateDetail?,
) {
    /** A row shows when it has tokens, and Tool calls whenever the snapshot reported it. */
    fun showsRow(segment: BreakdownSegment): Boolean =
        segment.value > 0 || (segment.category == BreakdownCategory.TOOL_CALLS && toolCallsReported)
}

/**
 * The breakdown of [usage]. [estimate] is the estimate path's detail when the reading is an
 * estimate (no segments then). [compactionReclaim] is computed by the caller from the branch
 * (it needs the latest exchange) and shown only when [compactionAvailable].
 */
fun buildContextBreakdown(
    usage: ContextUsage,
    estimate: EstimateDetail?,
    compactionAvailable: Boolean,
    compactionReclaim: Int,
): ContextBreakdownView {
    val usedTokens = usage.usedTokens.coerceAtLeast(0)
    val window = usage.windowTokens.coerceAtLeast(0)
    val percent = if (window > 0) (usedTokens.toFloat() / window * 100f).coerceIn(0f, 100f) else 0f
    val pressure = contextPressure(percentOf(usedTokens, window))
    val reclaim = compactionReclaim.takeIf { compactionAvailable && it > 0 }
    val free = (window - usedTokens).coerceAtLeast(0)

    if (estimate != null) {
        return ContextBreakdownView(
            usedTokens = usedTokens, windowTokens = window, percent = percent,
            segments = emptyList(), toolCallsReported = false, dynamicInstructionTokens = 0,
            cacheRead = 0, cacheWrite = 0, freeTokens = free, toolBreakdown = emptyList(),
            pressure = pressure, largestTool = null, compactionReclaim = reclaim, estimate = estimate,
        )
    }

    val breakdown = usage.breakdown
    val instructionTokens = (usage.effectiveInstructionTokens ?: breakdown.instructionTokens).coerceAtLeast(0)
    val dynamicInstructions = breakdown.dynamicInstructionTokens.coerceAtLeast(0)
    val systemTokens = breakdown.systemMessageTokens.coerceAtLeast(0) + dynamicInstructions
    val summaryTokens = breakdown.summaryTokens.coerceAtLeast(0)
    val retained = (usage.retainedToolTokens ?: 0).coerceAtLeast(0)
    // Summary and tool calls have rows of their own, so they come out of the Messages row.
    val messageBudget = (usedTokens - instructionTokens - summaryTokens).coerceAtLeast(0)
    // The tool-call share is a subset of pre-invoke messages; retained tool results sit outside
    // that total, so both sides widen by them before the share is bounded by what's left.
    val toolCallTokens = breakdown.toolMessageTokens?.let {
        minOf(it.coerceAtLeast(0) + retained, breakdown.messageTokens.coerceAtLeast(0) + retained, messageBudget)
    }
    val messageTokens = (messageBudget - (toolCallTokens ?: 0)).coerceAtLeast(0)

    val toolBreakdown = toolShares(breakdown.toolMessageTokenCounts, toolCallTokens)
    val segments = buildList {
        add(BreakdownSegment(BreakdownCategory.MESSAGES, messageTokens))
        add(BreakdownSegment(BreakdownCategory.TOOL_CALLS, toolCallTokens ?: 0))
        add(BreakdownSegment(BreakdownCategory.SYSTEM, systemTokens))
        val counts = breakdown.toolTokenCounts
        if (counts == null) {
            add(BreakdownSegment(BreakdownCategory.TOOLS, breakdown.toolSchemaTokens.coerceAtLeast(0)))
        } else {
            groupToolTokens(counts, breakdown.deferredToolNames).forEach { (category, value) ->
                add(BreakdownSegment(category, value))
            }
        }
        add(BreakdownSegment(BreakdownCategory.SUMMARY, summaryTokens))
    }
    return ContextBreakdownView(
        usedTokens = usedTokens,
        windowTokens = window,
        percent = percent,
        segments = segments,
        toolCallsReported = toolCallTokens != null,
        dynamicInstructionTokens = dynamicInstructions,
        cacheRead = (usage.cacheRead ?: 0).coerceAtLeast(0),
        cacheWrite = (usage.cacheWrite ?: 0).coerceAtLeast(0),
        freeTokens = free,
        toolBreakdown = toolBreakdown,
        pressure = pressure,
        largestTool = toolBreakdown.firstOrNull()?.takeIf { it.tokens > 0 },
        compactionReclaim = reclaim,
        estimate = null,
    )
}

/**
 * Schema tokens per display group: the skill tool, the subagent tool, MCP tools (named with the
 * `_mcp_` delimiter) and built-in system tools, deferred tools split out of the last two.
 * Upstream `groupToolTokens`; the order is the meter's.
 */
fun groupToolTokens(counts: Map<String, Int>, deferredToolNames: List<String>?): List<Pair<BreakdownCategory, Int>> {
    val deferred = deferredToolNames.orEmpty().toSet()
    val sums = mutableMapOf<BreakdownCategory, Int>()
    for ((name, raw) in counts) {
        val tokens = raw.coerceAtLeast(0)
        if (tokens == 0) continue
        val category = when {
            name == SKILL_TOOL -> BreakdownCategory.SKILLS
            name == ToolConstants.SUBAGENT -> BreakdownCategory.SUBAGENTS
            MCP_DELIMITER in name ->
                if (name in deferred) BreakdownCategory.TOOLS_MCP_DEFERRED else BreakdownCategory.TOOLS_MCP
            name in deferred -> BreakdownCategory.TOOLS_SYSTEM_DEFERRED
            else -> BreakdownCategory.TOOLS_SYSTEM
        }
        sums[category] = (sums[category] ?: 0) + tokens
    }
    return GROUP_ORDER.map { it to (sums[it] ?: 0) }
}

/**
 * Result tokens per tool, largest first. Each is clamped to what remains of the tool-call share,
 * so a malformed snapshot can't list more than the row it details; names past that point stay
 * listed at zero, since a known zero differs from an unavailable count.
 */
private fun toolShares(counts: Map<String, Int>?, toolCallTokens: Int?): List<ToolShare> {
    if (counts == null) return emptyList()
    var remaining = toolCallTokens ?: Int.MAX_VALUE
    return counts.entries
        .map { ToolShare(it.key, it.value.coerceAtLeast(0)) }
        .sortedByDescending { it.tokens }
        .map { share ->
            val tokens = minOf(share.tokens, remaining)
            remaining -= tokens
            share.copy(tokens = tokens)
        }
}

private val GROUP_ORDER = listOf(
    BreakdownCategory.TOOLS_SYSTEM,
    BreakdownCategory.TOOLS_SYSTEM_DEFERRED,
    BreakdownCategory.TOOLS_MCP,
    BreakdownCategory.TOOLS_MCP_DEFERRED,
    BreakdownCategory.SKILLS,
    BreakdownCategory.SUBAGENTS,
)

/** Upstream `Tools.skill`. */
private const val SKILL_TOOL = "skill"

/** Upstream `Constants.mcp_delimiter`: an MCP tool is named `<tool>_mcp_<server>`. */
private const val MCP_DELIMITER = "_mcp_"

/**
 * Pressure from the rounded percentage every surface shows, so the pill's colour, the warning
 * line and the compact nudge never disagree (79.5% reads as 80% and is amber everywhere).
 */
fun contextPressure(roundedPercent: Int): ContextPressure = when {
    roundedPercent >= DANGER_PERCENT -> ContextPressure.DANGER
    roundedPercent >= WARN_PERCENT -> ContextPressure.WARN
    else -> ContextPressure.NONE
}

internal const val WARN_PERCENT = 80
internal const val DANGER_PERCENT = 95
