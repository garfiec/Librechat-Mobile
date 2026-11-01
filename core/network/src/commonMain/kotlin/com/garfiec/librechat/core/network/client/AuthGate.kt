package com.garfiec.librechat.core.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A step the server requires before a signed-in user may go on (v0.8.9-rc1). Neither is an expired
 * session, a ban or a gateway block, and neither may be routed like one: an expiry fails over to
 * another signed-in account, which would hide the step the user has to take on this one.
 */
sealed interface AuthGate {
    /** The account it was raised for; null is the active (or legacy, unkeyed) session. */
    val accountId: String?

    /**
     * The server enforces 2FA and this account has none. Raised by a refresh that answered the
     * enrollment 200 (the server already deleted the session and cleared its cookie) or by any
     * authenticated request answering the enrollment 403. [tempToken] is the setup credential.
     */
    data class TwoFactorSetupRequired(override val accountId: String?, val tempToken: String) : AuthGate {
        override fun toString(): String = "TwoFactorSetupRequired(accountId=$accountId)"
    }

    /** A write answered `403 terms_not_accepted`: the deployment requires the terms to be accepted. */
    data class TermsNotAccepted(override val accountId: String?) : AuthGate
}

/** The constant a [TokenManager] that never reports a gate (fakes, the SDK) exposes. */
internal val NoAuthGate: StateFlow<AuthGate?> = MutableStateFlow<AuthGate?>(null).asStateFlow()
