package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Shows [content] and, while [preview] is set, the full-screen media viewer over it. Thumbnails in
 * [content] marked with [mediaThumbnail] are where the viewer's images fly back to as it closes;
 * [onDismiss] runs once the image has landed, so the caller just clears [preview].
 *
 * The viewer is emitted after [content] rather than wrapped around it, so the caller hosts this in
 * a stacking layout (a `Box`, or a screen's root), as it would any overlay.
 */
@Composable
fun MediaViewerHost(
    preview: MediaPreviewState?,
    onDismiss: () -> Unit,
    closeContentDescription: String = "",
    defaultContentDescription: String = "",
    actions: @Composable RowScope.(MediaItem) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val thumbnails = remember { MediaThumbnailRegistry() }
    CompositionLocalProvider(LocalMediaThumbnails provides thumbnails) {
        content()
        if (preview != null) {
            ZoomableMediaPager(
                items = preview.items,
                initialIndex = preview.initialIndex,
                onDismiss = onDismiss,
                thumbnails = thumbnails,
                closeContentDescription = closeContentDescription,
                defaultContentDescription = defaultContentDescription,
                actions = actions,
            )
        }
    }
}
