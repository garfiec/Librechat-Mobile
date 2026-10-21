package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garfiec.librechat.feature.chat.components.ForkOptionsBottomSheet
import com.garfiec.librechat.feature.chat.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * The ViewModel outcomes the chat screen acts on: lifecycle pause/resume, the share sheet, Fork and
 * Duplicate. Each is easy to drop silently (the ViewModel never hears a resume, a fork is made the
 * user never lands in), which is why they sit together here rather than in the screen.
 */
@Composable
internal fun ChatScreenOutcomes(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
    onNavigateToConversation: ((String) -> Unit)?,
) {
    // The effects below outlive a recomposition, so they read the navigation callbacks through
    // these rather than capturing whichever pair was current when they launched.
    val navigate by rememberUpdatedState(onNavigateToConversation)
    val start by rememberUpdatedState(onConversationStart)

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(uiState.forkedConversationId) {
        val forkId = uiState.forkedConversationId ?: return@LaunchedEffect
        viewModel.onForkedConversationHandled()
        openConversation(forkId, navigate, start)
    }

    LaunchedEffect(uiState.duplicatedConversationId) {
        val dupId = uiState.duplicatedConversationId ?: return@LaunchedEffect
        viewModel.onDuplicatedConversationHandled()
        openConversation(dupId, navigate, start)
    }

    // Copy feedback and errors render inside the sheet: a snackbar would sit behind its scrim.
    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)

    val forkMessageId = uiState.showForkOptionsForMessageId
    if (forkMessageId != null) {
        ForkOptionsBottomSheet(
            onDismiss = viewModel::dismissForkOptions,
            onFork = { option, splitAtTarget ->
                viewModel.forkFromMessage(
                    messageId = forkMessageId,
                    option = option,
                    splitAtTarget = splitAtTarget,
                )
            },
        )
    }
}

/** Forks and duplicates are always real conversations, never temporary ones. */
private fun openConversation(
    conversationId: String,
    onNavigateToConversation: ((String) -> Unit)?,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
) {
    if (onNavigateToConversation != null) {
        onNavigateToConversation(conversationId)
    } else {
        onConversationStart?.invoke(conversationId, false)
    }
}
