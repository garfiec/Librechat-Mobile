package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * Composition-scoped opener for an artifact viewer. Provided once at the chat/media
 * navigation entry, which owns the screen-level presentation: it reads the display-mode
 * pref and either pushes the full-screen `ArtifactFullscreen` route or opens the artifact in
 * the entry's `ArtifactPresentationHost`. Call sites deep in the message list (artifact buttons/inline views) just
 * fire this event with the tapped artifact and its version history.
 *
 * Null when no navigation host is in scope (e.g. previews/tests); callers no-op the tap.
 */
val LocalOpenArtifact =
    staticCompositionLocalOf<((Artifact, List<Artifact>) -> Unit)?> { null }

/** Where an artifact being opened was tapped, so the viewer can grow out of it and shrink back into it. */
@Stable
class ArtifactOrigin {
    /**
     * The tapped card's bounds in the window, or null once it has left the screen. Set by the card
     * just before it calls [LocalOpenArtifact].
     */
    var bounds: (() -> Rect?)? = null
}

/** Provided with [LocalOpenArtifact]; null where nothing animates from the card. */
val LocalArtifactOrigin = staticCompositionLocalOf<ArtifactOrigin?> { null }
