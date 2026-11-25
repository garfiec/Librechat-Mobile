package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Result of the inline-artifact dispatch ladder. Decouples the routing decision
 * from the composable that renders the selected strategy so the routing logic
 * can be unit-tested without Compose.
 */
sealed interface InlineArtifactStrategy {
    data class CachedMermaidSvg(val svg: String) : InlineArtifactStrategy
    data object NativeMarkdown : InlineArtifactStrategy
    data object IntrinsicSvg : InlineArtifactStrategy
    data object WebViewSlot : InlineArtifactStrategy
}

/**
 * Pure dispatch function for inline artifacts. Mirrors the previous inline
 * `when` ladder in `SharedContentParts.TextContentPart`:
 *  1. Cacheable mermaid with a populated cache entry → render the SVG directly.
 *  2. Markdown → native compose-markdown renderer.
 *  3. SVG → intrinsic-aspect-ratio renderer.
 *  4. Everything else (HTML, React, uncached/non-cacheable Mermaid) →
 *     platform WebView slot.
 *
 * The cache-hit gate intentionally stays narrow: even if a caller supplies a
 * cached SVG for a non-cacheable mermaid source, we fall through to the WebView
 * slot to avoid rendering a stale or wrong-shape SVG.
 */
fun selectInlineArtifactStrategy(
    type: ArtifactType,
    content: String,
    cachedMermaidSvg: String?,
): InlineArtifactStrategy = when {
    type == ArtifactType.MERMAID &&
        isCacheableMermaid(content) &&
        !cachedMermaidSvg.isNullOrEmpty() -> InlineArtifactStrategy.CachedMermaidSvg(cachedMermaidSvg)
    type == ArtifactType.MARKDOWN -> InlineArtifactStrategy.NativeMarkdown
    type == ArtifactType.SVG -> InlineArtifactStrategy.IntrinsicSvg
    else -> InlineArtifactStrategy.WebViewSlot
}

/**
 * Whether [content] draws with WebGL: it imports three.js or asks a canvas for a WebGL context.
 * Where the inline view can't composite WebGL ([inlineArtifactDrawsWebGl]), such an artifact gets
 * the tap-to-open card instead of a preview that would stay blank.
 */
fun usesWebGl(content: String): Boolean = WEBGL_USE.containsMatchIn(content)

private val WEBGL_USE = Regex(
    """from\s*['"]three['"]|\bWebGL2?Renderer\b|getContext\(\s*['"](?:experimental-)?webgl2?['"]""",
)
