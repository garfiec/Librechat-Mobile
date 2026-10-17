package com.garfiec.librechat.core.common.identity

/**
 * A server URL with the parts that are recognisably not a deployment address removed.
 *
 * Used in two places that have to agree. **At entry**, on what the user typed or pasted, before it
 * is validated and stored. **At every hand-built join** — the refresh pin, image and file URLs,
 * share links, the iOS SSE transport — on the stored string, so that a URL stored *before* the entry
 * cleaning existed still resolves.
 *
 * People paste the address out of a browser that has already redirected them into LibreChat's web
 * UI — `https://host/login?redirect_to=%2Fc%2Fnew`, `https://host/c/new`, a `/share/…` link. It
 * validates, because Ktor's request merge resolves `/api/config` against the origin, and then the
 * stored string carries a page path and a query for the life of the account — into the identity
 * hash, the roster, and every URL built off it by hand, which 4xx. See `core/network/CLAUDE.md`.
 * Strip:
 *
 * - the fragment and the query, unconditionally — no LibreChat deployment is addressed by one;
 * - one trailing LibreChat web route (`/login`, `/register`, `/c/<id>`, `/share/<id>`,
 *   `/forgot-password`, `/reset-password`, `/verify`), case-sensitive, with or without a trailing
 *   slash;
 * - trailing slashes.
 *
 * Anything else in the path is kept. A deployment under a subpath (`https://host/librechat`) is a
 * different server from `https://host`, [normalizeServerUrl] deliberately preserves that for
 * identity, and `AvatarUrlResolverTest` pins that the joins keep it too; this function is not allowed
 * to decide that a path it does not recognise is junk. Byte-preserving on everything it keeps — no
 * lowercasing, no default-port elision — because the joined image URLs are cache keys shared by every
 * image surface and the prefetcher, and a clean base must come back exactly as it went in. Scheme
 * handling stays with the caller: the result has a scheme only if the input did. Blank in, blank out.
 */
fun cleanServerUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val schemeSep = trimmed.indexOf("://")
    val prefix = if (schemeSep >= 0) trimmed.substring(0, schemeSep + SCHEME_SEPARATOR_LENGTH) else ""
    val rest = trimmed.substring(prefix.length).substringBefore('#').substringBefore('?')
    val pathStart = rest.indexOf('/')
    if (pathStart < 0) return prefix + rest
    val authority = rest.substring(0, pathStart)
    val path = stripWebRoute(rest.substring(pathStart).trimEnd('/')).trimEnd('/')
    return prefix + authority + path
}

/**
 * [url] reduced to its scheme and authority — `https://host:8443/librechat?x` → `https://host:8443`.
 * The fallback the server screen tries when the typed path doesn't answer as LibreChat. Byte-preserving
 * like [cleanServerUrl], and equal to [cleanServerUrl]`(url)` when the cleaned URL has no path.
 */
fun serverOrigin(url: String): String {
    val trimmed = url.trim()
    val schemeSep = trimmed.indexOf("://")
    val prefix = if (schemeSep >= 0) trimmed.substring(0, schemeSep + SCHEME_SEPARATOR_LENGTH) else ""
    val rest = trimmed.substring(prefix.length)
    val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
    return prefix + rest.substring(0, authorityEnd)
}

/** Drops one trailing web-UI route from [path] (no trailing slash), or returns it unchanged. */
private fun stripWebRoute(path: String): String {
    WEB_ROUTES_EXACT.firstOrNull { path.endsWith(it) }?.let { return path.removeSuffix(it) }
    for (prefix in WEB_ROUTES_WITH_ID) {
        val at = path.indexOf(prefix)
        // The route owns exactly one more segment (`/c/<id>`, `/share/<id>`); a deeper path is not
        // one of LibreChat's and is left alone.
        if (at >= 0 && path.indexOf('/', at + prefix.length) < 0) return path.substring(0, at)
    }
    return path
}

private const val SCHEME_SEPARATOR_LENGTH = 3

private val WEB_ROUTES_EXACT = listOf("/login", "/register", "/forgot-password", "/reset-password", "/verify")

private val WEB_ROUTES_WITH_ID = listOf("/c/", "/share/")
