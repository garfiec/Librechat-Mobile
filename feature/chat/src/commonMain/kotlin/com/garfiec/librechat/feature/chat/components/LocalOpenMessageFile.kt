package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.FileReference

/**
 * Opens a non-image file attached to a message (`message.files`). Null, the default, leaves those
 * chips untappable, as they are in the chat; the shared-chat viewer provides it, since a viewer has
 * no other way to read a shared file.
 */
val LocalOpenMessageFile = staticCompositionLocalOf<((FileReference) -> Unit)?> { null }
