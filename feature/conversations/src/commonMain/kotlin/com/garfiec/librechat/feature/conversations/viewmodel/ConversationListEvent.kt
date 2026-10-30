package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.feature.conversations.export.ExportFormat
import org.jetbrains.compose.resources.StringResource

/** One-shot UI events from a conversation list: the drawer, a project's chats, a bookmark's chats. */
sealed interface ConversationListEvent {
    data class NavigateToConversation(val conversationId: String) : ConversationListEvent

    /** The currently-open conversation was deleted: move off it to a fresh new chat. */
    data object NavigateToNewChat : ConversationListEvent
    data class ShowError(val message: String) : ConversationListEvent

    /** An error whose text is this module's own string, resolved where it's shown. */
    data class ShowErrorRes(val message: StringResource) : ConversationListEvent
    data class ExportReady(val content: String, val format: ExportFormat, val title: String) : ConversationListEvent
    data class ImportSuccess(val title: String) : ConversationListEvent
}
