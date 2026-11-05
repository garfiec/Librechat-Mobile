package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownModel
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import com.garfiec.librechat.feature.chat.viewmodel.CompactNudge
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails

// The composer's settings line over scrolled thread text (the composer's top is transparent):
// tools on, pinned and off, locked by a spec; MCP servers; Thinking idle and staged; the context
// ring at a normal reading and with a compact suggestion due.

@PreviewNarrowScreens
@Composable
private fun SettingsLineMaterialPreview() = LineSurface(UiStyle.MATERIAL) { SettingsLineStates() }

@PreviewNarrowScreens
@Composable
private fun SettingsLineGlassPreview() = LineSurface(UiStyle.LIQUID_GLASS) { SettingsLineStates() }

@Composable
private fun LineSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            // Thread text behind the line, so a see-through chip would show.
            Text(
                text = "The parking garage opens at seven, an hour before the park. ".repeat(6),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            content()
        }
    }
}

@Composable
private fun SettingsLineStates() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // On, pinned and off, an MCP server, Thinking at the chat's level, the ring at 41%.
        ComposerSettingsLine(
            chips = SettingChips(
                listOf(
                    SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.ON, pinned = true),
                    SettingChip.Tool(ToolConstants.CODE_INTERPRETER, SettingChipState.PINNED_OFF, pinned = true),
                    SettingChip.Tool(ToolConstants.MEMORY, SettingChipState.ON, pinned = false),
                    SettingChip.Mcp("linear", "Linear", SettingChipState.ON, connected = true),
                    SettingChip.Mcp("github", "GitHub Enterprise (internal mirror)", SettingChipState.ON, connected = false),
                    SettingChip.Thinking(thinking(pending = null)),
                ),
            ),
            onChipTap = {},
            ring = ContextRingState(gauge(used = 52_000, nudge = null), onCompact = {}, onSnoozeCompact = {}),
        )
        // Locked by a spec, with Thinking staged for the next message and a compact suggestion due.
        ComposerSettingsLine(
            chips = SettingChips(
                listOf(
                    SettingChip.Tool(ToolConstants.FILE_SEARCH, SettingChipState.LOCKED, pinned = false),
                    SettingChip.Mcp("docs", "Docs", SettingChipState.LOCKED, connected = true),
                    SettingChip.Thinking(thinking(pending = "high")),
                ),
                lockedBy = "Research assistant",
            ),
            onChipTap = {},
            ring = ContextRingState(
                details = gauge(used = 117_000, nudge = CompactNudge(band = 1, percent = 91)),
                onCompact = {},
                onSnoozeCompact = {},
            ),
        )
        // Nothing on but the ring.
        ComposerSettingsLine(
            chips = SettingChips.EMPTY,
            onChipTap = {},
            ring = ContextRingState(gauge(used = 8_000, nudge = null), onCompact = null, onSnoozeCompact = {}),
        )
    }
}

private fun thinking(pending: String?) = ThinkingChip(
    key = "reasoning_effort",
    options = listOf("", "low", "medium", "high"),
    configured = "medium",
    pending = pending,
    unsetIsOff = false,
)

private fun gauge(used: Int, nudge: CompactNudge?) = ContextGaugeDetails(
    model = ContextBreakdownModel(
        usage = ContextUsage(
            breakdown = TokenBudgetBreakdown(
                maxContextTokens = 128_000,
                instructionTokens = 4_000,
                messageTokens = used - 4_000,
                messageCount = 24,
            ),
        ),
        isEstimate = false,
        totals = ContextUsageTotals(),
        costEnabled = false,
        currency = null,
        isCompacting = false,
        compactionAvailable = true,
    ),
    sections = ContextDetailPreset.STANDARD.sections,
    nudge = nudge,
)
