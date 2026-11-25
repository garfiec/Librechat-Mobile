package com.garfiec.librechat.feature.chat.components

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
    /** The file picker for "Upload to Code Environment"; its picks go to `onCodeFilesSelect`. */
    val onAttachToCode: () -> Unit,
)
