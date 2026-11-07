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
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import com.garfiec.librechat.feature.chat.util.draftItemsOf

@PreviewNarrowScreens
@Composable
private fun StripOneMaterialPreview() = StripSurface(UiStyle.MATERIAL) { StripHost(files = listOf(draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StripOneGlassPreview() = StripSurface(UiStyle.LIQUID_GLASS) { StripHost(files = listOf(draftReport)) }

@PreviewNarrowScreens
@Composable
private fun StripMixedMaterialPreview() = StripSurface(UiStyle.MATERIAL) {
    StripHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), quotes = listOf(DRAFT_LONG_QUOTE))
}

@PreviewNarrowScreens
@Composable
private fun StripMixedGlassPreview() = StripSurface(UiStyle.LIQUID_GLASS) {
    StripHost(files = listOf(draftPhoto, draftReport), skills = listOf("brand-voice"), quotes = listOf(DRAFT_LONG_QUOTE))
}

/** Uploading and failed: the ring on its tile, the red cover, Send held and the line saying why. */
@PreviewNarrowScreens
@Composable
private fun StripUploadsMaterialPreview() = StripSurface(UiStyle.MATERIAL) {
    StripHost(files = listOf(draftFailed, draftUploadingPhoto, draftUploading, draftPhoto))
}

@PreviewNarrowScreens
@Composable
private fun StripUploadsGlassPreview() = StripSurface(UiStyle.LIQUID_GLASS) {
    StripHost(files = listOf(draftFailed, draftUploadingPhoto, draftUploading, draftPhoto))
}

/** More than fits: the strip scrolls, fades at its end and shows the total. */
@PreviewNarrowScreens
@Composable
private fun StripFullMaterialPreview() = StripSurface(UiStyle.MATERIAL) {
    StripHost(
        files = List(5) { draftPhoto.copy(uri = "file://photo$it", fileId = "p$it") } + draftReport + draftTextRouted,
        compactWidth = true,
    )
}

/** A phone comparing two models: the A·B pill in the field, with a photo in the strip above. */
@PreviewNarrowScreens
@Composable
private fun StripWithCompareMaterialPreview() = StripSurface(UiStyle.MATERIAL) { CompareStripHost() }

@PreviewNarrowScreens
@Composable
private fun StripWithCompareGlassPreview() = StripSurface(UiStyle.LIQUID_GLASS) { CompareStripHost() }

@Composable
internal fun StripSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun StripHost(
    files: List<AttachedFile>,
    skills: List<String> = emptyList(),
    quotes: List<String> = emptyList(),
    compactWidth: Boolean = false,
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
            chrome = ComposerChrome(draftItems = items, draftUploadProgress = DraftUploadProgress { files }),
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
private fun CompareStripHost() {
    val comparison = ComposerComparison(
        primaryLabel = "gpt-5",
        secondaryLabel = "claude-opus-4-5-20251101-thinking",
        onChangePrimary = {},
        onChangeSecondary = {},
        compact = true,
    )
    val items = draftItemsOf(listOf(draftPhoto), emptyList(), emptyList(), serverUrl = "")
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
internal val draftUploadingPhoto = AttachedFile(
    uri = "file://photo-up",
    name = "IMG_20261106_091500.jpg",
    isImage = true,
    uploadProgress = 0.7f,
    type = "image/jpeg",
)
internal val draftFailed = AttachedFile(
    uri = "file://deck",
    name = "launch-deck.pptx",
    uploadFailed = true,
    type = "application/vnd.ms-powerpoint",
)
internal const val DRAFT_LONG_QUOTE =
    "The quarterly numbers beat the forecast in every region except the north-east, where the new warehouse"
