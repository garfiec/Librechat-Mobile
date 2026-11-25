package com.garfiec.librechat.feature.chat.components.artifact

import com.garfiec.librechat.feature.chat.components.LruSnapshotCache

/**
 * Compose-observable in-memory cache of mermaid-rendered SVG strings, keyed by
 * `${contentHashCode}:$isDark`. Filled by inline-artifact WebViews via a JS
 * bridge; read by `SharedContentParts` when a recompose reaches an already-
 * rendered flowchart mermaid.
 */
class MermaidRenderCache(maxEntries: Int = MAX_ENTRIES_DEFAULT) :
    LruSnapshotCache<String>(maxEntries) {

    private companion object {
        const val MAX_ENTRIES_DEFAULT = 100
    }
}

fun mermaidCacheKey(content: String, isDark: Boolean): String =
    "${content.hashCode()}:$isDark"

/**
 * Allowlist of mermaid diagram types that render pixel-equivalent through Coil's
 * SvgDecoder. State and gantt diagrams render incorrectly (wrong-scale and
 * silent-blank respectively), so only flowchart/graph artifacts route through
 * the SVG cache; the rest re-render in the WebView on every recompose.
 *
 * The scan skips blank lines and `%%`-prefixed mermaid directives (e.g.
 * `%%{init: {...}}%%`) so flowcharts with config preambles still match.
 */
fun isCacheableMermaid(content: String): Boolean {
    val firstKeyword = content.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }
        ?.substringBefore(' ')
        ?.substringBefore('\t')
        ?: return false
    return firstKeyword == "flowchart" || firstKeyword == "graph"
}
