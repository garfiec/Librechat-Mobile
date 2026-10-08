package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import kotlinx.coroutines.CoroutineScope

/**
 * The chat composer: [ChatInput] on Android, [IosChatInput] on iOS. The two differ in what they do
 * on their own (iOS offers to paste a copied image, handing it to [onPasteFiles]), so the screen
 * hands both everything and each takes what it uses. [pasteScope] must outlive the composer, which
 * a pause panel can replace while a paste is still decoding.
 */
@Suppress("LongParameterList", "LambdaParameterEventTrailing")
@Composable
internal expect fun ChatComposer(
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
    pasteScope: CoroutineScope,
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
    modifier: Modifier = Modifier,
)
