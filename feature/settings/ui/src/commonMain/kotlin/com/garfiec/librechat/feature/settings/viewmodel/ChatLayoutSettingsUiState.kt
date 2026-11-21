package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.preferences.ChatFontSize
import com.garfiec.librechat.core.model.preferences.ChatHeaderAlignment
import com.garfiec.librechat.core.model.preferences.ChatHeaderContent

@Immutable
data class ChatLayoutSettingsUiState(
    /** [ChatLayoutConstants.THREAD] or [ChatLayoutConstants.TWO_SIDED]. */
    val layoutStyle: String = ChatLayoutConstants.THREAD,
    val showBubbles: Boolean = false,
    val showAvatars: Boolean = true,
    val fontSize: ChatFontSize = ChatFontSize.MEDIUM,
    val headerContent: ChatHeaderContent = ChatHeaderContent.TITLE,
    val headerAlignment: ChatHeaderAlignment = ChatHeaderAlignment.LEFT,
)
