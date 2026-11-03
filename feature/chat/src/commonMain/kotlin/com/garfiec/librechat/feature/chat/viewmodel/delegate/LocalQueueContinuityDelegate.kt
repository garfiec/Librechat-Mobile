package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.LocalQueueStash
import com.garfiec.librechat.feature.chat.viewmodel.QueueHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a chat's locally queued follow-ups across its ViewModel: leaving the chat mid-run, or the
 * process dying, must never silently drop a message the user typed.
 *
 * - **Leaving and coming back** (the chat entry is replaced, so this ViewModel is cleared): the
 *   local rows are mirrored into the app-scoped [LocalQueueStash] as they change, and the next
 *   ViewModel for the chat takes them back. They return **paused** until the reopened chat attaches
 *   to the run that is still going ([onRunAttached]), whose end then drains them as it would have.
 *   With no run to attach to they stay paused behind "Send queued": nothing can send them while no
 *   screen is open, and a run that was stopped or failed must not set them off on return.
 * - **Process death**: the stash is gone, so the rows' text is also kept in the draft store under
 *   [queuedTextKey]. A chat that finds that text but no stash puts it back into the composer and
 *   says so. Never written for a temporary chat (data at rest).
 *
 * Server-owned rows are left out of both: the server holds them, and the reconcile poll brings them
 * back. Rows are keyed by account, so another account never sees them.
 */
class LocalQueueContinuityDelegate(
    private val handle: QueueHandle,
    private val queueStates: Flow<QueueState>,
    private val stash: LocalQueueStash,
    private val draftRepository: DraftRepository,
    private val activeAccountProvider: ActiveAccountProvider,
    private val isTemporary: () -> Boolean,
    /** Puts recovered text into the composer after whatever draft it holds, and persists it. */
    private val putBackInComposer: suspend (String) -> Unit,
) {

    /** The queue was paused only because it was restored, not by the user or a refusal. */
    private var pausedByRestore = false
    private var runAttached = false

    fun start(conversationId: String) {
        handle.scope.launch {
            val accountId = activeAccountProvider.awaitResolvedAccount().value
            val key = LocalQueueStash.Key(accountId, conversationId)
            val snapshot = stash.take(key)
            if (snapshot != null) {
                restore(snapshot)
            } else if (!isTemporary()) {
                recoverText(conversationId)
            }
            // A restored queue's text is already in the draft store, written by the last ViewModel.
            mirror(key, textWritten = snapshot != null)
        }
    }

    /** The reopened chat attached to a live run: let its end drain the restored rows. */
    fun onRunAttached() {
        runAttached = true
        if (!pausedByRestore) return
        pausedByRestore = false
        handle.update { queue = queue.copy(isQueuePaused = false) }
    }

    private fun restore(snapshot: LocalQueueStash.Snapshot) {
        val holdForRun = !snapshot.paused && !runAttached
        handle.update {
            val present = queue.messageQueue.map { it.localId }.toSet()
            val restored = snapshot.rows.filter { it.localId !in present }
            queue = queue.copy(
                messageQueue = restored + queue.messageQueue,
                isQueuePaused = queue.isQueuePaused || snapshot.paused || holdForRun,
            )
        }
        pausedByRestore = holdForRun
    }

    private suspend fun recoverText(conversationId: String) {
        val key = queuedTextKey(conversationId)
        val text = draftRepository.awaitDraft(key)
        if (text.isNullOrBlank()) return
        putBackInComposer(text)
        draftRepository.deleteDraft(key)
        handle.setError(ChatNotice.QUEUE_RESTORED_TO_COMPOSER.marker())
    }

    private suspend fun mirror(key: LocalQueueStash.Key, textWritten: Boolean) {
        val textKey = queuedTextKey(key.conversationId)
        // Nothing to clear until something was written: most chats never queue anything.
        var written = textWritten
        queueStates
            .map { queue ->
                LocalQueueStash.Snapshot(
                    rows = queue.messageQueue.filter {
                        it.server == null && (it.accountId == null || it.accountId == key.accountId)
                    },
                    // A pause that only holds restored rows for the run is not the user's: the next
                    // restore decides that again.
                    paused = queue.isQueuePaused && !pausedByRestore,
                )
            }
            .distinctUntilChanged()
            .collect { snapshot ->
                stash.put(key, snapshot)
                // The draft store writes under whichever account is active now.
                if (isTemporary() || activeAccountProvider.currentAccountId()?.value != key.accountId) {
                    return@collect
                }
                val text = snapshot.rows.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isNotBlank()) {
                    draftRepository.saveDraft(textKey, text)
                    written = true
                } else if (written) {
                    draftRepository.deleteDraft(textKey)
                    written = false
                }
            }
    }

    companion object {
        /** The draft-store key holding a chat's queued text for recovery after process death. */
        fun queuedTextKey(conversationId: String): String = "queued:$conversationId"
    }
}
