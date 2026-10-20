package com.garfiec.librechat.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts changes to the user's projects that the server accepted: [ProjectRepository]'s own
 * mutations, and a new chat filed into a project, which [ChatRepository] reports once its first reply
 * is saved. One shared instance, so every list of projects reloads on either. Surfaced as
 * [ProjectRepository.revision].
 */
class ProjectChangeSignal {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun bump() = _revision.update { it + 1 }
}
