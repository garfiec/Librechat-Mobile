package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.error.UserKeyError

/**
 * What [StreamingManagerDelegate] needs from the ViewModel that owns it: the conversation loader,
 * the message tree and composer, the one-shot error channel, and how this screen was opened. All
 * of it sits outside the streaming handle's writable slices.
 */
interface StreamingHost {
    val isNewConversation: Boolean
    val isHandedOffNewChat: Boolean

    /** Emits a typed user-provided-key error for one-shot UI surfacing (snackbar + CTA). */
    fun emitUserKeyError(error: UserKeyError)

    /** Reloads the conversation from the server (VM-owned Room observer). */
    fun reloadConversation(conversationId: String)

    /**
     * [reloadConversation], then restores [unsent]'s text to the composer if the reloaded
     * conversation holds no server copy of it.
     */
    fun reloadRestoringUnsaved(conversationId: String, unsent: Message)

    /**
     * Un-sends a turn the server never persisted: removes its optimistic user message
     * ([optimisticId]) and puts that message's text and quotes back into the composer. Null when
     * the turn re-submitted a persisted message, which must never be removed — the live reply is
     * still cleared.
     */
    fun unsendTurn(optimisticId: String?)
}
