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
        if (loadedFor == parentConversationId) {
            // Reopened on a conversation already read. Back to the list first, so dismissing while
            // a child was open does not reopen onto a stale thread the user did not ask for.
            backToList()
            focusChild(parentConversationId, focusParentToolCallId)
            return
        }
        loadedFor = parentConversationId
        _uiState.value = SubagentThreadsUiState(isLoadingIndex = true)
        viewModelScope.launch {
            when (val result = subagentRepository.getChildren(parentConversationId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        children = result.data.children,
                        childrenTruncated = result.data.childrenTruncated,
                        isLoadingIndex = false,
                    )
                    focusChild(parentConversationId, focusParentToolCallId)
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
            when (val result = subagentRepository.getThread(parentConversationId, threadId)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    openThread = result.data,
                    isLoadingThread = false,
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
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
            when (val result = subagentRepository.getOlderPage(parentConversationId, threadId, cursor)) {
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

    fun backToList() {
        _uiState.value = _uiState.value.copy(openThread = null, openThreadId = null, error = null)
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
