package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.feature.conversations.ArchivedConversationDisplayData
import com.garfiec.librechat.feature.conversations.toArchivedDisplayData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ArchivedConversationsUiState(
    val conversations: List<ArchivedConversationDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    /** Only the server's cursor decides this: the cache can hold archived chats past the pages loaded. */
    val hasMore: Boolean = true,
    val loadMoreFailed: Boolean = false,
)

sealed interface ArchivedConversationsEvent {
    data class ShowError(val message: String) : ArchivedConversationsEvent
}

class ArchivedConversationsViewModel(
    private val conversationRepository: ConversationRepository,
    private val configRepository: ConfigRepository,
    private val agentRepository: AgentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchivedConversationsUiState())
    val uiState: StateFlow<ArchivedConversationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ArchivedConversationsEvent>()
    val events: SharedFlow<ArchivedConversationsEvent> = _events.asSharedFlow()

    /** Raw conversations kept for delete confirmation dialog titles. */
    private var rawConversations: List<Conversation> = emptyList()

    private var nextCursor: String? = null

    /** Bumped by each first page, so a next page started before it doesn't land its stale cursor. */
    private var generation = 0

    /** An agent chat's model is the agent's id; its name is looked up once per id. */
    private val agentNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val requestedAgentIds = mutableSetOf<String>()

    init {
        observeArchivedConversations()
        loadArchivedConversations()
    }

    fun getConversation(conversationId: String): Conversation? =
        rawConversations.firstOrNull { it.conversationId == conversationId }

    private fun observeArchivedConversations() {
        viewModelScope.launch {
            combine(
                conversationRepository.observeConversations(isArchived = true),
                configRepository.endpointConfigs,
                configRepository.startupConfig.map { it?.modelSpecs }.distinctUntilChanged(),
                agentNames,
            ) { result, configs, modelSpecs, names ->
                if (result is Result.Success) {
                    result.data.forEach { resolveAgentName(it.model) }
                    result to result.data.map { it.toArchivedDisplayData(configs, modelSpecs, names) }
                } else {
                    result to null
                }
            }.collect { (result, rows) ->
                when (result) {
                    // isLoading is left to the first page: the cache's first emission is often empty
                    // and would otherwise flash the empty state while that page is still loading.
                    is Result.Success -> {
                        rawConversations = result.data
                        _uiState.update { it.copy(conversations = rows.orEmpty()) }
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(error = result.message, isLoading = false) }
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

    private fun resolveAgentName(model: String?) {
        if (model == null || !model.startsWith(AGENT_ID_PREFIX) || !requestedAgentIds.add(model)) return
        viewModelScope.launch {
            val name = (agentRepository.getAgent(model) as? Result.Success)?.data?.name
                ?.takeIf { it.isNotBlank() } ?: return@launch
            agentNames.update { it + (model to name) }
        }
    }

    fun loadArchivedConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val error = loadFirstPage()
            _uiState.update { it.copy(isLoading = false, error = error ?: it.error) }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val error = loadFirstPage()
            _uiState.update { it.copy(isRefreshing = false, error = error ?: it.error) }
        }
    }

    /**
     * Starts the list over from the server's first page; returns the failure's message, if any. A
     * failure puts the previous paging back, so the list keeps paging (or offering Retry) from where
     * it was: the rows on screen come from the cache, so its cursor still follows them.
     */
    private suspend fun loadFirstPage(): String? {
        val gen = ++generation
        val previousCursor = nextCursor
        val previous = _uiState.value
        nextCursor = null
        _uiState.update { it.copy(hasMore = true, isLoadingMore = false, loadMoreFailed = false) }
        val result = conversationRepository.loadNextPage(cursor = null, isArchived = true)
        if (gen != generation) return (result as? Result.Error)?.message
        when (result) {
            is Result.Success -> {
                nextCursor = result.data
                _uiState.update { it.copy(hasMore = result.data != null) }
            }
            is Result.Error -> {
                nextCursor = previousCursor
                _uiState.update { it.copy(hasMore = previous.hasMore, loadMoreFailed = previous.loadMoreFailed) }
            }
            is Result.Loading -> Unit
        }
        return (result as? Result.Error)?.message
    }

    fun loadMore() {
        val state = _uiState.value
        // A next page alongside a first page would follow a cursor the first page is replacing.
        if (state.isLoadingMore || !state.hasMore || state.loadMoreFailed || state.isLoading || state.isRefreshing) return
        val cursor = nextCursor ?: return
        val gen = generation
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = conversationRepository.loadNextPage(cursor = cursor, isArchived = true)
            if (gen != generation) return@launch
            when (result) {
                is Result.Success -> {
                    nextCursor = result.data
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = result.data != null) }
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Failed to load archived conversations page" }
                    _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
                }
                is Result.Loading -> _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    fun unarchiveConversation(id: String) {
        viewModelScope.launch {
            val result = conversationRepository.archive(id, false)
            if (result is Result.Error) {
                Logger.e(result.exception) { "Failed to unarchive conversation" }
                _events.emit(ArchivedConversationsEvent.ShowError("Failed to unarchive conversation"))
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val result = conversationRepository.delete(id)
            if (result is Result.Error) {
                Logger.e(result.exception) { "Failed to delete conversation" }
                _events.emit(ArchivedConversationsEvent.ShowError("Failed to delete conversation"))
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val AGENT_ID_PREFIX = "agent_"
    }
}
