package com.garfiec.librechat.core.model.config

/** Schemes a configured link may use: web pages, and the mail and phone links that run no script. */
private val LINK_SCHEMES = setOf("http", "https", "mailto", "tel")

/**
 * Port of upstream's `isSafeLinkUrl` (`packages/data-provider/src/config.ts`, v0.8.9-rc1; registered
 * in `scripts/mirrors.json`): whether a configured link such as a privacy policy or terms of service
 * `externalUrl` may be opened. An http(s), `mailto:` or `tel:` URL, a relative URL, or a blank value.
 *
 * The scheme is read the way a URL parser reads it: leading C0 controls, spaces and Unicode white
 * space are skipped, and tabs and newlines inside the scheme are dropped, so `" JavaScript:"` or
 * `"java\tscript:"` cannot pass as something else. Only the scheme is judged.
 *
 * A hand-written scan rather than upstream's regex: a JVM-valid pattern can fail to compile under
 * Android's ICU engine at class init, and the scan is a few lines.
 */
fun isSafeLinkUrl(url: String): Boolean {
    val scheme = linkScheme(url) ?: return true
    return scheme in LINK_SCHEMES
}

/** [url] when it is non-blank and [isSafeLinkUrl]; otherwise null (no link is shown). */
fun safeLinkUrlOrNull(url: String?): String? = url?.trim()?.takeIf { it.isNotEmpty() && isSafeLinkUrl(it) }

/**
 * [url], a value [safeLinkUrlOrNull] let through, as an absolute URL the app can hand to the system
 * opener. The web client resolves a relative link against the page it is on, a page under the
 * deployment root, so the same is done here against [deploymentRoot] (the cleaned server URL): a
 * path-absolute link against its origin, a scheme-relative one with its scheme, anything else under
 * the root. A link that already names a scheme is returned as is.
 */
fun absoluteLinkUrl(url: String, deploymentRoot: String): String {
    if (linkScheme(url) != null) return url
    val root = deploymentRoot.trimEnd('/')
    val schemeEnd = root.indexOf("://")
    if (schemeEnd < 0) return url
    val authorityEnd = root.indexOf('/', schemeEnd + SCHEME_SEPARATOR_LENGTH).let { if (it < 0) root.length else it }
    return when {
        url.startsWith("//") -> root.substring(0, schemeEnd + 1) + url
        url.startsWith("/") -> root.substring(0, authorityEnd) + url
        else -> "$root/$url"
    }
}

private const val SCHEME_SEPARATOR_LENGTH = 3

/** Upstream's `/^[\u0000- \s]*([a-z][a-z\d+.\t\n\r-]*):/i`, lower-cased without tabs/newlines. */
private fun linkScheme(url: String): String? {
    var i = 0
    while (i < url.length && isSkippable(url[i])) i++
    if (i >= url.length || !url[i].isAsciiLetter()) return null
    val scheme = StringBuilder()
    while (i < url.length) {
        val c = url[i]
        when {
            c == ':' -> return scheme.toString().lowercase()
            c == '\t' || c == '\n' || c == '\r' -> Unit
            c.isAsciiLetter() || c in '0'..'9' || c == '+' || c == '.' || c == '-' -> scheme.append(c)
            else -> return null
        }
        i++
    }
    return null
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/** C0 controls and space, plus what JavaScript's `\s` (and so `String.prototype.trim`) matches. */
private fun isSkippable(c: Char): Boolean =
    c <= ' ' ||
        c == ' ' || c == ' ' || c in ' '..' ' ||
        c == ' ' || c == ' ' || c == ' ' || c == ' ' ||
        c == '　' || c == '﻿'
