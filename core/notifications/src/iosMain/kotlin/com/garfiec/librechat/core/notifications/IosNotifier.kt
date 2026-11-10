package com.garfiec.librechat.core.notifications

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.reply_body_failed
import com.garfiec.librechat.core.notifications.resources.reply_body_finished
import com.garfiec.librechat.core.notifications.resources.reply_public_body
import com.garfiec.librechat.core.notifications.resources.reply_title_fallback
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

private const val REPLY_CATEGORY = "reply"
private const val REPLY_ID_PREFIX = "reply."

/** The `userInfo` key holding the link a tap opens; `AppDelegate` in iOSApp.swift reads it by this name. */
private const val NOTIFICATION_LINK_KEY = "link"

class IosNotifier : PlatformNotifier {

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun post(notification: ReplyNotification, link: String, silent: Boolean) {
        registerCategory()
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title?.takeIf { it.isNotBlank() } ?: getString(Res.string.reply_title_fallback))
        content.setBody(
            when {
                notification.failed -> getString(Res.string.reply_body_failed)
                notification.preview.isNotBlank() -> notification.preview
                else -> getString(Res.string.reply_body_finished)
            },
        )
        content.setThreadIdentifier(notification.conversationId)
        content.setCategoryIdentifier(REPLY_CATEGORY)
        content.setUserInfo(mapOf<Any?, Any?>(NOTIFICATION_LINK_KEY to link))
        if (!silent) content.setSound(UNNotificationSound.defaultSound)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = REPLY_ID_PREFIX + notification.conversationId,
            content = content,
            trigger = null,
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) Logger.w { "Reply notification not posted: ${error.localizedDescription}" }
        }
    }

    override fun cancel(conversationId: String) {
        center.removeDeliveredNotificationsWithIdentifiers(listOf(REPLY_ID_PREFIX + conversationId))
    }

    override fun cancelAll() {
        center.removeAllDeliveredNotifications()
    }

    /**
     * The `reply` category carries what a hidden preview shows ("Show Previews: When Unlocked").
     * Registered before each post so it follows the in-app language.
     */
    private suspend fun registerCategory() {
        val category = UNNotificationCategory.categoryWithIdentifier(
            identifier = REPLY_CATEGORY,
            actions = emptyList<Any>(),
            intentIdentifiers = emptyList<Any>(),
            hiddenPreviewsBodyPlaceholder = getString(Res.string.reply_public_body),
            options = UNNotificationCategoryOptionNone,
        )
        center.setNotificationCategories(setOf(category))
    }
}
