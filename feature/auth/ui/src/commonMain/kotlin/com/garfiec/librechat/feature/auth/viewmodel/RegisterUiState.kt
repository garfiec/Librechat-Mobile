package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Banner

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
