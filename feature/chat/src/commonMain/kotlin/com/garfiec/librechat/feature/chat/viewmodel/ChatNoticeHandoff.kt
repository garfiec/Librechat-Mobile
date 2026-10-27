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

    private val _pending = MutableStateFlow<StringResource?>(null)
    val pending: StateFlow<StringResource?> = _pending.asStateFlow()

    fun put(message: StringResource) {
        _pending.value = message
    }

    /** Clears [message] once shown, unless a newer one has replaced it meanwhile. */
    fun consume(message: StringResource) {
        _pending.compareAndSet(message, null)
    }
}
