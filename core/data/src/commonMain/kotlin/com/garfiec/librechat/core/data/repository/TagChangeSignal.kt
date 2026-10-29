package com.garfiec.librechat.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts changes to the user's bookmarks (conversation tags) that the server accepted: a tag
 * created, edited, moved or deleted, a chat's tags changed, and a chat deleted (its tags' counts
 * drop). One shared instance, so every bookmark surface reloads on any of them. Surfaced as
 * [TagRepository.revision].
 *
 * A plain [TagRepository.refreshTags] does not bump it: a screen that refreshes on a new revision
 * would otherwise loop.
 */
class TagChangeSignal {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun bump() = _revision.update { it + 1 }
}
