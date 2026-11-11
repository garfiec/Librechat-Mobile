package com.garfiec.librechat.core.notifications

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.ask_reply_notifications
import com.garfiec.librechat.core.notifications.resources.ask_reply_notifications_allow
import com.garfiec.librechat.core.notifications.resources.ask_scheduled_notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

/** How long a reply streams before the user is offered notifications for replies. */
private val LONG_REPLY = 10.seconds

/**
 * Offers notifications once, on [snackbarHostState], when a reply has been streaming for
 * [LONG_REPLY] ([streaming] still true): the moment the user may leave before it ends. Allow opens
 * the system prompt; any other ending counts as "Not now".
 */
@Composable
fun ReplyNotificationAsk(streaming: Boolean, snackbarHostState: SnackbarHostState) {
    val offer = rememberNotificationOffer(
        NotificationAskTrigger.LongReply,
        Res.string.ask_reply_notifications,
        snackbarHostState,
    )
    LaunchedEffect(streaming) {
        if (!streaming) return@LaunchedEffect
        delay(LONG_REPLY)
        offer()
    }
}

/** Offers notifications for scheduled chats once, when the Schedules screen opens with schedules [available]. */
@Composable
fun ScheduledChatsNotificationAsk(available: Boolean, snackbarHostState: SnackbarHostState) {
    val offer = rememberNotificationOffer(
        NotificationAskTrigger.SchedulesOpened,
        Res.string.ask_scheduled_notifications,
        snackbarHostState,
    )
    LaunchedEffect(available) {
        if (available) offer()
    }
}

/** Shows [message] with Allow, once per [trigger], when [NotificationAsk.shouldOffer] says to. */
@Composable
private fun rememberNotificationOffer(
    trigger: NotificationAskTrigger,
    message: StringResource,
    snackbarHostState: SnackbarHostState,
): suspend () -> Unit {
    val ask = koinInject<NotificationAsk>()
    val request = rememberNotificationPermissionRequest(onResult = {})
    // Not the caller's effect scope: the moment ending while the snackbar is up must not take it away.
    val scope = rememberCoroutineScope()
    return {
        if (ask.shouldOffer(trigger).first()) {
            ask.answered(trigger)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = getString(message),
                    actionLabel = getString(Res.string.ask_reply_notifications_allow),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) request()
            }
        }
    }
}
