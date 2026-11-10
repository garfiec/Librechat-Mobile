package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ApiKeyRepository
import com.garfiec.librechat.core.model.ApiKey
import com.garfiec.librechat.core.model.request.CreateApiKeyRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ApiKeysUiState(
    val keys: List<ApiKey> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val isCreating: Boolean = false,
    val createdKey: ApiKey? = null,
    val deletingKeyId: String? = null,
    /** Why the last load of the list failed; the page shows it in place of an empty list. */
    val loadFailure: ApiKeysLoadFailure? = null,
)

@Immutable
sealed interface ApiKeysLoadFailure {
    /** The server has no API keys route (404). */
    data object NotSupported : ApiKeysLoadFailure

    /** Any other failure, with the server's or client's message when there is one. Retry reloads. */
    data class Failed(val message: String?) : ApiKeysLoadFailure
}

/**
 * Manages API key CRUD operations for the API Keys settings screen.
 *
 * After creation, the key value is shown once via [ApiKeysUiState.createdKey]
 * and cannot be retrieved again from the server.
 */
class ApiKeysViewModel(
    private val apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeysUiState())
    val uiState: StateFlow<ApiKeysUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, loadFailure = null)
            when (val result = apiKeyRepository.listApiKeys()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        keys = result.data,
                        isLoading = false,
                    )
                }
                is Result.Error -> {
                    val notFound = (result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadFailure = if (notFound) {
                            ApiKeysLoadFailure.NotSupported
                        } else {
                            ApiKeysLoadFailure.Failed(result.message)
                        },
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            when (val result = apiKeyRepository.listApiKeys()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        keys = result.data,
                        isRefreshing = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.message ?: "Failed to refresh API keys",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            createdKey = null,
        )
    }

    fun createKey(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            when (val result = apiKeyRepository.createApiKey(CreateApiKeyRequest(name = name))) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdKey = result.data,
                    )
                    loadKeys()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        error = result.message ?: "Failed to create API key",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deleteKey(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingKeyId = id)
            when (val result = apiKeyRepository.deleteApiKey(id)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        keys = _uiState.value.keys.filter { it.id != id },
                        deletingKeyId = null,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        deletingKeyId = null,
                        error = result.message ?: "Failed to delete API key",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

private const val HTTP_NOT_FOUND = 404
