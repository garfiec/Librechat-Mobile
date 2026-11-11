package com.garfiec.librechat.core.notifications

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceSpec
import kotlinx.coroutines.flow.StateFlow

/** The kinds of notification, each with its own toggle (and, on Android, its own channel). */
enum class NotificationTopic { Replies, ScheduledChats }

val NotificationTopic.toggle: PreferenceSpec<Boolean, Boolean>
    get() = when (this) {
        NotificationTopic.Replies -> AppPreferences.ReplyNotifications
        NotificationTopic.ScheduledChats -> AppPreferences.ScheduledChatNotifications
    }

/**
 * Posts and removes the app's system notifications. One per conversation, or per schedule when
 * there is no chat: a newer one replaces it.
 */
interface PlatformNotifier {
    /** [link] opens the conversation (`librechat://conversation/<id>?account=…`). */
    suspend fun post(notification: ReplyNotification, link: String, silent: Boolean)

    /** [link] opens the run's conversation, or the Schedules list. */
    suspend fun post(notification: ScheduledRunNotification, link: String, silent: Boolean)

    /** Removes what was posted for [conversationId], of either topic. */
    fun cancel(conversationId: String)

    /** Removes every notification of [topic], or of every topic when null. */
    fun cancelAll(topic: NotificationTopic? = null)
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

    /** Whether a system notification of [topic] would show; Android can turn off one topic's channel. */
    fun allows(topic: NotificationTopic): Boolean = status.value == NotificationPermissionStatus.Allowed

    /** The app's notification settings: Android's per-app page, iOS's app page in Settings. */
    fun openSystemSettings()
}

/**
 * Shows the system permission prompt and reports whether notifications are now allowed. On Android
 * below 13 there is no prompt, so it opens the system settings instead and reports false.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (allowed: Boolean) -> Unit): () -> Unit
