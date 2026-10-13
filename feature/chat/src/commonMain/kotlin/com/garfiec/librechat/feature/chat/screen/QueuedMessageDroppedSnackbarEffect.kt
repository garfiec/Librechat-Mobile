package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.queued_messages_dropped_account_switch
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * Collects [ChatViewModel.queuedMessagesDropped] and surfaces a snackbar when queued follow-up
 * messages were discarded on drain because they were composed under an account the user has since
 * switched away from. Without this the drop would be silent — the user would just never see their
 * queued message send. The count is carried on the signal for logic but the message stays generic
 * (no per-locale plural handling needed).
 */
@Composable
fun QueuedMessageDroppedSnackbarEffect(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
) {
    SignalSnackbarEffect(
        signal = viewModel.queuedMessagesDropped,
        message = Res.string.queued_messages_dropped_account_switch,
        snackbarHostState = snackbarHostState,
    )
}
