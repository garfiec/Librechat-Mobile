package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.reasoningLevelLabel
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_thinking_chip
import com.garfiec.librechat.feature.chat.resources.thinking_chip_next_message
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import org.jetbrains.compose.resources.stringResource

/**
 * Inline quick-toggle chips for the server's pinned tools (v0.8.7 `defaultPinnedTools`),
 * shown on the input bar so common tools are one tap instead of buried in the tools sheet.
 * Keys are pre-mapped and gated by the caller ([ChatUiState.pinnedToolChips]); selected
 * state reads from [enabledTools] (which includes the synthesized web_search/url_context).
 * Icon + label come from the shared [ephemeralToolMeta] so this can't drift from the sheet.
 *
 * [thinking] adds the per-turn Thinking chip after them (v0.8.9-rc1): a button, not a toggle, that
 * opens the level sheet ([onOpenThinking]). It shows the level the next message will use, and fills
 * with "next message" while a one-turn choice is staged.
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
    thinking: ThinkingChip? = null,
    onOpenThinking: () -> Unit = {},
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
        if (thinking != null) ThinkingChipButton(thinking, onOpenThinking)
    }
}

@Composable
private fun ThinkingChipButton(chip: ThinkingChip, onClick: () -> Unit) {
    val level = reasoningLevelLabel(chip.shown, chip.unsetIsOff)
    val staged = chip.pending != null
    val label = if (staged) stringResource(Res.string.thinking_chip_next_message, level) else level
    val description = stringResource(Res.string.cd_thinking_chip, level)
    // Same container as the tool chips when idle (see above); secondaryContainer while a choice is
    // staged, so it reads as set for the next message.
    val colors = when {
        isLiquidGlass -> AssistChipDefaults.assistChipColors()
        staged -> AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    AdaptiveAssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.semantics { contentDescription = description },
        colors = colors,
        border = if (staged) null else AssistChipDefaults.assistChipBorder(enabled = true),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}
