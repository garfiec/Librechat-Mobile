package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.Conversation

/** Group key for the drawer's "Running" section; localized at render, like no date bucket is. */
internal const val RUNNING_CHATS_GROUP = "\u0000running"

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
    groups: List<Pair<String, List<T>>>,
    activeJobIds: Set<String>,
    idOf: (T) -> String?,
): List<Pair<String, List<T>>> {
    if (activeJobIds.isEmpty()) return groups
    val running = mutableListOf<T>()
    val remaining = mutableListOf<Pair<String, List<T>>>()
    for ((name, items) in groups) {
        val (live, idle) = items.partition { item -> idOf(item)?.let { it in activeJobIds } == true }
        running += live
        if (idle.isNotEmpty()) remaining += name to idle
    }
    return if (running.isEmpty()) groups else listOf(RUNNING_CHATS_GROUP to running) + remaining
}

internal fun List<Pair<String, List<Conversation>>>.withRunningFirst(
    activeJobIds: Set<String>,
): List<Pair<String, List<Conversation>>> = groupWithRunning(this, activeJobIds) { it.conversationId }
