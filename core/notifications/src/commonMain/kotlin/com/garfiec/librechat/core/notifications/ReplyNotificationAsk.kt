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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val ask = koinInject<NotificationAsk>()
    val request = rememberNotificationPermissionRequest(onResult = {})
    // Not the effect's scope: the reply ending while the snackbar is up must not take it away.
    val scope = rememberCoroutineScope()
    LaunchedEffect(streaming) {
        if (!streaming) return@LaunchedEffect
        delay(LONG_REPLY)
        if (!ask.shouldOffer(NotificationAskTrigger.LongReply).first()) return@LaunchedEffect
        ask.answered(NotificationAskTrigger.LongReply)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.ask_reply_notifications),
                actionLabel = getString(Res.string.ask_reply_notifications_allow),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) request()
        }
    }
}
