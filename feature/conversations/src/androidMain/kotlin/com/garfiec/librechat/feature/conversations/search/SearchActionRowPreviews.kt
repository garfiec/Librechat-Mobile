package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.search.ScanProgress
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.search_filters_title
import org.jetbrains.compose.resources.stringResource

@PreviewNarrowScreens
@Composable
private fun FiltersHeaderMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { FiltersHeaderHost() }

@PreviewNarrowScreens
@Composable
private fun FiltersHeaderGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { FiltersHeaderHost() }

@PreviewNarrowScreens
@Composable
private fun ScanMoreMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ScanMoreHost() }

@PreviewNarrowScreens
@Composable
private fun ScanMoreGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ScanMoreHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun FiltersHeaderHost() {
    OptionsHeader(title = stringResource(Res.string.search_filters_title), onReset = {}, onDone = {})
}

/** A partial scan that stopped at its budget, so Scan more shows. */
@Composable
private fun ScanMoreHost() {
    Column {
        ScanMoreRow(
            messageCount = 50,
            scan = ScanProgress(scanned = 2000, total = 12480, resumeFrom = null),
            onScanMore = {},
        )
    }
}
