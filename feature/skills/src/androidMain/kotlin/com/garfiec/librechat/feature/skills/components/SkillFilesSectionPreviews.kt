package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesUiState

// Sizes either side of each unit boundary, so the meta line's size format is visible.
private val files = listOf(
    SkillFile(id = "1", relativePath = "SKILL.md", mimeType = "text/markdown", bytes = 812),
    SkillFile(id = "2", relativePath = "scripts/extract_tables.py", mimeType = "text/x-python", bytes = 48_213),
    SkillFile(id = "3", relativePath = "reference/handbook.pdf", mimeType = "application/pdf", bytes = 3_481_920),
)

@PreviewNarrowScreens
@Composable
private fun SkillFilesSectionMaterialPreview() = SkillFilesSectionHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SkillFilesSectionGlassPreview() = SkillFilesSectionHost(UiStyle.LIQUID_GLASS)

@Composable
private fun SkillFilesSectionHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            SkillFilesSection(
                state = SkillFilesUiState(files = files),
                onAddFile = {},
                onRemoveFile = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
