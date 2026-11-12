package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders an artifact inline in a chat message. Fills the available width and
 * scales height to match the rendered content. Tapping opens the
 * [ArtifactViewer] via [onTap].
 *
 * Used when the user has enabled inline rendering for the artifact's type in
 * Settings > Chat > Artifacts.
 *
 * [fitContent] sizes the slot to the page's reported height, between
 * [INLINE_FIT_MIN_HEIGHT] and the inline cap, instead of the fixed cap; it settles once per
 * card, so it costs one resize as the page lays out. [header] sits above the page inside the
 * card, where a tap can't land on the WebView, which takes touches for itself.
 * [allowedLibraries] go to [ArtifactWebContent.buildHtml]; [ArtifactLibraryGate] decides them.
 */
@Composable
expect fun InlineArtifactView(
    artifact: Artifact,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    fitContent: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    allowedLibraries: Set<String> = emptySet(),
)

/**
 * Whether the inline view composites WebGL. Android's doesn't: it renders in a software layer
 * (see `configureLazyListWebView`), where a WebGL canvas never reaches the screen.
 */
internal expect val inlineArtifactDrawsWebGl: Boolean
