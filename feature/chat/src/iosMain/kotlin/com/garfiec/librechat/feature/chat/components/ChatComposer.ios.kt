package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.clipboardHasImage
import com.garfiec.librechat.feature.chat.util.readClipboardImage
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import kotlinx.coroutines.launch

@Suppress("LongParameterList", "LambdaParameterEventTrailing")
@Composable
internal actual fun ChatComposer(
    inputText: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenTools: () -> Unit,
    onDuringRunSend: () -> Unit,
    onQueue: () -> Unit,
    canQueue: Boolean,
    onSteer: () -> Unit,
    canSteer: Boolean,
    duringRunAction: DuringRunAction,
    duringRunSendTarget: DuringRunSendTarget,
    pendingSteers: List<PendingSteerChip>,
    pendingQuotes: List<String>,
    onRemoveQuote: (index: Int) -> Unit,
    onCancelSteer: (steerId: String) -> Unit,
    onSetDuringRunAction: (DuringRunAction) -> Unit,
    attachedFiles: List<AttachedFile>,
    onRemoveFile: (AttachedFile) -> Unit,
    onPasteFiles: (List<Any>) -> Unit,
    promptSuggestions: List<PromptMentionDisplayData>,
    onSlashCommandSelect: (PromptMentionDisplayData) -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    enabledTools: Set<String>,
    pinnedToolKeys: List<String>,
    onToggleTool: (String) -> Unit,
    mcpServers: List<McpServerDisplayData>,
    selectedMcpServerNames: Set<String>,
    selectedModelDisplay: String?,
    isCodeInterpreterAvailable: Boolean,
    gates: ChatInputGates,
    contextGauge: ContextGaugeDetails?,
    contextUsageEnabled: Boolean,
    onCompact: (() -> Unit)?,
    onSnoozeCompact: () -> Unit,
    compactSuggested: Boolean,
    contextBarPlacement: ContextBarPlacement,
    queuedPausedCount: Int,
    onSendQueuedMessages: () -> Unit,
    isEditingQueued: Boolean,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    isAwaitingUploadSend: Boolean,
    arePicksUnsettled: Boolean,
    onCancelPendingSend: () -> Unit,
    queuedMessages: List<QueuedMessage>,
    onEditQueuedMessage: (localId: String) -> Unit,
    onCancelQueuedMessage: (localId: String) -> Unit,
    onReorderQueuedMessages: (fromIndex: Int, toIndex: Int) -> Unit,
    fontSizeMultiplier: Float,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var hasClipboardImage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasClipboardImage = clipboardHasImage()
    }
    IosChatInput(
        inputText = inputText,
        isStreaming = isStreaming,
        onInputChange = onInputChange,
        onSend = onSend,
        onStop = onStop,
        onOpenTools = onOpenTools,
        onDuringRunSend = onDuringRunSend,
        onQueue = onQueue,
        canQueue = canQueue,
        onSteer = onSteer,
        canSteer = canSteer,
        duringRunAction = duringRunAction,
        duringRunSendTarget = duringRunSendTarget,
        pendingSteers = pendingSteers,
        pendingQuotes = pendingQuotes,
        onRemoveQuote = onRemoveQuote,
        onCancelSteer = onCancelSteer,
        onSetDuringRunAction = onSetDuringRunAction,
        attachedFiles = attachedFiles,
        onRemoveFile = onRemoveFile,
        promptSuggestions = promptSuggestions,
        onSlashCommandSelect = onSlashCommandSelect,
        isRecording = isRecording,
        isTranscribing = isTranscribing,
        onStartRecording = onStartRecording,
        onStopRecording = onStopRecording,
        enabledTools = enabledTools,
        pinnedToolKeys = pinnedToolKeys,
        onToggleTool = onToggleTool,
        mcpServers = mcpServers,
        selectedMcpServerNames = selectedMcpServerNames,
        selectedModelDisplay = selectedModelDisplay,
        isCodeInterpreterAvailable = isCodeInterpreterAvailable,
        gates = gates,
        contextGauge = contextGauge,
        contextUsageEnabled = contextUsageEnabled,
        onCompact = onCompact,
        onSnoozeCompact = onSnoozeCompact,
        compactSuggested = compactSuggested,
        contextBarPlacement = contextBarPlacement,
        queuedPausedCount = queuedPausedCount,
        onSendQueuedMessages = onSendQueuedMessages,
        isEditingQueued = isEditingQueued,
        onCommitEdit = onCommitEdit,
        onCancelEdit = onCancelEdit,
        isAwaitingUploadSend = isAwaitingUploadSend,
        arePicksUnsettled = arePicksUnsettled,
        onCancelPendingSend = onCancelPendingSend,
        queuedMessages = queuedMessages,
        onEditQueuedMessage = onEditQueuedMessage,
        onCancelQueuedMessage = onCancelQueuedMessage,
        onReorderQueuedMessages = onReorderQueuedMessages,
        fontSizeMultiplier = fontSizeMultiplier,
        hasClipboardImage = hasClipboardImage,
        onPasteImage = {
            coroutineScope.launch {
                readClipboardImage()?.let { onPasteFiles(listOf(it)) }
                hasClipboardImage = clipboardHasImage()
            }
        },
        modifier = modifier,
    )
}
