package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.LayoutCoordinates
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.DraftItemTap
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
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
    /** The uploads' progress, kept out of [draftItems] and read by the rings as they draw. */
    val draftUploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
    /** What was last added to [draftItems], tracked by the screen so the first item counts too. */
    val draftAdditions: DraftAdditions? = null,
    /** The attachment list is open (the composer draws it between the settings line and the field). */
    val isDraftListOpen: Boolean = false,
    /** A screen reader opened the list: it takes focus at its header ([DraftListState.focusHeader]). */
    val draftListFocusHeader: Boolean = false,
    /**
     * The list's slot (zero tall while it is shut), for the screen's outside-tap check and for the
     * height the rest of the composer takes.
     */
    val onDraftListPositioned: (LayoutCoordinates) -> Unit = {},
    /**
     * The most the list may take, in px, read as it lays out: what is left below the floating top bar.
     * Keyboard frames move it, so it is a function the layout reads, not a value that recomposes.
     */
    val draftListMaxHeightPx: () -> Int = { Int.MAX_VALUE },
    /**
     * A tool-approval panel holds the space above the composer, so the list doesn't open: the stack
     * draws as usual with no click action, and the failed-upload line says to deal with the file
     * after the approval.
     */
    val draftListShut: Boolean = false,
    /** The stack's tap: open the list, or close it when open. */
    val onToggleDraftList: () -> Unit = {},
    /**
     * The same toggle from a screen reader (the stack's accessibility click action), which also
     * moves focus to the list's header on open. A touch leaves focus, and the keyboard, where they are.
     */
    val onToggleDraftListFromScreenReader: () -> Unit = {},
    /** A list row's ×: cancels a file still uploading, else removes the item with an Undo. */
    val onRemoveDraftItem: (DraftItem) -> Unit = {},
    /** A failed file's retry. */
    val onRetryUpload: (AttachedFile) -> Unit = {},
    /** A list row's tap ([com.garfiec.librechat.feature.chat.util.tapFor]): retry, or open the item. */
    val onDraftItemTap: (DraftItemTap) -> Unit = {},
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
    /** "Cancel sign-in" in a connecting MCP chip's menu: the server's explicit OAuth cancel. */
    val onCancelMcpSignIn: (serverName: String) -> Unit = {},
    /** "Start over" in a connecting MCP chip's menu: reconnect it and open the sign-in again. */
    val onStartMcpSignInOver: (serverName: String) -> Unit = {},
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
