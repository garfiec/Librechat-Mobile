package com.garfiec.librechat.core.network.client

/**
 * Reads one account's **refresh token**, for the only request that needs it besides the refresh POST:
 * the cookie [ImageCookiePlugin] attaches to the server's local image mount.
 *
 * A narrow seam on purpose. The two existing ways to reach a refresh token are both wrong here:
 * [SecureTokenStorage.getRefreshToken] is active-account-only, so it would send the live account's
 * credential on a switched-away account's straggler image fetch; and widening [TokenManager] — the
 * interface every transport and half a dozen test fakes implement — to carry a second credential
 * would put it within reach of every one of them for one caller's sake.
 *
 * **Never cache what this returns.** Since upstream 0.8.8-rc3 the image middleware matches the token
 * against `session.refreshTokenHash`, which `generateRefreshToken` overwrites on every refresh, so
 * rotation hard-invalidates the previous token server-side the instant it happens. A value snapshotted
 * before a refresh is not stale-but-workable, it is rejected — with a **403**, not a 401. Read it at
 * attach time and re-read it on that 403; see [ImageCookiePlugin].
 */
fun interface ImageCookieCredentials {
    /** The stored refresh token for [accountId], or null when there is none (or no resolved account). */
    suspend fun refreshTokenFor(accountId: String?): String?
}
