package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.feature.conversations.ArchivedConversationDisplayData
import com.garfiec.librechat.feature.conversations.toArchivedDisplayData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ArchivedConversationsUiState(
    val conversations: List<ArchivedConversationDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

sealed interface ArchivedConversationsEvent {
    data class ShowError(val message: String) : ArchivedConversationsEvent
}

class ArchivedConversationsViewModel(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchivedConversationsUiState())
    val uiState: StateFlow<ArchivedConversationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ArchivedConversationsEvent>()
    val events: SharedFlow<ArchivedConversationsEvent> = _events.asSharedFlow()

    /** Raw conversations kept for delete confirmation dialog titles. */
    private var rawConversations: List<Conversation> = emptyList()

    init {
        observeArchivedConversations()
        loadArchivedConversations()
    }

    fun getConversation(conversationId: String): Conversation? =
        rawConversations.firstOrNull { it.conversationId == conversationId }

    private fun observeArchivedConversations() {
        viewModelScope.launch {
            conversationRepository.observeConversations(isArchived = true).collect { result ->
                when (result) {
                    is Result.Success -> {
                        rawConversations = result.data
                        _uiState.value = _uiState.value.copy(
                            conversations = result.data.map { it.toArchivedDisplayData() },
                            isLoading = false,
                        )
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message,
                            isLoading = false,
                        )
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

    fun loadArchivedConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = conversationRepository.loadNextPage(cursor = null, isArchived = true)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            when (val result = conversationRepository.loadNextPage(cursor = null, isArchived = true)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.message,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun unarchiveConversation(id: String) {
        viewModelScope.launch {
            try {
                conversationRepository.archive(id, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to unarchive conversation" }
                _events.emit(ArchivedConversationsEvent.ShowError("Failed to unarchive conversation"))
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            try {
                conversationRepository.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to delete conversation" }
                _events.emit(ArchivedConversationsEvent.ShowError("Failed to delete conversation"))
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
