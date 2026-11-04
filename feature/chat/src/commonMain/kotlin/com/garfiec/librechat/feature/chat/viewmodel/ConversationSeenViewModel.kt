package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.isUnseen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * The reply the open chat has not acknowledged yet. [messageId] names the reply that must be on
 * screen; null for a "Mark as unread" marker (or a stamp from before reply identity), which any
 * rendered history acknowledges, as upstream's `replyBranchIsVisible` falls back.
 */
@Immutable
data class SeenTarget(
    val conversationId: String,
    val lastResponseAt: Instant,
    val messageId: String?,
)

/**
 * Sends `POST /api/convos/seen` for the open chat by the web's rule (upstream `useConversationSeen`):
 * only when the newest reply has rendered, sits near the bottom of the list, the app is in front and
 * nothing is streaming. Never merely on open. One attempt per reply stamp; coming back to the app
 * re-arms it, so a write that failed offline tries again.
 */
class ConversationSeenViewModel(
    private val conversationRepository: ConversationRepository,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val conversationId = MutableStateFlow<String?>(null)

    /** The last stamp each chat was acknowledged for; see [maybeMarkSeen]. */
    private val attempted = mutableMapOf<String, Instant>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val target: StateFlow<SeenTarget?> = combine(
        conversationId.flatMapLatest { id -> id?.let(conversationRepository::observeConversation) ?: flowOf(null) },
        configRepository.detectedBackendVersion,
    ) { conversation, version ->
        conversation?.takeIf { version != null && BackendVersion.isCompatibleOrNewer(version, MIN_VERSION) }?.seenTarget()
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun bind(id: String?) {
        if (conversationId.value == id) return
        // Arriving is a deliberate return to the chat, so it re-arms the attempt.
        id?.let(attempted::remove)
        conversationId.value = id
    }

    /** The app came back to the front: a write that failed while it was away may try again. */
    fun onForeground() = attempted.clear()

    /**
     * Acknowledges [target] once every condition holds. [replyRendered] is the caller's proof that
     * the reply [target] names is in the rendered thread.
     */
    fun maybeMarkSeen(
        target: SeenTarget?,
        replyRendered: Boolean,
        nearBottom: Boolean,
        inForeground: Boolean,
        isStreaming: Boolean,
    ) {
        if (target == null || target.conversationId != conversationId.value) return
        // The stream's own Room writes anchor the live reply: nothing is written while it runs.
        if (!replyRendered || !nearBottom || !inForeground || isStreaming) return
        if (attempted[target.conversationId] == target.lastResponseAt) return
        attempted[target.conversationId] = target.lastResponseAt
        viewModelScope.launch {
            val result = conversationRepository.markSeen(target.conversationId, target.lastResponseAt)
            if (result is Result.Error) Logger.w(result.exception) { "Failed to mark conversation seen" }
        }
    }

    private companion object {
        const val MIN_VERSION = "0.8.9-rc1"
    }
}

/** The unseen reply on [this], or null when it is caught up. */
internal fun Conversation.seenTarget(): SeenTarget? {
    val id = conversationId ?: return null
    if (!isUnseen) return null
    val stamp = lastResponseAt ?: return null
    return SeenTarget(id, stamp, lastResponseMessageId?.takeIf { lastResponseIsManual != true })
}
