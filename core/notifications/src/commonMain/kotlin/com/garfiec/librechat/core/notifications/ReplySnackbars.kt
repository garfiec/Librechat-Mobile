package com.garfiec.librechat.core.notifications

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.reply_snackbar_failed
import com.garfiec.librechat.core.notifications.resources.reply_snackbar_finished
import com.garfiec.librechat.core.notifications.resources.reply_snackbar_open
import com.garfiec.librechat.core.notifications.resources.scheduled_snackbar
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

/**
 * Shows each reply or scheduled run that ended while the app was on another screen, with Open to
 * go to its chat ([onOpenChat]), or to the Schedules list when there is none ([onOpenSchedules]).
 */
@Composable
fun ReplySnackbars(
    snackbarHostState: SnackbarHostState,
    onOpenChat: (conversationId: String) -> Unit,
    onOpenSchedules: () -> Unit,
) {
    val notifications = koinInject<ReplyNotifications>()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnOpenSchedules by rememberUpdatedState(onOpenSchedules)
    LaunchedEffect(notifications, snackbarHostState) {
        notifications.inApp.collect { notification ->
            val title = notification.titleText()
            val message = when (notification) {
                is ReplyNotification -> getString(
                    if (notification.failed) Res.string.reply_snackbar_failed else Res.string.reply_snackbar_finished,
                    title,
                )
                is ScheduledRunNotification -> getString(Res.string.scheduled_snackbar, title, notification.bodyText())
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = getString(Res.string.reply_snackbar_open),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                notification.conversationId?.let(currentOnOpenChat) ?: currentOnOpenSchedules()
            }
        }
    }
}
