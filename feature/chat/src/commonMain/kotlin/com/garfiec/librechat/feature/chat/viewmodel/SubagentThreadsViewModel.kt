package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SubagentRepository
import com.garfiec.librechat.core.model.subagent.SubagentSummary
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class SubagentThreadsUiState(
    val children: List<SubagentSummary> = emptyList(),
    val childrenTruncated: Boolean = false,
    val isLoadingIndex: Boolean = false,
    /** The child being viewed, or null while the list is showing. */
    val openThread: SubagentThreadView? = null,
    val openThreadId: String? = null,
    val isLoadingThread: Boolean = false,
    val isLoadingOlder: Boolean = false,
    /**
     * The conversation has no child view — it has no children, it is not the caller's, or it is
     * itself a subagent thread. One 404 covers all three, and none of them is a verdict about the
     * server, so this never turns the feature off anywhere else.
     */
    val hasNoChildView: Boolean = false,
    val error: String? = null,
) {
    val isShowingThread: Boolean get() = openThreadId != null

    /** Older history exists and can still be asked for. */
    val canLoadOlder: Boolean
        get() = openThread?.nextCursor != null && openThread.historyUnavailable != true

    /**
     * Older history is gone and no cursor will bring it back — the retained chain vanished between
     * requests. Rendering a load-more here would spin forever on nothing.
     */
    val olderHistoryUnavailable: Boolean
        get() = openThread?.historyUnavailable == true || (openThread?.historyTruncated == true && !canLoadOlder)
}

/**
 * Read-only viewing of the child threads a conversation spawned (v0.8.8-rc2).
 *
 * **Everything is fetched lazily, on the user opening the sheet.** The index is the only place a
 * `threadId` exists, so nothing can be shown without it — and a conversation that never spawned a
 * child should cost no request at all.
 */
class SubagentThreadsViewModel(
    private val subagentRepository: SubagentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubagentThreadsUiState())
    val uiState: StateFlow<SubagentThreadsUiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null

    /**
     * The tool call the LATEST [openFor] asked to focus.
     *
     * Read back when the index lands rather than captured by the launch: the sheet can be
     * dismissed and reopened on a different card while the first read is still out, and the
     * launching call's parameter then names the tap the user abandoned.
     */
    private var requestedFocus: String? = null

    /**
     * Loads the conversation's children, once per conversation.
     *
     * Skipped entirely on a server KNOWN to predate the routes; an unplaceable one is asked,
     * because it is the population most likely to have them.
     */
    fun openFor(parentConversationId: String, focusParentToolCallId: String?) {
        if (subagentRepository.isRuledOutForServer()) {
            _uiState.value = SubagentThreadsUiState(hasNoChildView = true)
            return
        }
        requestedFocus = focusParentToolCallId
        if (loadedFor == parentConversationId) {
            // Still reading this conversation's index: there is nothing to focus against yet, and
            // restarting would throw away a request already in flight. The read lands on
            // `requestedFocus`, which the line above has just moved to this tap.
            if (_uiState.value.isLoadingIndex) return
            // Reopened on a conversation already read. Back to the list first, so dismissing while
            // a child was open does not reopen onto a stale thread the user did not ask for.
            backToList()
            focusChild(parentConversationId, focusParentToolCallId)
            return
        }
        loadedFor = parentConversationId
        _uiState.value = SubagentThreadsUiState(isLoadingIndex = true)
        viewModelScope.launch {
            val result = subagentRepository.getChildren(parentConversationId)
            // The sheet moved to another conversation while this was in flight. Landing A's
            // children under B's header would leave focusChild fetching A's threadIds against B —
            // and on the failure arm, A's 404 would clear the state B is loading under.
            if (loadedFor != parentConversationId) return@launch
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        children = result.data.children,
                        childrenTruncated = result.data.childrenTruncated,
                        isLoadingIndex = false,
                    )
                    // The latest request's focus, not this launch's: a reopen on another card
                    // while the read was out has already moved it.
                    focusChild(parentConversationId, requestedFocus)
                }
                is Result.Error -> {
                    // NOTHING here is remembered. A 404 is per-conversation — missing, not yours,
                    // or itself a child thread — and never a verdict about the server, so caching
                    // it would let one conversation's answer suppress every other. A transient
                    // failure is not remembered either: pinning it would leave the sheet broken
                    // for this conversation until the nav entry is recreated.
                    loadedFor = null
                    _uiState.value = _uiState.value.copy(
                        isLoadingIndex = false,
                        hasNoChildView = result.isNotFound(),
                        error = result.message.takeUnless { _ -> result.isNotFound() },
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** Opens the child spawned by [parentToolCallId], when the index names one. */
    private fun focusChild(parentConversationId: String, parentToolCallId: String?) {
        val threadId = parentToolCallId
            ?.let { id -> _uiState.value.children.firstOrNull { it.parentToolCallId == id } }
            ?.threadId
            ?: return
        openThread(parentConversationId, threadId)
    }

    fun openThread(parentConversationId: String, threadId: String) {
        _uiState.value = _uiState.value.copy(
            openThreadId = threadId,
            openThread = null,
            isLoadingThread = true,
            error = null,
        )
        viewModelScope.launch {
            val result = subagentRepository.getThread(parentConversationId, threadId)
            // The selection moved on while this was in flight — the user went back to the list, or
            // opened a different child. Landing this page anyway would re-open a thread nobody
            // asked for, under the wrong header.
            if (_uiState.value.openThreadId != threadId) return@launch
            when (result) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    openThread = result.data,
                    isLoadingThread = false,
                )
                // The thread never opened, so the id has to go with the spinner. Left set, the
                // sheet falls past every thread arm of its `when` and draws the child list again
                // — under a back arrow (gated on [SubagentThreadsUiState.isShowingThread]) that
                // returns to the list the user is already looking at. The tap reads as a no-op.
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    openThreadId = null,
                    isLoadingThread = false,
                    error = result.message,
                )
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Replaces the view with the page older than the current one.
     *
     * The server refuses `taskId` and `cursor` together, so paging is always a plain cursor read.
     */
    fun loadOlder(parentConversationId: String) {
        val state = _uiState.value
        val cursor = state.openThread?.nextCursor ?: return
        val threadId = state.openThreadId ?: return
        if (state.isLoadingOlder) return
        _uiState.value = state.copy(isLoadingOlder = true, error = null)
        viewModelScope.launch {
            val result = subagentRepository.getOlderPage(parentConversationId, threadId, cursor)
            // Same rule as the thread read: a page for a thread that is no longer open would
            // re-open it.
            if (_uiState.value.openThreadId != threadId) return@launch
            when (result) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    openThread = result.data,
                    isLoadingOlder = false,
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingOlder = false,
                    error = result.message,
                )
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Returns to the child list, including from the spinner a thread load puts up.
     *
     * [SubagentThreadsUiState.isLoadingThread] has to be cleared too: the sheet renders the
     * spinner ahead of everything else, so leaving it set strands the user on a spinner with the
     * back arrow — gated on [SubagentThreadsUiState.isShowingThread] — already gone. The in-flight
     * read is left to finish and discards itself, since [openThreadId] no longer names it.
     */
    fun backToList() {
        _uiState.value = _uiState.value.copy(
            openThread = null,
            openThreadId = null,
            isLoadingThread = false,
            isLoadingOlder = false,
            error = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun Result.Error.isNotFound(): Boolean =
        (exception as? ApiException)?.statusCode == HTTP_NOT_FOUND

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
