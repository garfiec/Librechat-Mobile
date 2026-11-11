package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.isSchedulesEnabled
import com.garfiec.librechat.core.model.config.replyNotificationsAllowed
import com.garfiec.librechat.core.notifications.NotificationPermission
import com.garfiec.librechat.core.notifications.NotificationPermissionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class NotificationSettingsUiState(
    val permission: NotificationPermissionStatus = NotificationPermissionStatus.NotAsked,
    val repliesEnabled: Boolean = true,
    /** The server's `interface.replyNotifications.desktop`; allowed until its config says otherwise. */
    val serverAllowsReplies: Boolean = true,
    val scheduledEnabled: Boolean = true,
    /** The server offers scheduled chats; the Scheduled chats row is shown only then. */
    val schedulesAvailable: Boolean = false,
) {
    /** What the Settings home row says. */
    val on: Boolean
        get() = permission == NotificationPermissionStatus.Allowed && serverAllowsReplies &&
            (repliesEnabled || (schedulesAvailable && scheduledEnabled))
}

class NotificationSettingsViewModel(
    private val permission: NotificationPermission,
    private val preferences: PreferenceStore,
    configRepository: ConfigRepository,
) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        permission.status,
        preferences.observe(AppPreferences.ReplyNotifications),
        preferences.observe(AppPreferences.ScheduledChatNotifications),
        configRepository.startupConfig,
    ) { status, replies, scheduled, config ->
        NotificationSettingsUiState(
            permission = status,
            repliesEnabled = replies,
            serverAllowsReplies = config == null || config.interfaceConfig.replyNotificationsAllowed,
            scheduledEnabled = scheduled,
            schedulesAvailable = isSchedulesEnabled(config?.interfaceConfig?.schedules),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationSettingsUiState())

    fun setRepliesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(AppPreferences.ReplyNotifications, enabled) }
    }

    fun setScheduledEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.set(AppPreferences.ScheduledChatNotifications, enabled) }
    }

    fun openSystemSettings() = permission.openSystemSettings()
}
