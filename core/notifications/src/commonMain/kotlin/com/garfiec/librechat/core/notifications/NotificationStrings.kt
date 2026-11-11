package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.reply_body_failed
import com.garfiec.librechat.core.notifications.resources.reply_body_finished
import com.garfiec.librechat.core.notifications.resources.reply_title_fallback
import com.garfiec.librechat.core.notifications.resources.scheduled_body_failed
import com.garfiec.librechat.core.notifications.resources.scheduled_body_finished
import com.garfiec.librechat.core.notifications.resources.scheduled_body_needs_approval
import com.garfiec.librechat.core.notifications.resources.scheduled_body_paused
import com.garfiec.librechat.core.notifications.resources.scheduled_body_skipped_balance
import com.garfiec.librechat.core.notifications.resources.scheduled_title_fallback
import org.jetbrains.compose.resources.getString

// Resolved outside composition: a notification is posted from a background job too.

suspend fun AppNotification.titleText(): String = when (this) {
    is ReplyNotification -> title?.takeIf { it.isNotBlank() } ?: getString(Res.string.reply_title_fallback)
    is ScheduledRunNotification -> name.takeIf { it.isNotBlank() } ?: getString(Res.string.scheduled_title_fallback)
}

suspend fun AppNotification.bodyText(): String = when (this) {
    is ReplyNotification -> when {
        failed -> getString(Res.string.reply_body_failed)
        preview.isNotBlank() -> preview
        else -> getString(Res.string.reply_body_finished)
    }
    is ScheduledRunNotification -> when (outcome) {
        ScheduledRunOutcome.Finished -> preview.ifBlank { getString(Res.string.scheduled_body_finished) }
        ScheduledRunOutcome.Failed -> getString(Res.string.scheduled_body_failed)
        ScheduledRunOutcome.NeedsApproval -> getString(Res.string.scheduled_body_needs_approval)
        ScheduledRunOutcome.SkippedForBalance -> getString(Res.string.scheduled_body_skipped_balance)
        ScheduledRunOutcome.Paused -> getString(Res.string.scheduled_body_paused)
    }
}

/** Replaces the notification for the same chat, or for the same schedule when there is no chat. */
val ScheduledRunNotification.key: String get() = conversationId ?: scheduleId
