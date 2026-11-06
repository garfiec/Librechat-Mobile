package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.LayoutCoordinates
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.util.draftItemsOf
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
    /**
     * What goes with the message being composed (files, picked skills, staged quotes), mapped once
     * by the screen ([draftItemsOf]). The field's leading stack draws it; the list panel opens from it.
     */
    val draftItems: DraftItems = DraftItems.EMPTY,
    /** What was last added to [draftItems], tracked by the screen so the first item counts too. */
    val draftAdditions: DraftAdditions? = null,
    /** The attachment list panel is open (the screen draws it, above the composer). */
    val isDraftListOpen: Boolean = false,
    /** The stack's tap: open the list, or close it when open. */
    val onToggleDraftList: () -> Unit = {},
    /** A list row's ×: cancels a file still uploading, else removes the item with an Undo. */
    val onRemoveDraftItem: (DraftItem) -> Unit = {},
    /** A failed file's row tap. */
    val onRetryUpload: (AttachedFile) -> Unit = {},
    /** The Undo snackbar's action, for the removal its id names. */
    val onUndoRemove: (undoId: Long) -> Unit = {},
    /** The message field gained focus; the list panel closes then. */
    val onFieldFocused: () -> Unit = {},
    /**
     * Where the stack is, for the screen's outside-tap check: a tap on the stack toggles the list,
     * any other tap outside the list (the field included) closes it.
     */
    val onDraftStackPositioned: (LayoutCoordinates) -> Unit = {},
    /** The tools in use, for the "+" button's dot. */
    val enabledTools: Set<String> = emptySet(),
    /** The MCP servers in use, for the "+" button's dot. */
    val selectedMcpServerNames: Set<String> = emptySet(),
    /**
     * The settings line above the field: tools, MCP servers and Thinking, mapped once by the
     * ViewModel ([com.garfiec.librechat.feature.chat.util.settingChipsOf]).
     */
    val settingChips: SettingChips = SettingChips.EMPTY,
    /** A settings chip's tap; the screen decides what it does ([SettingChips.tapFor]). */
    val onSettingChipTap: (SettingChip) -> Unit = {},
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
