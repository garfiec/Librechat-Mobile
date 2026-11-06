package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.draftItemsOf

// The composer's attachment stack in the field's leading slot: one, two and four items, uploading,
// failed, a 320dp row, under Compare's A·B pill. The list it opens is in ComposerDraftListPreviews.kt.

@PreviewNarrowScreens
@Composable
private fun StackOneMaterialPreview() = StackSurface(UiStyle.MATERIAL) { StackHost(files = listOf(draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackOneGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { StackHost(files = listOf(draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackTwoMaterialPreview() = StackSurface(UiStyle.MATERIAL) { StackHost(files = listOf(draftPhoto, draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackTwoGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { StackHost(files = listOf(draftPhoto, draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackFourMaterialPreview() = StackSurface(UiStyle.MATERIAL) {
    StackHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), quotes = listOf(DRAFT_LONG_QUOTE))
}

@PreviewNarrowScreens
@Composable
private fun StackFourGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) {
    StackHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), quotes = listOf(DRAFT_LONG_QUOTE))
}

@PreviewNarrowScreens
@Composable
private fun StackUploadingMaterialPreview() = StackSurface(UiStyle.MATERIAL) { StackHost(files = listOf(draftUploading, draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackUploadingGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { StackHost(files = listOf(draftUploading, draftReport)) }

/** A failed upload: the "!" on the stack, Send held, and the line under the field saying why. */
@PreviewNarrowScreens
@Composable
private fun StackFailedMaterialPreview() = StackSurface(UiStyle.MATERIAL) { StackHost(files = listOf(draftFailed, draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StackFailedGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { StackHost(files = listOf(draftFailed, draftReport)) }

// During a tool-approval pause: the stack shows but opens nothing, and the line says "after the approval".
@PreviewNarrowScreens
@Composable
private fun StackFailedDuringToolPauseMaterialPreview() = StackSurface(UiStyle.MATERIAL) {
    StackHost(files = listOf(draftFailed, draftReport), toolPause = true)
}

@PreviewNarrowScreens
@Composable
private fun StackFailedDuringToolPauseGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) {
    StackHost(files = listOf(draftFailed, draftReport), toolPause = true)
}

/** Below a 360dp phone's row the stack draws one tile and the count. */
@PreviewNarrowScreens
@Composable
private fun StackCompactMaterialPreview() = StackSurface(UiStyle.MATERIAL) {
    StackHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), compactWidth = true)
}

@PreviewNarrowScreens
@Composable
private fun StackCompactGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) {
    StackHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), compactWidth = true)
}

/** A phone comparing two models: the A·B pill starts past the stack, empty and with a draft (dots). */
@PreviewNarrowScreens
@Composable
private fun StackWithCompareMaterialPreview() = StackSurface(UiStyle.MATERIAL) { CompareStackHost() }

@PreviewNarrowScreens
@Composable
private fun StackWithCompareGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { CompareStackHost() }

@Composable
internal fun StackSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun StackHost(
    files: List<AttachedFile>,
    skills: List<String> = emptyList(),
    quotes: List<String> = emptyList(),
    compactWidth: Boolean = false,
    toolPause: Boolean = false,
) {
    val items = draftItemsOf(files, skills, quotes, serverUrl = "")
    val composer = @Composable {
        ChatInput(
            inputText = "",
            isStreaming = false,
            onInputChange = {},
            onSend = {},
            onStop = {},
            onOpenTools = {},
            chrome = ComposerChrome(draftItems = items, draftListShut = toolPause),
            selectedModelDisplay = "claude-opus-4-5",
        )
    }
    if (compactWidth) {
        // A 320dp screen, whatever width the preview device has.
        Column(Modifier.width(320.dp)) { composer() }
    } else {
        composer()
    }
}

@Composable
private fun CompareStackHost() {
    val comparison = ComposerComparison(
        primaryLabel = "gpt-5",
        secondaryLabel = "claude-opus-4-5-20251101-thinking",
        onChangePrimary = {},
        onChangeSecondary = {},
        compact = true,
    )
    val items = draftItemsOf(listOf(draftPhoto, draftReport), emptyList(), emptyList(), serverUrl = "")
    Column {
        listOf("", "Which of these is stable?").forEach { draft ->
            ChatInput(
                inputText = draft,
                isStreaming = false,
                onInputChange = {},
                onSend = {},
                onStop = {},
                onOpenTools = {},
                chrome = ComposerChrome(draftItems = items),
                comparison = comparison,
                comparisonHint = ComposerComparisonHint.BOTH,
            )
        }
    }
}

internal val draftPhoto = AttachedFile(
    uri = "file://photo",
    name = "IMG_20261105_142233.jpg",
    isImage = true,
    fileId = "p1",
    type = "image/jpeg",
)
internal val draftReport = AttachedFile(
    uri = "file://report",
    name = "quarterly-board-pack-and-investor-appendix-final.pdf",
    fileId = "f1",
    type = "application/pdf",
)
internal val draftTextRouted = AttachedFile(
    uri = "file://notes",
    name = "meeting-notes.docx",
    fileId = "f2",
    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    route = UploadRoute.TEXT,
)
internal val draftUploading = AttachedFile(
    uri = "file://data",
    name = "sales.csv",
    uploadProgress = 0.4f,
    type = "text/csv",
)
internal val draftFailed = AttachedFile(
    uri = "file://deck",
    name = "launch-deck.pptx",
    uploadFailed = true,
    type = "application/vnd.ms-powerpoint",
)
internal const val DRAFT_LONG_QUOTE =
    "The quarterly numbers beat the forecast in every region except the north-east, where the new warehouse"
