package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.subagent.SubagentIndex
import com.garfiec.librechat.core.model.subagent.SubagentThreadView

/**
 * Read-only child-thread viewing (v0.8.8-rc2). Server is the sole source of truth; no cache.
 *
 * **Nothing here latches.** The index answers `404` for three unrelated conditions behind one
 * body — conversation missing, not the caller's, or itself a subagent thread — and none of them
 * says anything about whether the deployment has the routes. A latch would therefore disable the
 * feature account-wide the first time a user opened a conversation that simply has no children.
 * Suppression on a proven-old server is a version check ([isRuledOutForServer]) instead, and every
 * `404` means only "no child view for this conversation".
 */
interface SubagentRepository {

    /**
     * Whether this server is KNOWN to predate the routes, so nothing should be requested.
     *
     * Three-state underneath: only an `ABSENT` verdict suppresses. An unplaceable server is asked,
     * because it is the population most likely to have them — and the ask costs one GET, made
     * lazily when a user actually looks.
     */
    fun isRuledOutForServer(): Boolean

    suspend fun getChildren(parentConversationId: String): Result<SubagentIndex>

    suspend fun getThread(parentConversationId: String, threadId: String): Result<SubagentThreadView>

    /** One execution boundary of the child. Mutually exclusive with [getOlderPage] server-side. */
    suspend fun getThreadTask(
        parentConversationId: String,
        threadId: String,
        taskId: String,
    ): Result<SubagentThreadView>

    /** The page older than [cursor]; pass a `nextCursor` a previous page returned. */
    suspend fun getOlderPage(
        parentConversationId: String,
        threadId: String,
        cursor: String,
    ): Result<SubagentThreadView>
}
