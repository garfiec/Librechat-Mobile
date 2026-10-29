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

// The bookmark checklist: loading, a list with a long name and a save in flight, a list long enough
// for search, none yet, a tick that failed. The editor's previews are in BookmarkEditorPreviews.kt.

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

private val failedState = BookmarkPickerUiState(
    items = someBookmarks,
    attached = setOf("Work"),
    error = "Couldn't update bookmarks. Try again.",
)

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerFailedMaterialPreview() = PickerPreviewHost(UiStyle.MATERIAL, failedState)

@PreviewNarrowScreens
@Composable
private fun BookmarkPickerFailedGlassPreview() = PickerPreviewHost(UiStyle.LIQUID_GLASS, failedState)

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
