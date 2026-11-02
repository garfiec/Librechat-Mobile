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
    /** What to say once [forkedConversationId] opens; set only by "Branch without files". */
    val forkNotice: ForkNotice? = null,
    val shareLinkActive: Boolean = false,
)

/** The confirmation a "Branch without files" copy opens with. */
enum class ForkNotice {
    /** Copied; the failed turn had no text to stage. */
    COPIED_WITHOUT_FILES,

    /** Copied, with the failed turn's text waiting in the copy's composer. */
    COPIED_WITHOUT_FILES_DRAFT,
}
