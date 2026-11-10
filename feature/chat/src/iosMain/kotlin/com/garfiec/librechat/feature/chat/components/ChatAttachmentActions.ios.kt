package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.ui.media.openPhotoPicker
import com.garfiec.librechat.feature.chat.util.openCamera
import com.garfiec.librechat.feature.chat.util.openDocumentPicker
import com.garfiec.librechat.feature.chat.util.photoPickerAttachment

/** The native iOS pickers. [filePickerMimeTypes] is not applied: the document picker shows every type. */
@Composable
actual fun rememberChatAttachmentActions(
    onFilesSelect: (List<Any>) -> Unit,
    @Suppress("UnusedParameter") filePickerMimeTypes: List<String>,
    onCodeFilesSelect: (List<Any>) -> Unit,
): ChatAttachmentActions {
    val currentOnFilesSelect by rememberUpdatedState(onFilesSelect)
    val currentOnCodeFilesSelect by rememberUpdatedState(onCodeFilesSelect)
    return remember {
        val deliver: (List<Any>) -> Unit = { files -> if (files.isNotEmpty()) currentOnFilesSelect(files) }
        val deliverToCode: (List<Any>) -> Unit = { files -> if (files.isNotEmpty()) currentOnCodeFilesSelect(files) }
        ChatAttachmentActions(
            onAttachFiles = { openDocumentPicker(deliver) },
            onTakePhoto = { openCamera(deliver) },
            onPickPhotos = { openPhotoPicker(selectionLimit = 10, decode = ::photoPickerAttachment, onResult = deliver) },
            onAttachToCode = { openDocumentPicker(deliverToCode) },
        )
    }
}
