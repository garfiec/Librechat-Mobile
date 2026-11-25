package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.WebSearchKeysRow

/** The "+" sheet's row while Web Search is on and the built-in search stands in. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetupMaterialPreview() = RowSurface(UiStyle.MATERIAL, setupRow)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetupGlassPreview() = RowSurface(UiStyle.LIQUID_GLASS, setupRow)

/** The row once the user's keys work. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetMaterialPreview() = RowSurface(UiStyle.MATERIAL, setRow)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetGlassPreview() = RowSurface(UiStyle.LIQUID_GLASS, setRow)

@Composable
private fun RowSurface(style: UiStyle, row: WebSearchKeysRow) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 16.dp)) {
            WebSearchKeysActionRow(row = row, onClick = {})
        }
    }
}

private val setupRow = WebSearchKeysRow(authenticated = false, fallbackProvider = "OpenAI")
private val setRow = WebSearchKeysRow(authenticated = true, summary = "Serper · Firecrawl")
