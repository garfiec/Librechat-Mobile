package com.garfiec.librechat.feature.chat.viewmodel

/**
 * App-scoped home for the follow-ups a chat queued locally, so they outlive its ViewModel.
 *
 * A queue that the server does not hold (any endpoint but agents, or a server without queued
 * turns) would otherwise be cleared with the ViewModel when the chat is left mid-run, and never
 * sent. Upstream web keeps the same queue in an app-wide per-conversation atom for the same
 * reason. Each chat mirrors its local rows here as they change and the next ViewModel for that
 * chat takes them back.
 *
 * Keyed by account as well as conversation, so a queue never reaches another account. Memory only:
 * process death is covered by the text copy kept in the draft store (see
 * `LocalQueueContinuityDelegate`). Touched only from the main thread.
 */
class LocalQueueStash {

    data class Key(val accountId: String, val conversationId: String)

    /** The rows a chat had queued locally, and whether its queue was paused. */
    data class Snapshot(val rows: List<QueuedMessage>, val paused: Boolean)

    private val snapshots = mutableMapOf<Key, Snapshot>()

    fun put(key: Key, snapshot: Snapshot) {
        if (snapshot.rows.isEmpty()) snapshots.remove(key) else snapshots[key] = snapshot
    }

    /** Hands the snapshot to the ViewModel now showing the chat; it mirrors it back from there. */
    fun take(key: Key): Snapshot? = snapshots.remove(key)
}
