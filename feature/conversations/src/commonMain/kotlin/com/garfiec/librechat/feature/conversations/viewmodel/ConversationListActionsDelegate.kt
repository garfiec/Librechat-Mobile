package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.favorite_update_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Shared conversation row-action logic (favorite/rename/archive/delete/share/duplicate/export)
 * used by both [ConversationListViewModel] (Room-backed) and [ProjectChatsViewModel] (network-direct).
 *
 * Actions emit one-shot UI signals through [events] and run on the host VM's [scope]. After a
 * mutation that the host must reflect, [onMutated] is invoked: the Room-backed list passes a no-op
 * (its observe-Flow already re-emits), while the network-direct project list passes its `refresh`.
 */
class ConversationListActionsDelegate(
    private val scope: CoroutineScope,
    private val events: MutableSharedFlow<ConversationListEvent>,
    private val conversationRepository: ConversationRepository,
    private val tagRepository: TagRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    private val conversationExporter: ConversationExporter,
    private val onMutated: suspend () -> Unit,
) {

    /**
     * The share sheet. Share from a list shares the conversation's newest branch: there is no
     * branch on screen to target.
     */
    val shareLink: ShareLinkController = shareLinkControllerFactory.create(scope)

    fun toggleFavorite(conversation: Conversation) {
        val id = conversation.conversationId ?: return
        scope.launch {
            when (tagRepository.toggleFavorite(id, conversation.tags)) {
                is Result.Error -> events.emit(ConversationListEvent.ShowErrorRes(Res.string.favorite_update_failed))
                else -> onMutated()
            }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        scope.launch {
            when (conversationRepository.updateTitle(id, newTitle)) {
                is Result.Error -> events.emit(ConversationListEvent.ShowError("Failed to rename conversation"))
                else -> onMutated()
            }
        }
    }

    fun archiveConversation(id: String) {
        scope.launch {
            when (conversationRepository.archive(id, true)) {
                is Result.Error -> events.emit(ConversationListEvent.ShowError("Failed to archive conversation"))
                else -> onMutated()
            }
        }
    }

    /**
     * Deletes [id]. When [isActive] (the deleted conversation is the one currently open in the chat
     * pane), emits [ConversationListEvent.NavigateToNewChat] on success so the host moves the pane off
     * the now-deleted thread to a fresh chat. This is the clean-UX half: the repo's delete also purges
     * the conversation's cached messages, so surfaces that DON'T navigate don't render a stale thread
     * either — the two are complementary, not redundant.
     */
    fun deleteConversation(id: String, isActive: Boolean = false) {
        scope.launch {
            when (conversationRepository.delete(id)) {
                is Result.Error -> events.emit(ConversationListEvent.ShowError("Failed to delete conversation"))
                else -> {
                    if (isActive) events.emit(ConversationListEvent.NavigateToNewChat)
                    onMutated()
                }
            }
        }
    }

    /** Opens the share sheet on [conversationId]. Nothing is created until the sheet's Create. */
    fun shareConversation(conversationId: String) = shareLink.open(conversationId)

    fun closeShareSheet() = shareLink.close()

    fun duplicateConversation(conversationId: String, title: String?) {
        scope.launch {
            when (val result = conversationRepository.duplicateConversation(conversationId, title)) {
                is Result.Success -> result.data.conversationId?.let {
                    events.emit(ConversationListEvent.NavigateToConversation(it))
                }
                is Result.Error -> events.emit(
                    ConversationListEvent.ShowError(result.message ?: "Failed to duplicate conversation"),
                )
                is Result.Loading -> Unit
            }
        }
    }

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) {
        scope.launch {
            val result = when (format) {
                ExportFormat.JSON -> conversationExporter.exportAsJson(conversationId)
                ExportFormat.MARKDOWN -> conversationExporter.exportAsMarkdown(conversationId)
            }
            when (result) {
                is Result.Success -> events.emit(
                    ConversationListEvent.ExportReady(result.data, format, title ?: "conversation"),
                )
                is Result.Error -> events.emit(
                    ConversationListEvent.ShowError(result.message ?: "Failed to export conversation"),
                )
                is Result.Loading -> Unit
            }
        }
    }
}
