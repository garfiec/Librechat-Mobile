package com.garfiec.librechat.core.data.bookmarks

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AccountSwitchedException
import com.garfiec.librechat.core.data.repository.CreatedTag
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
    /**
     * The last tick or create that failed while the sheet was up, which the sheet shows itself: a
     * snackbar would sit behind it. Cleared by the next tap. While the editor is up this is only ever
     * a [BookmarkPickerFailure.CreateFailed]: a tick that fails behind the editor waits for the
     * checklist to come back.
     */
    val error: BookmarkPickerFailure? = null,
) {
    val isOpen: Boolean get() = conversationId != null

    /** Names the editor checks a new name against. */
    val names: List<String> get() = bookmarks.mapNotNull { it.tag }
}

/** Why the checklist couldn't do what was asked; the host turns it into a message. */
sealed interface BookmarkPickerFailure {
    /** The chat's bookmarks couldn't be read, so the checklist closed rather than guess. */
    data object LoadFailed : BookmarkPickerFailure

    /** A tick or untick didn't save and was put back, or a new bookmark was made but not added to the chat. */
    data class ToggleFailed(val name: String) : BookmarkPickerFailure

    /** The new bookmark wasn't created; the editor stays up with what was typed. */
    data class CreateFailed(val name: String) : BookmarkPickerFailure
}

/**
 * Drives the per-chat bookmark checklist, one chat at a time, for every screen that offers it.
 * [open] reads the chat's bookmarks from the server before showing a tick; each [toggle] saves at
 * once through [TagRepository.setConversationTag] (which reads the chat again first, so tags set on
 * the web survive), its row disabled until the server answers, and a failure puts the tick back and
 * shows as [BookmarkPickerState.error]. [create] makes a new bookmark, attached to the chat when
 * asked, which then shows ticked. The favorite star (Saved) is never shown or touched here.
 *
 * [failures] carries what the open sheet can't show: a load that closed it, and a tick or create
 * that failed after it closed or moved to another chat.
 *
 * Answers that land after the checklist closed or moved to another chat leave its state alone.
 * A plain class: each host builds one on its own [scope] (a ViewModel's). The saves themselves run
 * in the repository's application scope, so a host cleared mid-save (the user leaves the screen)
 * only stops listening: the save still lands, though its failure then has no one to tell.
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
        val error: BookmarkPickerFailure? = null,
        /**
         * A tick that failed while the editor was up. The editor would show it as its own failure,
         * so it waits here and becomes [error] when the checklist comes back.
         */
        val heldChecklistError: BookmarkPickerFailure? = null,
        /** The [saveSeq] of the newest save whose answer was applied; an older answer is out of date. */
        val newestApplied: Long = 0L,
    )

    private val session = MutableStateFlow(Session())

    /** Bumped on every open and close, so a late answer can tell it belongs to an earlier one. */
    private var epoch = 0L
    private var loadJob: Job? = null

    /**
     * Stamps each tick and create in the order asked, which is the order the repository writes them
     * in. Answers can land out of that order (each waits on its own tag-list re-read), and an older
     * one's list of the chat's bookmarks predates the newer writes.
     */
    private var saveSeq = 0L

    /** Ticks and creates still queued or out, which outlive a close but not an account switch. */
    private val saveJobs = mutableSetOf<Job>()

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
            error = s.error,
        )
    }.stateIn(scope, SharingStarted.Eagerly, BookmarkPickerState())

    /** Opens the checklist on [conversationId], reads which bookmarks it carries and re-reads the list. */
    fun open(conversationId: String) {
        loadJob?.cancel()
        val opened = ++epoch
        session.value = Session(conversationId = conversationId, loading = true)
        loadJob = scope.launch {
            // The tag list too, so a bookmark deleted or renamed elsewhere is gone before anyone can
            // tick it. Its answer is never read: a stale list still shows, and the tick posts the name.
            val listRefresh = launch { tagRepository.refreshTags() }
            val result = tagRepository.getConversationTags(conversationId)
            listRefresh.join()
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

    /**
     * Closes the checklist and stops waiting on every tick and create still queued or out, failures
     * unreported: the active account changed, and the chat they were for belongs to the old one.
     * The repository sends none of their further requests to the new account; one already sent
     * may still land on the old account's server, where it belongs.
     */
    fun closeForAccountSwitch() {
        close()
        saveJobs.toList().forEach { it.cancel() }
        saveJobs.clear()
    }

    /** Launches a save tracked in [saveJobs] until it ends. */
    private fun launchSave(block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(block = block)
        if (job.isActive) {
            saveJobs += job
            job.invokeOnCompletion { saveJobs -= job }
        }
    }

    /** Ticks or unticks [name] on the open chat and saves it. Ignored while that row is saving. */
    fun toggle(name: String) {
        val current = session.value
        val conversationId = current.conversationId ?: return
        if (current.loading || current.editorOpen || name in current.pending) return
        val attach = name !in current.attached
        session.update {
            it.copy(
                attached = if (attach) it.attached + name else it.attached - name,
                pending = it.pending + name,
                error = null,
            )
        }
        val opened = epoch
        val seq = ++saveSeq
        launchSave {
            val result = tagRepository.setConversationTag(conversationId, name, attach)
            if (opened != epoch) {
                if (result is Result.Error && !result.accountSwitched()) {
                    _failures.emit(BookmarkPickerFailure.ToggleFailed(name))
                }
                return@launchSave
            }
            session.update { s ->
                val stillPending = s.pending - name
                when (result) {
                    // Older than an answer already applied: its list misses the newer writes, so only
                    // this row is taken from it.
                    is Result.Success -> if (seq < s.newestApplied) {
                        s.copy(
                            attached = if (name in result.data) s.attached + name else s.attached - name,
                            pending = stillPending,
                        )
                    } else {
                        // The server's answer is the truth, except for other rows whose own save is out.
                        s.copy(
                            attached = (result.data.bookmarkNames() - stillPending) + (s.attached intersect stillPending),
                            pending = stillPending,
                            newestApplied = seq,
                        )
                    }
                    is Result.Error -> {
                        val failure = BookmarkPickerFailure.ToggleFailed(name)
                        s.copy(
                            attached = if (attach) s.attached - name else s.attached + name,
                            pending = stillPending,
                            error = if (s.editorOpen) s.error else failure,
                            heldChecklistError = if (s.editorOpen) failure else s.heldChecklistError,
                        )
                    }
                    is Result.Loading -> s
                }
            }
        }
    }

    /** "New bookmark": shows the editor in place of the checklist. */
    fun openEditor() {
        val current = session.value
        if (current.conversationId == null || current.loading) return
        session.update { it.copy(editorOpen = true, error = null) }
    }

    /**
     * Back from the editor to the checklist, which then shows a tick that failed meanwhile. Ignored
     * while its create is in flight.
     */
    fun closeEditor() {
        session.update {
            if (it.creating) it else it.copy(editorOpen = false, error = it.heldChecklistError, heldChecklistError = null)
        }
    }

    /**
     * Creates [name] at the end of the order, added to the open chat when [addToChat]. On success
     * the editor closes and, if added, the new bookmark shows ticked. Made but not added, the editor
     * closes too and the bookmark's row shows unticked with a [BookmarkPickerFailure.ToggleFailed].
     */
    fun create(name: String, description: String, addToChat: Boolean) {
        val current = session.value
        val conversationId = current.conversationId ?: return
        if (current.creating) return
        session.update { it.copy(creating = true, error = null) }
        val opened = epoch
        // Ticks are held while the editor is up, so none can be asked between this and its answer.
        val seq = ++saveSeq
        launchSave {
            val result = tagRepository.createTag(
                name = name,
                description = description.takeIf { it.isNotBlank() },
                addToConversationId = conversationId.takeIf { addToChat },
            )
            if (opened != epoch) {
                failureAfterClose(result, name)?.let { _failures.emit(it) }
                return@launchSave
            }
            session.update { s ->
                when (result) {
                    is Result.Success -> {
                        val created = result.data.tag.tag?.takeIf { it.isNotBlank() } ?: name.trim()
                        when {
                            // Made but not added: the editor's job is done, and its row shows the untick.
                            // It says the same as a tick held while the editor was up, and is newer.
                            result.data.attachFailed -> s.copy(
                                creating = false,
                                editorOpen = false,
                                attached = s.attached - created,
                                error = BookmarkPickerFailure.ToggleFailed(created),
                                heldChecklistError = null,
                                newestApplied = maxOf(s.newestApplied, seq),
                            )
                            else -> s.copy(
                                creating = false,
                                editorOpen = false,
                                attached = if (addToChat) s.attached + created else s.attached,
                                error = s.heldChecklistError,
                                heldChecklistError = null,
                                newestApplied = maxOf(s.newestApplied, seq),
                            )
                        }
                    }
                    // Not made: the editor stays up with what was typed, and says why.
                    is Result.Error -> s.copy(creating = false, error = BookmarkPickerFailure.CreateFailed(name))
                    is Result.Loading -> s
                }
            }
        }
    }

    /** What a create that answered after the checklist closed or moved on must still report. */
    private fun failureAfterClose(result: Result<CreatedTag>, name: String): BookmarkPickerFailure? = when {
        result is Result.Error -> BookmarkPickerFailure.CreateFailed(name).takeUnless { result.accountSwitched() }
        result is Result.Success && result.data.attachFailed ->
            BookmarkPickerFailure.ToggleFailed(result.data.tag.tag?.takeIf { it.isNotBlank() } ?: name.trim())
        else -> null
    }

    /** Stopped because the account changed: the chat is the old account's, so nothing to report. */
    private fun Result.Error.accountSwitched(): Boolean = exception is AccountSwitchedException

    private fun List<String>.bookmarkNames(): Set<String> =
        // Not trimmed: server names are opaque, and a trimmed one would match no row.
        filter { it.isNotBlank() && it != SAVED_TAG }.toSet()

    private companion object {
        const val FAILURE_BUFFER = 4
    }
}
