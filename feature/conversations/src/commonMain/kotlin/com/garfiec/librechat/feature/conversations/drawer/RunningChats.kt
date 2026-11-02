package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.isFinishedUnseen

/** A drawer section: "Running", "Finished", or a date bucket. All are localized at render. */
sealed interface DrawerGroupKey {
    /** Stable, Bundle-safe string for `LazyColumn` item keys. */
    val key: String

    /** Status groups head the list and carry a count in their heading. */
    val isStatus: Boolean get() = false

    data object Running : DrawerGroupKey {
        override val key = "running"
        override val isStatus: Boolean get() = true
    }

    /** Replies that finished while the user was elsewhere (v0.8.9-rc1). */
    data object Finished : DrawerGroupKey {
        override val key = "finished"
        override val isStatus: Boolean get() = true
    }

    data class Date(val group: DateGroup) : DrawerGroupKey {
        override val key: String get() = group.key
    }
}

/**
 * MIRRORED from upstream `groupConversationsByStatus`
 * (`client/src/components/Conversations/running.ts`, v0.8.9-rc1): conversations with a live
 * generation job are lifted out of their date groups into a Running group placed first; idle ones
 * whose reply finished unseen ([isFinished]) follow as Finished. Each group keeps its order, and a
 * date group left empty is dropped. [unlisted] adds running rows the groups never held, so Running
 * lists every running chat; with any of them it is re-sorted newest first ([newestFirst]).
 *
 * Applies to the drawer's recency ordering only — the archive keeps its server order, and pinned
 * chats stay in the Pinned section. Search results are promoted too: upstream's call site passes
 * `includePinned: isArchivedView`, not the search-aware flag its date grouping uses.
 *
 * Upstream's `field !== 'updatedAt' || direction !== 'desc'` guard has no counterpart because the
 * drawer has no other ordering: `ConversationListStateHolder` requests pages with no sort and
 * reads Room's `ORDER BY updatedAt DESC`. A sort picker added to the drawer has to add the guard.
 */
internal fun <T> groupWithRunning(
    groups: List<Pair<DateGroup, List<T>>>,
    activeJobIds: Set<String>,
    idOf: (T) -> String?,
    isFinished: (T) -> Boolean = { false },
    unlisted: List<T> = emptyList(),
    newestFirst: Comparator<T>? = null,
): List<Pair<DrawerGroupKey, List<T>>> {
    val dated = groups.map { (group, items) -> DrawerGroupKey.Date(group) to items }
    val running = mutableListOf<T>()
    val runningIds = mutableSetOf<String>()
    val finished = mutableListOf<T>()
    val remaining = mutableListOf<Pair<DrawerGroupKey, List<T>>>()
    for ((key, items) in dated) {
        val idle = mutableListOf<T>()
        for (item in items) {
            val id = idOf(item)
            when {
                id != null && id in activeJobIds -> {
                    running += item
                    runningIds += id
                }
                isFinished(item) -> finished += item
                else -> idle += item
            }
        }
        if (idle.isNotEmpty()) remaining += key to idle
    }
    val added = if (activeJobIds.isEmpty()) {
        emptyList()
    } else {
        unlisted.filter { item ->
            val id = idOf(item)
            id != null && id in activeJobIds && runningIds.add(id)
        }
    }
    if (running.isEmpty() && finished.isEmpty() && added.isEmpty()) return dated
    val status = mutableListOf<Pair<DrawerGroupKey, List<T>>>()
    if (running.isNotEmpty() || added.isNotEmpty()) {
        val all = if (added.isEmpty() || newestFirst == null) running + added else (running + added).sortedWith(newestFirst)
        status += DrawerGroupKey.Running to all
    }
    if (finished.isNotEmpty()) status += DrawerGroupKey.Finished to finished
    val pulled = running.isNotEmpty() || finished.isNotEmpty()
    return status + if (pulled) remaining else dated
}

private val NewestFirst: Comparator<Conversation> =
    compareByDescending<Conversation, kotlin.time.Instant?>(nullsFirst()) { it.updatedAt }

/**
 * The drawer's status grouping over conversations. [unlisted] are running chats no loaded row holds
 * (filed in a project, pinned, or past the loaded pages); archived and temporary ones never join,
 * as upstream drops them.
 */
internal fun List<Pair<DateGroup, List<Conversation>>>.withRunningFirst(
    activeJobIds: Set<String>,
    unlisted: List<Conversation> = emptyList(),
): List<Pair<DrawerGroupKey, List<Conversation>>> = groupWithRunning(
    groups = this,
    activeJobIds = activeJobIds,
    idOf = { it.conversationId },
    isFinished = { it.isFinishedUnseen },
    unlisted = unlisted.filterNot { it.isArchived || it.isTemporary == true },
    newestFirst = NewestFirst,
)

/**
 * Upstream `unlistedRunningIds`: the running chats [groups] hold no row for. Empty while nothing runs.
 */
internal fun unlistedRunningIds(
    groups: List<Pair<DateGroup, List<Conversation>>>,
    activeJobIds: Set<String>,
): Set<String> {
    if (activeJobIds.isEmpty()) return emptySet()
    val unlisted = activeJobIds.toMutableSet()
    for ((_, conversations) in groups) {
        for (conversation in conversations) conversation.conversationId?.let(unlisted::remove)
    }
    return unlisted
}
