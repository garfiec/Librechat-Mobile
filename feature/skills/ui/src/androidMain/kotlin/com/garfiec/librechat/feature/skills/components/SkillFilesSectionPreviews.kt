package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesUiState

// Sizes either side of each unit boundary, so the subtitle's size format is visible, and a long
// nested path that ellipsizes beside the ⋮.
private val files = listOf(
    SkillFile(id = "1", relativePath = "SKILL.md", mimeType = "text/markdown", bytes = 812),
    SkillFile(id = "2", relativePath = "scripts/extract_tables.py", mimeType = "text/x-python", bytes = 48_213),
    SkillFile(
        id = "3",
        relativePath = "reference/regional-handbooks/quarterly-close-procedures-handbook.pdf",
        mimeType = "application/pdf",
        bytes = 3_481_920,
    ),
)

@PreviewNarrowScreens
@Composable
private fun SkillFilesEditablePreview() =
    SkillFilesHost(UiStyle.MATERIAL, SkillFilesUiState(files = files, loaded = true, canEditFiles = true))

@PreviewNarrowScreens
@Composable
private fun SkillFilesEditableGlassPreview() =
    SkillFilesHost(UiStyle.LIQUID_GLASS, SkillFilesUiState(files = files, loaded = true, canEditFiles = true))

@PreviewNarrowScreens
@Composable
private fun SkillFilesReadOnlyPreview() = SkillFilesHost(UiStyle.MATERIAL, SkillFilesUiState(files = files, loaded = true))

@PreviewNarrowScreens
@Composable
private fun SkillFilesReadOnlyGlassPreview() =
    SkillFilesHost(UiStyle.LIQUID_GLASS, SkillFilesUiState(files = files, loaded = true))

@PreviewNarrowScreens
@Composable
private fun SkillFilesEmptyPreview() =
    SkillFilesHost(UiStyle.MATERIAL, SkillFilesUiState(loaded = true, canEditFiles = true, isUploading = true))

@PreviewNarrowScreens
@Composable
private fun SkillFilesEmptyGlassPreview() =
    SkillFilesHost(UiStyle.LIQUID_GLASS, SkillFilesUiState(loaded = true, canEditFiles = true, isUploading = true))

// The list never loaded: the failure with a retry, and no "No files" or Add file under it.
private val loadFailedState = SkillFilesUiState(
    canEditFiles = true,
    error = SkillError(SkillErrorKind.LOAD_FILES),
)

@PreviewNarrowScreens
@Composable
private fun SkillFilesLoadFailedPreview() = SkillFilesHost(UiStyle.MATERIAL, loadFailedState)

@PreviewNarrowScreens
@Composable
private fun SkillFilesLoadFailedGlassPreview() = SkillFilesHost(UiStyle.LIQUID_GLASS, loadFailedState)

@Composable
private fun SkillFilesHost(style: UiStyle, state: SkillFilesUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        AdaptiveGroupedPage {
            LazyColumn {
                skillFilesSection(state, SkillFilesActions(onOpen = {}, onRemove = {}, onAdd = {}, onRetry = {}))
            }
        }
    }
}
