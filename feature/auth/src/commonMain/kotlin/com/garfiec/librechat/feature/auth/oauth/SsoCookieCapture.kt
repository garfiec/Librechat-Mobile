package com.garfiec.librechat.feature.auth.oauth

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
