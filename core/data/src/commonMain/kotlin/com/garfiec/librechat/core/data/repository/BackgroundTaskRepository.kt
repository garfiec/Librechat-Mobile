package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.background.BackgroundTaskCancelResponse
import com.garfiec.librechat.core.model.background.BackgroundTaskIndex

/**
 * Per-conversation background tool tasks (v0.8.8). Server is the sole source of truth; no cache.
 *
 * **Probe-and-latch.** Suppressed on a server KNOWN to predate the routes; an unplaceable one is
 * asked once, and a 404 from it latches the feature off for the rest of the server session. Unlike
 * the subagent index, a 404 here has one meaning: the index handler answers 404 only for a missing
 * user or a malformed id, never for a conversation that is not the caller's (the registry is keyed
 * by user, so another user's tasks are simply absent). [clear] resets the latch on account switch.
 */
interface BackgroundTaskRepository {

    /** Whether to ask this server at all. Re-read on every poll tick: the answer flips once, when the probe lands. */
    fun isRuledOutForServer(): Boolean

    /** Null data on a server that has just been found to lack the route (the probe's 404, latched). */
    suspend fun getTasks(conversationId: String): Result<BackgroundTaskIndex?>

    /** Null [taskIds] stops every running task in the conversation. */
    suspend fun cancel(conversationId: String, taskIds: List<String>?): Result<BackgroundTaskCancelResponse>

    /** Account/server switch: the latched verdict belongs to the server being left. */
    fun clear()
}
