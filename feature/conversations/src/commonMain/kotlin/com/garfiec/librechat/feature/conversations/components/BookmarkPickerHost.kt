package com.garfiec.librechat.feature.conversations.components

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
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmark_create_failed
import com.garfiec.librechat.feature.conversations.resources.bookmark_update_failed
import com.garfiec.librechat.feature.conversations.resources.bookmarks_load_failed
import org.jetbrains.compose.resources.StringResource

/**
 * The per-chat bookmark checklist and its "New bookmark" editor, while [controller] is open. One
 * sheet at a time: the editor takes the checklist's place and gives it back when it closes.
 */
@Composable
fun BookmarkPickerHost(controller: BookmarkPickerController) {
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

/** What a checklist failure tells the user. */
internal val BookmarkPickerFailure.message: StringResource
    get() = when (this) {
        BookmarkPickerFailure.LoadFailed -> Res.string.bookmarks_load_failed
        is BookmarkPickerFailure.ToggleFailed -> Res.string.bookmark_update_failed
        is BookmarkPickerFailure.CreateFailed -> Res.string.bookmark_create_failed
    }
