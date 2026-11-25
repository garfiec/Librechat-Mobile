package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ApiKey

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
