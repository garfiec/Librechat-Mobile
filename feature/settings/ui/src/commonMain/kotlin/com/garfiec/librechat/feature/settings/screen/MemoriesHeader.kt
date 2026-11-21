package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveLinearProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.AdaptiveSwitchPlaceholder
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.theme.StatusColors
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.resources.memories_usage_a11y
import com.garfiec.librechat.feature.settings.resources.memories_usage_percent
import com.garfiec.librechat.feature.settings.resources.memories_usage_tokens
import com.garfiec.librechat.feature.settings.resources.memories_use_desc
import com.garfiec.librechat.feature.settings.resources.memories_use_load_failed
import com.garfiec.librechat.feature.settings.resources.memories_use_loading
import com.garfiec.librechat.feature.settings.resources.memories_use_title
import com.garfiec.librechat.feature.settings.resources.memories_view_only
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How full the personal pool is, as web's usage badge grades it. */
internal enum class MemoryUsageLevel { OK, WARNING, CRITICAL }

/**
 * The personal pool's usage, or null when the deployment sets no token limit. [percent] is the
 * server's own (capped at 100); [fraction] is the bar's fill, clamped to 0..1 because a PATCH can
 * push [totalTokens] past [tokenLimit], while the numbers themselves are printed as they are.
 */
internal data class MemoryUsage(
    val totalTokens: Int,
    val tokenLimit: Int,
    val percent: Int,
    val fraction: Float,
    val level: MemoryUsageLevel,
)

internal fun memoryUsage(snapshot: MemoriesResponse?): MemoryUsage? {
    val limit = snapshot?.tokenLimit ?: return null
    if (limit <= 0) return null
    val total = (snapshot.totalTokens ?: 0).coerceAtLeast(0)
    val percent = snapshot.usagePercentage ?: percentOf(total, limit)
    return MemoryUsage(
        totalTokens = total,
        tokenLimit = limit,
        percent = percent,
        fraction = (total.toFloat() / limit).coerceIn(0f, 1f),
        level = memoryUsageLevel(percent),
    )
}

/** Web's thresholds (`MemoryUsageBadge.tsx` getStatusColor): over 90 critical, over 75 warning. */
internal fun memoryUsageLevel(percent: Int): MemoryUsageLevel = when {
    percent > CRITICAL_ABOVE -> MemoryUsageLevel.CRITICAL
    percent > WARNING_ABOVE -> MemoryUsageLevel.WARNING
    else -> MemoryUsageLevel.OK
}

private const val CRITICAL_ABOVE = 90
private const val WARNING_ABOVE = 75

private fun MemoryUsageLevel.color(colors: StatusColors): Color = when (this) {
    MemoryUsageLevel.OK -> colors.success
    MemoryUsageLevel.WARNING -> colors.warning
    MemoryUsageLevel.CRITICAL -> colors.error
}

/**
 * The page's first card: the opt-out switch (with [showSwitch], i.e. OPT_OUT) and, under it, the
 * pool's usage when the server sets a limit. [enabled] null is "not known yet": the switch's place
 * is held by a blank pill so it never shows a value it hasn't read. When reading it failed
 * ([loadFailed]) the switch is left out and a Retry that calls [onRetryLoad] sits under the text.
 */
@Composable
internal fun MemoriesSwitchCard(
    showSwitch: Boolean,
    enabled: Boolean?,
    busy: Boolean,
    usage: MemoryUsage?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    loadFailed: Boolean = false,
    onRetryLoad: () -> Unit = {},
) {
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        shape = MemoryCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (showSwitch) {
            // Once the value is known the whole row is the switch, as in the role editors'
            // PermissionToggle: title and description toggle it, a screen reader reads them with
            // it, and the row is the touch target (the glass switch alone is 32dp tall).
            val failed = enabled == null && loadFailed
            val loading = stringResource(Res.string.memories_use_loading)
            val rowControl = when {
                enabled != null ->
                    Modifier.toggleable(value = enabled, enabled = !busy, role = Role.Switch, onValueChange = onToggle)
                // Read as one item that says it is still loading, rather than a title with no state.
                !failed -> Modifier.semantics(mergeDescendants = true) { stateDescription = loading }
                else -> Modifier
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(rowControl)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(Res.string.memories_use_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(Res.string.memories_use_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (failed) {
                        Text(
                            text = stringResource(Res.string.memories_use_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            // Announced: the switch just never appears otherwise.
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        // Under the text, not in the switch's place beside it: there a long Retry
                        // label at a large font takes the row's width and squeezes the title out.
                        TextButton(onClick = onRetryLoad, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(Res.string.action_retry))
                        }
                    }
                }
                when {
                    failed -> Unit
                    enabled == null -> AdaptiveSwitchPlaceholder()
                    else -> AdaptiveSwitch(checked = enabled, onCheckedChange = null, enabled = !busy)
                }
            }
        }
        if (usage != null) {
            MemoryUsageRow(
                usage = usage,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (showSwitch) 0.dp else 12.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * The percentage pill and the token numbers on one line, the bar under them: on one line the bar
 * would be squeezed to nothing at 320dp and a large font.
 */
@Composable
private fun MemoryUsageRow(usage: MemoryUsage, modifier: Modifier = Modifier) {
    val tint = usage.level.color(statusColors())
    val localeTag = LocalDateTimeFormat.current.localeTag
    val (percent, total, limit) = remember(usage, localeTag) {
        listOf(usage.percent, usage.totalTokens, usage.tokenLimit).map { formatLocalizedNumber(it.toDouble(), localeTag) }
    }
    val percentText = stringResource(Res.string.memories_usage_percent, percent)
    // One node for screen readers, naming what is measured: the pill, the numbers and the bar
    // would otherwise be three unlabeled stops that read the percentage twice.
    val description = pluralStringResource(Res.plurals.memories_usage_a11y, usage.tokenLimit, percentText, total, limit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                modifier = Modifier
                    .background(tint.copy(alpha = PILL_TINT_ALPHA), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = pluralStringResource(Res.plurals.memories_usage_tokens, usage.tokenLimit, total, limit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        AdaptiveLinearProgressIndicator(
            progress = { usage.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = tint,
        )
    }
}

/** Shown when the role may neither add nor change memories, so the missing buttons aren't a mystery. */
@Composable
internal fun MemoriesViewOnlyNotice(modifier: Modifier = Modifier) {
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.memories_view_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PILL_TINT_ALPHA = 0.16f
