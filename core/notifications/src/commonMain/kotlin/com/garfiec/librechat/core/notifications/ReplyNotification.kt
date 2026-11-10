package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId

/** A reply the user wasn't watching has ended, on [conversationId] of [accountId]. */
data class ReplyNotification(
    val accountId: AccountId,
    val conversationId: String,
    /** The conversation's title; null shows a generic one. */
    val title: String?,
    /** Built by [notificationPreviewOf]; blank shows a generic body. */
    val preview: String,
    /** The reply ended in an error. */
    val failed: Boolean,
)

/** Where a [ReplyNotification] goes. */
enum class ReplyDelivery { System, InApp, Drop }

/** What [replyDelivery] decides from, read at the moment the reply ends. */
data class ReplyDeliveryContext(
    val activeAccountId: AccountId?,
    val foreground: Boolean,
    val openConversationId: String?,
    /** The user's Replies toggle. */
    val enabled: Boolean,
    /** The server's `interface.replyNotifications.desktop`; false until its config has loaded. */
    val serverAllows: Boolean,
    /** The OS will show a notification (permission granted, channel not blocked). */
    val systemAllows: Boolean,
)

/**
 * Never another account's reply (it would open under the wrong account), nothing for the chat on
 * screen, a snackbar while the app is in front, and a system notification only from the background.
 */
fun replyDelivery(notification: ReplyNotification, context: ReplyDeliveryContext): ReplyDelivery = when {
    notification.accountId != context.activeAccountId -> ReplyDelivery.Drop
    !context.enabled || !context.serverAllows -> ReplyDelivery.Drop
    context.foreground && context.openConversationId == notification.conversationId -> ReplyDelivery.Drop
    context.foreground -> ReplyDelivery.InApp
    context.systemAllows -> ReplyDelivery.System
    else -> ReplyDelivery.Drop
}
