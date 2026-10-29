package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerState
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkEditorSheet
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkItem
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkPickerSheet
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkPickerUiState

/**
 * The open chat's bookmark checklist and its "New bookmark" editor (with "Add to this chat"), while
 * [controller] is open. One sheet at a time: the editor takes the checklist's place and gives it back
 * when it closes. Failures reach the chat's error channel through the ViewModel, not this host.
 *
 * The same host as `:feature:conversations`' `BookmarkPickerHost`: feature modules can't share it,
 * and `:core:ui` doesn't see the controller.
 */
@Composable
internal fun BookmarkPickerSheetHost(controller: BookmarkPickerController) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.isOpen) return
    if (state.editorOpen) {
        BookmarkEditorSheet(
            existingNames = state.names,
            onConfirm = controller::create,
            onDismiss = controller::closeEditor,
            offerAddToChat = true,
            saving = state.creating,
        )
    } else {
        val uiState = remember(state) { state.toUiState() }
        BookmarkPickerSheet(
            state = uiState,
            onToggle = controller::toggle,
            onNewBookmark = controller::openEditor,
            onDismiss = controller::close,
        )
    }
}

private fun BookmarkPickerState.toUiState() = BookmarkPickerUiState(
    items = bookmarks.mapNotNull { tag ->
        tag.tag?.let { name -> BookmarkItem(name = name, description = tag.description, count = tag.count) }
    },
    attached = attached,
    pending = pending,
    loading = loading,
)
