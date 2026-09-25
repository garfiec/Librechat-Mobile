package com.garfiec.librechat.core.network.client

import io.ktor.http.URLBuilder
import io.ktor.http.Url

/** The single path segment LibreChat mounts its local image store under, directly off the base path. */
private const val IMAGES_SEGMENT = "images"

/**
 * True when [url] addresses the server's **local image mount** — `${basePath}/images/…`.
 *
 * Two plugins must agree on this, which is why it is shared rather than inlined twice:
 *
 * - [AuthInterceptorPlugin] keeps these out of the 401 refresh/session-expiry leg. Since upstream
 *   0.8.8-rc2 the mount is guarded by a middleware that authenticates on a **cookie only** — there is
 *   no `Authorization` branch, no query token and no signed URL — and it is secured by *default*
 *   (`secureImageLinks !== false`, so unset now means on). A bearer therefore cannot change the
 *   verdict: refreshing and re-sending it produces a second 401, which the plugin would otherwise read
 *   as a dead session and log the user out of a perfectly live one.
 * - [ImageCookiePlugin] uses it as the attach gate, so the refresh-token cookie goes to this mount and
 *   nowhere else.
 *
 * Deliberately **not** an entry in `AUTH_SKIP_PATHS`: that set means "takes no bearer *and* its 401 is
 * the endpoint's own verdict", it also suppresses [SwitchBarrierPlugin]'s proactive renewal, and its
 * matcher is a whole-path-suffix match for `auth/login`-shaped strings rather than a prefix.
 *
 * Matched against [baseUrl]'s **base path**, not as a bare `/images/` prefix: upstream mounts the
 * store at `${basePath}/images`, so a deployment served under `https://host/librechat` answers images
 * at `https://host/librechat/images/…` and a naive prefix match would miss every one of them. The
 * mirror case matters just as much — `https://host/images/…` on a base-path deployment is *not* the
 * mount, and must not be handed a credential. An unresolvable base URL falls back to an empty base
 * path (the overwhelmingly common host-root deployment); that fallback is inert for the cookie, which
 * is additionally gated on [isSameServerAuthority] and so fails closed on an unknown base URL anyway.
 */
internal fun isSecuredImagePath(url: URLBuilder, baseUrl: String?): Boolean {
    val requestSegments = url.encodedPathSegments.filter { it.isNotEmpty() }
    val baseSegments = basePathSegments(baseUrl)
    if (requestSegments.size <= baseSegments.size) return false
    if (baseSegments.indices.any { requestSegments[it] != baseSegments[it] }) return false
    return requestSegments[baseSegments.size] == IMAGES_SEGMENT
}

private fun basePathSegments(baseUrl: String?): List<String> {
    if (baseUrl.isNullOrEmpty()) return emptyList()
    val path = runCatching { Url(baseUrl).encodedPath }.getOrNull() ?: return emptyList()
    return path.split('/').filter { it.isNotEmpty() }
}
