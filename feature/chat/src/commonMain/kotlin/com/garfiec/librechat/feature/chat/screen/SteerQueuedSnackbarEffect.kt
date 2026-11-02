package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.steer_interrupt_failed
import com.garfiec.librechat.feature.chat.resources.steer_interrupt_unavailable
import com.garfiec.librechat.feature.chat.resources.steer_interrupt_unconfirmed
import com.garfiec.librechat.feature.chat.resources.steer_queued_as_follow_up
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.SteerNotice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Collects [ChatViewModel.steersQueuedAsFollowUp] and says that a steer went to the queue instead
 * of into the reply. One wording covers any count, like [QueuedMessageDroppedSnackbarEffect].
 *
 * Also collects [ChatViewModel.steerNotices]: an interrupt the run couldn't take, or an "Interrupt
 * now" that was refused or never confirmed. The message is still on its way in every case, so these
 * are polite snackbars, never the error channel.
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
    LaunchedEffect(viewModel) {
        viewModel.steerNotices.collect { notice ->
            snackbarHostState.showSnackbar(message = getString(notice.message))
        }
    }
}

internal val SteerNotice.message: StringResource
    get() = when (this) {
        SteerNotice.INTERRUPT_UNAVAILABLE -> Res.string.steer_interrupt_unavailable
        SteerNotice.ARM_FAILED -> Res.string.steer_interrupt_failed
        SteerNotice.ARM_UNCONFIRMED -> Res.string.steer_interrupt_unconfirmed
    }
