package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Ports of upstream's MCP App media-type, CSP and link rules. Each is registered in
 * `scripts/mirrors.json`; the server never serves them, so a drift here is silent.
 */

private const val MCP_APP_PROFILE = "mcp-app"

/**
 * Upstream `isMcpAppMimeType` (`packages/data-provider/src/mcp/appMime.ts`): media type exactly
 * `text/html` (case-insensitive) plus a `profile` parameter whose value, optionally quoted, is the
 * literal `mcp-app`. A plain `text/html` is the older MCP-UI feature, not an App.
 */
fun isMcpAppMimeType(mimeType: String?): Boolean {
    if (mimeType == null) return false
    val parts = mimeType.split(';')
    if (parts[0].trim().lowercase() != "text/html") return false
    for (i in 1 until parts.size) {
        val separator = parts[i].indexOf('=')
        if (separator == -1) continue
        if (parts[i].substring(0, separator).trim().lowercase() != "profile") continue
        if (unquote(parts[i].substring(separator + 1).trim()) == MCP_APP_PROFILE) return true
    }
    return false
}

private fun unquote(value: String): String =
    if (value.length > 1 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value

/** Upstream `MCPAppCspDeclaration`, after [normalize]: each list is null or non-empty. */
data class McpAppCspDeclaration(
    val resourceDomains: List<String>? = null,
    val connectDomains: List<String>? = null,
    val frameDomains: List<String>? = null,
    val baseUriDomains: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = resourceDomains == null && connectDomains == null && frameDomains == null && baseUriDomains == null

    /** The serialized length the web's sandbox URL would carry (`withSandboxCsp`'s bound). */
    fun serializedLength(): Int {
        fun list(name: String, values: List<String>?) = values?.let { v ->
            "\"$name\":[" + v.joinToString(",") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } + "]"
        }
        return (
            "{" + listOfNotNull(
                list("resourceDomains", resourceDomains),
                list("connectDomains", connectDomains),
                list("frameDomains", frameDomains),
                list("baseUriDomains", baseUriDomains),
            ).joinToString(",") + "}"
            ).length
    }

    companion object {
        /** Upstream `MCP_APP_CSP_SOURCE_PATTERN` (`packages/data-provider/src/mcp/csp.ts`). */
        private val SOURCE_PATTERN = Regex(
            """^(?:(?:https?|wss?)://)?(?:\*\.)?[a-zA-Z0-9][a-zA-Z0-9\-.]*(?::(?:\d{1,5}|\*))?(?:/[^\s;,'"?#]*)?$""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Upstream `normalizeMCPAppCspDeclaration`: keeps up to `maxSourcesPerDirective` trimmed
         * entries per directive that match the source grammar; anything else (a non-object, a
         * non-array directive, raw CSP syntax) contributes nothing.
         */
        fun normalize(raw: JsonElement?, limits: McpAppCspLimits = McpAppCspLimits()): McpAppCspDeclaration {
            val obj = raw as? JsonObject ?: return McpAppCspDeclaration()
            fun sources(key: String): List<String>? {
                val array = obj[key] as? JsonArray ?: return null
                val out = mutableListOf<String>()
                for (entry in array) {
                    val source = (entry as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
                    if (source.isNotEmpty() && SOURCE_PATTERN.matches(source)) {
                        out += source
                        if (out.size == limits.maxSourcesPerDirective) break
                    }
                }
                return out.ifEmpty { null }
            }
            return McpAppCspDeclaration(
                resourceDomains = sources("resourceDomains"),
                connectDomains = sources("connectDomains"),
                frameDomains = sources("frameDomains"),
                baseUriDomains = sources("baseUriDomains"),
            )
        }
    }
}

/**
 * Upstream `buildCspPolicy` (`packages/api/src/mcp/sandbox.ts`). [proxyLoader] true is the outer
 * relay page's policy, which must also admit its inner frame; false is the View's own policy,
 * installed as a `<meta>` inside the App document.
 */
fun buildMcpAppCspPolicy(csp: McpAppCspDeclaration, proxyLoader: Boolean): String {
    val resourceDomains = csp.resourceDomains?.joinToString(" ").orEmpty()
    val connectDomains = csp.connectDomains?.joinToString(" ")?.ifEmpty { null } ?: "'none'"
    val frameDomains = csp.frameDomains?.joinToString(" ").orEmpty()
    return listOf(
        "default-src 'none'",
        "script-src 'self' 'unsafe-inline' $resourceDomains".trim(),
        "style-src 'self' 'unsafe-inline' $resourceDomains".trim(),
        "connect-src $connectDomains",
        "form-action $connectDomains",
        "img-src 'self' data: $resourceDomains".trim(),
        "media-src 'self' data: $resourceDomains".trim(),
        "font-src 'self' $resourceDomains".trim(),
        if (proxyLoader) "frame-src blob: $frameDomains".trim() else "frame-src " + frameDomains.ifEmpty { "'none'" },
        "worker-src 'self' $resourceDomains".trim(),
        "object-src 'none'",
        "base-uri " + (csp.baseUriDomains?.joinToString(" ")?.ifEmpty { null } ?: "'self'"),
    ).joinToString("; ")
}

/** Upstream `APP_LINK_HOST_PATTERN` (`client/src/utils/mcpApps.ts`). */
private val APP_LINK_HOST_PATTERN = Regex(
    """^(https?|wss?)://(\*\.)?([a-zA-Z0-9](?:[a-zA-Z0-9\-.]*[a-zA-Z0-9])?)(?::(\d{1,5}|\*))?$""",
    RegexOption.IGNORE_CASE,
)

private val DEFAULT_PORTS = mapOf("http" to "80", "https" to "443")

/** The parts of a link [isAllowedAppLink] compares. Null for anything it would not open. */
private class ParsedLink(val scheme: String, val host: String, val port: String)

/**
 * A narrow http(s) URL reader: scheme, host and port only. It refuses what WHATWG parsing would
 * rewrite (backslashes, percent-escapes, whitespace in the authority) rather than reproduce that
 * rewriting, so it can only ever authorize less than upstream's `new URL`.
 */
private fun parseLink(url: String): ParsedLink? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = url.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return null
    val rest = url.substring(schemeEnd + 3)
    var authorityEnd = rest.length
    for (i in rest.indices) {
        if (rest[i] == '/' || rest[i] == '?' || rest[i] == '#') {
            authorityEnd = i
            break
        }
    }
    var authority = rest.substring(0, authorityEnd)
    if (authority.any { it == '\\' || it == '%' || it.isWhitespace() }) return null
    authority = authority.substringAfterLast('@')
    if (authority.startsWith('[')) return null
    val colon = authority.lastIndexOf(':')
    val host = if (colon >= 0) authority.substring(0, colon) else authority
    val rawPort = if (colon >= 0) authority.substring(colon + 1) else ""
    val port = when {
        rawPort.isEmpty() -> DEFAULT_PORTS.getValue(scheme)
        rawPort.all { it in '0'..'9' } && rawPort.length <= 5 -> rawPort.toInt().toString()
        else -> return null
    }
    return ParsedLink(scheme, host.lowercase(), port)
}

/** IPv4-shaped (dot-separated decimal labels); never matched, as upstream refuses address literals. */
private fun isAddressLiteral(host: String): Boolean {
    if (host.isEmpty()) return false
    var labelLength = 0
    for (c in host) {
        when (c) {
            in '0'..'9' -> labelLength++
            '.' -> if (labelLength == 0) return false else labelLength = 0
            else -> return false
        }
    }
    return labelLength > 0
}

/** Upstream `urlMatchesDeclaredSource`: CSP3 host-source matching, narrowed. */
private fun matchesDeclaredSource(link: ParsedLink, entry: String): Boolean {
    val match = APP_LINK_HOST_PATTERN.matchEntire(entry.trim()) ?: return false
    val declaredScheme = match.groupValues[1].lowercase()
    val wildcard = match.groupValues[2].isNotEmpty()
    val declaredHost = match.groupValues[3].lowercase()
    val declaredPort = match.groupValues[4]
    if (declaredScheme != "http" && declaredScheme != "https") return false
    if (link.scheme != declaredScheme) return false
    if (declaredPort != "*" && link.port != declaredPort.ifEmpty { DEFAULT_PORTS.getValue(declaredScheme) }) return false
    val host = link.host
    if (host.isEmpty() || host.endsWith('.') || isAddressLiteral(host)) return false
    if (!wildcard) return host == declaredHost
    return host.length > declaredHost.length + 1 && host.endsWith(".$declaredHost")
}

/**
 * Upstream `isAllowedAppLink`: an App may open only an http(s) link on a host its delivered CSP
 * declared for `connect`, `resource` or `frame` egress. A host-opened link is outside the
 * sandbox's CSP, so this is what keeps an App from exfiltrating proxied data through a URL.
 */
fun isAllowedAppLink(url: String, csp: McpAppCspDeclaration): Boolean {
    val link = parseLink(url) ?: return false
    val declared = csp.connectDomains.orEmpty() + csp.resourceDomains.orEmpty() + csp.frameDomains.orEmpty()
    return declared.any { matchesDeclaredSource(link, it) }
}

const val MIN_APP_VIEW_HEIGHT = 80
const val MAX_APP_VIEW_HEIGHT = 4000

/** Upstream `clampAppViewHeight`: null unless a finite positive number, else rounded into bounds. */
fun clampAppViewHeight(height: Double?, min: Int = MIN_APP_VIEW_HEIGHT, max: Int = MAX_APP_VIEW_HEIGHT): Int? {
    if (height == null || !height.isFinite() || height <= 0) return null
    return kotlin.math.round(height).toInt().coerceIn(min, max)
}
