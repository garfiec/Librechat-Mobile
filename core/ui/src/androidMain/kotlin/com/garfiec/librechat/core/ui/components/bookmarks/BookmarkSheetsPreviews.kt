package com.garfiec.librechat.core.ui.components.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

// The bookmark checklist (loading, a list with a long name and a save in flight, a list long enough
// for search, none yet) and the bookmark editor (a new name that's taken, an edit).

private val someBookmarks = listOf(
    BookmarkItem("Work", "Client threads and status notes", count = 12),
    BookmarkItem(
        "Recipes to try when the whole family visits over the long weekend in spring",
        "Mostly vegetarian, nothing that needs a sous-vide",
        count = 3,
    ),
    BookmarkItem("Taxes", count = 1),
    BookmarkItem("Reading list", "Papers and long reads to come back to", count = 0),
)

private val manyBookmarks = someBookmarks + listOf(
    BookmarkItem("Kotlin", count = 8),
    BookmarkItem("Garden", count = 2),
    BookmarkItem("Interview prep", "Questions, answers, follow-ups", count = 5),
    BookmarkItem("Travel", count = 4),
    BookmarkItem("Health", count = 1),
)

private val listState = BookmarkPickerUiState(
    items = someBookmarks,
    attached = setOf("Work", "Taxes"),
    pending = setOf("Taxes"),
)

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerListMaterialPreview() = PickerPreviewHost(UiStyle.MATERIAL, listState)

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerListGlassPreview() = PickerPreviewHost(UiStyle.LIQUID_GLASS, listState)

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerLoadingMaterialPreview() =
    PickerPreviewHost(UiStyle.MATERIAL, BookmarkPickerUiState(items = someBookmarks, loading = true))

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerLoadingGlassPreview() =
    PickerPreviewHost(UiStyle.LIQUID_GLASS, BookmarkPickerUiState(items = someBookmarks, loading = true))

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerSearchMaterialPreview() =
    PickerPreviewHost(UiStyle.MATERIAL, BookmarkPickerUiState(items = manyBookmarks, attached = setOf("Kotlin")))

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerSearchGlassPreview() =
    PickerPreviewHost(UiStyle.LIQUID_GLASS, BookmarkPickerUiState(items = manyBookmarks, attached = setOf("Kotlin")))

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerEmptyMaterialPreview() = PickerPreviewHost(UiStyle.MATERIAL, BookmarkPickerUiState())

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerEmptyGlassPreview() = PickerPreviewHost(UiStyle.LIQUID_GLASS, BookmarkPickerUiState())

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorTakenMaterialPreview() =
    EditorPreviewHost(UiStyle.MATERIAL, name = "work", offerAddToChat = true)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorTakenGlassPreview() =
    EditorPreviewHost(UiStyle.LIQUID_GLASS, name = "work", offerAddToChat = true)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorReservedMaterialPreview() = EditorPreviewHost(UiStyle.MATERIAL, name = "saved")

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorEditMaterialPreview() =
    EditorPreviewHost(UiStyle.MATERIAL, editing = "Reading list", description = "Papers and long reads to come back to")

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorEditGlassPreview() =
    EditorPreviewHost(UiStyle.LIQUID_GLASS, editing = "Reading list", description = "Papers and long reads to come back to")

/**
 * Glass renders the real sheet, portalled into the host. Material's sheet is its own window, which
 * a preview doesn't draw, so its body sits on the same surface at the bottom instead.
 */
@Composable
private fun PickerPreviewHost(style: UiStyle, state: BookmarkPickerUiState) = DialogPreviewHost(style) {
    if (style == UiStyle.LIQUID_GLASS) {
        BookmarkPickerSheet(state = state, onToggle = {}, onNewBookmark = {}, onDismiss = {})
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AdaptiveSheetSurface {
                LowProfileDragHandle()
                BookmarkPickerSheetContent(state = state, onToggle = {}, onNewBookmark = {})
            }
        }
    }
}

@Composable
private fun EditorPreviewHost(
    style: UiStyle,
    name: String? = null,
    editing: String? = null,
    description: String = "",
    offerAddToChat: Boolean = false,
) = DialogPreviewHost(style) {
    val existing = someBookmarks.map { it.name } + "Saved"
    if (style == UiStyle.LIQUID_GLASS && name == null) {
        BookmarkEditorSheet(
            existingNames = existing,
            onConfirm = { _, _, _ -> },
            onDismiss = {},
            editing = editing,
            initialDescription = description,
            offerAddToChat = offerAddToChat,
        )
    } else {
        // A typed name needs the draft seeded past the sheet's own empty start.
        val draft = rememberBookmarkDraft(name ?: editing.orEmpty(), description)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AdaptiveSheetSurface {
                LowProfileDragHandle()
                BookmarkEditorSheetContent(
                    draft = draft,
                    existingNames = existing,
                    onCancel = {},
                    onConfirm = { _, _, _ -> },
                    editing = editing,
                    initialDescription = description,
                    offerAddToChat = offerAddToChat,
                )
            }
        }
    }
}
