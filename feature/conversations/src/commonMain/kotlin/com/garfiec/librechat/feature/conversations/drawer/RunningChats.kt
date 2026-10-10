package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.Conversation

/** A drawer section: the "Running" group, or a date bucket. Both are localized at render. */
sealed interface DrawerGroupKey {
    /** Stable, Bundle-safe string for `LazyColumn` item keys. */
    val key: String

    data object Running : DrawerGroupKey {
        override val key = "running"
    }

    data class Date(val group: DateGroup) : DrawerGroupKey {
        override val key: String get() = group.key
    }
}

/**
 * MIRRORED from upstream `groupConversationsWithRunning`
 * (`client/src/components/Conversations/running.ts`, v0.8.8): conversations with a live generation
 * job are lifted out of their date groups into one group placed first, keeping each group's order
 * and dropping any date group left empty. Applies to the drawer's recency ordering only — the
 * archive keeps its server order, and pinned chats stay in the Pinned section.
 *
 * Search results are promoted too, pinned matches included: upstream's call site
 * (`Conversations.tsx` `groupedConversations`) passes `includePinned: isArchivedView`, not the
 * search-aware flag its date grouping uses, so only the archive skips this.
 *
 * Upstream's `field !== 'updatedAt' || direction !== 'desc'` guard has no counterpart because the
 * drawer has no other ordering: `ConversationListStateHolder` requests pages with no sort and
 * reads Room's `ORDER BY updatedAt DESC`. A sort picker added to the drawer has to add the guard.
 */
internal fun <T> groupWithRunning(
    groups: List<Pair<DateGroup, List<T>>>,
    activeJobIds: Set<String>,
    idOf: (T) -> String?,
): List<Pair<DrawerGroupKey, List<T>>> {
    val dated = groups.map { (group, items) -> DrawerGroupKey.Date(group) to items }
    if (activeJobIds.isEmpty()) return dated
    val running = mutableListOf<T>()
    val remaining = mutableListOf<Pair<DrawerGroupKey, List<T>>>()
    for ((key, items) in dated) {
        val (live, idle) = items.partition { item -> idOf(item)?.let { it in activeJobIds } == true }
        running += live
        if (idle.isNotEmpty()) remaining += key to idle
    }
    return if (running.isEmpty()) dated else listOf(DrawerGroupKey.Running to running) + remaining
}

internal fun List<Pair<DateGroup, List<Conversation>>>.withRunningFirst(
    activeJobIds: Set<String>,
): List<Pair<DrawerGroupKey, List<Conversation>>> = groupWithRunning(this, activeJobIds) { it.conversationId }
