package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId

/** Something the app tells the user about: a reply they left, or a scheduled chat's run. */
sealed interface AppNotification {
    val accountId: AccountId

    /** The chat a tap opens, when there is one. */
    val conversationId: String?
}

/** A reply the user wasn't watching has ended, on [conversationId] of [accountId]. */
data class ReplyNotification(
    override val accountId: AccountId,
    override val conversationId: String,
    /** The conversation's title; null shows a generic one. */
    val title: String?,
    /** Built by [notificationPreviewOf]; blank shows a generic body. */
    val preview: String,
    /** The reply ended in an error. */
    val failed: Boolean,
) : AppNotification

/** What a scheduled chat's run, or the server, did to [scheduleId]. */
enum class ScheduledRunOutcome { Finished, Failed, NeedsApproval, SkippedForBalance, Paused }

data class ScheduledRunNotification(
    override val accountId: AccountId,
    val scheduleId: String,
    /** The schedule's name; blank shows a generic one. */
    val name: String,
    /** The run's chat for a finished run or one awaiting approval; null opens the Schedules list. */
    override val conversationId: String?,
    val outcome: ScheduledRunOutcome,
    /** Built by [notificationPreviewOf] for [ScheduledRunOutcome.Finished]; blank otherwise. */
    val preview: String = "",
) : AppNotification

/** Where an [AppNotification] goes. */
enum class ReplyDelivery { System, InApp, Drop }

/** What [deliveryOf] decides from, read at the moment the reply or run ends. */
data class ReplyDeliveryContext(
    val activeAccountId: AccountId?,
    val foreground: Boolean,
    val openConversationId: String?,
    /** The user's toggle for this kind (Replies, or Scheduled chats). */
    val enabled: Boolean,
    /** The server's `interface.replyNotifications.desktop`; false until its config has loaded. */
    val serverAllows: Boolean,
    /** The OS will show a notification (permission granted, channel not blocked). */
    val systemAllows: Boolean,
)

/**
 * Never another account's (it would open under the wrong account), nothing for the chat on screen,
 * a snackbar while the app is in front, and a system notification only from the background.
 */
fun deliveryOf(notification: AppNotification, context: ReplyDeliveryContext): ReplyDelivery = when {
    notification.accountId != context.activeAccountId -> ReplyDelivery.Drop
    !context.enabled || !context.serverAllows -> ReplyDelivery.Drop
    context.foreground && notification.conversationId != null &&
        context.openConversationId == notification.conversationId -> ReplyDelivery.Drop
    context.foreground -> ReplyDelivery.InApp
    context.systemAllows -> ReplyDelivery.System
    else -> ReplyDelivery.Drop
}
