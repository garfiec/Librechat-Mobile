package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextUsage

/** One entry of the chat overflow menu. Labels and icons are resolved by each renderer. */
internal enum class ChatOverflowItem {
    /** "Open apps (n)": MCP Apps still running while the panel is down. Opens it on the last-used one. */
    OPEN_APPS,
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

    /** "PR #482": the chat's pull request, only once a lookup found one. Opens the sheet. */
    PULL_REQUEST,
    SHARE,
    BOOKMARKS,
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
    val firstTurnStreaming: Boolean = false,
    /** BOOKMARKS USE and `interface.bookmarks`. */
    val bookmarksEnabled: Boolean = false,
    val isTemporaryChat: Boolean = false,
    /** A lookup found the chat's pull request. Absent, not disabled, until then. */
    val hasPullRequest: Boolean = false,
    /** MCP Apps running minimized (the panel is down). */
    val openApps: Int = 0,
)

internal fun chatOverflowSections(gates: ChatOverflowGates): List<List<ChatOverflowItem>> = with(gates) {
    val tools = buildList {
        // At the top: the way back to Apps whose cards may have scrolled far away.
        if (openApps > 0) add(ChatOverflowItem.OPEN_APPS)
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
            // Upstream's small-screen header puts the pull request in the overflow, first.
            if (hasPullRequest) add(ChatOverflowItem.PULL_REQUEST)
            if (sharedLinksEnabled && !firstTurnStreaming) add(ChatOverflowItem.SHARE)
            // A temporary chat is never listed or kept, and a first turn's chat isn't saved yet:
            // the server has nothing to put a bookmark on in either.
            if (bookmarksEnabled && !isTemporaryChat && !firstTurnStreaming) add(ChatOverflowItem.BOOKMARKS)
            add(ChatOverflowItem.RENAME)
            add(ChatOverflowItem.DUPLICATE)
            add(ChatOverflowItem.ARCHIVE)
        }
    }
    val destructive = if (conversationId == null) emptyList() else listOf(ChatOverflowItem.DELETE)
    listOf(tools, conversation, destructive).filter { it.isNotEmpty() }
}
