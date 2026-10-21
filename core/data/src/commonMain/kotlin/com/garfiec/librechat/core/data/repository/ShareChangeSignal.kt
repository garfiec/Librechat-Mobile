package com.garfiec.librechat.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts changes to the user's shared links: a link created, updated or deleted, or found gone.
 * One shared instance, so the Shared chats list reloads after a change made from any share sheet.
 * Surfaced as [ShareRepository.revision].
 */
class ShareChangeSignal {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun bump() = _revision.update { it + 1 }
}
