package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Native inline rendering for `image/svg+xml` artifacts via Coil's SVG decoder.
 * The decoder produces an image at the SVG's intrinsic aspect ratio, so Compose
 * sizes the artifact deterministically (width × intrinsic-aspect = height) once
 * Coil has parsed the bytes — no WebView, no async height measurement, no JS.
 * SvgDecoder.Factory must be registered on the singleton ImageLoader.
 */
@Composable
fun InlineSvgArtifact(
    artifact: Artifact,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineSvgSurface(
        svg = artifact.content,
        onTap = onTap,
        modifier = modifier,
        contentDescription = artifact.title,
    )
}

/**
 * Pure SVG-string entry point reused by the cached-mermaid path, which doesn't
 * have an [Artifact] handy at the moment of the cache hit. [contentPadding] is
 * exposed because mermaid SVGs have their own internal `<g>` translate offset;
 * the default 12.dp is correct for user-authored SVGs but leaves too much
 * whitespace around cached mermaids.
 */
@Composable
fun InlineSvgSurface(
    svg: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentPadding: Dp = 12.dp,
) {
    val svgBytes = remember(svg) { svg.encodeToByteArray() }
    val aspectRatio = remember(svg) { parseSvgAspectRatio(svg) }
    ArtifactCardSurface(onTap = onTap, modifier = modifier) {
        AsyncImage(
            model = svgBytes,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it.heightIn(min = 80.dp) }
                .padding(contentPadding),
        )
    }
}
