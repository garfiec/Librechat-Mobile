package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.FileReference

/**
 * Overrides how a non-image file attached to a message (`message.files`) opens. Null, the default,
 * opens it as a tool's file opens ([ToolFileActions.openToolFile]): a PDF in the native viewer,
 * anything else in the file-actions sheet. (A message file carries no extracted text, so it never
 * opens in the artifact panel.) The shared-chat viewer provides it, since a shared file downloads
 * through the share's route.
 */
val LocalOpenMessageFile = staticCompositionLocalOf<((FileReference) -> Unit)?> { null }
