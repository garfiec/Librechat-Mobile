package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerFailure
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerState
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkEditorSheet
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkItem
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkPickerSheet
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkPickerUiState
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.notice_bookmark_create_failed
import com.garfiec.librechat.feature.chat.resources.notice_bookmark_update_failed
import com.garfiec.librechat.feature.chat.resources.notice_bookmarks_load_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The open chat's bookmark checklist and its "New bookmark" editor (with "Add to this chat"), while
 * [controller] is open. One sheet at a time: the editor takes the checklist's place and gives it back
 * when it closes. A tick or create that fails shows in the sheet; what the sheet can't show (a
 * load that closed it, a save that failed after it closed) reaches the chat's error line through
 * the ViewModel.
 *
 * The same host as `:feature:conversations`' `BookmarkPickerHost`: feature modules can't share it,
 * and `:core:ui` doesn't see the controller.
 */
@Composable
internal fun BookmarkPickerSheetHost(controller: BookmarkPickerController) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.isOpen) return
    // Shown in the sheet: a snackbar would sit behind it.
    val error = state.error?.let { stringResource(it.message) }
    if (state.editorOpen) {
        BookmarkEditorSheet(
            existingNames = state.names,
            onConfirm = controller::create,
            onDismiss = controller::closeEditor,
            offerAddToChat = true,
            conversationId = state.conversationId,
            saving = state.creating,
            error = error,
        )
    } else {
        val uiState = remember(state, error) { state.toUiState(error) }
        BookmarkPickerSheet(
            state = uiState,
            onToggle = controller::toggle,
            onNewBookmark = controller::openEditor,
            onDismiss = controller::close,
        )
    }
}

private fun BookmarkPickerState.toUiState(error: String?) = BookmarkPickerUiState(
    conversationId = conversationId,
    items = bookmarks.mapNotNull { tag ->
        tag.tag?.let { name -> BookmarkItem(name = name, description = tag.description, count = tag.count) }
    },
    attached = attached,
    pending = pending,
    loading = loading,
    error = error,
)

/** What a failure shown in the sheet says. */
private val BookmarkPickerFailure.message: StringResource
    get() = when (this) {
        BookmarkPickerFailure.LoadFailed -> Res.string.notice_bookmarks_load_failed
        is BookmarkPickerFailure.ToggleFailed -> Res.string.notice_bookmark_update_failed
        is BookmarkPickerFailure.CreateFailed -> Res.string.notice_bookmark_create_failed
    }
