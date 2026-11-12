package com.garfiec.librechat.core.data.datastore

/**
 * Preferences controlling how the artifact viewer presents content. Configured
 * via Settings > Chat > Artifacts.
 *
 * - [mode] — automatic, bottom sheet or full-screen presentation. Artifacts open preview-first;
 *   source is reached via the viewer's source/preview toggle button.
 * - [splitFraction] — the artifact's share of the width when it stands beside the chat, set by
 *   dragging the divider; one value for every window.
 */
data class ArtifactDisplayPrefs(
    val mode: ArtifactDisplayMode = ArtifactDisplayMode.AUTOMATIC,
    val splitFraction: Float = DEFAULT_SPLIT_FRACTION,
) {
    companion object {
        const val DEFAULT_SPLIT_FRACTION = 0.5f
    }
}
