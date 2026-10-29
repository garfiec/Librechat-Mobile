package com.garfiec.librechat.core.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _events = MutableSharedFlow<TagEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** Renames and deletes the server accepted, each emitted before its [revision] bump. */
    val events: SharedFlow<TagEvent> = _events.asSharedFlow()

    fun bump() = _revision.update { it + 1 }

    fun emit(event: TagEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val EVENT_BUFFER = 16
    }
}

/**
 * A bookmark that changed name or went away, for a screen that shows one bookmark by name. Only
 * changes made in this app: one made on the web shows up as the old name missing from the list.
 */
sealed interface TagEvent {
    data class Renamed(val from: String, val to: String) : TagEvent

    data class Deleted(val name: String) : TagEvent
}
