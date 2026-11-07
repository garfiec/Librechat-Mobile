package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
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
 * What the composer draws around its field. A new row adds a field here, never a parameter to the
 * five signatures that thread it: the two iOS ones are not compiled on a Linux host.
 */
@Immutable
data class ComposerChrome(
    /** Steers accepted by the running turn but not yet injected; rendered above the queue. */
    val pendingSteers: List<PendingSteerChip> = emptyList(),
    val onCancelSteer: (steerId: String) -> Unit = {},
    val onInterruptSteer: (steerId: String) -> Unit = {},
    /** Queued follow-ups (ghost rows), pinned above the composer so the list's auto-follow can't
     *  make them bounce as the reply streams. */
    val queuedMessages: List<QueuedMessage> = emptyList(),
    val onEditQueuedMessage: (localId: String) -> Unit = {},
    val onCancelQueuedMessage: (localId: String) -> Unit = {},
    val onReorderQueuedMessages: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    /** Mapped once by the screen ([draftItemsOf]). */
    val draftItems: DraftItems = DraftItems.EMPTY,
    /** The uploads' progress, kept out of [draftItems] and read by the rings as they draw. */
    val draftUploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
    /** What was last added to [draftItems], tracked by the screen so the first item counts too. */
    val draftAdditions: DraftAdditions? = null,
    /** A strip item's ×: cancels a file still uploading, else removes the item with an Undo. */
    val onRemoveDraftItem: (DraftItem) -> Unit = {},
    /** A strip item's tap ([com.garfiec.librechat.feature.chat.util.taps]): retry, or open the item. */
    val onDraftItemTap: (DraftItemTap) -> Unit = {},
    /** The tools in use, for the "+" button's dot. */
    val enabledTools: Set<String> = emptySet(),
    /** The MCP servers in use, for the "+" button's dot. */
    val selectedMcpServerNames: Set<String> = emptySet(),
    /** Mapped once by the ViewModel ([com.garfiec.librechat.feature.chat.util.settingChipsOf]). */
    val settingChips: SettingChips = SettingChips.EMPTY,
    /** A settings chip's tap; the screen decides what it does ([SettingChips.tapFor]). */
    val onSettingChipTap: (SettingChip) -> Unit = {},
    /** "Cancel sign-in" in a connecting MCP chip's menu: the server's explicit OAuth cancel. */
    val onCancelMcpSignIn: (serverName: String) -> Unit = {},
    /** "Start over" in a connecting MCP chip's menu: reconnect it and open the sign-in again. */
    val onStartMcpSignInOver: (serverName: String) -> Unit = {},
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
