package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import org.jetbrains.compose.resources.stringResource

/**
 * Inline quick-toggle chips for the server's pinned tools (v0.8.7 `defaultPinnedTools`),
 * shown on the input bar so common tools are one tap instead of buried in the tools sheet.
 * Keys are pre-mapped and gated by the caller ([ChatUiState.pinnedToolChips]); selected
 * state reads from [enabledTools] (which includes the synthesized web_search/url_context).
 * Icon + label come from the shared [ephemeralToolMeta] so this can't drift from the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PinnedToolsRow(
    pinnedToolKeys: List<String>,
    enabledTools: Set<String>,
    onToggleTool: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Tools the active model spec turns on: shown selected and disabled. */
    lockedTools: Set<String> = emptySet(),
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Filled when off: the composer overlays the message list and is transparent up here, so
        // M3's outline-only chip would show scrolled text through its label. Glass keeps M3's defaults,
        // which is what tells AdaptiveFilterChip to apply its glass label and icon colours.
        val chipColors = if (isLiquidGlass) {
            FilterChipDefaults.filterChipColors()
        } else {
            FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        pinnedToolKeys.forEach { key ->
            val meta = ephemeralToolMeta(key) ?: return@forEach
            AdaptiveFilterChip(
                selected = key in enabledTools,
                onClick = { onToggleTool(key) },
                enabled = key !in lockedTools,
                label = { Text(stringResource(meta.titleRes)) },
                colors = chipColors,
                leadingIcon = {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
