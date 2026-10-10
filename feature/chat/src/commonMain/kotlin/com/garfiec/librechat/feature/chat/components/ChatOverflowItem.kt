package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextUsage

/** One entry of the chat overflow menu. Labels and icons are resolved by each renderer. */
internal enum class ChatOverflowItem {
    SEARCH,
    SHOW_ALL_MEDIA,
    LOAD_PRESET,
    SAVE_PRESET,
    PROMPTS_LIBRARY,
    COMPARE,
    TRACE,
    CONTEXT_USAGE,

    /** "Compact conversation…": only while a compact suggestion is due, and only in this placement. */
    COMPACT,
    SHARE,
    RENAME,
    DUPLICATE,
    ARCHIVE,
    DELETE,
}

/**
 * Which overflow items exist, in which order, split into divider-separated sections. The Material
 * dropdown and the native iOS menu both render from this one list, so they cannot disagree about
 * gating or order. Empty sections are dropped.
 */
/** What the overflow menu's gating reads: server flags, the conversation, and which callbacks exist. */
internal data class ChatOverflowGates(
    val conversationId: String?,
    val hasShowAllMedia: Boolean,
    val presetsEnabled: Boolean,
    val hasPromptsLibrary: Boolean,
    val promptsEnabled: Boolean,
    val multiConvoEnabled: Boolean,
    val traceViewerAvailable: Boolean,
    val contextUsage: ContextUsage?,
    val contextUsageEnabled: Boolean,
    val contextBarPlacement: ContextBarPlacement,
    val sharedLinksEnabled: Boolean,
    /** A compact suggestion is due. A Boolean, not the percent, so the sections aren't rebuilt per tick. */
    val compactNudgeVisible: Boolean = false,
)

internal fun chatOverflowSections(gates: ChatOverflowGates): List<List<ChatOverflowItem>> = with(gates) {
    val tools = buildList {
        if (conversationId != null) add(ChatOverflowItem.SEARCH)
        if (hasShowAllMedia) add(ChatOverflowItem.SHOW_ALL_MEDIA)
        // Hidden when the server disables `interface.presets` (or `interface.modelSelect`), matching
        // web's Header.tsx presets menu.
        if (presetsEnabled) {
            add(ChatOverflowItem.LOAD_PRESET)
            add(ChatOverflowItem.SAVE_PRESET)
        }
        if (hasPromptsLibrary && promptsEnabled) add(ChatOverflowItem.PROMPTS_LIBRARY)
        if (multiConvoEnabled) add(ChatOverflowItem.COMPARE)
        // Availability is a precondition, so reaching this means there is a trace to read.
        if (traceViewerAvailable) add(ChatOverflowItem.TRACE)
        if (contextBarPlacement == ContextBarPlacement.OVERFLOW_MENU &&
            contextUsageEnabled &&
            contextUsage != null &&
            contextUsage.usedTokens > 0
        ) {
            add(ChatOverflowItem.CONTEXT_USAGE)
        }
        if (contextBarPlacement == ContextBarPlacement.OVERFLOW_MENU && compactNudgeVisible) {
            add(ChatOverflowItem.COMPACT)
        }
    }
    val conversation = if (conversationId == null) {
        emptyList()
    } else {
        buildList {
            if (sharedLinksEnabled) add(ChatOverflowItem.SHARE)
            add(ChatOverflowItem.RENAME)
            add(ChatOverflowItem.DUPLICATE)
            add(ChatOverflowItem.ARCHIVE)
        }
    }
    val destructive = if (conversationId == null) emptyList() else listOf(ChatOverflowItem.DELETE)
    listOf(tools, conversation, destructive).filter { it.isNotEmpty() }
}
