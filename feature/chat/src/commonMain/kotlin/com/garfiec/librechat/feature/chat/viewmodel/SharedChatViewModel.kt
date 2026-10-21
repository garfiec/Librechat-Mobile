package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.share.ShareErrorCode
import com.garfiec.librechat.core.data.share.ShareFailure
import com.garfiec.librechat.core.data.share.toShareFailure
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FILE_SOURCE_TEXT
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.extractBranchMedia
import com.garfiec.librechat.feature.chat.util.withAttachmentSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the viewer has nothing to show. */
enum class SharedChatError {
    /** The link is gone, was never there, or holds no messages. Retry is offered. */
    NOT_FOUND,

    /** The server refused this account (401/403): the link is not shared with it. */
    NO_ACCESS,

    /** Past the server's share-view rate limit (429). */
    RATE_LIMITED,

    /** The server's content filter now refuses the shared content; see [SharedChatUiState.blockedMessage]. */
    BLOCKED,

    /** No answer from the server. */
    NETWORK,
    GENERIC,
}

/** A one-shot outcome the screen reports in a snackbar, or acts on. */
sealed interface SharedChatEvent {
    /** Continue made [conversationId]; the viewer gives way to it. */
    data class Continued(val conversationId: String) : SharedChatEvent

    /** The owner updated the link since it loaded; the viewer reloaded it, and Continue can be tried again. */
    data object LinkUpdated : SharedChatEvent

    /** Continue hit the fork rate limit. */
    data object ContinueRateLimited : SharedChatEvent

    /** The server's content filter refused to copy the chat; [serverMessage] says why, when it did. */
    data class ContinueBlocked(val serverMessage: String?) : SharedChatEvent

    /** Continue failed for another reason. */
    data object ContinueFailed : SharedChatEvent

    /** A refresh over content on screen didn't get through; [rateLimited] when the server said slow down. */
    data class RefreshFailed(val rateLimited: Boolean) : SharedChatEvent
}

/** A text-source file opened as text: its [text] once read, or [failed]. */
data class SharedTextFile(
    val filename: String,
    val text: String? = null,
    val failed: Boolean = false,
)

data class SharedChatUiState(
    /** The snapshot on screen, or null until the first load lands. */
    val snapshot: SharedConversationSnapshot? = null,
    /** The active branch through [snapshot], one node per level; see [buildActiveMessagePath]. */
    val displayMessages: List<MessageNode> = emptyList(),
    /** Chosen sibling per tree-parent key; what the user switched. Reset with every load. */
    val activeBranches: Map<String, Int> = emptyMap(),
    /** The first load (or a Retry from an error) is under way: nothing to show yet. */
    val isLoading: Boolean = true,
    /** A reload over content already on screen (pull to refresh, the 409 reload). */
    val isRefreshing: Boolean = false,
    /** Set when there is nothing to show; [snapshot] is then null. */
    val error: SharedChatError? = null,
    /** The content filter's explanation for [SharedChatError.BLOCKED], when the server gave one. */
    val blockedMessage: String? = null,
    /** Continue is in flight. Also the double-fork guard: a second tap does nothing until it lands. */
    val isContinuing: Boolean = false,
    val baseUrl: String = "",
    /** The link's public URL on the active server, for "Open in browser". */
    val shareUrl: String = "",
    val mediaPreview: MediaPreviewState? = null,
    val textFile: SharedTextFile? = null,
    /**
     * Files in [snapshot] whose source is `text`: the server keeps no bytes for them and serves
     * their extracted text, so they open as text whatever their original type says.
     */
    val textSourceFileIds: Set<String> = emptySet(),
) {
    /**
     * The link was published with a configured sender (a preset or model spec chose the label), so
     * the model behind it is withheld wherever a model name would show. The mobile message header
     * shows only `sender` today, so nothing draws a model name to hide; this is carried for any
     * label that does, as web's `getHeaderHoverLabel` does.
     */
    val hidesModelName: Boolean get() = snapshot?.hasConfiguredSender == true
}

/**
 * The in-app viewer for one shared link (`GET /api/share/:shareId`): the active branch with sibling
 * switching, its files read through the share's own routes, and Continue, which forks the branch
 * on screen into the user's account (`POST /api/share/:shareId/fork`).
 *
 * Every load hands out fresh anonymized ids, so branch choices are dropped with each one; the fork
 * addresses the branch by its tip's index in the snapshot instead (scripts/mirrors.json
 * `share-fork-index-revision`, `share-viewer-active-tail`).
 */
class SharedChatViewModel(
    private val shareId: String,
    private val shareRepository: ShareRepository,
    private val conversationRepository: ConversationRepository,
    private val activeAccountProvider: ActiveAccountProvider,
    private val serverUrlProvider: ServerUrlProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SharedChatUiState(baseUrl = serverUrlProvider.getBaseUrl(), shareUrl = shareRepository.shareUrl(shareId)),
    )
    val uiState: StateFlow<SharedChatUiState> = _uiState.asStateFlow()

    private val _events = Channel<SharedChatEvent>(Channel.BUFFERED)
    val events: Flow<SharedChatEvent> = _events.receiveAsFlow()

    /** Counts loads; a load writes back only while it is still the newest. */
    private var loadGeneration = 0L
    private var textFileJob: Job? = null

    init {
        load(quiet = false)
    }

    /** Loads the link again: Retry from an error, or pull to refresh over content. */
    fun reload() {
        load(quiet = _uiState.value.snapshot != null)
    }

    private fun load(quiet: Boolean) {
        val generation = ++loadGeneration
        _uiState.update {
            if (quiet) it.copy(isRefreshing = true) else it.copy(isLoading = true, error = null, blockedMessage = null)
        }
        viewModelScope.launch {
            val result = shareRepository.getSharedSnapshot(shareId)
            if (generation != loadGeneration) return@launch
            when (result) {
                is Result.Success -> show(result.data)
                is Result.Error -> fail(result.toShareFailure())
                Result.Loading -> Unit
            }
        }
    }

    private fun show(snapshot: SharedConversationSnapshot) {
        if (snapshot.messages.isEmpty()) {
            // Web reads an empty share as not found, and there is nothing to continue.
            _uiState.update {
                it.copy(
                    snapshot = null,
                    displayMessages = emptyList(),
                    activeBranches = emptyMap(),
                    isLoading = false,
                    isRefreshing = false,
                    error = SharedChatError.NOT_FOUND,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                snapshot = snapshot,
                // Ids are re-anonymized per request: a choice keyed on the last load's ids means nothing.
                activeBranches = emptyMap(),
                displayMessages = buildActiveMessagePath(snapshot.messages),
                textSourceFileIds = textSourceFileIds(snapshot.messages),
                isLoading = false,
                isRefreshing = false,
                error = null,
                blockedMessage = null,
            )
        }
    }

    private fun fail(failure: ShareFailure) {
        val error = failure.toViewerError()
        // A refresh that couldn't get through leaves what is on screen, says so, and the next pull
        // tries again. A link that is gone, closed to this account or now filtered is not shown.
        val keepsContent = _uiState.value.snapshot != null && error in TRANSIENT_ERRORS
        _uiState.update {
            if (keepsContent) {
                it.copy(isRefreshing = false)
            } else {
                it.copy(
                    snapshot = null,
                    displayMessages = emptyList(),
                    activeBranches = emptyMap(),
                    isLoading = false,
                    isRefreshing = false,
                    error = error,
                    blockedMessage = failure.serverMessage.takeIf { failure.isContentFilter },
                )
            }
        }
        if (keepsContent) {
            _events.trySend(SharedChatEvent.RefreshFailed(rateLimited = error == SharedChatError.RATE_LIMITED))
        }
    }

    /** Shows sibling [siblingIndex] at [parentKey], as the chat screen's branch arrows do. */
    fun switchBranch(parentKey: String, siblingIndex: Int) {
        _uiState.update { state ->
            val snapshot = state.snapshot ?: return@update state
            val branches = state.activeBranches + (parentKey to siblingIndex)
            state.copy(
                activeBranches = branches,
                displayMessages = buildActiveMessagePath(snapshot.messages, branches),
            )
        }
    }

    /**
     * Copies the branch on screen into the user's account. Sends the index of the branch's tip in
     * the snapshot and the snapshot's `updatedAt`, without which the server ignores the index and
     * copies everything. A second tap while one is in flight does nothing.
     */
    fun continueChat() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        if (state.isContinuing) return
        val targetIndex = activeTailIndex(snapshot.messages, state.displayMessages)
        // Captured now: the fork belongs to the account it was made on, even if a switch lands mid-call.
        val account = activeAccountProvider.currentAccountId()
        _uiState.update { it.copy(isContinuing = true) }
        viewModelScope.launch {
            when (val result = shareRepository.forkSharedConversation(shareId, targetIndex, snapshot.updatedAt)) {
                is Result.Success -> {
                    val conversation = result.data.conversation
                    val id = conversation.conversationId
                    if (id.isNullOrBlank()) {
                        _uiState.update { it.copy(isContinuing = false) }
                        _events.send(SharedChatEvent.ContinueFailed)
                    } else {
                        // Cached as the owner-side fork is, so the drawer has it before its next sync.
                        // isContinuing stays set: the viewer is on its way out.
                        conversationRepository.saveConversation(conversation, account)
                        _events.send(SharedChatEvent.Continued(id))
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isContinuing = false) }
                    onContinueFailed(result.toShareFailure())
                }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun onContinueFailed(failure: ShareFailure) {
        when {
            failure.code == ShareErrorCode.SHARE_REVISION_MISMATCH || failure.status == HTTP_CONFLICT -> {
                // What Continue aimed at no longer exists. Pull the current version in, so a retry
                // continues what is then on screen; never retried on the user's behalf.
                load(quiet = true)
                _events.send(SharedChatEvent.LinkUpdated)
            }
            failure.status == HTTP_TOO_MANY_REQUESTS -> _events.send(SharedChatEvent.ContinueRateLimited)
            failure.isContentFilter -> _events.send(SharedChatEvent.ContinueBlocked(failure.serverMessage))
            failure.status == HTTP_NOT_FOUND -> {
                // The link went away under the viewer: reloading shows that.
                load(quiet = true)
                _events.send(SharedChatEvent.ContinueFailed)
            }
            else -> _events.send(SharedChatEvent.ContinueFailed)
        }
    }

    /** Opens the full-screen media viewer at [url], paging through the branch's images. */
    fun openMedia(url: String) {
        if (url.isBlank()) return
        val state = _uiState.value
        val items = extractBranchMedia(
            displayMessages = state.displayMessages,
            activeToolCalls = emptyList(),
            streamingAttachments = emptyList(),
            baseUrl = state.baseUrl,
        )
        val index = items.indexOfFirst { it.url == url }
        val shown = if (index >= 0) items else listOf(MediaItem(url = url, contentDescription = ""))
        val attachments = state.displayMessages.flatMap { it.message.attachments.orEmpty() }
        _uiState.update {
            it.copy(
                mediaPreview = MediaPreviewState(
                    items = withAttachmentSources(shown, attachments, state.baseUrl),
                    initialIndex = index.coerceAtLeast(0),
                ),
            )
        }
    }

    fun closeMedia() {
        _uiState.update { it.copy(mediaPreview = null) }
    }

    /**
     * A file's bytes, through the share's own route. The owner's download route refuses a viewer,
     * so every file action in the viewer (save, share, open in, the PDF preview) comes through here.
     */
    suspend fun downloadAttachment(attachment: Attachment): ByteArray? {
        val fileId = attachment.fileId ?: return null
        return (shareRepository.downloadSharedFile(shareId, fileId) as? Result.Success)?.data
    }

    /**
     * Opens a `text`-source file as text. The server stores no bytes for one and serves its
     * extracted text, which is what a viewer can read of it.
     */
    fun openTextFile(fileId: String, filename: String) {
        textFileJob?.cancel()
        _uiState.update { it.copy(textFile = SharedTextFile(filename)) }
        textFileJob = viewModelScope.launch {
            val result = shareRepository.downloadSharedFile(shareId, fileId)
            _uiState.update { state ->
                if (state.textFile?.filename != filename) return@update state
                state.copy(
                    textFile = when (result) {
                        is Result.Success -> SharedTextFile(filename, text = result.data.decodeToString())
                        else -> SharedTextFile(filename, failed = true)
                    },
                )
            }
        }
    }

    fun closeTextFile() {
        textFileJob?.cancel()
        _uiState.update { it.copy(textFile = null) }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY_REQUESTS = 429

        val TRANSIENT_ERRORS = setOf(SharedChatError.NETWORK, SharedChatError.GENERIC, SharedChatError.RATE_LIMITED)

        fun ShareFailure.toViewerError(): SharedChatError = when {
            isContentFilter -> SharedChatError.BLOCKED
            status == HTTP_NOT_FOUND || code == ShareErrorCode.SHARE_NOT_FOUND -> SharedChatError.NOT_FOUND
            status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN -> SharedChatError.NO_ACCESS
            status == HTTP_TOO_MANY_REQUESTS -> SharedChatError.RATE_LIMITED
            kind == FailureKind.Network || kind == FailureKind.Timeout -> SharedChatError.NETWORK
            else -> SharedChatError.GENERIC
        }
    }
}

/** The ids of every `text`-source file and attachment in [messages]. */
internal fun textSourceFileIds(messages: List<Message>): Set<String> = buildSet {
    for (message in messages) {
        message.files?.forEach { file -> if (file.source == FILE_SOURCE_TEXT) file.fileId?.let(::add) }
        message.attachments?.forEach { a -> if (a.source == FILE_SOURCE_TEXT) a.fileId?.let(::add) }
    }
}

/**
 * The index, in the snapshot's [messages], of the message at the tip of the branch on screen: what
 * a fork names, since a viewer never sees stable message ids. Null when the path is empty or its
 * tip isn't in the list (it always is; the server then copies the whole share). Mirrors web's
 * `selectActiveBranchTail` + `findIndex` (scripts/mirrors.json `share-viewer-active-tail`).
 */
internal fun activeTailIndex(messages: List<Message>, displayMessages: List<MessageNode>): Int? {
    val tail = displayMessages.lastOrNull()?.message?.messageId ?: return null
    return messages.indexOfFirst { it.messageId == tail }.takeIf { it >= 0 }
}
