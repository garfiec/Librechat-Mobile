package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The platform-specific preview renderer for an artifact — an Android `WebView`
 * or an iOS `WKWebView`. Everything else about the artifact viewer (shell,
 * header, selector, code body) is shared in [ArtifactViewer].
 *
 * [allowedLibraries] go to [ArtifactWebContent.buildHtml]; [ArtifactLibraryGate] decides them.
 */
@Composable
expect fun ArtifactPreviewSurface(
    content: String,
    type: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    allowedLibraries: Set<String> = emptySet(),
)

/**
 * Returns a share action for the current platform. Android routes through
 * `ArtifactDownloadHelper` (needs a `Context`); iOS uses `UIActivityViewController`.
 */
@Composable
expect fun rememberShareArtifact(): (Artifact) -> Unit

/**
 * Returns an action that pins [Artifact] to the device home screen as a launcher shortcut, or `null`
 * on platforms that can't place home-screen icons (iOS). The action snapshots the artifact into local
 * storage and requests the pin; [label]/[emoji] come from the confirmation dialog. Distributed to call
 * sites (viewer toolbar, inline card) via [LocalAddArtifactToHomeScreen] — invoked once here.
 */
@Composable
expect fun rememberAddArtifactToHomeScreen(): ((artifact: Artifact, label: String, emoji: String?) -> Unit)?
