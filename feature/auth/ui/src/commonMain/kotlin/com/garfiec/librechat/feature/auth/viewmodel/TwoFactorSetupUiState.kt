package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable

/** The four steps of upstream's required-enrollment screen (`TwoFactorSetupScreen.tsx`). */
enum class TwoFactorSetupPhase { Start, Authenticator, Verify, BackupCodes }

/** What went wrong, for the screen to word. */
enum class TwoFactorSetupError {
    /** The server could not stage a secret; retryable. */
    StartFailed,

    /** The code did not match the staged secret; enter a fresh one. */
    InvalidCode,

    /** Too many attempts; wait and retry. */
    RateLimited,

    /** Saving the backup codes and enabling 2FA failed in a way a retry can fix. */
    FinalizeFailed,

    /** No answer from the server. */
    Connection,

    /** 2FA was enabled, but the session came back without its `refreshToken` cookie and wasn't kept. */
    SessionCookieMissing,
}

@Immutable
data class TwoFactorSetupUiState(
    val phase: TwoFactorSetupPhase = TwoFactorSetupPhase.Start,
    val isLoading: Boolean = false,
    val otpauthUrl: String? = null,
    /** The `secret` of [otpauthUrl], for an authenticator's "enter a setup key" screen. */
    val setupKey: String? = null,
    val code: String = "",
    /** Bumped when a rejected code is cleared, so the input can take focus again. */
    val codeAttempt: Int = 0,
    val backupCodes: List<String> = emptyList(),
    /** The user saved the codes to a file or confirmed they have them; gates completing setup. */
    val codesSaved: Boolean = false,
    val error: TwoFactorSetupError? = null,
    /**
     * The setup credential is dead (expired, retired by a password reset, already spent, or the
     * server stopped requiring setup). No step can succeed any more; the user starts over from
     * sign-in, as upstream's screen does.
     */
    val mustSignInAgain: Boolean = false,
    /**
     * With [mustSignInAgain]: 2FA is on, but the server retired no earlier session and handed back
     * none, so the user signs in with the new factor.
     */
    val enabledWithoutSession: Boolean = false,
    /** Setup finished and the session is committed; leave the auth flow. */
    val isComplete: Boolean = false,
)
