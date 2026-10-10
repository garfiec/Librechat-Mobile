package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * The three attachment entry points offered by the chat tools sheet, ready to invoke.
 * Produced by [rememberChatAttachmentActions].
 */
@Immutable
class ChatAttachmentActions(
    val onAttachFiles: () -> Unit,
    val onTakePhoto: () -> Unit,
    val onPickPhotos: () -> Unit,
)

/**
 * The platform's file picker, camera and photo picker, each handing what the user picked to
 * [onFilesSelect] as the platform references `ChatViewModel.onFilesSelected` takes. Call this
 * **once** high in the chat screen and share the result between the composer's "+" sheet and the
 * pull-up sheet.
 *
 * [filePickerMimeTypes] narrows the file picker to the server's `supportedMimeTypes` allowlist for
 * the active endpoint (`FileUploadConfig.pickerMimeTypes`). Empty means "no restriction" — every
 * case the allowlist can't be represented faithfully resolves to that, and the picker shows
 * everything rather than hiding a file the server would have accepted. iOS does not narrow yet.
 */
@Composable
expect fun rememberChatAttachmentActions(
    onFilesSelect: (List<Any>) -> Unit,
    filePickerMimeTypes: List<String> = emptyList(),
): ChatAttachmentActions
