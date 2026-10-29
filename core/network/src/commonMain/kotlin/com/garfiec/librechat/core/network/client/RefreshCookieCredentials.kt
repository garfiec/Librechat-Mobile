package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.SessionCredentials
/**
 * Reads one account's **refresh token**, for the requests that send it as the `refreshToken` cookie
 * besides the refresh POST: the server's local image mount ([ImageCookiePlugin]) and logout, which
 * the server scopes to the session that cookie names.
 *
 * A narrow seam on purpose. An active-account read would send the live account's credential on a
 * switched-away account's straggler image fetch, and widening [TokenManager] — the interface every
 * transport and half a dozen test fakes implement — to carry a second credential would put it within
 * reach of every one of them for a couple of callers' sake.
 *
 * **Never cache what this returns.** Since upstream 0.8.8-rc2 the server matches the token against
 * `session.refreshTokenHash`, which `generateRefreshToken` overwrites on every refresh, so rotation
 * hard-invalidates the previous token server-side the instant it happens. A value snapshotted before a
 * refresh is not stale-but-workable, it is rejected — an image fetch gets a **403**, not a 401, and a
 * logout matches no session and revokes nothing. Read it at send time, and again after anything that
 * may have refreshed; see [ImageCookiePlugin].
 */
fun interface RefreshCookieCredentials {
    /** The stored refresh token for [accountId], or null when there is none (or no resolved account). */
    suspend fun refreshTokenFor(accountId: String?): String?

    /** Read token and markers together, never as separate secure-store reads. */
    suspend fun authCookiesFor(accountId: String?): AuthCookies? = refreshTokenFor(accountId)?.let(::AuthCookies)

    /** Logout needs the bearer and cookies from one generation after proactive renewal. */
    suspend fun sessionFor(accountId: String): SessionCredentials? = null
}
