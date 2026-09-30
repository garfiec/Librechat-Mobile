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
