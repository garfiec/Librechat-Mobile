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
    /** Find in chat is on the side rail, so the menu leaves it out. */
    val findInRail: Boolean = false,
)

internal fun chatOverflowSections(gates: ChatOverflowGates): List<List<ChatOverflowItem>> = with(gates) {
    val tools = toolItems()
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

private fun ChatOverflowGates.toolItems(): List<ChatOverflowItem> = buildList {
    // At the top: the way back to Apps whose cards may have scrolled far away.
    if (openApps > 0) add(ChatOverflowItem.OPEN_APPS)
    if (conversationId != null && !findInRail) add(ChatOverflowItem.SEARCH)
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

/** A control of the chat's top bar, or of its side rail. */
internal enum class ChatBarItem {
    PULL_REQUEST,
    NEW_CHAT,
    FIND,
    BACKGROUND_TASKS,
    TEMPORARY_CHAT,
    OVERFLOW,
    CONTEXT_RING,
}

/** What the bar's controls read. */
internal data class ChatBarGates(
    /** The bar draws as the side rail (the window has vertical bars). */
    val rail: Boolean,
    /** The chat has a drawer to open (a phone's drawer, or the tablet sidebar's toggle). */
    val hasDrawer: Boolean,
    /** The app's sidebar is open beside the chat and a swipe closes it. */
    val sidebarOpen: Boolean,
    val conversationId: String?,
    val hasNewChat: Boolean,
    val showTempChatToggle: Boolean,
    /** A wide pane's pull request chip; the rail leaves the pull request in the menu. */
    val showPullRequestChip: Boolean,
    val backgroundTasksVisible: Boolean,
    val contextUsageEnabled: Boolean,
    val contextBarPlacement: ContextBarPlacement,
    val usedTokens: Int,
)

/**
 * The bar's controls in order: [drawer] (the drawer button leads), [actions] along the bar or down the
 * rail's top, [status] at the rail's bottom.
 */
internal data class ChatBarItems(val drawer: Boolean, val actions: List<ChatBarItem>, val status: List<ChatBarItem>)

internal fun chatBarItems(gates: ChatBarGates): ChatBarItems = if (gates.rail) gates.railItems() else gates.barItems()

private fun ChatBarGates.barItems(): ChatBarItems {
    val actions = buildList {
        if (showPullRequestChip) add(ChatBarItem.PULL_REQUEST)
        if (backgroundTasksVisible) add(ChatBarItem.BACKGROUND_TASKS)
        if (showTempChatToggle) add(ChatBarItem.TEMPORARY_CHAT)
        add(ChatBarItem.OVERFLOW)
    }
    return ChatBarItems(drawer = hasDrawer, actions = actions, status = emptyList())
}

private fun ChatBarGates.railItems(): ChatBarItems {
    val actions = buildList {
        // First: the rail never moves its first action into the overflow menu.
        if (hasNewChat) add(ChatBarItem.NEW_CHAT)
        if (conversationId != null) add(ChatBarItem.FIND)
        if (showTempChatToggle) add(ChatBarItem.TEMPORARY_CHAT)
        add(ChatBarItem.OVERFLOW)
    }
    val status = buildList {
        if (backgroundTasksVisible) add(ChatBarItem.BACKGROUND_TASKS)
        if (contextUsageEnabled && contextBarPlacement != ContextBarPlacement.HIDDEN && usedTokens > 0) {
            add(ChatBarItem.CONTEXT_RING)
        }
    }
    // Beside the open sidebar the rail leads with nothing: the drawer it would open is on screen.
    return ChatBarItems(drawer = hasDrawer && !sidebarOpen, actions = actions, status = status)
}
