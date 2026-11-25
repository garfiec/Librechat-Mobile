package com.garfiec.librechat.feature.settings.screen

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
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.ReportProblemUiState

private val details = listOf(
    "Switchboard version: 2026.11.1",
    "Switchboard build: 1a2b3c4d",
    "LibreChat version: 0.8.7",
    "Commit: 9f8e7d6c5b4a39281706f5e4d3c2b1a098765432",
    "Branch: main",
    "Build date: 2026-11-02",
    "Device: Android 17 · Google Pixel 9 Pro Fold",
).joinToString("\n")

private val blank = ReportProblemUiState(logBytes = 1_240_000, details = details)

private val filled = blank.copy(
    text = "Sending a message with a PDF attached spins forever. It worked yesterday on the same server.",
)

@PreviewNarrowScreens
@Composable
private fun ReportFormBlankMaterialPreview() = ReportFormHost(UiStyle.MATERIAL, blank)

@PreviewNarrowScreens
@Composable
private fun ReportFormFilledMaterialPreview() = ReportFormHost(UiStyle.MATERIAL, filled)

@PreviewNarrowScreens
@Composable
private fun ReportFormBlankGlassPreview() = ReportFormHost(UiStyle.LIQUID_GLASS, blank)

@PreviewNarrowScreens
@Composable
private fun ReportFormFilledGlassPreview() = ReportFormHost(UiStyle.LIQUID_GLASS, filled.copy(includeLog = false))

@Composable
private fun ReportFormHost(style: UiStyle, state: ReportProblemUiState) {
    LibreChatTheme(darkTheme = style == UiStyle.LIQUID_GLASS, uiStyle = style) {
        ReportProblemContent(
            uiState = state,
            onBack = {},
            onKindChange = {},
            onTextChange = {},
            onIncludeLogChange = {},
            onViewDetails = {},
            onContinue = {},
            snackbarHostState = null,
        )
    }
}

@PreviewNarrowScreens
@Composable
private fun ReportDetailsSheetMaterialPreview() = ReportSheetHost(UiStyle.MATERIAL) { DetailsContent() }

@PreviewNarrowScreens
@Composable
private fun ReportDetailsSheetGlassPreview() = ReportSheetHost(UiStyle.LIQUID_GLASS) { DetailsContent() }

@PreviewNarrowScreens
@Composable
private fun SendAnotherWaySheetMaterialPreview() = ReportSheetHost(UiStyle.MATERIAL) { AnotherWayContent(logIncluded = true) }

@PreviewNarrowScreens
@Composable
private fun SendAnotherWaySheetGlassPreview() = ReportSheetHost(UiStyle.LIQUID_GLASS) { AnotherWayContent(logIncluded = false) }

@Composable
private fun DetailsContent() = ReportDetailsSheetContent(details = details, onSaveCopy = {}, onDone = {})

@Composable
private fun AnotherWayContent(logIncluded: Boolean) = SendAnotherWaySheetContent(
    logIncluded = logIncluded,
    onCopyAddress = {},
    onShare = {},
    onCopyText = {},
    onClose = {},
)

/** The sheet's body on the sheet's surface at the bottom; a preview draws no sheet window. */
@Composable
private fun ReportSheetHost(style: UiStyle, content: @Composable () -> Unit) =
    DialogPreviewHost(style) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AdaptiveSheetSurface {
                LowProfileDragHandle()
                content()
            }
        }
    }
