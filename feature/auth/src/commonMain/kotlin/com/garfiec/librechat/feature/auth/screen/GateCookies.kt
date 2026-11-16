package com.garfiec.librechat.feature.auth.screen

import com.garfiec.librechat.core.network.client.CookieHelper

/**
 * The gate's session from a `Cookie` header's worth of [cookieHeader] read off the web view's jar for
 * the server: every cookie but the app's own (LibreChat's refresh token and OpenID markers, which
 * the app keeps itself). Null when nothing is left, which means the gate set no session.
 */
internal fun gateCookies(cookieHeader: String?): String? {
    val own = CookieHelper.authCookieNames.toSet() + CookieHelper.openIdCookieNames
    return cookieHeader.orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.contains('=') && it.substringBefore('=').trim() !in own }
        .joinToString("; ")
        .takeIf { it.isNotEmpty() }
}
