package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesUiState
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorMode
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorState
import com.garfiec.librechat.feature.settings.viewmodel.MemoryFailure
import com.garfiec.librechat.feature.settings.viewmodel.derive

// The memory sheet for create, edit and view, with the counter, token share and dates the
// ViewModel derives (the states below go through the same derive()). The keyboard variants stand
// in for an iPhone SE with the keyboard up, which leaves about 400pt.

private val preferredLanguage = Memory(
    id = "m1",
    key = "preferred_language",
    value = "Replies in British English, metric units, 24-hour clock",
    updatedAt = "2026-10-18T14:02:00Z",
    tokenCount = 18,
)

private val snapshot = MemoriesResponse(
    memories = listOf(preferredLanguage),
    totalTokens = 3_120,
    tokenLimit = 4_000,
    charLimit = 10_000,
)

/** [editor] as the page would show it, derived against [snapshot]. */
internal fun memorySheetDraft(editor: MemoryEditorState, response: MemoriesResponse = snapshot): Pair<MemoryEditorState, Int> {
    val state = MemoriesUiState(snapshot = response, isFirstLoad = false, editor = editor)
        .derive(role = null, memoriesEnabled = true)
    return checkNotNull(state.editor) to state.charLimit
}

private fun createDraft() =
    memorySheetDraft(MemoryEditorState(mode = MemoryEditorMode.Create, key = "favourite_tea", value = "Earl Grey, no milk"))

private fun editDraft() = memorySheetDraft(
    MemoryEditorState(
        mode = MemoryEditorMode.Edit,
        original = preferredLanguage,
        key = preferredLanguage.key,
        value = preferredLanguage.value,
    ),
)

/** A value past a small cap: the counter turns to the error colour and Save is off. */
private fun overLimitDraft() = memorySheetDraft(
    MemoryEditorState(
        mode = MemoryEditorMode.Edit,
        original = preferredLanguage,
        key = preferredLanguage.key,
        value = preferredLanguage.value + ", and dates written day first",
    ),
    response = snapshot.copy(charLimit = 64),
)

private val viewDraft = memorySheetDraft(
    MemoryEditorState(
        mode = MemoryEditorMode.View,
        original = preferredLanguage,
        key = preferredLanguage.key,
        value = preferredLanguage.value,
    ),
)

/** A save the server turned down: the reason sits above the actions, where the sheet can't cover it. */
private val failedDraft = memorySheetDraft(
    MemoryEditorState(
        mode = MemoryEditorMode.Edit,
        original = preferredLanguage,
        key = preferredLanguage.key,
        value = preferredLanguage.value + ", and dates written day first",
        saveFailure = MemoryFailure.ServerMessage(
            "Adding this memory would exceed the token limit of 4000. Current usage: 3990 tokens.",
        ),
    ),
)

@PreviewNarrowScreens
@Composable
private fun MemorySheetCreateMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, createDraft())

@PreviewNarrowScreens
@Composable
private fun MemorySheetCreateGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, createDraft())

@PreviewNarrowScreens
@Composable
private fun MemorySheetEditMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, editDraft())

@PreviewNarrowScreens
@Composable
private fun MemorySheetEditGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, editDraft())

@PreviewNarrowScreens
@Composable
private fun MemorySheetOverLimitMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, overLimitDraft())

@PreviewNarrowScreens
@Composable
private fun MemorySheetFailedMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, failedDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetFailedGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, failedDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetViewMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, viewDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetViewGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, viewDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetKeyboardMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, editDraft(), height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun MemorySheetKeyboardGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, createDraft(), height = 400.dp)

/**
 * Glass renders the real sheet, portalled into the host. Material's sheet is its own window, which
 * a preview doesn't draw, so its body sits on the same surface and shape at the bottom instead.
 */
@Composable
internal fun MemorySheetPreviewHost(
    style: UiStyle,
    draft: Pair<MemoryEditorState, Int>,
    height: Dp = 640.dp,
    canDelete: Boolean = true,
) =
    DialogPreviewHost(style, height = height) {
        val (editor, charLimit) = draft
        if (style == UiStyle.LIQUID_GLASS) {
            MemoryEditSheet(
                editor = editor,
                charLimit = charLimit,
                canDelete = canDelete,
                onKeyChange = {},
                onValueChange = {},
                onDismiss = {},
                onSave = {},
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AdaptiveSheetSurface {
                    LowProfileDragHandle()
                    MemoryEditSheetContent(
                        editor = editor,
                        charLimit = charLimit,
                        canDelete = canDelete,
                        onKeyChange = {},
                        onValueChange = {},
                        onDismiss = {},
                        onSave = {},
                    )
                }
            }
        }
    }
