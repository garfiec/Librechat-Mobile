package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * Dialog / in-flight state for conversation-level actions (rename, delete, duplicate, fork, share).
 * Owned by [com.garfiec.librechat.feature.chat.viewmodel.delegate.ConversationActionsDelegate].
 */
@Immutable
data class ConversationActionsState(
    val showRenameDialog: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val duplicatedConversationId: String? = null,
    val showForkOptionsForMessageId: String? = null,
    val isForkInProgress: Boolean = false,
    val forkedConversationId: String? = null,
    /** The open chat has a live share link, so the ⋮ Share item reads "Share · Link active". */
    val shareLinkActive: Boolean = false,
)
