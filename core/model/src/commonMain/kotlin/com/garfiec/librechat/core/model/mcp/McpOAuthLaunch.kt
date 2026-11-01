package com.garfiec.librechat.core.model.mcp

/**
 * How to send the user to an MCP server's OAuth provider.
 *
 * From v0.8.9-rc1 the server's callback (`GET /api/mcp/:serverName/oauth/callback`) accepts only a
 * browser that carries the `oauth_csrf` or `oauth_session` cookie, both set on the response to an
 * authenticated request. The system browser holds neither, so the flow runs in an in-app WebView
 * seeded with the cookies `POST /api/mcp/:serverName/oauth/bind` set ([InApp]). Older servers fall
 * back to a pending flow when the cookies are missing, and keep the system browser ([External]).
 */
sealed interface McpOAuthLaunch {
    val serverName: String
    val authorizationUrl: String

    data class InApp(
        override val serverName: String,
        override val authorizationUrl: String,
        /** The server the cookies belong to (the callback's origin). */
        val serverUrl: String,
        /** Each binding cookie as one `Set-Cookie` header value, attributes intact. */
        val seedCookies: List<String>,
    ) : McpOAuthLaunch

    data class External(
        override val serverName: String,
        override val authorizationUrl: String,
    ) : McpOAuthLaunch
}

/** How an in-app MCP sign-in ended. */
sealed interface McpOAuthOutcome {
    /** The callback redirected to `/oauth/success`. */
    data object Success : McpOAuthOutcome

    /** The callback redirected to `/oauth/error`; [code] is its `error` parameter, when present. */
    data class Error(val code: String?) : McpOAuthOutcome

    /** The page failed to load, or the user closed it before the callback. */
    data object Abandoned : McpOAuthOutcome
}

/**
 * The real MCP server name an OAuth prompt belongs to.
 *
 * Read first from the authorization URL's `redirect_uri` (`…/api/mcp/{name}/oauth/callback`), which
 * carries the stored name. The tool-call name (`oauth_mcp_{name}`, upstream `buildOAuthToolCallName`)
 * carries `normalizeServerName(name)`, which differs for names outside `[A-Za-z0-9_.-]`; it is the
 * fallback. Upstream `ToolCall.tsx` reads both. Plain string scanning, no regex.
 */
fun mcpOAuthServerName(toolName: String?, authorizationUrl: String?): String? =
    authorizationUrl?.let(::serverNameFromRedirectUri) ?: toolName?.let(::serverNameFromOAuthToolName)

private const val MCP_OAUTH_TOOL_PREFIX = "oauth_mcp_"
private const val CALLBACK_PREFIX = "/api/mcp/"
private const val CALLBACK_SUFFIX = "/oauth/callback"

private fun serverNameFromOAuthToolName(name: String): String? =
    name.takeIf { it.startsWith(MCP_OAUTH_TOOL_PREFIX) }
        ?.removePrefix(MCP_OAUTH_TOOL_PREFIX)
        ?.takeIf { it.isNotBlank() }

private fun serverNameFromRedirectUri(url: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    val raw = query.split('&')
        .firstOrNull { it.substringBefore('=') == "redirect_uri" }
        ?.substringAfter('=', "")
        ?: return null
    val redirect = percentDecode(raw, plusIsSpace = true)
    val start = redirect.indexOf(CALLBACK_PREFIX)
    if (start < 0) return null
    val rest = redirect.substring(start + CALLBACK_PREFIX.length)
    val end = rest.indexOf(CALLBACK_SUFFIX)
    if (end <= 0) return null
    val segment = rest.substring(0, end)
    if ('/' in segment) return null
    return percentDecode(segment, plusIsSpace = false).takeIf { it.isNotBlank() }
}

private fun percentDecode(value: String, plusIsSpace: Boolean): String {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        if (c == '+' && plusIsSpace) {
            bytes.add(' '.code.toByte())
        } else {
            c.toString().encodeToByteArray().forEach(bytes::add)
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
