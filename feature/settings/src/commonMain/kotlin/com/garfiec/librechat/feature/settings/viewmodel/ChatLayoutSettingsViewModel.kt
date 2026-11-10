package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/** Backs the Chat appearance page: message layout, bubbles, avatars, text size and the title bar. */
class ChatLayoutSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val uiState: StateFlow<ChatLayoutSettingsUiState> = combine(
        settingsDataStore.chatLayoutStyle,
        settingsDataStore.showBubbles,
        settingsDataStore.showAvatars,
        settingsDataStore.chatFontSize,
        combine(settingsDataStore.chatHeaderContent, settingsDataStore.chatHeaderAlignment, ::Pair),
    ) { layoutStyle, showBubbles, showAvatars, fontSize, (headerContent, headerAlignment) ->
        ChatLayoutSettingsUiState(
            layoutStyle = layoutStyle,
            showBubbles = showBubbles,
            showAvatars = showAvatars,
            fontSize = fontSize,
            headerContent = headerContent,
            headerAlignment = headerAlignment,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatLayoutSettingsUiState())

    fun setLayoutStyle(style: String) {
        viewModelScope.launch { settingsDataStore.setChatLayoutStyle(style) }
    }

    fun setShowBubbles(show: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowBubbles(show) }
    }

    fun setShowAvatars(show: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowAvatars(show) }
    }

    fun setFontSize(size: ChatFontSize) {
        viewModelScope.launch { settingsDataStore.setChatFontSize(size) }
    }

    fun setHeaderContent(content: ChatHeaderContent) {
        viewModelScope.launch { settingsDataStore.setChatHeaderContent(content) }
    }

    fun setHeaderAlignment(alignment: ChatHeaderAlignment) {
        viewModelScope.launch { settingsDataStore.setChatHeaderAlignment(alignment) }
    }
}
