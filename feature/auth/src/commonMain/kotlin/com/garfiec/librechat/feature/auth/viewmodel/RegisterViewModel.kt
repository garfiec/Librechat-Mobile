package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.LoginOutcome
import com.garfiec.librechat.feature.auth.diagnostics.offersDiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    /** A form problem the screen words itself; shown in place of [error]. */
    val registerError: RegisterError? = null,
    /** Where the screen goes once the account exists. */
    val outcome: RegisterOutcome? = null,
    /** The server mails a verification link (`emailEnabled`); otherwise the new account is already verified. */
    val emailEnabled: Boolean = true,
    val emailLoginEnabled: Boolean = true,
    val minPasswordLength: Int = 8,
    /** The server's published policy links, as the sign-in footer shows them; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
    val banner: Banner? = null,
)

enum class RegisterError { MissingFields, PasswordTooShort, PasswordMismatch, EmailTaken }

sealed interface RegisterOutcome {
    /** The server mailed a verification link to [email]. */
    data class VerifyEmail(val email: String) : RegisterOutcome
    data object SignedIn : RegisterOutcome
    data class TwoFactor(val tempToken: String) : RegisterOutcome
    data class TwoFactorSetup(val tempToken: String) : RegisterOutcome

    /** The account exists but this server doesn't take email sign-in; the user signs in from Login. */
    data object SignIn : RegisterOutcome
}

class RegisterViewModel(
    private val authRepository: AuthRepository,
    configRepository: ConfigRepository,
    accountSwitcher: AccountSwitcher,
    serverDataStore: ServerDataStore,
    bannerRepository: BannerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The add-account flow's server when one is pending, not the live account's.
            authServerConfig(configRepository, accountSwitcher, serverDataStore).collect { (config, url) ->
                _uiState.value = _uiState.value.copy(
                    minPasswordLength = config.minPasswordLength ?: 8,
                    policyLinks = PolicyLinks.from(config, url),
                    emailEnabled = config.emailEnabled,
                    emailLoginEnabled = config.emailLoginEnabled,
                )
            }
        }
        viewModelScope.launch {
            val banner = publicBanner(bannerRepository, accountSwitcher)
            _uiState.update { it.copy(banner = banner) }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, error = null, registerError = null)
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null, registerError = null)
    }

    fun onUsernameChanged(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null, registerError = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null, registerError = null)
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword, error = null, registerError = null)
    }

    fun register() {
        val state = _uiState.value.copy(offerDiagnosticLog = false, refusal = null, error = null)

        // The server accepts a blank username.
        val formError = when {
            state.name.isBlank() || state.email.isBlank() || state.password.isBlank() -> RegisterError.MissingFields
            state.password.length < state.minPasswordLength -> RegisterError.PasswordTooShort
            state.password != state.confirmPassword -> RegisterError.PasswordMismatch
            else -> null
        }
        if (formError != null) {
            _uiState.value = state.copy(registerError = formError)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                registerError = null,
                offerDiagnosticLog = false,
            )

            when (val result = authRepository.register(state.name, state.email, state.username, state.password)) {
                is Result.Success -> onRegistered(state)
                is Result.Error -> showError(result)
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * The register reply is the same whether or not a mail went out (or the address was already in
     * use), so the server's `emailEnabled` decides: with mail, the account waits for its link; without,
     * it is already verified and signs in with the same credentials.
     */
    private suspend fun onRegistered(state: RegisterUiState) {
        val current = _uiState.value
        val outcome = when {
            current.emailEnabled -> RegisterOutcome.VerifyEmail(state.email)
            !current.emailLoginEnabled -> RegisterOutcome.SignIn
            else -> when (val login = authRepository.login(state.email, state.password)) {
                is Result.Success -> when (val signedIn = login.data) {
                    is LoginOutcome.Success -> RegisterOutcome.SignedIn
                    is LoginOutcome.TwoFactorRequired -> RegisterOutcome.TwoFactor(signedIn.tempToken)
                    is LoginOutcome.TwoFactorSetupRequired -> RegisterOutcome.TwoFactorSetup(signedIn.tempToken)
                }
                is Result.Error -> {
                    // A new account has this password, so a wrong-password 404 means the address was
                    // already registered.
                    val apiException = login.exception as? ApiException
                    if (apiException?.statusCode == HTTP_NOT_FOUND && apiException.serverAuthored) {
                        _uiState.value = _uiState.value.copy(isLoading = false, registerError = RegisterError.EmailTaken)
                    } else {
                        showError(login)
                    }
                    return
                }
                is Result.Loading -> return
            }
        }
        _uiState.value = _uiState.value.copy(isLoading = false, outcome = outcome)
    }

    private fun showError(result: Result.Error) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = result.message ?: "Registration failed",
            refusal = result.authRefusalOrNull(),
            offerDiagnosticLog = result.offersDiagnosticLog(),
        )
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
