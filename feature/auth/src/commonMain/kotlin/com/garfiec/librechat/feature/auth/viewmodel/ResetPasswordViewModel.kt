package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.feature.auth.diagnostics.offersDiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    val isReset: Boolean = false,
    /** The server's published policy links, as the sign-in footer shows them; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
)

class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    configRepository: ConfigRepository,
    accountSwitcher: AccountSwitcher,
    serverDataStore: ServerDataStore,
    initialUserId: String? = null,
    initialToken: String? = null,
) : ViewModel() {

    private val userId: String = initialUserId ?: ""
    private val token: String = initialToken ?: ""

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authServerConfig(configRepository, accountSwitcher, serverDataStore).collect { (config, url) ->
                _uiState.update { it.copy(policyLinks = PolicyLinks.from(config, url)) }
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword, error = null)
    }

    fun resetPassword() {
        val state = _uiState.value.copy(offerDiagnosticLog = false, refusal = null)

        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a new password")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords do not match")
            return
        }
        if (userId.isBlank() || token.isBlank()) {
            _uiState.value = state.copy(error = "Invalid reset link. Please request a new one.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, offerDiagnosticLog = false)
            when (val result = authRepository.resetPassword(userId, token, state.password, state.confirmPassword)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isReset = true,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.rateLimitMessageOrNull()
                            ?: "Could not reset password. The link may have expired.",
                        refusal = result.authRefusalOrNull(),
                        offerDiagnosticLog = result.offersDiagnosticLog(),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
