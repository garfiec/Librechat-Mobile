package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A run on [conversationId] that no screen is attached to any more. */
data class ReplyWatch(val accountId: AccountId, val conversationId: String)

/** Keeps a [ReplyWatcher] running for a watch once the chat screen lets go of the run. */
interface ReplyWatchScheduler {
    fun watch(watch: ReplyWatch)

    /** The chat is attached to the run again (or it was left on purpose): nothing to announce. */
    fun stop(conversationId: String)
}

enum class ReplyWatchOutcome {
    /** The run ended and was announced. */
    Ended,

    /** Still running when the budget ran out: watch again later. */
    StillRunning,

    /** Another account became active: its reply can't be read or opened any more. */
    Abandoned,
}

/**
 * Polls until a run ends, then announces it once.
 *
 * Reads only `GET /chat/active`, as web's away watcher does, never `/chat/status`: on servers whose
 * parked-steer store is the legacy one, a status read of an ended run deletes the user's parked steer
 * text, and the chat that reattaches later is the only reader allowed to take it.
 * A run paused for review is still active, so it is announced only when it ends.
 */
class ReplyWatcher(
    private val chatRepository: ChatRepository,
    private val replyReader: ReplyReader,
    private val accounts: ActiveAccountProvider,
    private val notifications: ReplyNotifications,
    private val pollInterval: Duration = POLL_INTERVAL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    suspend fun watch(watch: ReplyWatch, budget: Duration): ReplyWatchOutcome {
        // A process started just for this watch is still loading the account.
        accounts.state.first { it !is AccountState.Warming }
        val started = timeSource.markNow()
        while (started.elapsedNow() < budget) {
            if (accounts.currentAccountId() != watch.accountId) return ReplyWatchOutcome.Abandoned
            val active = chatRepository.getActiveJobIds()
            if (active is Result.Success && watch.conversationId !in active.data) {
                announce(watch)
                return ReplyWatchOutcome.Ended
            }
            delay(pollInterval)
        }
        return ReplyWatchOutcome.StillRunning
    }

    private suspend fun announce(watch: ReplyWatch) {
        val reply = replyReader.read(watch.conversationId, watch.accountId)
        // A temporary chat is kept out of anything that outlives it.
        if (reply.isTemporary) return
        notifications.announce(
            ReplyNotification(
                accountId = watch.accountId,
                conversationId = watch.conversationId,
                title = reply.title,
                preview = reply.preview,
                failed = reply.failed,
            ),
        )
    }

    companion object {
        val POLL_INTERVAL: Duration = 5.seconds
    }
}
