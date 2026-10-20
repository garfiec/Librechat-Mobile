package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.ui.resources.snackbar_dismiss
import com.garfiec.librechat.feature.chat.components.localizedStreamError
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.compare_pick_to_send
import com.garfiec.librechat.feature.chat.resources.jump_branch_switched
import com.garfiec.librechat.feature.chat.resources.jump_message_not_found
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

/**
 * Hosts the chat screen's one-shot side effects so [ChatScreen] reads as layout.
 * Covers: new-conversation navigation handoff, error snackbars, the provider-key error
 * snackbar, and back-navigation after a delete/archive clears the conversation. Share, fork,
 * duplicate and lifecycle live in [ChatScreenOutcomes].
 */
// debt — ViewModelForwarding: screen split across files; state not hoisted yet
@Suppress("ViewModelForwarding")
@Composable
internal fun ChatScreenEffects(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val currentOnConversationStart by rememberUpdatedState(onConversationStart)
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    // When a new conversation starts, navigate to Chat(conversationId) immediately
    // (at StreamEvent.Created) so the NewChat landing page stays clean in the back
    // stack. The new ChatViewModel at Chat(id) will resume the active stream.
    // onPendingNavigationHandled() resets this ViewModel to a fresh landing state.
    LaunchedEffect(uiState.pendingNavigationConversationId) {
        val pendingId = uiState.pendingNavigationConversationId
        val onStart = currentOnConversationStart
        if (pendingId != null && onStart != null) {
            // Carry temp-ness onto the Chat(id) route so the new (and any process-death-restored)
            // VM stays temp-aware and never persists the server-hidden conversation to Room.
            onStart(pendingId, uiState.isTemporaryChat)
            viewModel.onPendingNavigationHandled()
        }
    }

    val errorMessage = uiState.error?.let { localizedStreamError(it) }
    val dismissLabel = stringResource(CoreUiRes.string.snackbar_dismiss)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Long,
            )
            viewModel.dismissError()
        }
    }

    UserKeyErrorSnackbarEffect(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
    )

    QueuedMessageDroppedSnackbarEffect(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )

    SteerQueuedSnackbarEffect(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )

    // A send (voice auto-send) arrived during Compare setup: it waits for model B.
    SignalSnackbarEffect(
        signal = viewModel.sendHeldForCompareSetup,
        message = Res.string.compare_pick_to_send,
        snackbarHostState = snackbarHostState,
    )

    // A search hit's message wasn't in the chat (deleted, or on a branch the server no longer has).
    SignalSnackbarEffect(
        signal = viewModel.messageNotFound,
        message = Res.string.jump_message_not_found,
        snackbarHostState = snackbarHostState,
    )

    // A search hit lived on another branch: say the chat now shows that one.
    LaunchedEffect(viewModel) {
        viewModel.jumpBranchSwitched.collect { switch ->
            snackbarHostState.showSnackbar(message = getString(Res.string.jump_branch_switched, switch.index, switch.count))
        }
    }

    // Navigate back after delete/archive (conversationId becomes null)
    var hadConversation by remember { mutableStateOf(uiState.conversationId != null) }
    LaunchedEffect(uiState.conversationId) {
        if (hadConversation && uiState.conversationId == null) {
            currentOnNavigateBack?.invoke()
        }
        hadConversation = uiState.conversationId != null
    }
}
