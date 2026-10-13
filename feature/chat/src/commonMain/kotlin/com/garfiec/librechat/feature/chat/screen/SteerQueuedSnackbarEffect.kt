package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.steer_queued_as_follow_up
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * Collects [ChatViewModel.steersQueuedAsFollowUp] and says that a steer went to the queue instead
 * of into the reply. One wording covers any count, like [QueuedMessageDroppedSnackbarEffect].
 */
@Composable
fun SteerQueuedSnackbarEffect(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
) {
    SignalSnackbarEffect(
        signal = viewModel.steersQueuedAsFollowUp,
        message = Res.string.steer_queued_as_follow_up,
        snackbarHostState = snackbarHostState,
    )
}
