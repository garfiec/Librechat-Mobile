package com.garfiec.librechat.core.notifications

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/** Posts and removes the app's system notifications. One per conversation: a newer one replaces it. */
interface PlatformNotifier {
    /** [link] opens the conversation (`librechat://conversation/<id>?account=…`). */
    suspend fun post(notification: ReplyNotification, link: String, silent: Boolean)

    fun cancel(conversationId: String)

    fun cancelAll()
}

enum class NotificationPermissionStatus {
    /** Notifications are shown (and, on Android, the Replies channel isn't blocked). */
    Allowed,

    /** Not shown, and the app may still ask. */
    NotAsked,

    /** Not shown, and only the system settings can change that. */
    Off,
}

interface NotificationPermission {
    /** Re-read whenever the app comes to the front: the user changes it in the system settings. */
    val status: StateFlow<NotificationPermissionStatus>

    fun refresh()

    /** The app's notification settings: Android's per-app page, iOS's app page in Settings. */
    fun openSystemSettings()
}

/**
 * Shows the system permission prompt and reports whether notifications are now allowed. On Android
 * below 13 there is no prompt, so it opens the system settings instead and reports false.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (allowed: Boolean) -> Unit): () -> Unit
