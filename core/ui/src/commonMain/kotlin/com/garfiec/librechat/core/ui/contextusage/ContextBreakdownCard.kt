package com.garfiec.librechat.core.ui.contextusage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.config.CurrencyConfig
import com.garfiec.librechat.core.model.usage.BreakdownCategory
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.model.usage.ContextPressure
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.CostRows
import com.garfiec.librechat.core.model.usage.EstimateDetail
import com.garfiec.librechat.core.model.usage.VisibleBreakdown
import com.garfiec.librechat.core.model.usage.buildContextBreakdown
import com.garfiec.librechat.core.model.usage.contextPressure
import com.garfiec.librechat.core.model.usage.formatTokens
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.model.usage.visibleBreakdown
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.context_compact
import com.garfiec.librechat.core.ui.resources.context_compact_info
import com.garfiec.librechat.core.ui.resources.context_compacting
import com.garfiec.librechat.core.ui.resources.context_usage_agent_instructions
import com.garfiec.librechat.core.ui.resources.context_usage_by_tool
import com.garfiec.librechat.core.ui.resources.context_usage_cache_read
import com.garfiec.librechat.core.ui.resources.context_usage_cache_write
import com.garfiec.librechat.core.ui.resources.context_usage_cached
import com.garfiec.librechat.core.ui.resources.context_usage_cost
import com.garfiec.librechat.core.ui.resources.context_usage_cost_all
import com.garfiec.librechat.core.ui.resources.context_usage_cost_branch
import com.garfiec.librechat.core.ui.resources.context_usage_estimated
import com.garfiec.librechat.core.ui.resources.context_usage_estimated_row
import com.garfiec.librechat.core.ui.resources.context_usage_free
import com.garfiec.librechat.core.ui.resources.context_usage_input
import com.garfiec.librechat.core.ui.resources.context_usage_insights
import com.garfiec.librechat.core.ui.resources.context_usage_largest_tool
import com.garfiec.librechat.core.ui.resources.context_usage_messages
import com.garfiec.librechat.core.ui.resources.context_usage_output
import com.garfiec.librechat.core.ui.resources.context_usage_pressure_danger
import com.garfiec.librechat.core.ui.resources.context_usage_pressure_warn
import com.garfiec.librechat.core.ui.resources.context_usage_reclaim
import com.garfiec.librechat.core.ui.resources.context_usage_skills
import com.garfiec.librechat.core.ui.resources.context_usage_subagents
import com.garfiec.librechat.core.ui.resources.context_usage_subagents_all
import com.garfiec.librechat.core.ui.resources.context_usage_summary
import com.garfiec.librechat.core.ui.resources.context_usage_system
import com.garfiec.librechat.core.ui.resources.context_usage_tool_calls
import com.garfiec.librechat.core.ui.resources.context_usage_tools
import com.garfiec.librechat.core.ui.resources.context_usage_tools_all
import com.garfiec.librechat.core.ui.resources.context_usage_tools_mcp
import com.garfiec.librechat.core.ui.resources.context_usage_tools_mcp_deferred
import com.garfiec.librechat.core.ui.resources.context_usage_tools_system
import com.garfiec.librechat.core.ui.resources.context_usage_tools_system_deferred
import com.garfiec.librechat.core.ui.resources.context_usage_totals
import com.garfiec.librechat.core.ui.resources.context_usage_used
import com.garfiec.librechat.core.ui.resources.context_usage_window
import com.garfiec.librechat.core.ui.theme.LocalAppLocale
import com.garfiec.librechat.core.ui.theme.SeriesColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything the context breakdown renders: the reading plus the branch-wide totals derived from
 * it, and the server's cost settings. The chat builds it from live state; Settings shows
 * [SampleContextModel].
 */
@Immutable
data class ContextBreakdownModel(
    val usage: ContextUsage,
    val isEstimate: Boolean,
    val totals: ContextUsageTotals,
    val costEnabled: Boolean,
    val currency: CurrencyConfig?,
    val isCompacting: Boolean,
    val compactionAvailable: Boolean,
)

/** The pressure tint for a rounded usage percent: amber from 80%, the error colour from 95%. */
@Composable
fun pressureColor(roundedPercent: Int): Color? = when (contextPressure(roundedPercent)) {
    ContextPressure.NONE -> null
    ContextPressure.WARN -> SeriesColors.slot(WARNING_SLOT)
    ContextPressure.DANGER -> MaterialTheme.colorScheme.error
}

/**
 * The context breakdown, mirroring the web client's `Breakdown.tsx` (v0.8.8) and filtered to
 * [sections]: a `used / window` header, a meter whose segments are the rows below, then Totals
 * and Cost. [onCompact] adds the Compact button; null hides it (and the Settings preview passes
 * null).
 */
@Composable
fun ContextBreakdownCard(
    model: ContextBreakdownModel,
    sections: ContextDetailSections,
    modifier: Modifier = Modifier,
    onCompact: (() -> Unit)? = null,
) {
    val totals = model.totals
    val view = remember(model.usage, totals, model.compactionAvailable) {
        buildContextBreakdown(model.usage, totals.estimate, model.compactionAvailable, totals.compactionReclaim)
    }
    val visible = remember(view, sections, totals, model.costEnabled) {
        visibleBreakdown(view, sections, totals, model.costEnabled)
    }
    val percent = percentOf(view.usedTokens, view.windowTokens)
    val tint = pressureColor(percent)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(Res.string.context_usage_window), style = MaterialTheme.typography.titleMedium)
            val used = (if (model.isEstimate) "~" else "") + formatTokens(view.usedTokens)
            Text(
                text = if (view.windowTokens > 0) "$used / ${formatTokens(view.windowTokens)} ($percent%)" else used,
                style = MaterialTheme.typography.labelMedium,
                color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SegmentedMeter(visible = visible, modifier = Modifier.fillMaxWidth())

        PressureAndInsights(visible, tint)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val estimate = visible.estimate
            when {
                visible.usedAndFreeOnly -> UsedAndFreeRows(visible, model.isEstimate)
                estimate != null -> EstimateRows(estimate, view.windowTokens)
                else -> SnapshotRows(visible)
            }
        }

        visible.totals?.let { branch ->
            AdaptiveDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.context_usage_totals).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UsageRow(stringResource(Res.string.context_usage_input), branch.input)
                UsageRow(stringResource(Res.string.context_usage_output), branch.output)
                if (branch.cacheRead > 0) UsageRow(stringResource(Res.string.context_usage_cache_read), branch.cacheRead)
                if (branch.cacheWrite > 0) UsageRow(stringResource(Res.string.context_usage_cache_write), branch.cacheWrite)
                // Subagent calls aren't attributed to a reply, so they can't be scoped to this
                // branch like the rows above; labelled as spanning all branches instead.
                if (visible.subagentTokens > 0) {
                    UsageRow(stringResource(Res.string.context_usage_subagents_all), visible.subagentTokens)
                }
            }
        }

        visible.cost?.let { cost ->
            AdaptiveDivider()
            CostRows(cost, model.currency)
        }

        // Where the context usage is already being read, matching upstream's placement. Withheld
        // entirely rather than disabled when the server does not offer compaction.
        if (onCompact != null) {
            AdaptiveDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AdaptiveOutlinedButton(onClick = onCompact, enabled = !model.isCompacting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (model.isCompacting) Res.string.context_compacting else Res.string.context_compact))
                }
                Text(
                    text = stringResource(Res.string.context_compact_info),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The pressure warning, and an ⓘ that reveals the insights inline (there is no hover on touch). */
@Composable
private fun PressureAndInsights(visible: VisibleBreakdown, tint: Color?) {
    val insights = buildList {
        visible.largestTool?.let {
            add(stringResource(Res.string.context_usage_largest_tool, it.name, formatTokens(it.tokens)))
        }
        visible.compactionReclaim?.let { add(stringResource(Res.string.context_usage_reclaim, formatTokens(it))) }
    }
    if (!visible.showWarning && insights.isEmpty()) return
    var insightsOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (visible.showWarning && tint != null) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (visible.view.pressure == ContextPressure.DANGER) {
                            Res.string.context_usage_pressure_danger
                        } else {
                            Res.string.context_usage_pressure_warn
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (insights.isNotEmpty()) {
                IconButton(onClick = { insightsOpen = !insightsOpen }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(Res.string.context_usage_insights),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        AnimatedVisibility(visible = insightsOpen && insights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                insights.forEach {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Simple: how much of the window is used and how much is left. */
@Composable
private fun UsedAndFreeRows(visible: VisibleBreakdown, isEstimate: Boolean) {
    val view = visible.view
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UsageRow(stringResource(Res.string.context_usage_used), view.usedTokens, view.windowTokens)
        UsageRow(stringResource(Res.string.context_usage_free), view.freeTokens, view.windowTokens, swatch = Swatch.Track)
        if (isEstimate) EstimateNote()
    }
}

/** Snapshot rows: one per meter segment (swatched), the cache shares indented, then free space. */
@Composable
private fun SnapshotRows(visible: VisibleBreakdown) {
    var toolsExpanded by remember { mutableStateOf(false) }
    val max = visible.view.windowTokens
    visible.rows.forEach { segment ->
        val category = segment.category
        val expandable = category == BreakdownCategory.TOOL_CALLS && visible.toolBreakdown.isNotEmpty()
        UsageRow(
            label = stringResource(category.label),
            tokens = segment.value,
            max = max,
            swatch = Swatch.Series(category.slot, category.hatched),
            onClick = if (expandable) ({ toolsExpanded = !toolsExpanded }) else null,
            expanded = toolsExpanded.takeIf { expandable },
        )
        if (expandable) {
            AnimatedVisibility(visible = toolsExpanded) {
                Column(modifier = Modifier.padding(start = INDENT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.context_usage_by_tool),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    visible.toolBreakdown.forEach { UsageRow(it.name, it.tokens) }
                }
            }
        }
        if (category == BreakdownCategory.SYSTEM && visible.dynamicInstructionTokens > 0) {
            UsageRow(stringResource(Res.string.context_usage_agent_instructions), visible.dynamicInstructionTokens, indent = true)
        }
    }
    // The reconciled call's cached prompt is already inside the segments above, so its rows are
    // indented subtotals; as peers the visible rows would sum past the meter.
    if (visible.cacheRead > 0) UsageRow(stringResource(Res.string.context_usage_cached), visible.cacheRead, max, indent = true)
    if (visible.cacheWrite > 0) {
        UsageRow(stringResource(Res.string.context_usage_cache_write), visible.cacheWrite, max, indent = true)
    }
    UsageRow(stringResource(Res.string.context_usage_free), visible.view.freeTokens, max, swatch = Swatch.Track)
}

/** Estimate rows: the composition is unknown, so counts by author and a note, with no swatches. */
@Composable
private fun EstimateRows(estimate: EstimateDetail, max: Int) {
    if (estimate.summaryBaseline > 0) {
        UsageRow(stringResource(Res.string.context_usage_summary), estimate.summaryBaseline, max)
    }
    if (estimate.messagesPruned) {
        // Over the window: the per-author split no longer describes what's sent.
        UsageRow(stringResource(Res.string.context_usage_messages), estimate.messageTokens, max)
    } else {
        UsageRow(stringResource(Res.string.context_usage_input), estimate.input)
        UsageRow(stringResource(Res.string.context_usage_output), estimate.output)
        if (estimate.estimated > 0) UsageRow(stringResource(Res.string.context_usage_estimated_row), estimate.estimated)
    }
    // Already inside the rows above (their tool-content share), so an indented subtotal.
    if (estimate.toolCallTokens > 0) {
        UsageRow(stringResource(Res.string.context_usage_tool_calls), estimate.toolCallTokens, indent = true)
    }
    if (estimate.overheadTokens > 0) UsageRow(stringResource(Res.string.context_usage_system), estimate.overheadTokens)
    EstimateNote()
}

@Composable
private fun EstimateNote() {
    Text(
        text = stringResource(Res.string.context_usage_estimated),
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CostRows(cost: CostRows, currency: CurrencyConfig?) {
    val locale = LocalAppLocale.current
    val label = when {
        cost.primaryIsWholeConversation || cost.allBranches == null -> Res.string.context_usage_cost
        else -> Res.string.context_usage_cost_branch
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = formatCost(cost.primary, currency, locale),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        cost.allBranches?.let { total ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(Res.string.context_usage_cost_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCost(total, currency, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private sealed interface Swatch {
    data class Series(val slot: Int, val hatched: Boolean) : Swatch

    /** The free-space remainder, keyed to the bare meter track rather than a series. */
    data object Track : Swatch
}

@Composable
private fun UsageRow(
    label: String,
    tokens: Int,
    max: Int = 0,
    swatch: Swatch? = null,
    indent: Boolean = false,
    onClick: (() -> Unit)? = null,
    expanded: Boolean? = null,
) {
    val percent = if (max > 0) percentOf(tokens, max) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (indent) INDENT else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (swatch) {
                is Swatch.Series -> SwatchDot(SeriesColors.slot(swatch.slot), swatch.hatched)
                Swatch.Track -> Box(
                    Modifier
                        .size(SWATCH)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, SWATCH_SHAPE)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SWATCH_SHAPE),
                )
                null -> Unit
            }
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded != null) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatTokens(tokens),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (percent != null) {
                Text(text = " ($percent%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val BreakdownCategory.label: StringResource
    get() = when (this) {
        BreakdownCategory.MESSAGES -> Res.string.context_usage_messages
        BreakdownCategory.TOOL_CALLS -> Res.string.context_usage_tool_calls
        BreakdownCategory.SYSTEM -> Res.string.context_usage_system
        BreakdownCategory.TOOLS -> Res.string.context_usage_tools
        BreakdownCategory.TOOLS_SYSTEM -> Res.string.context_usage_tools_system
        BreakdownCategory.TOOLS_SYSTEM_DEFERRED -> Res.string.context_usage_tools_system_deferred
        BreakdownCategory.TOOLS_MCP -> Res.string.context_usage_tools_mcp
        BreakdownCategory.TOOLS_MCP_DEFERRED -> Res.string.context_usage_tools_mcp_deferred
        BreakdownCategory.SKILLS -> Res.string.context_usage_skills
        BreakdownCategory.SUBAGENTS -> Res.string.context_usage_subagents
        BreakdownCategory.SUMMARY -> Res.string.context_usage_summary
        BreakdownCategory.TOOLS_ALL -> Res.string.context_usage_tools_all
    }

/** The series slot used for the "filling up" tint (amber in both themes). */
private const val WARNING_SLOT = 4
private val INDENT = 24.dp
