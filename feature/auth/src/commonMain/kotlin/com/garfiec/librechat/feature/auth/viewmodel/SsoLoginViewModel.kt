package com.garfiec.librechat.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.model.auth.AuthCookies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SsoLoginViewModel(
    private val authRepository: AuthRepository,
    serverDataStore: ServerDataStore,
    accountSwitcher: AccountSwitcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SsoLoginUiState())
    val uiState: StateFlow<SsoLoginUiState> = _uiState.asStateFlow()

    /** The server this screen signs into: the pending add target when set, else the live one. */
    val serverUrl: String = accountSwitcher.pendingAdd?.serverUrl ?: serverDataStore.getBaseUrl()

    /** Whether the round-trip may reuse the provider's session: only to re-authenticate the account
     *  whose session ended. Any other sign-in must reach the provider's account chooser. */
    val keepProviderSession: Boolean = accountSwitcher.signInReauthenticatesActive()

    private var tokenConsumed = false

    fun onTokenCapture(cookies: AuthCookies) {
        if (tokenConsumed) return
        tokenConsumed = true

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = authRepository.loginWithOAuthToken(cookies)) {
                is Result.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                is Result.Error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (result.exception is ApiException) {
                                SsoLoginError.ExchangeRejected
                            } else {
                                SsoLoginError.Exchange(result.message)
                            },
                        )
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
