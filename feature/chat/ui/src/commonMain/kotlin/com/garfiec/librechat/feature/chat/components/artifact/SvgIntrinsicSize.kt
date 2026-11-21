package com.garfiec.librechat.feature.chat.components.artifact

private val VIEW_BOX_REGEX = Regex(
    """<svg\b[^>]*\bviewBox\s*=\s*["']\s*(-?[\d.]+)\s+(-?[\d.]+)\s+([\d.]+)\s+([\d.]+)\s*["']""",
    RegexOption.IGNORE_CASE,
)

private val WIDTH_REGEX = Regex(
    """<svg\b[^>]*\bwidth\s*=\s*["']\s*([\d.]+)(?:px)?\s*["']""",
    RegexOption.IGNORE_CASE,
)

private val HEIGHT_REGEX = Regex(
    """<svg\b[^>]*\bheight\s*=\s*["']\s*([\d.]+)(?:px)?\s*["']""",
    RegexOption.IGNORE_CASE,
)

/**
 * Extracts the SVG's intrinsic aspect ratio (width / height) from the root
 * `<svg>` tag without doing a full XML parse. `viewBox` is preferred because
 * mermaid always emits it; explicit `width`/`height` attributes are the
 * fallback for hand-authored SVG artifacts. Returns null when neither is
 * usable, which lets callers fall back to the legacy `heightIn(min = 80.dp)`
 * behavior.
 *
 * Used by [InlineSvgArtifact] to apply `Modifier.aspectRatio` so the artifact
 * occupies its final height before Coil's async decode completes — eliminating
 * the LazyColumn scroll-jump for cached-mermaid and SVG artifacts.
 */
fun parseSvgAspectRatio(svg: String): Float? {
    val viewBoxMatch = VIEW_BOX_REGEX.find(svg)
    if (viewBoxMatch != null) {
        val width = viewBoxMatch.groupValues[3].toFloatOrNull() ?: return null
        val height = viewBoxMatch.groupValues[4].toFloatOrNull() ?: return null
        if (width > 0f && height > 0f) return width / height
    }
    val widthMatch = WIDTH_REGEX.find(svg) ?: return null
    val heightMatch = HEIGHT_REGEX.find(svg) ?: return null
    val width = widthMatch.groupValues[1].toFloatOrNull() ?: return null
    val height = heightMatch.groupValues[1].toFloatOrNull() ?: return null
    return if (width > 0f && height > 0f) width / height else null
}
