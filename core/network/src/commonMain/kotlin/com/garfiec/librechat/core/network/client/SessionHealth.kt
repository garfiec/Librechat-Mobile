package com.garfiec.librechat.core.network.client

import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a signed-in session can still renew itself.
 *
 * A session is kept — deliberately — when the refresh endpoint answers with something LibreChat did
 * not write: a proxy's block page, a WAF, a wrong path in front of the route. That 403 is no evidence
 * the credential is dead, and logging out over it costs a re-login the block may not even permit
 * (#376). But a kept session whose refresh never succeeds is one where every request runs on an
 * expired bearer, the server answers each with `401 jwt expired`, and nothing on screen says why or
 * offers a way out. This is the signal that fills that gap: the shell shows it and offers "sign in
 * again", and the user decides.
 *
 * [RenewalBlocked] is set the moment a refresh is refused that way and cleared by the next
 * successful refresh, a teardown, a sign-in, or a hard expiry (which routes to re-auth itself). It is
 * **not** cleared when the stand-down between refresh attempts lapses — that means the client will
 * POST again, not that the server has started answering.
 */
sealed interface SessionHealth {
    data object Healthy : SessionHealth

    /**
     * [accountId]'s refresh endpoint is answering with a 403 LibreChat did not author. Null is the
     * bare (pre-account-resolution / legacy) slot. One value for the whole store: the shell shows it
     * only for the active account, and a later outcome on any slot replaces it.
     */
    data class RenewalBlocked(val accountId: String?) : SessionHealth
}

/** The constant a [TokenManager] that never refreshes (fakes, the SDK) reports. */
internal val AlwaysHealthy: StateFlow<SessionHealth> =
    MutableStateFlow<SessionHealth>(SessionHealth.Healthy).asStateFlow()

/**
 * Set on the **call** whose 401 the [AuthInterceptorPlugin] could not clear with a refresh — the
 * refresh was refused, stood down or failed. The response validator reads it off
 * `response.call.request.attributes` to replace the server's own `401` wording (`jwt expired`) with a
 * sentence about the session, since the server's message describes the token, not what the user can
 * do. A 401 on an auth route, or one the interceptor never saw, keeps the server's text.
 *
 * The call's attributes, not the request builder's: the call the engine hands back carries its own
 * `Attributes` instance (the identities differ even with no retry plugin installed), so a flag put on
 * the builder after the send is one the validator never sees. `SessionRenewalMessageTest` pins this
 * through the real client.
 */
val SessionRenewalFailedKey: AttributeKey<Boolean> = AttributeKey("SessionRenewalFailed")
