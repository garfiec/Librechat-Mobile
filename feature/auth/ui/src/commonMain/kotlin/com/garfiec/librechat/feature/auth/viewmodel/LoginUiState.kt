package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.absoluteLinkUrl

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    val isLoggedIn: Boolean = false,
    val twoFactorTempToken: String? = null,
    /** Setup credential when the server requires 2FA enrollment; consumed by navigation. */
    val twoFactorSetupTempToken: String? = null,
    /** A coded sign-in refusal the screen words itself; shown instead of [error] when set. */
    val refusal: AuthRefusal? = null,
    val inputError: LoginInputError? = null,
    val minPasswordLength: Int = DEFAULT_MIN_PASSWORD_LENGTH,
    /** LDAP signs in without the local strategy's password bounds, so they aren't checked. */
    val ldapLogin: Boolean = false,
    val registrationEnabled: Boolean = false,
    /** The sign-in buttons to show, in the server's order. */
    val socialProviders: List<SocialProvider> = emptyList(),
    // ALLOW_EMAIL_LOGIN (upstream #14180): when the server disables email/password login it now
    // enforces it with a 403 on POST /api/auth/login. Fail-open to true so the form shows until
    // config confirms otherwise. Drives hiding the email/password form.
    val emailLoginEnabled: Boolean = true,
    /** Off unless the server allows reset: its reset routes 403 otherwise. */
    val passwordResetEnabled: Boolean = false,
    val usernameLogin: Boolean = false,
    /** Set while the in-app-browser warning is up; carries the provider the user picked. */
    val pendingSsoProvider: String? = null,
    val ssoProvider: String? = null,
    val trustedViaUserCa: Boolean = false,
    /** The server's published policy links, absolute and safe to open; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
    /**
     * The accounts on this server that are signed out because it now requires 2FA setup, joined
     * for the notice; null when there are none. Signing in to one runs the setup flow.
     */
    val twoFactorSetupAccounts: String? = null,
    /** The server's `appTitle`, for the heading; null until its config is known. */
    val appTitle: String? = null,
    /** The address being signed in to, always shown under the heading: [appTitle] is the operator's to set. */
    val serverLabel: String? = null,
    val banner: Banner? = null,
)

/** A problem with the form that the server would reject the same way, caught before sending. */
enum class LoginInputError { MissingFields, InvalidEmail, PasswordTooShort, PasswordTooLong }

/** Upstream `MIN_PASSWORD_LENGTH` default and `loginSchema`'s maximum (`api/strategies/validators.js`). */
const val DEFAULT_MIN_PASSWORD_LENGTH = 8

const val MAX_PASSWORD_LENGTH = 128

/**
 * The privacy policy and terms of service a deployment publishes (`interface.privacyPolicy` /
 * `termsOfService.externalUrl`), as the auth footer shows them. Only a non-blank URL with a safe
 * scheme (see `isSafeLinkUrl`) is kept, resolved against the server so a relative one opens.
 */
@Immutable
data class PolicyLinks(
    val privacyPolicyUrl: String? = null,
    val termsOfServiceUrl: String? = null,
) {
    companion object {
        fun from(config: StartupConfig, serverUrl: String?): PolicyLinks {
            val root = serverUrl?.takeIf { it.isNotBlank() }?.let(::cleanServerUrl) ?: return PolicyLinks()
            val interfaceConfig = config.interfaceConfig ?: return PolicyLinks()
            return PolicyLinks(
                privacyPolicyUrl = interfaceConfig.privacyPolicy?.safeExternalUrl?.let { absoluteLinkUrl(it, root) },
                termsOfServiceUrl = interfaceConfig.termsOfService?.safeExternalUrl?.let { absoluteLinkUrl(it, root) },
            )
        }
    }
}

/** One social sign-in button: [id] is the `/oauth/<id>` route; a null [label] means "Continue with <name>". */
@Immutable
data class SocialProvider(val id: String, val label: String? = null)
