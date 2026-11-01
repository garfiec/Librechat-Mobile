package com.garfiec.librechat.feature.auth.oauth

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.network.client.CookieHelper

const val REFRESH_TOKEN_COOKIE = "refreshToken"

fun extractRefreshTokenFromCookies(cookieHeader: String?): String? =
    cookieHeader
        ?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$REFRESH_TOKEN_COOKIE=") }
        ?.substringAfter("$REFRESH_TOKEN_COOKIE=")
        ?.takeIf { it.isNotEmpty() }

fun extractQueryParameter(url: String, name: String): String? {
    val query = url.substringAfter("?", "").substringBefore("#")
    return query
        .split("&")
        .firstOrNull { it.substringBefore("=") == name }
        ?.substringAfter("=", "")
        ?.takeIf { it.isNotEmpty() }
}

/**
 * The failure code of the page the server sent the round-trip to, or null.
 *
 * Normally the `error` parameter. Under strict tenant isolation (v0.8.9-rc1) a callback that named
 * no tenant redirects to `/login?redirect=false&error=auth_failed&code=TENANT_CONTEXT_REQUIRED`
 * (`api/server/routes/oauth.js`), and the specific reason is in `code`; `error` there is the
 * generic `auth_failed`. Matched on the exact value: a successful callback's own URL carries the
 * provider's authorization `code`.
 */
fun ssoFailureCode(url: String): String? {
    if (extractQueryParameter(url, "code") == ServerErrorCode.TENANT_CONTEXT_REQUIRED) {
        return ServerErrorCode.TENANT_CONTEXT_REQUIRED
    }
    return extractQueryParameter(url, "error")
}

/**
 * Whether a cookie scoped to [cookieDomain] would be sent to [host].
 *
 * Asked in this direction on purpose. The inverse — does the cookie's domain end with the host —
 * accepts `evil-chat.example.com` for a server at `chat.example.com`. iOS has to filter by hand
 * because `getCookiesForURL:` is iOS 27+ and the deployment target is 16.
 */
fun cookieAppliesTo(cookieDomain: String, host: String): Boolean {
    val domain = cookieDomain.removePrefix(".").lowercase()
    val target = host.lowercase()
    return target == domain || target.endsWith(".$domain")
}

/** Complete cookie handoff; partial OpenID markers must never select the LibreChat JWT path. */
fun extractAuthCookies(cookieHeader: String?): AuthCookies? = CookieHelper.capture(cookieHeader)

fun cookiePathAppliesTo(cookiePath: String, requestPath: String): Boolean =
    requestPath == cookiePath || (requestPath.startsWith(cookiePath) &&
        (cookiePath.endsWith('/') || requestPath.getOrNull(cookiePath.length) == '/'))
