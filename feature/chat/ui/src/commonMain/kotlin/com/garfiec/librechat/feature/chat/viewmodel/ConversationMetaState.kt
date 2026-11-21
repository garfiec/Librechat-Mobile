package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * Identity and top-level metadata of the current conversation. Written by [ChatViewModel],
 * StreamingManagerDelegate, SendCompletionDelegate, ConversationActionsDelegate,
 * ComparisonModeDelegate and MessageTreeDelegate; [sharedLinksEnabled] by ChatConfigDelegate and
 * [conversationTitle] also by ConversationLoadDelegate.
 */
@Immutable
data class ConversationMetaState(
    val conversationId: String? = null,
    val conversationTitle: String? = null,
    val isTemporaryChat: Boolean = false,
    /**
     * The user renamed this chat here. A generated title (a mid-stream `title` frame or the
     * post-run `gen_title` answer) that lands afterwards is not applied over it; the server keeps
     * the manual title too (v0.8.9-rc1 title ownership).
     */
    val titleSetByUser: Boolean = false,
    val sharedLinksEnabled: Boolean = false,
    /** Set at StreamEvent.Created when a new conversation's conversationId becomes available.
     *  The UI navigates to Chat(id) and then clears this via [ChatViewModel.onPendingNavigationHandled],
     *  which also resets this ViewModel to a clean landing state. */
    val pendingNavigationConversationId: String? = null,
)
