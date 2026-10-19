package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownModel
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.viewmodel.CompactNudge
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState

// The composer and what docks with it: the during-run row, the notices and queue above it, the
// context gauge on each of its surfaces, and the options sheet the "+" opens.

@PreviewNarrowScreens
@Composable
private fun ComposerMidRunMaterialPreview() = ComposerSurface(UiStyle.MATERIAL) { MidRunComposerHost() }

@PreviewNarrowScreens
@Composable
private fun ComposerMidRunGlassPreview() = ComposerSurface(UiStyle.LIQUID_GLASS) { MidRunComposerHost() }

@PreviewNarrowScreens
@Composable
private fun ComposerQueueHeldPreview() = ComposerSurface(UiStyle.MATERIAL) { QueueHeldComposerHost() }

@PreviewNarrowScreens
@Composable
private fun ComposerContextPillPreview() = ComposerSurface(UiStyle.MATERIAL) { ContextPillComposerHost() }

@PreviewNarrowScreens
@Composable
private fun QueuedNeedsAttentionPreview() = ComposerSurface(UiStyle.MATERIAL) {
    QueuedMessagesSection(
        queuedMessages = listOf(queuedRefused, queuedPlain),
        onEdit = {},
        onCancel = {},
        onReorder = { _, _ -> },
    )
}

/** Measured the way a DropdownMenu measures its items, at the menu's widest. */
@Preview(name = "1.3x · ja", widthDp = 360, fontScale = 1.3f, locale = "ja", showBackground = true)
@PreviewNarrowScreens
@Composable
private fun ContextMenuItemPreview() = ComposerSurface(UiStyle.MATERIAL) {
    Column(Modifier.width(IntrinsicSize.Max)) {
        ContextUsageMenuItem(details = gaugeWithNudge, onClick = {})
    }
}

@PreviewNarrowScreens
@Composable
private fun ToolsSheetMaterialPreview() = ComposerSurface(UiStyle.MATERIAL) { ToolsSheetHost() }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetGlassPreview() = ComposerSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost() }

@Composable
private fun ComposerSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

/** A steer-capable server mid-run, with a draft typed: the caret beside the send button shows. */
@Composable
private fun MidRunComposerHost() {
    var text by remember { mutableStateOf("Also check whether the parking garage opens before the park does") }
    ChatInput(
        inputText = text,
        isStreaming = true,
        onInputChange = { text = it },
        onSend = {},
        onStop = {},
        onOpenTools = {},
        canQueue = true,
        canSteer = true,
        duringRunSendTarget = DuringRunSendTarget.STEER,
    )
}

/** A refused follow-up holds the queue mid-run, so the notice explaining it sits above the composer. */
@Composable
private fun QueueHeldComposerHost() {
    ChatInput(
        inputText = "",
        isStreaming = true,
        onInputChange = {},
        onSend = {},
        onStop = {},
        onOpenTools = {},
        queueHeldForRefusal = true,
        queuedMessages = listOf(queuedRefused),
    )
}

/** The gauge placed above the composer, with a compact suggestion due. */
@Composable
private fun ContextPillComposerHost() {
    ChatInput(
        inputText = "",
        isStreaming = false,
        onInputChange = {},
        onSend = {},
        onStop = {},
        onOpenTools = {},
        contextGauge = gaugeWithNudge,
        contextUsageEnabled = true,
        onCompact = {},
        contextBarPlacement = ContextBarPlacement.ABOVE_INPUT,
    )
}

@Composable
private fun ToolsSheetHost() {
    var expanded by remember { mutableStateOf(false) }
    ChatToolsSheetContent(
        enabledTools = emptySet(),
        onToggleTool = {},
        mcpServers = emptyList(),
        selectedMcpServerNames = emptySet(),
        onToggleMcpServer = {},
        onAttachFiles = {},
        onTakePhoto = {},
        onPickPhotos = {},
        onAttachFromServer = {},
        onOpenModelParameters = {},
        onOpenModelSelector = {},
        selectedModelDisplay = "gpt-5.1",
        onDismiss = {},
        contextGauge = gaugeWithNudge,
        contextUsageEnabled = true,
        onCompact = {},
        contextGaugeExpanded = expanded,
        onContextGaugeExpandedChange = { expanded = it },
    )
}

/** 82% of a 128k window, with a compact suggestion due. */
private val gaugeWithNudge = ContextGaugeDetails(
    model = ContextBreakdownModel(
        usage = ContextUsage(
            breakdown = TokenBudgetBreakdown(
                maxContextTokens = 128_000,
                instructionTokens = 12_000,
                messageTokens = 93_000,
                messageCount = 48,
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
    nudge = CompactNudge(band = 1, percent = 82),
)

private val previewDispatch = EndpointDispatch(endpointType = null, key = null, modelDisplayLabel = null)

/** A follow-up the server refused: held for the user, so it carries the "needs attention" cue. */
private val queuedRefused = QueuedMessage(
    localId = "refused",
    text = "And book the character breakfast for the second morning if it's still open",
    endpoint = "openAI",
    model = "gpt-5.1",
    agentId = null,
    dispatch = previewDispatch,
    server = QueuedTurnServerState(status = QueuedTurnServerState.Status.Rejected),
)

private val queuedPlain = queuedRefused.copy(localId = "plain", text = "Then a packing list", server = null)
