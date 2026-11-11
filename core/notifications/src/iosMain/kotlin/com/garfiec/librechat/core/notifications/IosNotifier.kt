package com.garfiec.librechat.core.notifications

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.reply_public_body
import com.garfiec.librechat.core.notifications.resources.scheduled_public_body
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/** Each topic's category (what a hidden preview shows) and identifier prefix. */
private enum class Kind(val topic: NotificationTopic, val category: String, val idPrefix: String, val hiddenBody: StringResource) {
    Reply(NotificationTopic.Replies, "reply", "reply.", Res.string.reply_public_body),
    Scheduled(NotificationTopic.ScheduledChats, "scheduled", "scheduled.", Res.string.scheduled_public_body),
}

/** The `userInfo` key holding the link a tap opens; `AppDelegate` in iOSApp.swift reads it by this name. */
private const val NOTIFICATION_LINK_KEY = "link"

class IosNotifier : PlatformNotifier {

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun post(notification: ReplyNotification, link: String, silent: Boolean) =
        post(Kind.Reply, notification.conversationId, notification, link, silent)

    override suspend fun post(notification: ScheduledRunNotification, link: String, silent: Boolean) =
        post(Kind.Scheduled, notification.key, notification, link, silent)

    private suspend fun post(kind: Kind, key: String, notification: AppNotification, link: String, silent: Boolean) {
        registerCategories()
        val content = UNMutableNotificationContent()
        content.setTitle(notification.titleText())
        content.setBody(notification.bodyText())
        content.setThreadIdentifier(key)
        content.setCategoryIdentifier(kind.category)
        content.setUserInfo(mapOf<Any?, Any?>(NOTIFICATION_LINK_KEY to link))
        if (!silent) content.setSound(UNNotificationSound.defaultSound)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = kind.idPrefix + key,
            content = content,
            trigger = null,
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) Logger.w { "Notification not posted: ${error.localizedDescription}" }
        }
    }

    override fun cancel(conversationId: String) {
        center.removeDeliveredNotificationsWithIdentifiers(Kind.entries.map { it.idPrefix + conversationId })
    }

    override fun cancelAll(topic: NotificationTopic?) {
        if (topic == null) {
            center.removeAllDeliveredNotifications()
            return
        }
        val prefixes = Kind.entries.filter { it.topic == topic }.map { it.idPrefix }
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty()
                .mapNotNull { (it as? UNNotification)?.request?.identifier }
                .filter { id -> prefixes.any(id::startsWith) }
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    /** Registered before each post so the hidden-preview text follows the in-app language. */
    private suspend fun registerCategories() {
        val categories = Kind.entries.map { kind ->
            UNNotificationCategory.categoryWithIdentifier(
                identifier = kind.category,
                actions = emptyList<Any>(),
                intentIdentifiers = emptyList<Any>(),
                hiddenPreviewsBodyPlaceholder = getString(kind.hiddenBody),
                options = UNNotificationCategoryOptionNone,
            )
        }
        center.setNotificationCategories(categories.toSet())
    }
}
