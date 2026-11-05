package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage

/**
 * What the composer draws around its field (the rows above it and the "+" button's dots), with the
 * callbacks those rows fire. One holder threaded from the screen through [ChatComposer], both
 * platform composers and [ChatInputState], so a new row adds a field here rather than a parameter to
 * five signatures, two of which (iOS) this module's Linux builds never compile.
 */
@Immutable
data class ComposerChrome(
    /** Steers accepted by the running turn but not yet injected; rendered above the queue. */
    val pendingSteers: List<PendingSteerChip> = emptyList(),
    /** Withdraw a steer the run has not injected yet. */
    val onCancelSteer: (steerId: String) -> Unit = {},
    /** "Interrupt now" on a waiting steer chip. */
    val onInterruptSteer: (steerId: String) -> Unit = {},
    /** Queued follow-ups (ghost rows), pinned above the composer so the list's auto-follow can't
     *  make them bounce as the reply streams. */
    val queuedMessages: List<QueuedMessage> = emptyList(),
    val onEditQueuedMessage: (localId: String) -> Unit = {},
    val onCancelQueuedMessage: (localId: String) -> Unit = {},
    val onReorderQueuedMessages: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    /** Staged "Add to chat" excerpts (v0.8.7 quotes). */
    val pendingQuotes: List<String> = emptyList(),
    val onRemoveQuote: (index: Int) -> Unit = {},
    /** Skills picked for the next message. */
    val pendingManualSkills: List<String> = emptyList(),
    val onRemoveManualSkill: (name: String) -> Unit = {},
    val attachedFiles: List<AttachedFile> = emptyList(),
    val onRemoveFile: (AttachedFile) -> Unit = {},
    val enabledTools: Set<String> = emptySet(),
    /** Tool keys (already mapped + gated) shown as quick-toggle chips (v0.8.7 `defaultPinnedTools`).
     *  Selected state reads from [enabledTools]. */
    val pinnedToolKeys: List<String> = emptyList(),
    /** Toggle a pinned tool from its chip. */
    val onToggleTool: (String) -> Unit = {},
    /** The per-turn Thinking chip (v0.8.9-rc1), shown after the pinned tools; null hides it. */
    val thinking: ThinkingChip? = null,
    val onOpenThinking: () -> Unit = {},
    val mcpServers: List<McpServerDisplayData> = emptyList(),
    val selectedMcpServerNames: Set<String> = emptySet(),
    /** The context gauge's reading and breakdown. */
    val contextGauge: ContextGaugeDetails? = null,
    /** Server/version gate for the context gauge (`interface.contextUsage` AND backend ≥ 0.8.7). */
    val contextUsageEnabled: Boolean = false,
    /** Null when the server does not offer compaction, or nothing can be compacted right now. */
    val onCompact: (() -> Unit)? = null,
    /** "Not now" on a compact suggestion. */
    val onSnoozeCompact: () -> Unit = {},
    /** A compact suggestion is due under the options-sheet placement: hint at it on the + button. */
    val compactSuggested: Boolean = false,
    /** Where the context gauge is surfaced; the composer draws it only for [ContextBarPlacement.ABOVE_INPUT]. */
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
)
