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
import com.garfiec.librechat.core.notifications.resources.reply_title_fallback
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

/** Shows each reply that ended while the app was on another screen, with Open to go to its chat. */
@Composable
fun ReplySnackbars(snackbarHostState: SnackbarHostState, onOpen: (conversationId: String) -> Unit) {
    val notifications = koinInject<ReplyNotifications>()
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(notifications, snackbarHostState) {
        notifications.inApp.collect { reply ->
            val title = reply.title?.takeIf { it.isNotBlank() } ?: getString(Res.string.reply_title_fallback)
            val message = getString(
                if (reply.failed) Res.string.reply_snackbar_failed else Res.string.reply_snackbar_finished,
                title,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = getString(Res.string.reply_snackbar_open),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) currentOnOpen(reply.conversationId)
        }
    }
}
