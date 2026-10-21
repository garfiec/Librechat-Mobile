package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.components.SharedTextFileContent
import com.garfiec.librechat.feature.chat.viewmodel.SharedTextFile

private val textFile = SharedTextFile(
    filename = "quarterly-planning-notes-final-revised-for-the-whole-team.pdf",
    text = "Q4 goals\n- Ship the shared chat viewer\n- Cut cold start by 20%\n\nRisks\n- Server versions older than 0.8.8",
)

@PreviewNarrowScreens
@Composable
private fun TextFileMaterialPreview() = TextFilePreview(UiStyle.MATERIAL, textFile)

@PreviewNarrowScreens
@Composable
private fun TextFileGlassPreview() = TextFilePreview(UiStyle.LIQUID_GLASS, textFile)

@PreviewNarrowScreens
@Composable
private fun TextFileLoadingMaterialPreview() = TextFilePreview(UiStyle.MATERIAL, SharedTextFile("notes.txt"))

@PreviewNarrowScreens
@Composable
private fun TextFileFailedMaterialPreview() = TextFilePreview(UiStyle.MATERIAL, SharedTextFile("notes.txt", failed = true))

@PreviewNarrowScreens
@Composable
private fun TextFileFailedGlassPreview() = TextFilePreview(UiStyle.LIQUID_GLASS, SharedTextFile("notes.txt", failed = true))

@Composable
private fun TextFilePreview(style: UiStyle, file: SharedTextFile) {
    LibreChatTheme(darkTheme = style == UiStyle.LIQUID_GLASS, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxWidth().height(SHARED_CHAT_PREVIEW_HEIGHT.dp)) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AdaptiveSheetSurface(Modifier.fillMaxWidth()) {
                    SharedTextFileContent(file)
                }
            }
        }
    }
}
