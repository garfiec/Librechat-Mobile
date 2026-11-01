package com.garfiec.librechat.core.ui.mcp

import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome

/**
 * Ends the in-app MCP sign-in at the server's own verdict.
 *
 * The callback (`/api/mcp/:serverName/oauth/callback`) answers with a redirect to
 * `{basePath}/oauth/success?serverName=…` or `{basePath}/oauth/error?error=…`, both pages of the
 * LibreChat web app. The redirect is relative, so it lands on the callback's host: the one in the
 * authorization URL's `redirect_uri`, which is the server's `DOMAIN_SERVER` and may differ from the
 * address the app was given. Either host counts. `basePath` comes from `DOMAIN_CLIENT`, so the
 * match is on the path's tail, not a fixed prefix. A provider page on its own host with the same
 * path never matches. Loading that page would boot the web app for nothing, so it is cancelled.
 */
class McpOAuthNavigationGate(serverUrl: String, authorizationUrl: String) {

    private val hosts: Set<String> = setOfNotNull(
        hostOf(serverUrl),
        queryParameter(authorizationUrl, "redirect_uri")?.let(::hostOf),
    )

    /** Null to let the navigation proceed; an outcome to cancel it and finish. */
    fun decide(url: String): McpOAuthOutcome? {
        val host = hostOf(url) ?: return null
        if (host !in hosts) return null
        val path = pathOf(url).trimEnd('/')
        return when {
            path.endsWith(SUCCESS_PATH) -> McpOAuthOutcome.Success
            path.endsWith(ERROR_PATH) -> McpOAuthOutcome.Error(queryParameter(url, "error"))
            else -> null
        }
    }

    private companion object {
        const val SUCCESS_PATH = "/oauth/success"
        const val ERROR_PATH = "/oauth/error"
    }
}

private fun authorityOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
    return rest.substring(0, end)
}

private fun hostOf(url: String): String? {
    val authority = authorityOf(url)?.substringAfterLast('@') ?: return null
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']') + "]"
    } else {
        authority.substringBefore(':')
    }
    return host.lowercase().takeIf { it.isNotEmpty() }
}

private fun pathOf(url: String): String {
    val schemeEnd = url.indexOf("://")
    val rest = if (schemeEnd < 0) url else url.substring(schemeEnd + 3)
    val start = rest.indexOf('/')
    if (start < 0) return ""
    return rest.substring(start).substringBefore('?').substringBefore('#')
}

private fun queryParameter(url: String, name: String): String? =
    url.substringAfter('?', "").substringBefore('#').split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.let(::decodeQueryValue)

private fun decodeQueryValue(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        when {
            hex != null -> { bytes.add(hex.toByte()); i += 3 }
            c == '+' -> { bytes.add(' '.code.toByte()); i++ }
            else -> { c.toString().encodeToByteArray().forEach(bytes::add); i++ }
        }
    }
    return bytes.toByteArray().decodeToString()
}
