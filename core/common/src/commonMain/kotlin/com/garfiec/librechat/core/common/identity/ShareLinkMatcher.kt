package com.garfiec.librechat.core.common.identity

/** A shared-chat link taken apart: the deployment it lives on and the link's id. */
data class ParsedShareLink(
    /** The deployment the link lives on, subpath included: `https://host/librechat`. */
    val serverUrl: String,
    val shareId: String,
)

/** An account the app holds, as far as matching a link to it goes. */
data class ShareLinkAccount(val accountId: String, val serverUrl: String)

/** A link the in-app viewer can open: [shareId] on [accountId]'s server. */
data class ShareLinkTarget(val shareId: String, val accountId: String)

/**
 * Recognises a LibreChat shared-chat link, `<server>/share/<shareId>`, and finds the account it
 * belongs to.
 *
 * The server half may carry a subpath (`https://host/librechat/share/<id>`): a deployment under a
 * subpath is a different server from the host root, so a link matches an account only when the two
 * normalize to the same deployment ([normalizeServerUrl] on both sides, after [cleanServerUrl] on
 * the stored one). The link format mirrors web's `buildShareLinkUrl` and its `share/:shareId` route
 * (scripts/mirrors.json `share-url-format`, `share-link-route`).
 */
object ShareLinkMatcher {

    /**
     * [url] as a shared-chat link, or null when it is not one: not http(s), no host, or a path
     * that does not end in `/share/<id>` (one segment, an optional trailing slash). Query and
     * fragment are ignored. Surrounding whitespace is trimmed, but anything else around the link
     * (a second word, a second link) makes it not one.
     */
    fun parse(url: String): ParsedShareLink? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) return null
        val scheme = trimmed.substring(0, schemeSep).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = trimmed.substring(schemeSep + SCHEME_SEPARATOR_LENGTH).substringBefore('#').substringBefore('?')
        val pathStart = rest.indexOf('/')
        if (pathStart <= 0) return null
        val authority = rest.substring(0, pathStart)
        val path = rest.substring(pathStart).removeSuffix("/")
        val marker = path.lastIndexOf(SHARE_SEGMENT)
        if (marker < 0) return null
        val shareId = path.substring(marker + SHARE_SEGMENT.length)
        if (shareId.isEmpty() || !shareId.all(::isShareIdChar)) return null
        val serverUrl = trimmed.substring(0, schemeSep + SCHEME_SEPARATOR_LENGTH) + authority + path.substring(0, marker)
        return ParsedShareLink(serverUrl = serverUrl, shareId = shareId)
    }

    /**
     * The first of [accounts] whose server [url] is a shared-chat link on, or null when [url] is
     * not a link or no account's server matches. [accounts] is in the caller's order of preference
     * (the active account first), since two accounts can sit on one server.
     */
    fun match(url: String, accounts: List<ShareLinkAccount>): ShareLinkTarget? {
        val link = parse(url) ?: return null
        val linkServer = normalizedOrNull(link.serverUrl) ?: return null
        val account = accounts.firstOrNull { normalizedOrNull(cleanServerUrl(it.serverUrl)) == linkServer }
            ?: return null
        return ShareLinkTarget(shareId = link.shareId, accountId = account.accountId)
    }

    private fun normalizedOrNull(serverUrl: String): String? =
        runCatching { normalizeServerUrl(serverUrl) }.getOrNull()

    // Share ids are nanoid/uuid strings. Anything else (a `%`, a `.`) is not a link we made.
    private fun isShareIdChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'

    private const val SHARE_SEGMENT = "/share/"
    private const val SCHEME_SEPARATOR_LENGTH = 3
}
