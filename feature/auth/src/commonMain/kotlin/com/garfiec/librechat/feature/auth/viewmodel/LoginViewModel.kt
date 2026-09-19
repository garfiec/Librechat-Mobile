package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SsoRiskDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.LoginOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val twoFactorTempToken: String? = null,
    val registrationEnabled: Boolean = false,
    val socialLoginEnabled: Boolean = false,
    val socialLogins: List<String> = emptyList(),
    // ALLOW_EMAIL_LOGIN (upstream #14180): when the server disables email/password login it now
    // enforces it with a 403 on POST /api/auth/login. Fail-open to true so the form shows until
    // config confirms otherwise. Drives hiding the email/password form.
    val emailLoginEnabled: Boolean = true,
    /** Set while the in-app-browser warning is up; carries the provider the user picked. */
    val pendingSsoProvider: String? = null,
    val ssoProvider: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
    private val accountSwitcher: AccountSwitcher,
    private val ssoRiskDataStore: SsoRiskDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // In an add-account flow this screen signs into the PENDING server while another account is
        // live, so its feature flags (registration, social logins) must come from the pending
        // session's probed config — the global startupConfig still describes the live server. The
        // repository layer routes the sign-in calls themselves via the same pending session.
        val configSource = accountSwitcher.pendingAdd?.startupConfig ?: configRepository.startupConfig
        viewModelScope.launch {
            configSource.collect { config ->
                if (config != null) {
                    _uiState.value = _uiState.value.copy(
                        registrationEnabled = config.registrationEnabled,
                        socialLoginEnabled = config.socialLoginEnabled,
                        socialLogins = config.socialLogins.orEmpty(),
                        emailLoginEnabled = config.emailLoginEnabled,
                    )
                }
            }
        }
    }

    /**
     * Latched synchronously on the tap, because the acknowledgement read that follows suspends.
     * Two taps would otherwise each resolve and each set `ssoProvider`, and since the screen
     * consumes that field back to null between them, both would navigate — stacking two SSO
     * routes, two WebViews and two round-trips over the one cookie jar they share.
     */
    private var ssoSelectionInFlight = false

    /**
     * Social sign-in runs in an in-app browser, which some providers disallow. Warn once, then
     * remember the acknowledgement — the flag is global because there is no account yet.
     */
    fun onSsoProviderSelected(provider: String) {
        if (ssoSelectionInFlight) return
        ssoSelectionInFlight = true
        viewModelScope.launch {
            // Fail towards warning: an unreadable store must not strand every social button behind
            // a latch that only the dialog's own dismiss can clear.
            val acknowledged = runCatching { ssoRiskDataStore.acknowledged.first() }.getOrDefault(false)
            if (acknowledged) {
                _uiState.value = _uiState.value.copy(ssoProvider = provider)
            } else {
                _uiState.value = _uiState.value.copy(pendingSsoProvider = provider)
            }
        }
    }

    fun onSsoRiskAccepted() {
        val provider = _uiState.value.pendingSsoProvider ?: return
        // Cleared before the suspending persist, so a second tap on the dialog finds nothing
        // pending and returns instead of queueing a second navigation.
        _uiState.value = _uiState.value.copy(pendingSsoProvider = null)
        viewModelScope.launch {
            // Same reason the read is guarded: a store that cannot be written must cost the user
            // one extra warning next time, not the latch that `consumeSsoNavigation` alone clears.
            runCatching { ssoRiskDataStore.acknowledge() }
            _uiState.value = _uiState.value.copy(ssoProvider = provider)
        }
    }

    fun onSsoRiskDismissed() {
        ssoSelectionInFlight = false
        _uiState.value = _uiState.value.copy(pendingSsoProvider = null)
    }

    fun consumeSsoNavigation() {
        ssoSelectionInFlight = false
        _uiState.value = _uiState.value.copy(ssoProvider = null)
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.login(state.email, state.password)) {
                is Result.Success -> {
                    when (val outcome = result.data) {
                        is LoginOutcome.Success -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true,
                            )
                        }
                        is LoginOutcome.TwoFactorRequired -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                twoFactorTempToken = outcome.tempToken,
                            )
                        }
                    }
                }
                is Result.Error -> {
                    // A 403 from /api/auth/login means the server enforces ALLOW_EMAIL_LOGIN=false
                    // (#14180). Surface a clear reason and hide the form so the user reaches for a
                    // provider instead of retrying credentials that will never be accepted.
                    // checkBan runs BEFORE validateEmailLogin on this route and also answers 403,
                    // so a banned account (or the non-browser-UA soft ban) must keep the server's
                    // own message and leave the form visible — isBanned is that discriminator.
                    //
                    // [ApiException.serverAuthored] is a second, independent one, and it is required:
                    // a bouncer or WAF in front of the deployment answers this route with a 403 HTML
                    // interstitial too, and asserting "this server has email login disabled" over a
                    // transient network block would hide the form for a condition the user can simply
                    // wait out. Only LibreChat's own JSON envelope sets that flag.
                    val apiException = result.exception as? ApiException
                    val isEmailLoginDisabled = apiException?.statusCode == 403 &&
                        apiException.serverAuthored &&
                        !apiException.isBanned
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (isEmailLoginDisabled) {
                            "Email and password sign-in is disabled on this server."
                        } else {
                            result.message ?: "Login failed"
                        },
                        emailLoginEnabled = if (isEmailLoginDisabled) false else _uiState.value.emailLoginEnabled,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun consumeTwoFactorNavigation() {
        _uiState.value = _uiState.value.copy(twoFactorTempToken = null)
    }
}
