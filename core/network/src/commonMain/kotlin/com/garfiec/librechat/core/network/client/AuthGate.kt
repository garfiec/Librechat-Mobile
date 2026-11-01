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

/**
 * Where the [AuthGate] lives: on the token store, beside the session it is about. A part of
 * [TokenManager], declared apart only to keep that interface readable; every member is defaulted so
 * a manager that never reports one (fakes, the SDK) says nothing.
 */
interface AuthGateHolder {
    /**
     * The step the server requires before the active session may continue, or null. A state rather
     * than an event so a report raised while no navigation host is composed (a background request,
     * a refresh) is still there when one is; the shell takes it with [consumeAuthGate].
     */
    val authGate: StateFlow<AuthGate?> get() = NoAuthGate

    /**
     * Report [gate]. Ignored unless [AuthGate.accountId] is the active binding (null = the active or
     * legacy session): a straggler request for a switched-away account must not move the live one.
     */
    fun reportAuthGate(gate: AuthGate) = Unit

    /** Clear [gate] once the shell has acted on it; a newer report is left alone. */
    fun consumeAuthGate(gate: AuthGate) = Unit
}

/** The constant a [TokenManager] that never reports a gate (fakes, the SDK) exposes. */
internal val NoAuthGate: StateFlow<AuthGate?> = MutableStateFlow<AuthGate?>(null).asStateFlow()
