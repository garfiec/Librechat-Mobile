package com.garfiec.librechat.core.network.client

import io.ktor.http.URLBuilder

/**
 * Endpoints that take no session bearer and whose `401` is the endpoint's own verdict rather than an
 * expired session.
 *
 * Two plugins must agree on this list. [AuthInterceptorPlugin] keeps these out of the attach and the
 * 401-refresh leg; the [SwitchBarrierPlugin] keeps them out of *proactive* renewal, for the same
 * reason — a sign-in POST must not first drive a refresh of the session it is replacing, and on a slow
 * or failing server that would put the whole refresh in front of the Sign in button.
 *
 * Shared rather than duplicated because a path added to one copy and not the other is silent: the
 * behaviour it protects only shows up on a server that is misbehaving at the time.
 *
 * Note this is deliberately **not** the gate for gateway headers — see `core/network/CLAUDE.md`.
 * Those must reach the auth endpoints or the app cannot sign in at all.
 */
private val AUTH_SKIP_PATHS = setOf(
    "auth/login", "auth/register", "auth/refresh",
    "auth/requestPasswordReset", "auth/resetPassword",
    "auth/2fa/verify-temp",
    // Required-enrollment setup (v0.8.9-rc1): authorised by the body's setup credential, not a
    // bearer. Listed one by one — matching is whole-path, so `auth/2fa/setup` does not cover
    // `auth/2fa/setup/confirm`.
    "auth/2fa/setup", "auth/2fa/setup/confirm",
    "auth/2fa/setup/acknowledge", "auth/2fa/setup/finalize",
)

/**
 * True when [url] addresses one of [AUTH_SKIP_PATHS].
 *
 * Matched on whole path segments, not as a substring of the URL, so a path-prefixed deployment (e.g.
 * `/apps/auth/login-x`) doesn't match every request it serves.
 */
internal fun isAuthSkipPath(url: URLBuilder): Boolean {
    val path = url.encodedPathSegments.filter { it.isNotEmpty() }.joinToString("/")
    return AUTH_SKIP_PATHS.any { path == it || path.endsWith("/$it") }
}
