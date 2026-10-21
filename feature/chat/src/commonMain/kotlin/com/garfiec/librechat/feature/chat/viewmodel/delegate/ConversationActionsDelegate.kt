package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareTarget
import com.garfiec.librechat.core.data.share.TargetResolver
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationActionsHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Conversation-level actions of the open chat: rename, delete, archive, duplicate, fork and Share.
 *
 * Share opens the share sheet ([shareLink]); it never creates or copies a link on its own. The
 * sheet shares up to the tail of the branch on screen ([resolveShareTarget]). The ⋮ Share item
 * says when the chat already has a live link: looked up when a chat opens, again after every
 * change to the user's links, and again when the sheet closes.
 */
class ConversationActionsDelegate(
    private val handle: ConversationActionsHandle,
    private val conversationRepository: ConversationRepository,
    private val shareRepository: ShareRepository,
    val shareLink: ShareLinkController,
) {

    /** Bumped when the share sheet closes, so the link-active lookup runs again. */
    private val sheetClosings = MutableStateFlow(0)

    init {
        observeShareLinkActive()
    }

    fun showRenameDialog() {
        handle.update { actions = actions.copy(showRenameDialog = true) }
    }

    fun dismissRenameDialog() {
        handle.update { actions = actions.copy(showRenameDialog = false) }
    }

    fun renameConversation(newTitle: String) {
        val conversationId = handle.state.conversationId ?: return
        handle.update { actions = actions.copy(showRenameDialog = false) }
        handle.scope.launch {
            when (conversationRepository.updateTitle(conversationId, newTitle)) {
                is Result.Success -> {
                    handle.update { conversation = conversation.copy(conversationTitle = newTitle) }
                }
                is Result.Error -> {
                    handle.setError(ChatNotice.RENAME_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showDeleteConfirmation() {
        handle.update { actions = actions.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        handle.update { actions = actions.copy(showDeleteConfirmation = false) }
    }

    fun deleteConversation() {
        val conversationId = handle.state.conversationId ?: return
        handle.update { actions = actions.copy(showDeleteConfirmation = false) }
        handle.scope.launch {
            when (conversationRepository.delete(conversationId)) {
                is Result.Success -> {
                    // Signal navigation back by clearing the conversation
                    handle.update { conversation = conversation.copy(conversationId = null) }
                }
                is Result.Error -> {
                    handle.setError(ChatNotice.DELETE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun archiveConversation() {
        val conversationId = handle.state.conversationId ?: return
        handle.scope.launch {
            when (conversationRepository.archive(conversationId, true)) {
                is Result.Success -> {
                    // Signal navigation back by clearing the conversation
                    handle.update { conversation = conversation.copy(conversationId = null) }
                }
                is Result.Error -> {
                    handle.setError(ChatNotice.ARCHIVE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicateConversation() {
        val conversationId = handle.state.conversationId ?: return
        val title = handle.state.conversationTitle
        handle.scope.launch {
            when (val result = conversationRepository.duplicateConversation(conversationId, title)) {
                is Result.Success -> {
                    handle.update { actions = actions.copy(duplicatedConversationId = result.data.conversationId) }
                }
                is Result.Error -> {
                    handle.setError(ChatNotice.DUPLICATE_FAILED.marker())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun onDuplicatedConversationHandled() {
        handle.update { actions = actions.copy(duplicatedConversationId = null) }
    }

    /** Opens the share sheet on the open chat. Nothing is created until the sheet's Create. */
    fun shareConversation() {
        val conversationId = handle.state.conversationId ?: return
        shareLink.open(conversationId, target = TargetResolver { resolveShareTarget(handle.state) })
    }

    fun closeShareSheet() {
        shareLink.close()
        sheetClosings.value++
    }

    private fun observeShareLinkActive() {
        handle.scope.launch {
            var lookedUpFor: String? = null
            combine(
                handle.stateFlow.map { it.conversationId to it.sharedLinksEnabled }.distinctUntilChanged(),
                shareRepository.revision,
                sheetClosings,
            ) { chat, _, _ -> chat }
                .collectLatest { (conversationId, sharedLinksEnabled) ->
                    // A different chat starts unmarked; a refresh of the same one keeps its mark
                    // until the lookup answers, so the label doesn't flicker.
                    if (conversationId != lookedUpFor) setShareLinkActive(false)
                    lookedUpFor = conversationId
                    if (conversationId == null || !sharedLinksEnabled) {
                        setShareLinkActive(false)
                        return@collectLatest
                    }
                    val result = shareRepository.lookupShareLink(conversationId)
                    if (result is Result.Success && handle.state.conversationId == conversationId) {
                        setShareLinkActive(result.data.success && result.data.shareId != null)
                    }
                }
        }
    }

    private fun setShareLinkActive(active: Boolean) {
        if (handle.state.actions.shareLinkActive == active) return
        handle.update { actions = actions.copy(shareLinkActive = active) }
    }

    fun showForkOptions(messageId: String) {
        handle.update { actions = actions.copy(showForkOptionsForMessageId = messageId) }
    }

    fun dismissForkOptions() {
        handle.update { actions = actions.copy(showForkOptionsForMessageId = null) }
    }

    fun forkFromMessage(messageId: String, option: String, splitAtTarget: Boolean = false) {
        val conversationId = handle.state.conversationId ?: return
        val latestMessageId = handle.state.displayMessages.lastOrNull()?.message?.messageId
        handle.update {
            actions = actions.copy(
                showForkOptionsForMessageId = null,
                isForkInProgress = true,
            )
        }
        handle.scope.launch {
            val result = conversationRepository.forkConversation(
                conversationId = conversationId,
                messageId = messageId,
                option = option,
                splitAtTarget = if (splitAtTarget) true else null,
                latestMessageId = if (splitAtTarget) latestMessageId else null,
            )
            when (result) {
                is Result.Success -> {
                    handle.update {
                        actions = actions.copy(
                            forkedConversationId = result.data.conversationId,
                            isForkInProgress = false,
                        )
                    }
                }
                is Result.Error -> {
                    handle.update {
                        error = result.message ?: "Could not fork conversation"
                        actions = actions.copy(isForkInProgress = false)
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun onForkedConversationHandled() {
        handle.update { actions = actions.copy(forkedConversationId = null) }
    }
}

/**
 * The message a link from the open chat shares up to: the tail of the branch on screen, as the
 * web share dialog does (scripts/mirrors.json `share-target-branch-tail`).
 *
 * Web confirms the tail is saved with `GET /api/messages/:conversationId/:messageId` first. The app
 * has no such call, so it reads the screen instead: while a reply is streaming, or a just-sent
 * message is still waiting for the server's copy, the tail isn't saved. A tail that slips past
 * this is rejected by the server (`TARGET_MESSAGE_NOT_FOUND`), and the controller re-resolves and
 * retries once before saying so.
 */
internal fun resolveShareTarget(state: ChatUiState): ShareTarget {
    val tail = state.displayMessages.lastOrNull()?.message ?: return ShareTarget.NoMessages
    return if (state.isStreaming || state.pendingResumeUserMessage != null) {
        ShareTarget.NotSaved
    } else {
        ShareTarget.Saved(tail.messageId)
    }
}
