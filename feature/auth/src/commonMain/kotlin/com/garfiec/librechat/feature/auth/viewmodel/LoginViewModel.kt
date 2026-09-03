package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.LoginOutcome
import com.garfiec.librechat.feature.auth.oauth.OAuthLauncher
import com.garfiec.librechat.feature.auth.oauth.oauthStartUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TCHAT: the two things [com.garfiec.librechat.feature.auth.oauth.OAuthWebView] needs, bundled so
 * "showing" and "not showing" is one nullable value rather than two independently-nullable fields
 * that could disagree (e.g. a URL set with no server, or vice versa).
 */
@Immutable
data class OAuthWebViewState(val startUrl: String, val serverUrl: String)

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
    // TCHAT: server-supplied OIDC button label/auto-redirect (StartupConfig.openidLabel /
    // .openidAutoRedirect) — parsed upstream but never surfaced until now. Null label falls back
    // to TchatBrand.OIDC_FALLBACK_LABEL in the screen.
    val openidLabel: String? = null,
    val openidAutoRedirect: Boolean = false,
    /**
     * TCHAT: non-null while the in-app OAuth WebView overlay should be showing. Set by
     * [launchOAuth], cleared by [onOAuthWebViewComplete] and [dismissOAuthWebView]. Replaces the
     * old external-browser launch — see [OAuthLauncher]'s kdoc.
     */
    val oAuthWebView: OAuthWebViewState? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
    private val oAuthLauncher: OAuthLauncher,
    private val serverDataStore: ServerDataStore,
    private val accountSwitcher: AccountSwitcher,
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
                        openidLabel = config.openidLabel, // TCHAT
                        openidAutoRedirect = config.openidAutoRedirect, // TCHAT
                    )
                }
            }
        }
    }

    /** The server this screen is signing into: the pending add target when set, else the live one. */
    private fun signInServerUrl(): String =
        accountSwitcher.pendingAdd?.serverUrl ?: serverDataStore.getBaseUrl()

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
                    // own message and leave the form visible — isBanned is the discriminator.
                    val apiException = result.exception as? ApiException
                    val isEmailLoginDisabled =
                        apiException?.statusCode == 403 && !apiException.isBanned
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

    /** Set once this screen launches its own OAuth round-trip; gates add-mode cookie consumption. */
    private var oAuthLaunched = false

    /**
     * TCHAT: shows the in-app OAuth WebView overlay (see [OAuthWebView][com.garfiec.librechat.feature.auth.oauth.OAuthWebView])
     * instead of launching an external browser. The overlay itself only detects completion; the
     * screen wires that back to [onOAuthWebViewComplete].
     */
    fun launchOAuth(provider: String) {
        oAuthLaunched = true
        val serverUrl = signInServerUrl()
        // Drop any stale refreshToken cookie for this host BEFORE launching. In add mode the cookie
        // jar is process-global and nothing clears it on add-flow entry, so a launch that the user then
        // cancels would otherwise leave a pre-existing cookie for checkOAuthResult() to consume as the
        // wrong user (the oAuthLaunched guard only blocks the never-launched case). Clearing here means
        // only a cookie minted by THIS round-trip can be present on return.
        oAuthLauncher.clearOAuthCookie(serverUrl)
        _uiState.value = _uiState.value.copy(
            oAuthWebView = OAuthWebViewState(startUrl = oauthStartUrl(serverUrl, provider), serverUrl = serverUrl),
        )
    }

    /** The WebView overlay detected the flow reached an ordinary page on this server — check now. */
    fun onOAuthWebViewComplete() {
        _uiState.value = _uiState.value.copy(oAuthWebView = null)
        checkOAuthResult()
    }

    /** The user closed the WebView overlay before the flow completed. Nothing to extract. */
    fun dismissOAuthWebView() {
        _uiState.value = _uiState.value.copy(oAuthWebView = null)
    }

    // TCHAT: called from two places now — onOAuthWebViewComplete() (the WebView overlay's own
    // completion signal, the primary path) and LoginScreen's LifecycleEventEffect(ON_RESUME) (a
    // belt-and-braces fallback: the in-app WebView no longer leaves the app the way the old
    // external-browser design did, so a killed process mid-flow is far less likely — but not
    // impossible, e.g. under memory pressure while backgrounded). Both call this same function so
    // there is exactly one extraction path to keep correct.
    fun checkOAuthResult() {
        // In add mode, only consume a cookie minted by THIS screen's own launchOAuth round-trip:
        // the cookie jar is process-global and nothing clears it on add-flow entry, so a stale
        // refreshToken cookie for this host would otherwise be auto-consumed on first ON_RESUME
        // and silently complete the add as the wrong user. The normal login screen keeps the
        // unconditional consume — it must survive process death mid-flow, which an add flow never
        // does (its pending session is memory-only, so a killed add flow is stripped by the
        // NavHost, not resumed).
        if (accountSwitcher.pendingAdd != null && !oAuthLaunched) return

        val serverUrl = signInServerUrl()
        if (serverUrl.isBlank()) return

        val refreshToken = oAuthLauncher.extractTokenFromCookies(serverUrl) ?: return

        // Clear the cookie immediately to avoid re-reading on next onResume
        oAuthLauncher.clearOAuthCookie(serverUrl)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.loginWithOAuthToken(refreshToken)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "OAuth login failed",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
