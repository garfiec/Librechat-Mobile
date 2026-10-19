package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.Attachment

/**
 * Composition-scoped, authenticated file downloader for tool-call attachments. Provided once at
 * the chat screen root (backed by `ChatViewModel.downloadFileBytes`), so a file chip deep in the
 * message list can fetch a generated file's bytes on tap without threading a lambda through
 * MessageList → MessageBubble → ContentPartRenderer → ToolCallDispatcher.
 *
 * Takes the whole [Attachment] because a code-output fallback has no file id, only the sandbox
 * route in its filepath (`FileRepository.downloadAttachment` picks the route).
 *
 * Returns the file's bytes, or `null` on failure / when no host is in scope (previews, tests).
 * Mirrors the [LocalChatMediaViewer] precedent.
 */
val LocalAttachmentDownloader = staticCompositionLocalOf<suspend (Attachment) -> ByteArray?> {
    { null }
}

/**
 * Shows a short message about a file, such as why an expired one can't be opened. Provided by
 * [ChatRoot] and wired to the chat screen's snackbar host.
 */
val LocalFileNotice = staticCompositionLocalOf<(message: String) -> Unit> {
    { message -> Logger.w { "LocalFileNotice not provided; dropping \"$message\"" } }
}
