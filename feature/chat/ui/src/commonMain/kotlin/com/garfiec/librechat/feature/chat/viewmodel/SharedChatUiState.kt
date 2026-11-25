package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.FILE_SOURCE_TEXT
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.serializeMessageForClipboard

/** Why the viewer has nothing to show. */
enum class SharedChatError {
    /** The link is gone, was never there, or holds no messages. Retry is offered. */
    NOT_FOUND,

    /** The server refused this account (401/403): the link is not shared with it. */
    NO_ACCESS,

    RATE_LIMITED,

    /** The server's content filter now refuses the shared content; see [SharedChatUiState.blockedMessage]. */
    BLOCKED,

    NETWORK,
    GENERIC,
}

/** A one-shot outcome the screen reports in a snackbar, or acts on. */
sealed interface SharedChatEvent {
    /** Continue made [conversationId]; the viewer gives way to it. */
    data class Continued(val conversationId: String) : SharedChatEvent

    /** The owner updated the link since it loaded; the viewer reloaded it, and Continue can be tried again. */
    data object LinkUpdated : SharedChatEvent

    data object ContinueRateLimited : SharedChatEvent

    /** The server's content filter refused to copy the chat; [serverMessage] says why, when it did. */
    data class ContinueBlocked(val serverMessage: String?) : SharedChatEvent

    data object ContinueFailed : SharedChatEvent

    /** This server has no Continue (its fork route is missing); the button is gone. */
    data object ContinueUnsupported : SharedChatEvent

    /** A refresh over content on screen didn't get through; [rateLimited] when the server said slow down. */
    data class RefreshFailed(val rateLimited: Boolean) : SharedChatEvent
}

/** A text-source file opened as text: its [text] once read, or [failed]. */
data class SharedTextFile(
    val filename: String,
    val text: String? = null,
    val failed: Boolean = false,
)

data class SharedChatUiState(
    /** The snapshot on screen, or null until the first load lands. */
    val snapshot: SharedConversationSnapshot? = null,
    /** The active branch through [snapshot], one node per level; see [buildActiveMessagePath]. */
    val displayMessages: List<MessageNode> = emptyList(),
    /** Chosen sibling per tree-parent key; what the user switched. Reset with every load. */
    val activeBranches: Map<String, Int> = emptyMap(),
    /** The first load (or a Retry from an error) is under way: nothing to show yet. */
    val isLoading: Boolean = true,
    /** A reload over content already on screen (pull to refresh, the 409 reload). */
    val isRefreshing: Boolean = false,
    /** Set when there is nothing to show; [snapshot] is then null. */
    val error: SharedChatError? = null,
    /** The content filter's explanation for [SharedChatError.BLOCKED], when the server gave one. */
    val blockedMessage: String? = null,
    /** Continue is in flight. Also the double-fork guard: a second tap does nothing until it lands. */
    val isContinuing: Boolean = false,
    /**
     * The server can fork a shared link. False once that is known not to be so; shown while the
     * version can't be placed.
     */
    val canContinue: Boolean = true,
    val baseUrl: String = "",
    /** The link's public URL on the active server, for "Open in browser". */
    val shareUrl: String = "",
    val mediaPreview: MediaPreviewState? = null,
    val textFile: SharedTextFile? = null,
    /**
     * Files in [snapshot] whose source is `text`: the server keeps no bytes for them and serves
     * their extracted text, so they open as text whatever their original type says.
     */
    val textSourceFileIds: Set<String> = emptySet(),
) {
    /**
     * The link was published with a configured sender (a preset or model spec chose the label), so
     * the model behind it is withheld wherever a model name would show. The mobile message header
     * shows only `sender` today, so nothing draws a model name to hide; this is carried for any
     * label that does, as web's `getHeaderHoverLabel` does.
     */
    val hidesModelName: Boolean get() = snapshot?.hasConfiguredSender == true

    /**
     * What Copy puts on the clipboard for [messageId], serialized as the chat screen copies: an
     * agent reply keeps its output in content parts and often leaves `text` blank.
     */
    fun clipboardText(messageId: String): String =
        displayMessages.firstOrNull { it.message.messageId == messageId }
            ?.let { serializeMessageForClipboard(it.message) }
            .orEmpty()
}

fun textSourceFileIds(messages: List<Message>): Set<String> = buildSet {
    for (message in messages) {
        message.files?.forEach { file -> if (file.source == FILE_SOURCE_TEXT) file.fileId?.let(::add) }
        message.attachments?.forEach { a -> if (a.source == FILE_SOURCE_TEXT) a.fileId?.let(::add) }
    }
}
