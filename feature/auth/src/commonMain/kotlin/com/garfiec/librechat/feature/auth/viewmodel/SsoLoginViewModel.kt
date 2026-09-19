package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why a type and not a message: the server's `?error=` value is a machine code (`AUTH_FAILED`), and
 * since upstream dropped `failureMessage` a rejected callback is a bare redirect to `/oauth/error`
 * carrying nothing at all. Neither is renderable prose, so the screen owns the wording.
 */
@Immutable
sealed interface SsoLoginError {
    /** The provider or the server's callback rejected the sign-in. [code] is diagnostic only. */
    data class Provider(val code: String?) : SsoLoginError

    /** The round-trip finished but no `refreshToken` cookie appeared. The primary failure signal. */
    data object CaptureFailed : SsoLoginError

    /** Token-exchange failure. [message] is already screened by `Throwable.toSafeError`. */
    data class Exchange(val message: String?) : SsoLoginError
}

@Immutable
data class SsoLoginUiState(
    val isLoading: Boolean = false,
    val error: SsoLoginError? = null,
    val isLoggedIn: Boolean = false,
)

class SsoLoginViewModel(
    private val authRepository: AuthRepository,
    serverDataStore: ServerDataStore,
    accountSwitcher: AccountSwitcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SsoLoginUiState())
    val uiState: StateFlow<SsoLoginUiState> = _uiState.asStateFlow()

    /** The server this screen signs into: the pending add target when set, else the live one. */
    val serverUrl: String = accountSwitcher.pendingAdd?.serverUrl ?: serverDataStore.getBaseUrl()

    private var tokenConsumed = false

    fun onTokenCapture(refreshToken: String) {
        if (tokenConsumed) return
        tokenConsumed = true

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = authRepository.loginWithOAuthToken(refreshToken)) {
                is Result.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                is Result.Error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = SsoLoginError.Exchange(result.message))
                    }
                is Result.Loading -> Unit
            }
        }
    }

    fun onOAuthError(code: String?) = fail(SsoLoginError.Provider(code))

    fun onCaptureFailed() = fail(SsoLoginError.CaptureFailed)

    private fun fail(error: SsoLoginError) {
        if (tokenConsumed) return
        _uiState.update { it.copy(isLoading = false, error = error) }
    }
}
