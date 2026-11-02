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
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    val isSent: Boolean = false,
    /** The server's published policy links, as the sign-in footer shows them; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
    configRepository: ConfigRepository,
    accountSwitcher: AccountSwitcher,
    serverDataStore: ServerDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authServerConfig(configRepository, accountSwitcher, serverDataStore).collect { (config, url) ->
                _uiState.update { it.copy(policyLinks = PolicyLinks.from(config, url)) }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun requestReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Please enter your email",
                offerDiagnosticLog = false,
                refusal = null,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, offerDiagnosticLog = false)
            when (val result = authRepository.requestPasswordReset(email)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSent = true)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                            ?: "Could not send reset email. Please check your connection and try again.",
                        refusal = result.authRefusalOrNull(),
                        offerDiagnosticLog = result.offersDiagnosticLog(),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
