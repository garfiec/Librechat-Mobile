package com.garfiec.librechat.core.model.usage

import kotlinx.serialization.Serializable

/*
 * How much of the context breakdown a user wants to see, and the compact-suggestion bands. The
 * full breakdown is always computed ([buildContextBreakdown]); these decide what renders, so the
 * chat surfaces and the Settings preview draw exactly the same thing.
 */

/** Which cost the breakdown shows. */
@Serializable
enum class ContextCostScope {
    OFF,

    /** One row: the cost of the whole conversation, every branch included. */
    WHOLE_CONVERSATION,

    /** This branch, plus all branches when they differ (web's layout). */
    BOTH,
}

/** The sections a user can turn on and off ("Custom" in Settings). */
@Serializable
data class ContextDetailSections(
    /** Category rows (Messages, Tool calls, System prompt, …); off leaves Used and Free. */
    val breakdown: Boolean,
    /** Tools split by type (system, MCP, skills, subagents, deferred) and the "By tool" list. */
    val toolTypes: Boolean,
    /** The cached and cache-write subtotals. */
    val cacheDetail: Boolean,
    /** The pressure warning line and the ⓘ insights. Pressure colour shows regardless. */
    val insights: Boolean,
    /** Provider token totals (input, output, cache). */
    val totals: Boolean,
    val cost: ContextCostScope,
)

/** The detail presets offered in Settings. */
enum class ContextDetailPreset(val sections: ContextDetailSections) {
    /** Used vs free, nothing else. */
    SIMPLE(ContextDetailSections(false, false, false, false, false, ContextCostScope.OFF)),

    /** Category rows with tools as one row, and the conversation's cost. The default. */
    STANDARD(ContextDetailSections(true, false, false, false, false, ContextCostScope.WHOLE_CONVERSATION)),

    /** Everything web shows. */
    DETAILED(ContextDetailSections(true, true, true, true, true, ContextCostScope.BOTH)),
    ;

    companion object {
        val DEFAULT = STANDARD

        fun fromString(value: String?): ContextDetailPreset = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** One cost line to render; [allBranches] is the conversation total, shown beside this branch. */
data class CostRows(val primary: Double, val primaryIsWholeConversation: Boolean, val allBranches: Double?)

/**
 * The reply at the branch's tail (upstream's "Last turn" panel). [usage] null renders the empty
 * line: "waiting for model usage" while [inProgress], "not available" once the reply settled
 * without any. [cost] is shown in the header, and only when known.
 */
data class LastTurnUsage(val usage: UsageAmount?, val inProgress: Boolean, val cost: Double?)

/** Usage summed over every branch, listed when it differs from this branch's ([showsAllBranches]). */
data class AllBranchesUsage(val usage: UsageAmount, val cost: Double?)

/** What the breakdown renders for a reading under some [ContextDetailSections]. */
data class VisibleBreakdown(
    val view: ContextBreakdownView,
    /** Category rows (and meter segments) in order; empty for Simple and for estimates. */
    val rows: List<BreakdownSegment>,
    /** Simple: only Used and Free (an estimate keeps its note). */
    val usedAndFreeOnly: Boolean,
    val toolBreakdown: List<ToolShare>,
    val dynamicInstructionTokens: Int,
    val cacheRead: Int,
    val cacheWrite: Int,
    val showWarning: Boolean,
    val largestTool: ToolShare?,
    val compactionReclaim: Int?,
    val estimate: EstimateDetail?,
    /** Totals to list, or null when hidden or there is no usage. */
    val totals: UsageAmount?,
    val subagentTokens: Int,
    /** A separate Cost section; null when hidden, or when the costs sit in the usage block headers. */
    val cost: CostRows?,
    /** The tail reply's usage, above Totals; null when Totals are hidden or the chat has no reply yet. */
    val lastTurn: LastTurnUsage? = null,
    /** This branch's cost in the Totals header, when the costs sit there ([ContextCostScope.BOTH]). */
    val totalsCost: Double? = null,
    /** Every branch, below Totals, when it differs from this branch. */
    val allBranches: AllBranchesUsage? = null,
)

/**
 * Filters a complete [view] down to [sections]. [costEnabled] is the server's
 * `interface.contextCost`; a cost is shown only when every part of it is known, so a reply saved
 * without one can't make the figure under-report.
 */
fun visibleBreakdown(
    view: ContextBreakdownView,
    sections: ContextDetailSections,
    totals: ContextUsageTotals,
    costEnabled: Boolean,
): VisibleBreakdown {
    val estimate = view.estimate
    val headerCosts = headerCosts(sections, costEnabled)
    val rows = when {
        !sections.breakdown || estimate != null -> emptyList()
        sections.toolTypes -> view.segments.filter(view::showsRow)
        else -> mergeTools(view)
    }
    return VisibleBreakdown(
        view = view,
        rows = rows,
        usedAndFreeOnly = !sections.breakdown,
        toolBreakdown = if (sections.breakdown && sections.toolTypes) view.toolBreakdown else emptyList(),
        dynamicInstructionTokens = if (sections.breakdown) view.dynamicInstructionTokens else 0,
        cacheRead = if (sections.breakdown && sections.cacheDetail) view.cacheRead else 0,
        cacheWrite = if (sections.breakdown && sections.cacheDetail) view.cacheWrite else 0,
        showWarning = sections.insights && view.pressure != ContextPressure.NONE,
        largestTool = view.largestTool.takeIf { sections.insights },
        compactionReclaim = view.compactionReclaim.takeIf { sections.insights },
        estimate = estimate.takeIf { sections.breakdown },
        totals = totals.branch.takeIf { sections.totals && totals.hasUsage },
        subagentTokens = if (sections.totals) totals.subagent.tokens else 0,
        cost = if (headerCosts) null else costRows(sections.cost, totals, costEnabled),
        lastTurn = if (sections.totals) lastTurnUsage(totals, headerCosts) else null,
        totalsCost = totals.branch.knownCost().takeIf { headerCosts && totals.hasUsage },
        allBranches = if (sections.totals && totals.hasUsage && showsAllBranches(totals.branch, totals.total)) {
            AllBranchesUsage(totals.total, totals.total.knownCost().takeIf { headerCosts })
        } else {
            null
        },
    )
}

/**
 * Under [ContextCostScope.BOTH] with Totals on, each usage block carries its own cost in its header
 * (web's layout), replacing the separate Cost section. Any other scope keeps the one cost line.
 */
private fun headerCosts(sections: ContextDetailSections, costEnabled: Boolean): Boolean =
    costEnabled && sections.totals && sections.cost == ContextCostScope.BOTH

private fun UsageAmount.knownCost(): Double? = cost.takeIf { costKnown && it.isFinite() }

/**
 * Last turn shows once the chat has something to report on: a reply in progress, a reply with
 * usage, or a branch that has usage (so a reply saved without any reads "not available").
 */
private fun lastTurnUsage(totals: ContextUsageTotals, headerCosts: Boolean): LastTurnUsage? {
    val usage = totals.lastTurn
    if (!totals.turnInProgress && usage == null && !totals.hasUsage) return null
    return LastTurnUsage(
        usage = usage,
        inProgress = totals.turnInProgress,
        cost = usage?.knownCost()?.takeIf { headerCosts },
    )
}

/**
 * Whether "All branches" differs from this branch (upstream `showTotal`, `Breakdown.tsx`): any
 * token bucket differs, or both costs are complete and the total is higher. Token differences
 * count whatever the cost coverage, so a partially priced history can't hide a real difference.
 */
fun showsAllBranches(branch: UsageAmount, total: UsageAmount): Boolean =
    total.input != branch.input ||
        total.output != branch.output ||
        total.cacheRead != branch.cacheRead ||
        total.cacheWrite != branch.cacheWrite ||
        (branch.costKnown && showsAllBranchesCost(branch, total))

private fun costRows(scope: ContextCostScope, totals: ContextUsageTotals, costEnabled: Boolean): CostRows? {
    if (!costEnabled || !totals.hasUsage) return null
    return when (scope) {
        ContextCostScope.OFF -> null
        ContextCostScope.WHOLE_CONVERSATION ->
            totals.total.takeIf { it.costKnown }?.let { CostRows(it.cost, primaryIsWholeConversation = true, null) }
        ContextCostScope.BOTH -> totals.branch.takeIf { it.costKnown }?.let { branch ->
            CostRows(
                primary = branch.cost,
                primaryIsWholeConversation = false,
                allBranches = totals.total.cost.takeIf { showsAllBranchesCost(branch, totals.total) },
            )
        }
    }
}

/** Every tool-schema category folded into one "Tools" row, where the first of them stood. */
private fun mergeTools(view: ContextBreakdownView): List<BreakdownSegment> {
    val toolTotal = view.segments.filter { it.category in TOOL_CATEGORIES }.sumOf { it.value }
    val merged = mutableListOf<BreakdownSegment>()
    var placed = false
    for (segment in view.segments) {
        if (segment.category in TOOL_CATEGORIES) {
            if (!placed && toolTotal > 0) merged += BreakdownSegment(BreakdownCategory.TOOLS_ALL, toolTotal)
            placed = true
        } else if (view.showsRow(segment)) {
            merged += segment
        }
    }
    return merged
}

private val TOOL_CATEGORIES = setOf(
    BreakdownCategory.TOOLS,
    BreakdownCategory.TOOLS_SYSTEM,
    BreakdownCategory.TOOLS_SYSTEM_DEFERRED,
    BreakdownCategory.TOOLS_MCP,
    BreakdownCategory.TOOLS_MCP_DEFERRED,
    BreakdownCategory.SKILLS,
    BreakdownCategory.SUBAGENTS,
)

/** The all-branches cost is worth showing only when it exceeds this branch and is fully covered. */
fun showsAllBranchesCost(branch: UsageAmount, total: UsageAmount): Boolean =
    total.costKnown && total.cost.isFinite() && branch.cost.isFinite() && total.cost - branch.cost > ALL_BRANCHES_EPSILON

/** Guards against float summation order surfacing an all-branches row in an unbranched chat. */
private const val ALL_BRANCHES_EPSILON = 1e-9

/**
 * The compact-suggestion band for a rounded usage [percent]: 0 from [threshold] to 79, 1 from 80
 * to 94, 2 from 95. Null below the threshold or when the suggestion is off (`threshold <= 0`). A
 * threshold of 80 starts at band 1, so a "Not now" there still re-arms at 95.
 */
fun compactNudgeBand(percent: Int, threshold: Int): Int? = when {
    threshold <= 0 || percent < threshold -> null
    percent >= DANGER_PERCENT -> 2
    percent >= WARN_PERCENT -> 1
    else -> 0
}

/** Thresholds offered in Settings; 0 is Off. */
val COMPACT_NUDGE_THRESHOLDS = listOf(0, 60, 70, 80)
const val DEFAULT_COMPACT_NUDGE_THRESHOLD = 70
