package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileImage
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileKind
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileUiState

// The file screen with an editable script mid-edit (Save enabled), a shared file the viewer can
// only read, a conflict after someone else saved first, an image preview and a binary file. Paths
// are long and nested so the title ellipsizes.

private const val SCRIPT_PATH = "scripts/regional-reports/extract_quarterly_tables.py"

private val script = List(24) { "rows_$it = transform(load_sheet(\"q$it\"), normalize=True)" }.joinToString("\n")

private val editableState = SkillFileUiState(
    relativePath = SCRIPT_PATH,
    kind = SkillFileKind.TEXT,
    loadedText = script,
    draft = script + "\n# edited",
    filename = "extract_quarterly_tables.py",
    mimeType = "text/x-python",
    fileId = "rev-1",
    canEditFiles = true,
    accessResolved = true,
)

private val readOnlyState = editableState.copy(draft = script, canEditFiles = false)

private val conflictState = editableState.copy(conflict = true)

private val imageState = SkillFileUiState(
    relativePath = "assets/diagrams/quarterly-close-workflow.png",
    kind = SkillFileKind.IMAGE,
    image = SkillFileImage(ByteArray(0)),
    filename = "quarterly-close-workflow.png",
    mimeType = "image/png",
    sizeBytes = 184_320,
    fileId = "rev-1",
    canEditFiles = true,
    accessResolved = true,
)

private val binaryState = SkillFileUiState(
    relativePath = "reference/regional-handbooks/quarterly-close-procedures-handbook.pdf",
    kind = SkillFileKind.BINARY,
    filename = "quarterly-close-procedures-handbook.pdf",
    mimeType = "application/pdf",
    sizeBytes = 3_481_920,
    fileId = "rev-1",
    accessResolved = true,
)

@PreviewNarrowScreens
@Composable
private fun SkillFileTextPreview() = FileHost(UiStyle.MATERIAL, editableState)

@PreviewNarrowScreens
@Composable
private fun SkillFileTextGlassPreview() = FileHost(UiStyle.LIQUID_GLASS, editableState)

@PreviewNarrowScreens
@Composable
private fun SkillFileReadOnlyPreview() = FileHost(UiStyle.MATERIAL, readOnlyState)

@PreviewNarrowScreens
@Composable
private fun SkillFileReadOnlyGlassPreview() = FileHost(UiStyle.LIQUID_GLASS, readOnlyState)

@PreviewNarrowScreens
@Composable
private fun SkillFileConflictPreview() = FileHost(UiStyle.MATERIAL, conflictState)

@PreviewNarrowScreens
@Composable
private fun SkillFileConflictGlassPreview() = FileHost(UiStyle.LIQUID_GLASS, conflictState)

@PreviewNarrowScreens
@Composable
private fun SkillFileImagePreview() = FileHost(UiStyle.MATERIAL, imageState)

@PreviewNarrowScreens
@Composable
private fun SkillFileImageGlassPreview() = FileHost(UiStyle.LIQUID_GLASS, imageState)

@PreviewNarrowScreens
@Composable
private fun SkillFileBinaryPreview() = FileHost(UiStyle.MATERIAL, binaryState)

@PreviewNarrowScreens
@Composable
private fun SkillFileBinaryGlassPreview() = FileHost(UiStyle.LIQUID_GLASS, binaryState)

@Composable
private fun FileHost(style: UiStyle, state: SkillFileUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        SkillFileScreenContent(
            state = state,
            actions = SkillFileActions(onBack = {}, onSave = {}, onReload = {}, onDraftChange = {}, onImageFailed = {}),
            // A gradient stands in for the decoded image: the loader has nothing to decode in a preview.
            image = { _, description, modifier ->
                Image(
                    painter = BrushPainter(Brush.linearGradient(listOf(Color(0xFF3A6FF7), Color(0xFF8E5CF7)))),
                    contentDescription = description,
                    modifier = modifier,
                )
            },
        )
    }
}
