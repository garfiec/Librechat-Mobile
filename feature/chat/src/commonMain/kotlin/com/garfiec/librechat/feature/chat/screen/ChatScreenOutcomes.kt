package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.components.ForkOptionsBottomSheet
import com.garfiec.librechat.feature.chat.util.copyToClipboard
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * Outcomes every platform's chat screen must act on, in one place.
 *
 * Each `ChatScreen` actual used to consume its own copy, and the iOS one consumed none: the
 * ViewModel never heard a pause or resume, so nothing reconciled on foreground; Share created a
 * public link the user never saw; and Fork and Duplicate did nothing visible. A new outcome belongs
 * here, not in a platform file, so it cannot reach one platform and not the other.
 */
@Composable
internal fun ChatScreenOutcomes(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
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

    val shareLinkUrl by viewModel.shareLinkUrl.collectAsStateWithLifecycle()
    LaunchedEffect(shareLinkUrl) {
        val url = shareLinkUrl ?: return@LaunchedEffect
        copyToClipboard(url, label = "Share Link")
        viewModel.onShareLinkHandled()
        snackbarHostState.showSnackbar("Share link copied to clipboard")
    }

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
