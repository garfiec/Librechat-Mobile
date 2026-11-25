package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.notifications.NotificationPermissionStatus

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
    /** The page's master switch and what the Settings home row says. */
    val on: Boolean
        get() = permission == NotificationPermissionStatus.Allowed && serverAllowsReplies &&
            (repliesEnabled || (schedulesAvailable && scheduledEnabled))
}
