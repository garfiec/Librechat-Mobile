package com.garfiec.librechat.core.data.bookmarks

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.bookmarks
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the per-chat bookmark checklist shows; [conversationId] is null while it's closed. */
data class BookmarkPickerState(
    val conversationId: String? = null,
    /** Every bookmark (Saved excluded), count 0 included, in the user's order. */
    val bookmarks: List<ConversationTag> = emptyList(),
    /** The bookmarks this chat carries, as the server last said plus the taps still in flight. */
    val attached: Set<String> = emptySet(),
    /** Bookmarks whose save is still out: their rows take no taps. */
    val pending: Set<String> = emptySet(),
    /** The chat's own bookmarks are being read: no tick can be trusted yet. */
    val loading: Boolean = false,
    /** The "New bookmark" editor is up in place of the checklist. */
    val editorOpen: Boolean = false,
    /** The editor's create is in flight. */
    val creating: Boolean = false,
) {
    val isOpen: Boolean get() = conversationId != null

    /** Names the editor checks a new name against. */
    val names: List<String> get() = bookmarks.mapNotNull { it.tag }
}

/** Why the checklist couldn't do what was asked; the host turns it into a message. */
sealed interface BookmarkPickerFailure {
    /** The chat's bookmarks couldn't be read, so the checklist closed rather than guess. */
    data object LoadFailed : BookmarkPickerFailure

    /** A tick or untick didn't save and was put back. */
    data class ToggleFailed(val name: String) : BookmarkPickerFailure

    /** The new bookmark wasn't created; the editor stays up with what was typed. */
    data class CreateFailed(val name: String) : BookmarkPickerFailure
}

/**
 * Drives the per-chat bookmark checklist, one chat at a time, for every screen that offers it.
 * [open] reads the chat's bookmarks from the server before showing a tick; each [toggle] saves at
 * once through [TagRepository.setConversationTag] (which reads the chat again first, so tags set on
 * the web survive), its row disabled until the server answers, and a failure puts the tick back and
 * reports through [failures]. [create] makes a new bookmark, attached to the chat when asked, which
 * then shows ticked. The favorite star (Saved) is never shown or touched here.
 *
 * Answers that land after the checklist closed or moved to another chat leave its state alone.
 * A plain class: each host builds one on its own [scope] (a ViewModel's).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkPickerController(
    private val tagRepository: TagRepository,
    private val scope: CoroutineScope,
) {
    private data class Session(
        val conversationId: String? = null,
        val attached: Set<String> = emptySet(),
        val pending: Set<String> = emptySet(),
        val loading: Boolean = false,
        val editorOpen: Boolean = false,
        val creating: Boolean = false,
    )

    private val session = MutableStateFlow(Session())

    /** Bumped on every open and close, so a late answer can tell it belongs to an earlier one. */
    private var epoch = 0L
    private var loadJob: Job? = null

    private val _failures = MutableSharedFlow<BookmarkPickerFailure>(extraBufferCapacity = FAILURE_BUFFER)
    val failures: SharedFlow<BookmarkPickerFailure> = _failures.asSharedFlow()

    // The tag list is only watched while the checklist is open.
    private val bookmarks = session
        .map { it.conversationId != null }
        .distinctUntilChanged()
        .flatMapLatest { open -> if (open) tagRepository.observeTags().map { it.bookmarks() } else flowOf(emptyList()) }

    val state: StateFlow<BookmarkPickerState> = combine(session, bookmarks) { s, tags ->
        BookmarkPickerState(
            conversationId = s.conversationId,
            bookmarks = if (s.conversationId == null) emptyList() else tags,
            attached = s.attached,
            pending = s.pending,
            loading = s.loading,
            editorOpen = s.editorOpen,
            creating = s.creating,
        )
    }.stateIn(scope, SharingStarted.Eagerly, BookmarkPickerState())

    /** Opens the checklist on [conversationId] and reads which bookmarks it carries. */
    fun open(conversationId: String) {
        loadJob?.cancel()
        val opened = ++epoch
        session.value = Session(conversationId = conversationId, loading = true)
        loadJob = scope.launch {
            val result = tagRepository.getConversationTags(conversationId)
            if (opened != epoch) return@launch
            when (result) {
                is Result.Success -> session.update { it.copy(attached = result.data.bookmarkNames(), loading = false) }
                is Result.Error -> {
                    close()
                    _failures.emit(BookmarkPickerFailure.LoadFailed)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        epoch++
        session.value = Session()
    }

    /** Ticks or unticks [name] on the open chat and saves it. Ignored while that row is saving. */
    fun toggle(name: String) {
        val current = session.value
        val conversationId = current.conversationId ?: return
        if (current.loading || current.editorOpen || name in current.pending) return
        val attach = name !in current.attached
        session.update {
            it.copy(attached = if (attach) it.attached + name else it.attached - name, pending = it.pending + name)
        }
        val opened = epoch
        scope.launch {
            val result = tagRepository.setConversationTag(conversationId, name, attach)
            if (result is Result.Error) _failures.emit(BookmarkPickerFailure.ToggleFailed(name))
            if (opened != epoch) return@launch
            session.update { s ->
                val stillPending = s.pending - name
                when (result) {
                    // The server's answer is the truth, except for other rows whose own save is out.
                    is Result.Success ->
                        s.copy(
                            attached = (result.data.bookmarkNames() - stillPending) + (s.attached intersect stillPending),
                            pending = stillPending,
                        )
                    is Result.Error ->
                        s.copy(attached = if (attach) s.attached - name else s.attached + name, pending = stillPending)
                    is Result.Loading -> s
                }
            }
        }
    }

    /** "New bookmark": shows the editor in place of the checklist. */
    fun openEditor() {
        val current = session.value
        if (current.conversationId == null || current.loading) return
        session.update { it.copy(editorOpen = true) }
    }

    /** Back from the editor to the checklist. Ignored while its create is in flight. */
    fun closeEditor() {
        session.update { if (it.creating) it else it.copy(editorOpen = false) }
    }

    /**
     * Creates [name] at the end of the order, added to the open chat when [addToChat]. On success
     * the editor closes and, if added, the new bookmark shows ticked.
     */
    fun create(name: String, description: String, addToChat: Boolean) {
        val current = session.value
        val conversationId = current.conversationId ?: return
        if (current.creating) return
        session.update { it.copy(creating = true) }
        val opened = epoch
        scope.launch {
            val result = tagRepository.createTag(
                name = name,
                description = description.takeIf { it.isNotBlank() },
                addToConversationId = conversationId.takeIf { addToChat },
            )
            if (result is Result.Error) _failures.emit(BookmarkPickerFailure.CreateFailed(name))
            if (opened != epoch) return@launch
            session.update { s ->
                when (result) {
                    is Result.Success -> {
                        val created = result.data.tag?.takeIf { it.isNotBlank() } ?: name.trim()
                        s.copy(
                            creating = false,
                            editorOpen = false,
                            attached = if (addToChat) s.attached + created else s.attached,
                        )
                    }
                    is Result.Error -> s.copy(creating = false)
                    is Result.Loading -> s
                }
            }
        }
    }

    private fun List<String>.bookmarkNames(): Set<String> =
        map { it.trim() }.filter { it.isNotEmpty() && it != SAVED_TAG }.toSet()

    private companion object {
        const val FAILURE_BUFFER = 4
    }
}
