package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable

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

    /**
     * The server answered the token exchange or the user fetch with an HTTP error: a refusal, a
     * redirect to `/login`, or a 5xx.
     */
    data object ExchangeRejected : SsoLoginError
}

@Immutable
data class SsoLoginUiState(
    val isLoading: Boolean = false,
    val error: SsoLoginError? = null,
    val isLoggedIn: Boolean = false,
)
