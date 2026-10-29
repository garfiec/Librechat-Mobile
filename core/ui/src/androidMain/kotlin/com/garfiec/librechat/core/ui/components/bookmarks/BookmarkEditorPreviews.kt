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

// The bookmark editor: a new name that's taken, a create that failed, a create in flight, the
// reserved name, an edit.

private val existingNames = listOf(
    "Work",
    "Recipes to try when the whole family visits over the long weekend in spring",
    "Taxes",
    "Reading list",
    "Saved",
)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorTakenMaterialPreview() =
    EditorPreviewHost(UiStyle.MATERIAL, name = "work", offerAddToChat = true)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorTakenGlassPreview() =
    EditorPreviewHost(UiStyle.LIQUID_GLASS, name = "work", offerAddToChat = true)

private const val CREATE_FAILED = "Couldn't create the bookmark. Try again."

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorFailedMaterialPreview() =
    EditorPreviewHost(UiStyle.MATERIAL, name = "Trips", offerAddToChat = true, error = CREATE_FAILED)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorFailedGlassPreview() =
    EditorPreviewHost(UiStyle.LIQUID_GLASS, name = "Trips", offerAddToChat = true, error = CREATE_FAILED)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorSavingMaterialPreview() =
    EditorPreviewHost(UiStyle.MATERIAL, name = "Trips", offerAddToChat = true, saving = true)

@PreviewNarrowScreens
@Composable
private fun BookmarkEditorSavingGlassPreview() =
    EditorPreviewHost(UiStyle.LIQUID_GLASS, name = "Trips", offerAddToChat = true, saving = true)

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
private fun EditorPreviewHost(
    style: UiStyle,
    name: String? = null,
    editing: String? = null,
    description: String = "",
    offerAddToChat: Boolean = false,
    error: String? = null,
    saving: Boolean = false,
) = DialogPreviewHost(style) {
    val existing = existingNames
    if (style == UiStyle.LIQUID_GLASS && name == null) {
        BookmarkEditorSheet(
            existingNames = existing,
            onConfirm = { _, _, _ -> },
            onDismiss = {},
            editing = editing,
            initialDescription = description,
            offerAddToChat = offerAddToChat,
            saving = saving,
            error = error,
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
                    saving = saving,
                    error = error,
                )
            }
        }
    }
}
