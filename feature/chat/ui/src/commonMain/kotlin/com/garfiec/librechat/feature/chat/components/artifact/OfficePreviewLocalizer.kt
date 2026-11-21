package com.garfiec.librechat.feature.chat.components.artifact

/**
 * The renderer scripts the server's office preview documents load from jsDelivr, keyed by the
 * exact URL upstream emits (`DOCX_PREVIEW_CDN` in `packages/api/src/files/documents/html.ts`),
 * mapped to the copy vendored under the web asset base (`scripts/web-assets.json`).
 *
 * Exact-URL keys are the version check: a server pinned to any other version emits a URL that
 * is not here, so its tag is stripped and the document shows its own fallback instead of running
 * a renderer it was not written against. `PPTX_PREVIEW_CDN`'s pptx-preview is absent on purpose:
 * it is not FOSS, so PPTX documents always show the server's slide-list fallback.
 */
internal val VENDORED_OFFICE_RENDERERS: Map<String, String> = mapOf(
    "https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js" to "jszip/jszip.min.js",
    "https://cdn.jsdelivr.net/npm/docx-preview@0.3.7/dist/docx-preview.min.js" to
        "docx-preview/docx-preview.min.js",
)

/**
 * Replaces the server document's CSP, which names `https://cdn.jsdelivr.net`. Scripts are
 * local (`'self'`/`file:`, the form every vendored document uses) plus the inline bootstrap
 * and base64 data block the document is built around; images and fonts only from `data:` /
 * `blob:`, which is where docx-preview puts embedded media. No remote origin anywhere.
 */
internal const val OFFICE_PREVIEW_CSP: String =
    "default-src 'none'; script-src 'self' 'unsafe-inline' file:; style-src 'unsafe-inline'; " +
        "img-src data: blob:; font-src data: blob:; base-uri 'none'; form-action 'none'"

private const val CSP_META = """<meta http-equiv="Content-Security-Policy" content="$OFFICE_PREVIEW_CSP">"""

/**
 * Makes a server-built office preview document load nothing from the network.
 *
 * - `<script src>` naming a [VENDORED_OFFICE_RENDERERS] URL becomes a plain tag pointing at the
 *   vendored copy. `integrity` and `crossorigin` are dropped: both platforms load these from
 *   `file:` URLs, whose origin is opaque, so the fetch is neither same-origin nor CORS-eligible
 *   and SRI would block the script outright. Byte integrity is enforced at build time instead,
 *   by the sha256 lock, and the vendored bytes equal the server's SRI pins.
 * - Every other `<script src>`, remote `<link>`, and remote `@import` in a `<style>` is removed,
 *   so an unknown renderer fails closed to the document's fallback (`#lc-fallback`).
 * - Every CSP `<meta>` is replaced by one [OFFICE_PREVIEW_CSP], placed first in `<head>`, or at
 *   the top when there is no `<head>` so a document without a policy does not fail open. A
 *   `<meta http-equiv="refresh">` is dropped.
 * - Inline scripts and comments are copied byte-for-byte without being scanned, as the HTML
 *   tokenizer treats them, so the bootstrap and the base64 payload survive untouched.
 *
 * Plain index scanning rather than Regex: tag-shaped patterns are where ICU and the JVM disagree.
 * Idempotent. Pure.
 */
internal fun localizeOfficePreviewHtml(html: String): String {
    val out = StringBuilder(html.length + CSP_META.length)
    var cspInserted = false
    var i = 0
    while (i < html.length) {
        if (html[i] != '<') {
            out.append(html[i])
            i++
            continue
        }
        when {
            html.startsWith("<!--", i) -> {
                val end = html.indexOf("-->", i + 4).let { if (it < 0) html.length else it + 3 }
                out.appendRange(html, i, end)
                i = end
            }
            isTagStart(html, i, "script") -> {
                val tag = readTag(html, i, "script")
                val end = endOfElement(html, tag.end, "script")
                val src = tag.attrs["src"]?.trim()
                if (src == null) {
                    out.appendRange(html, i, end)
                } else {
                    localScriptPath(src)?.let { out.append("<script src=\"").append(it).append("\"></script>") }
                }
                i = end
            }
            isTagStart(html, i, "style") -> {
                val tag = readTag(html, i, "style")
                val close = html.indexOf("</style", tag.end, ignoreCase = true).let { if (it < 0) html.length else it }
                out.appendRange(html, i, tag.end)
                out.append(stripRemoteImports(html.substring(tag.end, close)))
                i = close
                if (close < html.length) {
                    val end = readTag(html, close + 1, "style").end
                    out.appendRange(html, close, end)
                    i = end
                }
            }
            isTagStart(html, i, "link") -> {
                val tag = readTag(html, i, "link")
                if (!isRemote(tag.attrs["href"].orEmpty())) out.appendRange(html, i, tag.end)
                i = tag.end
            }
            isTagStart(html, i, "meta") -> {
                val tag = readTag(html, i, "meta")
                // A refresh is a navigation, which no CSP directive here can stop.
                val httpEquiv = tag.attrs["http-equiv"]?.trim()?.lowercase()
                if (httpEquiv != "content-security-policy" && httpEquiv != "refresh") out.appendRange(html, i, tag.end)
                i = tag.end
            }
            !cspInserted && isTagStart(html, i, "head") -> {
                val tag = readTag(html, i, "head")
                out.appendRange(html, i, tag.end).append(CSP_META)
                cspInserted = true
                i = tag.end
            }
            else -> {
                out.append('<')
                i++
            }
        }
    }
    if (!cspInserted) out.insert(insertionPointWithoutHead(out), CSP_META)
    return out.toString()
}

private fun localScriptPath(src: String): String? =
    VENDORED_OFFICE_RENDERERS[src] ?: src.takeIf { it in VENDORED_OFFICE_RENDERERS.values }

private fun isRemote(url: String): Boolean {
    val u = url.trim()
    return u.startsWith("http:", ignoreCase = true) ||
        u.startsWith("https:", ignoreCase = true) ||
        u.startsWith("//")
}

/** Just past a leading doctype, so the policy cannot push the document into quirks mode. */
private fun insertionPointWithoutHead(doc: CharSequence): Int {
    var start = 0
    while (start < doc.length && doc[start].isWhitespace()) start++
    if (!doc.startsWith("<!doctype", start, ignoreCase = true)) return 0
    val close = doc.indexOf('>', start)
    return if (close < 0) 0 else close + 1
}

/** Drops each `@import …;` that names a remote URL. Local imports and all other rules are kept. */
private fun stripRemoteImports(css: String): String {
    val out = StringBuilder(css.length)
    var i = 0
    while (i < css.length) {
        val at = css.indexOf("@import", i, ignoreCase = true)
        if (at < 0) {
            out.appendRange(css, i, css.length)
            break
        }
        val end = css.indexOf(';', at).let { if (it < 0) css.length else it + 1 }
        val statement = css.substring(at, end)
        out.appendRange(css, i, at)
        val remote = statement.contains("http:", ignoreCase = true) ||
            statement.contains("https:", ignoreCase = true) ||
            statement.contains("//")
        if (!remote) out.append(statement)
        i = end
    }
    return out.toString()
}

private class Tag(val end: Int, val attrs: Map<String, String>)

private fun isTagStart(html: String, at: Int, name: String): Boolean {
    if (!html.regionMatches(at + 1, name, 0, name.length, ignoreCase = true)) return false
    val next = html.getOrNull(at + 1 + name.length) ?: return false
    return next.isWhitespace() || next == '>' || next == '/'
}

/**
 * Reads the tag whose [name] starts just after [at] (the `<`, or the `/` of a closing tag),
 * honouring quoted attribute values so a `>` inside one does not end it. Returns the index just
 * past `>`, or the end of input for an unterminated tag, plus the attributes with lower-cased names.
 */
@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
private fun readTag(html: String, at: Int, name: String): Tag {
    val attrs = mutableMapOf<String, String>()
    var i = at + 1 + name.length
    while (i < html.length) {
        val c = html[i]
        when {
            c == '>' -> return Tag(i + 1, attrs)
            c.isWhitespace() || c == '/' -> i++
            else -> {
                val nameStart = i
                while (i < html.length && !endsAttributeName(html[i])) i++
                val attrName = html.substring(nameStart, i).lowercase()
                while (i < html.length && html[i].isWhitespace()) i++
                var value = ""
                if (i < html.length && html[i] == '=') {
                    i++
                    while (i < html.length && html[i].isWhitespace()) i++
                    val quote = html.getOrNull(i)
                    if (quote == '"' || quote == '\'') {
                        val close = html.indexOf(quote, i + 1).let { if (it < 0) html.length else it }
                        value = html.substring(i + 1, close)
                        i = (close + 1).coerceAtMost(html.length)
                    } else {
                        val valueStart = i
                        while (i < html.length && !html[i].isWhitespace() && html[i] != '>') i++
                        value = html.substring(valueStart, i)
                    }
                }
                if (attrName.isNotEmpty() && attrName !in attrs) attrs[attrName] = value
            }
        }
    }
    return Tag(html.length, attrs)
}

private fun endsAttributeName(c: Char): Boolean = c.isWhitespace() || c == '=' || c == '>' || c == '/'

/** Index just past `</name …>` at or after [from]; end of input when it never closes. */
private fun endOfElement(html: String, from: Int, name: String): Int {
    val close = html.indexOf("</$name", from, ignoreCase = true)
    if (close < 0) return html.length
    val gt = html.indexOf('>', close)
    return if (gt < 0) html.length else gt + 1
}
