package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownCard
import com.garfiec.librechat.core.ui.contextusage.pressureColor
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.compact_confirm_action
import com.garfiec.librechat.feature.chat.resources.compact_confirm_body
import com.garfiec.librechat.feature.chat.resources.compact_confirm_not_now
import com.garfiec.librechat.feature.chat.resources.compact_confirm_title
import com.garfiec.librechat.feature.chat.resources.compact_nudge_action
import com.garfiec.librechat.feature.chat.resources.compact_nudge_short
import com.garfiec.librechat.feature.chat.resources.context_usage_label
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import org.jetbrains.compose.resources.stringResource

/**
 * Compact context-window usage gauge (v0.8.7). A slim pill with a thin progress bar and a
 * "used / window" percentage; tapping opens a breakdown sheet. The caller gates visibility on
 * `contextUsageEnabled` and a non-null snapshot.
 *
 * When compacting is suggested the pill turns tonal and gains a separate compact control, which
 * opens a confirmation rather than compacting, so a stray tap near the composer does nothing.
 */
@Composable
fun ContextUsageGauge(
    details: ContextGaugeDetails,
    modifier: Modifier = Modifier,
    onCompact: (() -> Unit)? = null,
    onSnoozeCompact: () -> Unit = {},
) {
    var showSheet by remember { mutableStateOf(false) }
    var confirmCompact by remember { mutableStateOf(false) }
    val usage = details.usage
    val nudge = details.nudge.takeIf { onCompact != null }

    Surface(
        onClick = { showSheet = true },
        shape = RoundedCornerShape(8.dp),
        color = if (nudge != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        ContextGaugePill(
            percent = percentOf(usage.usedTokens, usage.windowTokens),
            usedFraction = usage.usedFraction,
            isEstimate = details.isEstimate,
        ) {
            if (nudge != null) {
                IconButton(onClick = { confirmCompact = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Compress,
                        contentDescription = stringResource(Res.string.compact_nudge_action),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showSheet) {
        ContextUsageSheet(details = details, onDismiss = { showSheet = false }, onCompact = onCompact)
    }
    if (confirmCompact && nudge != null && onCompact != null) {
        CompactConfirmDialog(
            percent = nudge.percent,
            onDismiss = { confirmCompact = false },
            onNotNow = {
                confirmCompact = false
                onSnoozeCompact()
            },
            onCompact = {
                confirmCompact = false
                onCompact()
            },
        )
    }
}

/**
 * Context-usage gauge styled as a top-bar overflow-menu entry: a standard [DropdownMenuItem] (so it
 * shares the menu's leading-icon column, padding, and row height with the other actions instead of
 * floating as a padded pill) with the label and bar + percentage trailing. Tapping it dismisses the
 * menu (via [onClick]) and the host opens the breakdown [ContextUsageSheet] — the breakdown can't be
 * opened from inside the open menu popup itself (that would nest modal surfaces), so the menu hands
 * the trigger up to its host (see [ChatFloatingTopBar]). The bar is a fixed width (not weighted): a
 * DropdownMenu measures its content at IntrinsicSize.Max, where a stretchy child would blow the menu
 * out to full screen width.
 */
@Composable
fun ContextUsageMenuItem(
    details: ContextGaugeDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usage = details.usage
    val percent = percentOf(usage.usedTokens, usage.windowTokens)
    DropdownMenuItem(
        modifier = modifier,
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = stringResource(Res.string.context_usage_label), modifier = Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = { usage.usedFraction },
                    modifier = Modifier.width(72.dp),
                    color = pressureColor(percent) ?: MaterialTheme.colorScheme.primary,
                    drawStopIndicator = {},
                )
                Text(text = percentLabel(percent, details.isEstimate), color = pressureColor(percent) ?: Color.Unspecified)
            }
        },
        onClick = onClick,
        leadingIcon = {
            Icon(Icons.Outlined.DataUsage, contentDescription = null)
        },
    )
}

/**
 * Full-width gauge that expands in place to reveal the breakdown — for the composer "+" sheet,
 * where a tap can't open a modal sheet (that would nest modal surfaces) but the inline space is
 * available. The header pill fills the row; tapping it toggles the breakdown below. The expanded
 * state is hoisted so the caller can persist it across sheet openings. A due compact suggestion
 * adds a quiet "Compact…" button on the row, which asks for confirmation.
 */
@Composable
fun ContextUsageExpandableGauge(
    details: ContextGaugeDetails,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCompact: (() -> Unit)? = null,
    onSnoozeCompact: () -> Unit = {},
) {
    val usage = details.usage
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "ctx_chevron")
    var confirmCompact by remember { mutableStateOf(false) }
    val nudge = details.nudge.takeIf { onCompact != null }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { onExpandedChange(!expanded) },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                ContextGaugePill(
                    percent = percentOf(usage.usedTokens, usage.windowTokens),
                    usedFraction = usage.usedFraction,
                    isEstimate = details.isEstimate,
                    fillWidth = true,
                ) {
                    if (nudge != null) {
                        TextButton(onClick = { confirmCompact = true }) {
                            Text(stringResource(Res.string.compact_nudge_short))
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                ContextBreakdownCard(
                    model = details.model,
                    sections = details.sections,
                    onCompact = onCompact,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
    if (confirmCompact && nudge != null && onCompact != null) {
        CompactConfirmDialog(
            percent = nudge.percent,
            onDismiss = { confirmCompact = false },
            onNotNow = {
                confirmCompact = false
                onSnoozeCompact()
            },
            onCompact = {
                confirmCompact = false
                onCompact()
            },
        )
    }
}

/**
 * The shared pill visual: "Context" label, a thin progress bar, and the used percentage, tinted
 * amber from 80% and red from 95%. When [fillWidth] is set the bar stretches to fill the row (the
 * rest hug their content); [trailing] appends optional elements after the percentage.
 */
@Composable
private fun ContextGaugePill(
    percent: Int,
    usedFraction: Float,
    isEstimate: Boolean,
    fillWidth: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val tint = pressureColor(percent)
    Row(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.context_usage_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { usedFraction },
            modifier = if (fillWidth) Modifier.weight(1f) else Modifier.width(72.dp),
            color = tint ?: MaterialTheme.colorScheme.primary,
            // Drop the M3 track stop indicator (the dot at the 100% end) so a low
            // percentage reads as a single thin bar, not "tiny dot … dot".
            drawStopIndicator = {},
        )
        Text(
            text = percentLabel(percent, isEstimate),
            style = MaterialTheme.typography.labelSmall,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        trailing()
    }
}

/** The breakdown in a modal sheet, for surfaces that can open one (the composer pill, the ⋮ menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextUsageSheet(
    details: ContextGaugeDetails,
    onDismiss: () -> Unit,
    onCompact: (() -> Unit)? = null,
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
    ) {
        ContextBreakdownCard(
            model = details.model,
            sections = details.sections,
            onCompact = onCompact,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        )
    }
}

/**
 * Confirms a compaction started from a suggestion. Every suggestion goes through it: compacting
 * changes what the model reads from then on, so it is never one tap. "Not now" quiets the
 * suggestion for this conversation until usage reaches the next band.
 */
@Composable
internal fun CompactConfirmDialog(
    percent: Int,
    onDismiss: () -> Unit,
    onNotNow: () -> Unit,
    onCompact: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.compact_confirm_title)) },
        text = { Text(stringResource(Res.string.compact_confirm_body, percent)) },
        confirmButton = { TextButton(onClick = onCompact) { Text(stringResource(Res.string.compact_confirm_action)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(Res.string.compact_confirm_not_now)) } },
    )
}

/** A computed estimate reads as approximate (`~42%`), matching the breakdown's `~used` header. */
internal fun percentLabel(percent: Int, isEstimate: Boolean): String =
    if (isEstimate) "~$percent%" else "$percent%"
