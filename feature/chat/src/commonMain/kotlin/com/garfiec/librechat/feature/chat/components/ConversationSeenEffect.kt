package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ConversationSeenViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Marks the open chat's newest reply seen (v0.8.9-rc1) once it has rendered, sits near the bottom
 * of the list, the app is in front and nothing streams. Opening a chat alone sends nothing.
 */
@Composable
internal fun ConversationSeenEffect(
    conversationId: String?,
    displayMessages: List<MessageNode>,
    isStreaming: Boolean,
    /** The list rests near its tail, as its own tail follower measures it. */
    nearBottom: Boolean,
    viewModel: ConversationSeenViewModel = koinViewModel(),
) {
    LaunchedEffect(viewModel, conversationId) { viewModel.bind(conversationId) }
    val target by viewModel.target.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val inForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(viewModel, inForeground) { if (inForeground) viewModel.onForeground() }
    val replyRendered = remember(target, displayMessages) {
        val current = target ?: return@remember false
        val messageId = current.messageId
        displayMessages.isNotEmpty() &&
            (messageId == null || displayMessages.any { it.message.messageId == messageId })
    }
    LaunchedEffect(viewModel, target, replyRendered, nearBottom, inForeground, isStreaming) {
        viewModel.maybeMarkSeen(target, replyRendered, nearBottom, inForeground, isStreaming)
    }
}
