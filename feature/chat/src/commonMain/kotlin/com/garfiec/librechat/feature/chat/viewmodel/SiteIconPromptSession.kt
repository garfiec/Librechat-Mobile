package com.garfiec.librechat.feature.chat.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers, for the life of the process, that the user closed the website-icons prompt
 * without choosing. A closed prompt stores nothing, so it should come back on the next launch
 * but not on the next conversation.
 *
 * Process-scoped (a Koin `single`) rather than held by [ChatViewModel] or the chat composition:
 * opening another conversation creates a new Chat entry with a fresh ViewModel, which would
 * re-ask every time.
 */
class SiteIconPromptSession {
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    fun dismiss() {
        _dismissed.value = true
    }
}
