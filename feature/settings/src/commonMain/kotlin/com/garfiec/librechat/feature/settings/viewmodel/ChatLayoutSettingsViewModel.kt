package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ChatLayoutConstants
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
)

/** Backs the Chat layout page: how messages are laid out, and whether they get bubbles and avatars. */
class ChatLayoutSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val uiState: StateFlow<ChatLayoutSettingsUiState> = combine(
        settingsDataStore.chatLayoutStyle,
        settingsDataStore.showBubbles,
        settingsDataStore.showAvatars,
    ) { layoutStyle, showBubbles, showAvatars ->
        ChatLayoutSettingsUiState(layoutStyle = layoutStyle, showBubbles = showBubbles, showAvatars = showAvatars)
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
}
