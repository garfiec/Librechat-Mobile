package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.OpenIdMarkers
/**
 * Result of a shared 401-recovery attempt. [Refreshed] carries the new bearer to retry the request
 * with; [Expired] is a hard failure that must route the account to re-auth; [Transient] means the
 * refresh failed in a recoverable way (network/5xx/malformed/a server false-negative) and the caller
 * must fail just this request **without** tearing down the session — a later request or an app
 * relaunch can still recover.
 */
sealed interface BearerResult {
    data class Refreshed(val token: String, val openId: OpenIdMarkers? = null) : BearerResult {
        override fun toString(): String = "Refreshed(<redacted>)"
    }
    data object Expired : BearerResult
    data object Transient : BearerResult
}

/**
 * The one 401-recovery path shared by every transport (Ktor [AuthInterceptorPlugin] and the iOS raw
 * SSE transport): refresh the account the request was snapshotted for — keyed and URL-pinned to the
 * snapshot, so a switch that raced the request refreshes the right account against the right server,
 * never the live one. Without a snapshot (refresh client / tests) falls back to the live active
 * account, preserving legacy behavior.
 *
 * Maps the refresh [RefreshResult] to a [BearerResult]: only a genuine [RefreshResult.HardExpired]
 * (or a refreshed-but-empty slot, meaning a logout/removal raced the refresh) becomes [Expired] and
 * logs the user out; a [RefreshResult.Transient] becomes [Transient] and keeps the session.
 *
 * The snapshot's bearer is handed to the refresh as `usedAccessToken`: it is exactly what this request
 * put on the wire (the attach phase reads the snapshot, never the live cache), so a cold-start fan-out
 * of N 401s collapses to one refresh POST — every waiter behind the flight lock sees the rotated token
 * and skips its own.
 */
suspend fun TokenManager.refreshBearerFor(identity: RequestIdentity?): BearerResult {
    val account = identity?.accountId
    val result = if (account != null) {
        refreshAccessTokenFor(account, identity.baseUrl, usedAccessToken = identity.bearer)
    } else {
        refreshAccessToken(usedAccessToken = identity?.bearer)
    }
    return when (result) {
        RefreshResult.Refreshed -> {
            val auth = getRequestAuthFor(account)
            // The refresh succeeded but the slot read back empty — a teardown (logout/removal) raced
            // between the commit and this read. That teardown owns the routing, so treat it as
            // Transient here rather than emitting a second session-expired over it.
            auth.bearer?.let { BearerResult.Refreshed(it, auth.openId) } ?: BearerResult.Transient
        }
        RefreshResult.HardExpired -> BearerResult.Expired
        RefreshResult.Transient -> BearerResult.Transient
    }
}
