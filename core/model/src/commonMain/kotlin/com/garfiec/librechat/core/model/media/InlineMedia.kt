package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.common.identity.serverOrigin
import com.garfiec.librechat.core.model.Attachment

/**
 * Resolves a markdown image in a reply (`![chart](chart.png)`) to the file the turn produced under
 * that name. Ported from upstream `client/src/utils/media.ts`.
 *
 * String operations only, no `Regex`: an ICU-invalid pattern throws at class init on Android while
 * passing every JVM test.
 */

/**
 * Indexes a turn's attachments by the filename a markdown image would name them with: the leaf of
 * `filename`, lowercased. Only displayable files are indexed, an image extension and a `filepath`.
 *
 * A name claimed by two different stored files (`fileId ?: filepath`) is ambiguous and dropped for
 * good: nothing in `![…](output.png)` says which one the author meant, and guessing renders one file
 * under the other's caption. A later record of the SAME file replaces the earlier one, since a tool
 * rewriting one path reuses its id and the later record carries the newer URL.
 */
fun buildAttachmentsByName(attachments: List<Attachment>?): Map<String, Attachment> {
    if (attachments.isNullOrEmpty()) return emptyMap()
    val byName = mutableMapOf<String, Attachment>()
    val ambiguous = mutableSetOf<String>()
    for (attachment in attachments) {
        val key = displayableNameKey(attachment)
        if (key == null || key in ambiguous) continue
        val claimed = byName[key]
        if (claimed == null || fileKeyOf(claimed) == fileKeyOf(attachment)) {
            byName[key] = attachment
        } else {
            ambiguous += key
            byName.remove(key)
        }
    }
    return byName.ifEmpty { emptyMap() }
}

/**
 * The attachment a markdown image [src] names, or null when [src] is already an address (any scheme,
 * or a root-relative path the server serves) or names nothing in [byName].
 *
 * An explicit `/api/files/…/chart.png` is never reduced to `chart.png`: that could resolve it to a
 * different attachment sharing the leaf and show the wrong image.
 */
fun resolveInlineMedia(src: String?, byName: Map<String, Attachment>?): Attachment? {
    if (src.isNullOrEmpty() || byName.isNullOrEmpty()) return null
    val key = mediaKeyFromSource(src)
    return if (key.isEmpty()) null else byName[key]
}

/**
 * Where a markdown image would load from. [foreignHost] is set when [url] is not on the server's
 * origin: fetching it tells that site the user's IP address and that they are reading this reply,
 * so the caller asks first, naming it. It is the bare host for another site, and the whole origin
 * (`http://chat.example.com`, `https://chat.example.com:8443`) for the server's own host on another
 * scheme or port, which the bare host would pass off as the server. The user's own server and the
 * turn's own files are never foreign.
 */
data class InlineMediaTarget(val url: String, val foreignHost: String? = null)

/**
 * A loadable URL for a markdown image [src]: the attachment it names, else [src] itself when it is
 * already loadable. A served root-relative path (`/images/…`, `/api/…`) joins the deployment base so
 * it survives a subpath deployment. Null for anything else (a bare name this turn did not produce,
 * an unsupported scheme), which the caller shows as its alt text rather than requesting.
 *
 * Also null, never asked about, for a web address whose host this can't name exactly as the request
 * would reach it (empty, non-ASCII, escaped, a backslash in the authority), and for the server's own
 * host over `http` when the server is `https`: the session bearer is scoped by host, so loading it
 * would put the bearer on the wire in cleartext.
 *
 * An attachment is never foreign, even when its stored URL is on a storage host: the turn produced
 * it and the server handed out that address.
 */
fun resolveInlineMediaTarget(src: String, byName: Map<String, Attachment>, baseUrl: String): InlineMediaTarget? {
    resolveInlineMedia(src, byName)?.let { attachment ->
        return resolveAttachmentUrl(attachment, baseUrl)?.let { InlineMediaTarget(it) }
    }
    return when {
        src.startsWith("https://", ignoreCase = true) || src.startsWith("http://", ignoreCase = true) ->
            webImageTarget(src, baseUrl)
        src.startsWith(DATA_URI_PREFIX, ignoreCase = true) -> InlineMediaTarget(src)
        isServedPath(src) && baseUrl.isNotBlank() -> InlineMediaTarget(cleanServerUrl(baseUrl) + src)
        else -> null
    }
}

/**
 * True when [url] is on the server's own origin, at any path: a subpath deployment at
 * `https://host/chat` owns `https://host/other`. A prefix match that has to end at a path, query or
 * fragment, so `https://host@elsewhere`, `https://host.elsewhere` and a spelled-out default port all
 * fail it and are asked about rather than loaded.
 */
private fun isOnServer(url: String, baseUrl: String): Boolean {
    if (baseUrl.isBlank()) return false
    val origin = serverOrigin(cleanServerUrl(baseUrl))
    if (!url.startsWith(origin, ignoreCase = true)) return false
    return url.length == origin.length || url[origin.length] in ORIGIN_END
}

private fun webImageTarget(src: String, baseUrl: String): InlineMediaTarget? {
    if (isOnServer(src, baseUrl)) return InlineMediaTarget(src)
    val image = parseWebOrigin(src) ?: return null
    val server = baseUrl.takeIf { it.isNotBlank() }?.let { parseWebOrigin(serverOrigin(cleanServerUrl(it))) }
    if (server == null || image.host != server.host) return InlineMediaTarget(src, foreignHost = image.hostAndPort)
    if (image.scheme == HTTP && server.scheme == HTTPS) return null
    return InlineMediaTarget(src, foreignHost = "${image.scheme}://${image.hostAndPort}")
}

/** A web address's scheme and authority, lowercased, without any `user@` prefix. */
private class WebOrigin(val scheme: String, val host: String, val port: String?) {
    val hostAndPort: String get() = if (port == null) host else "$host:$port"
}

/**
 * [url]'s origin, or null unless its host is one this can name exactly: ASCII letters, digits, dots
 * and hyphens, or a bracketed IPv6 literal, and a numeric port. Anything else (an empty host, which
 * a URL parser fills from the path, a non-ASCII host that reads as a lookalike, a percent escape, a
 * backslash parsers disagree about) could reach a host other than the one shown.
 */
private fun parseWebOrigin(url: String): WebOrigin? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = url.substring(0, schemeEnd).lowercase()
    if (scheme != HTTP && scheme != HTTPS) return null
    val rest = url.substring(schemeEnd + SCHEME_SEPARATOR.length)
    val end = rest.indexOfAny(ORIGIN_END).let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, end)
    if ('\\' in authority) return null
    val hostPort = authority.substringAfterLast('@').lowercase()
    val portSep = if (hostPort.startsWith('[')) {
        hostPort.indexOf("]:").let { if (it < 0) -1 else it + 1 }
    } else {
        hostPort.indexOf(':')
    }
    val host = if (portSep < 0) hostPort else hostPort.substring(0, portSep)
    val port = if (portSep < 0) null else hostPort.substring(portSep + 1)
    if (!isNameableHost(host)) return null
    if (port != null && (port.isEmpty() || port.any { it !in '0'..'9' })) return null
    return WebOrigin(scheme, host, port)
}

private fun isNameableHost(host: String): Boolean = when {
    host.isEmpty() -> false
    host.startsWith('[') -> host.length > 2 && host.endsWith(']') &&
        host.substring(1, host.length - 1).all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }
    else -> host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }
}

private val ORIGIN_END = charArrayOf('/', '?', '#')
private const val SCHEME_SEPARATOR = "://"
private const val HTTP = "http"
private const val HTTPS = "https"

/** Upstream `imageExtRegex` (`packages/data-provider/src/file-config.ts`). `svg` is absent there too. */
private val INLINE_IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif")

private fun hasInlineImageExtension(name: String): Boolean =
    INLINE_IMAGE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

/** The lookup key for an attachment an image tag can display, or null when it cannot display it. */
private fun displayableNameKey(attachment: Attachment): String? {
    val filename = attachment.filename ?: return null
    if (attachment.filepath.isNullOrEmpty() || !hasInlineImageExtension(filename)) return null
    return mediaNameKey(filename).ifEmpty { null }
}

private fun fileKeyOf(attachment: Attachment): String? = attachment.fileId ?: attachment.filepath

/** The final path segment, lowercased: all a markdown reference and an attachment need to agree on. */
private fun mediaNameKey(name: String): String = name.trim().substringAfterLast('/').lowercase()

private fun mediaKeyFromSource(src: String): String {
    if (hasScheme(src) || src.startsWith("//") || isServedPath(src)) return ""
    // A malformed escape is not a reference we can resolve; match the raw form, as upstream does.
    val decoded = percentDecode(src) ?: src
    val end = decoded.indexOfFirst { it == '?' || it == '#' }
    return mediaNameKey(if (end < 0) decoded else decoded.substring(0, end))
}

/** Upstream `SERVED_PATH_PATTERN`: root-relative paths the server serves itself. */
private fun isServedPath(src: String): Boolean =
    src.startsWith("/images/", ignoreCase = true) || src.startsWith("/api/", ignoreCase = true)

/** `^[a-z][a-z\d+\-.]*:`, case-insensitive. */
private fun hasScheme(src: String): Boolean {
    if (src.isEmpty() || !src[0].isAsciiLetter()) return false
    for (i in 1 until src.length) {
        val c = src[i]
        if (c == ':') return true
        if (!(c.isAsciiLetter() || c in '0'..'9' || c == '+' || c == '-' || c == '.')) return false
    }
    return false
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/** `decodeURIComponent`: `%XX` runs decode as UTF-8 and `+` stays. Null where that would throw. */
private fun percentDecode(src: String): String? {
    if ('%' !in src) return src
    val bytes = ArrayList<Byte>(src.length)
    var i = 0
    while (i < src.length) {
        if (src[i] == '%') {
            val hi = src.getOrNull(i + 1)?.digitToIntOrNull(HEX_RADIX) ?: return null
            val lo = src.getOrNull(i + 2)?.digitToIntOrNull(HEX_RADIX) ?: return null
            bytes += (hi * HEX_RADIX + lo).toByte()
            i += 3
        } else {
            val end = src.indexOf('%', i).let { if (it < 0) src.length else it }
            src.substring(i, end).encodeToByteArray().forEach { bytes += it }
            i = end
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }
}

private const val HEX_RADIX = 16

/** Inline-bytes scheme; already complete. */
private const val DATA_URI_PREFIX = "data:"
