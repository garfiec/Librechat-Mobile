package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerFailure
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.feature.chat.viewmodel.BookmarksHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The open chat's bookmarks: the ⋮ menu's "Bookmarks" checklist ([picker], the controller every
 * bookmark surface shares) and whether the chat carries one, for the menu icon.
 *
 * The icon reads the checklist's ticks while it is open on this chat and has read the server, and
 * the cached row's tags otherwise. The row came from the conversation list, which carries no tags,
 * so opening the chat reads its tags from the server once, into the row. Every checklist write
 * lands in that row too, so it agrees once the checklist closes.
 *
 * Only a saved chat has bookmarks: never a new or a temporary one, and nothing while the role or
 * `interface.bookmarks` turns them off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBookmarksDelegate(
    private val handle: BookmarksHandle,
    private val tagRepository: TagRepository,
) {
    val picker = BookmarkPickerController(tagRepository, handle.scope)

    init {
        observeChat()
        observeHasBookmark()
        handle.scope.launch {
            picker.failures.collect { failure -> handle.setError(failure.notice.marker()) }
        }
    }

    /** Opens the checklist on the open chat, which reads the chat's bookmarks from the server first. */
    fun open() {
        val chat = handle.state.bookmarkChat() ?: return
        picker.open(chat)
    }

    fun close() = picker.close()

    // The checklist belongs to one chat: it closes when the chat changes or bookmarks turn off.
    private fun observeChat() {
        handle.scope.launch {
            handle.stateFlow.map { it.bookmarkChat() }.distinctUntilChanged().collect { chat ->
                val open = picker.state.value.conversationId
                if (open != null && open != chat) picker.close()
            }
        }
    }

    private fun observeHasBookmark() {
        handle.scope.launch {
            handle.stateFlow.map { it.bookmarkChat() }.distinctUntilChanged()
                .flatMapLatest { chat ->
                    // SECURITY: a temporary chat never reaches here (bookmarkChat is null for one), so
                    // no read of its row is ever made.
                    if (chat == null) {
                        flowOf(false)
                    } else {
                        channelFlow {
                            // Into the cached row, which the icon reads; a failure leaves the row as it was.
                            launch { tagRepository.getConversationTags(chat) }
                            combine(tagRepository.observeConversationTags(chat), picker.state) { cached, checklist ->
                                val ticks = if (checklist.conversationId == chat && !checklist.loading) {
                                    checklist.attached
                                } else {
                                    cached
                                }
                                ticks.any { it.isNotBlank() && it != SAVED_TAG }
                            }.collect { send(it) }
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { has -> handle.update { bookmarks = bookmarks.copy(hasBookmark = has) } }
        }
    }

    private val BookmarkPickerFailure.notice: ChatNotice
        get() = when (this) {
            BookmarkPickerFailure.LoadFailed -> ChatNotice.BOOKMARKS_LOAD_FAILED
            is BookmarkPickerFailure.ToggleFailed -> ChatNotice.BOOKMARK_UPDATE_FAILED
            is BookmarkPickerFailure.CreateFailed -> ChatNotice.BOOKMARK_CREATE_FAILED
        }
}

/**
 * The chat bookmarks can go on, or null: a saved chat (not a new one, not one whose first turn is
 * still being saved, not a temporary one) while bookmarks are on. The ⋮ item uses the same rule.
 */
private fun ChatUiState.bookmarkChat(): String? =
    conversationId?.takeIf { bookmarksEnabled && !isTemporaryChat && !isFirstTurnStreaming }
