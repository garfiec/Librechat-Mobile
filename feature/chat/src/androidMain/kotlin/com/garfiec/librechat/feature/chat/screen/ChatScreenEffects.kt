package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.garfiec.librechat.feature.chat.components.localizedStreamError
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * Hosts the chat screen's one-shot side effects so [ChatScreen] reads as layout.
 * Covers: new-conversation navigation handoff, error snackbars, the provider-key error
 * snackbar, and back-navigation after a delete/archive clears the conversation. Share, fork,
 * duplicate and lifecycle live in [ChatScreenOutcomes], shared with iOS.
 */
@Composable
internal fun ChatScreenEffects(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
    onNavigateToConversation: ((String) -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    // When a new conversation starts, navigate to Chat(conversationId) immediately
    // (at StreamEvent.Created) so the NewChat landing page stays clean in the back
    // stack. The new ChatViewModel at Chat(id) will resume the active stream.
    // onPendingNavigationHandled() resets this ViewModel to a fresh landing state.
    LaunchedEffect(uiState.pendingNavigationConversationId) {
        val pendingId = uiState.pendingNavigationConversationId
        if (pendingId != null && onConversationStart != null) {
            // Carry temp-ness onto the Chat(id) route so the new (and any process-death-restored)
            // VM stays temp-aware and never persists the server-hidden conversation to Room.
            onConversationStart(pendingId, uiState.isTemporaryChat)
            viewModel.onPendingNavigationHandled()
        }
    }

    val errorMessage = uiState.error?.let { localizedStreamError(it) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = "Dismiss",
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


    // Navigate back after delete/archive (conversationId becomes null)
    var hadConversation by remember { mutableStateOf(uiState.conversationId != null) }
    LaunchedEffect(uiState.conversationId) {
        if (hadConversation && uiState.conversationId == null) {
            onNavigateBack?.invoke()
        }
        hadConversation = uiState.conversationId != null
    }
}
