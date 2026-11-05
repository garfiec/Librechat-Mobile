package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.mcp.MAX_APP_VIEW_HEIGHT
import com.garfiec.librechat.core.model.mcp.MIN_APP_VIEW_HEIGHT
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppHost

/**
 * The rules for several open MCP Apps, apart from Compose and the WebView so they test plainly.
 * The view model applies them; the screen only reports what is on screen.
 */
internal object McpAppSessionRules {
    /** Never more than this many Apps at once on a phone, whatever the server allows. */
    const val MAX_VIEWS = 3

    /** Before the App reports a size, as on web. */
    const val DEFAULT_INLINE_HEIGHT_DP = 320

    /** Upstream `clampAppViewHeight`'s bounds. */
    const val MIN_INLINE_HEIGHT_DP = MIN_APP_VIEW_HEIGHT
    const val MAX_INLINE_HEIGHT_DP = MAX_APP_VIEW_HEIGHT

    /** An inline App never takes more of the chat pane than this, so the reply under it shows. */
    const val INLINE_PANE_FRACTION = 0.6f

    /**
     * How many Apps may be open: the server's `maxActiveViews` capped at [MAX_VIEWS] (each is a
     * WebView with its own renderer), and one on a low-RAM Android device.
     */
    fun viewCap(maxActiveViews: Int, lowRamDevice: Boolean): Int =
        if (lowRamDevice) 1 else maxActiveViews.coerceIn(1, MAX_VIEWS)

    /** The inline slot for an App that reported [reportedDp], in a chat pane [paneHeightDp] tall. */
    fun inlineHeightDp(reportedDp: Int, paneHeightDp: Int): Int {
        val cap = inlineCapDp(paneHeightDp)
        return reportedDp.coerceIn(MIN_INLINE_HEIGHT_DP, cap)
    }

    /** The most height an inline App may take in a pane [paneHeightDp] tall. */
    fun inlineCapDp(paneHeightDp: Int): Int =
        (paneHeightDp * INLINE_PANE_FRACTION).toInt().coerceIn(MIN_INLINE_HEIGHT_DP, MAX_INLINE_HEIGHT_DP)

    /**
     * The Apps the user can see right now, the only ones whose approval or location question may
     * show: the panel's App while the panel is up (it covers the thread), else every inline App
     * whose card is in the viewport.
     */
    fun foreground(openIds: List<Long>, panelId: Long?, visibleInline: Set<Long>): Set<Long> =
        if (panelId != null) setOf(panelId) else openIds.filterTo(LinkedHashSet()) { it in visibleInline }

    /**
     * Whose dialog shows: the first foreground App, in open order, that waits on the user. One at a
     * time, so a background App never draws over another (no tap-jacking from behind).
     */
    fun presented(openIds: List<Long>, foreground: Set<Long>, awaiting: Set<Long>): Long? =
        openIds.firstOrNull { it in foreground && it in awaiting }

    /**
     * What memory pressure tears down: every App the user can't see and that waits on nothing. An
     * App with a question pending is never evicted (the question would go unanswered).
     */
    fun evictable(openIds: List<Long>, foreground: Set<Long>, awaiting: Set<Long>): List<Long> =
        openIds.filter { it !in foreground && it !in awaiting }

    /** The mode App [id] is drawn in. */
    fun displayMode(id: Long, panelId: Long?): String =
        if (panelId == id) McpAppHost.DISPLAY_MODE_FULLSCREEN else McpAppHost.DISPLAY_MODE_INLINE

    /** What an App's `ui/request-display-mode` does. */
    sealed interface ModeChange {
        data object Expand : ModeChange
        data object Collapse : ModeChange
        data object None : ModeChange
    }

    /**
     * Fullscreen expands an App the user can see; inline from the panel collapses it. Anything else,
     * including an off-screen App asking to cover the chat, changes nothing.
     */
    fun modeChange(id: Long, requested: String, panelId: Long?, foreground: Set<Long>): ModeChange = when {
        requested == McpAppHost.DISPLAY_MODE_FULLSCREEN && panelId != id && id in foreground -> ModeChange.Expand
        requested == McpAppHost.DISPLAY_MODE_INLINE && panelId == id -> ModeChange.Collapse
        else -> ModeChange.None
    }

    /** The tab the panel shows once App [closedId] closes, or null to return to the chat. */
    fun panelAfterClose(openIds: List<Long>, closedId: Long, panelId: Long?, lastUsed: List<Long>): Long? {
        if (panelId == null) return null
        if (panelId != closedId) return panelId
        return lastUsed.lastOrNull { it != closedId && it in openIds }
            ?: openIds.lastOrNull { it != closedId }
    }
}
