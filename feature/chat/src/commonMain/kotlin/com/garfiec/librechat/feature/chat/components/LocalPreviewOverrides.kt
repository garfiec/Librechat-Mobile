package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.compositionLocalOf
import com.garfiec.librechat.core.model.response.FilePreviewResponse

/**
 * Deferred-preview results the ViewModel learned after an attachment was recorded, keyed by
 * message and `file_id` (`MessagesState.previewOverrides`), supplied by [ChatRoot]. A local rather than a
 * parameter because the bubble is expect/actual and both platforms' bubbles reach the one place
 * that applies it, `MessageContentAndActions`; the streaming lists apply it to their own list.
 *
 * Not static: it changes once per resolved file, and only the readers should recompose then.
 */
internal val LocalPreviewOverrides = compositionLocalOf<Map<String, FilePreviewResponse>> { emptyMap() }
