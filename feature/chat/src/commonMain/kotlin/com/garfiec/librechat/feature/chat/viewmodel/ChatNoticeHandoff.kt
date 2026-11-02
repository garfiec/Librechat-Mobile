package com.garfiec.librechat.feature.chat.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource

/**
 * Carries a confirmation from a screen that closes itself back to the chat: a key saved or
 * revoked on Provider Keys, opened from chat, pops straight back, and its own snackbar goes with
 * it. Staged by the nav host, since feature/settings can't see this type.
 *
 * A state, not a take-once slot like [PromptInsertionHandoff]: on a tablet the chat may stay
 * composed beside the closing screen, so it is collected rather than taken on re-entry.
 * Single-slot (the latest wins) and in memory only.
 */
class ChatNoticeHandoff {

    /** A notice, and the conversation that should show it (null: whichever chat is on screen). */
    data class Pending(val message: StringResource, val conversationId: String? = null)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /**
     * [conversationId] targets a chat about to open (a fork's copy), so the screen being left
     * can't take the notice in the moment before it is replaced.
     */
    fun put(message: StringResource, conversationId: String? = null) {
        _pending.value = Pending(message, conversationId)
    }

    /** Clears [notice] once shown, unless a newer one has replaced it meanwhile. */
    fun consume(notice: Pending) {
        _pending.compareAndSet(notice, null)
    }
}
