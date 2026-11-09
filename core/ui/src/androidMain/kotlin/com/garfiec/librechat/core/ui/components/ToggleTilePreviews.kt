package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

@PreviewNarrowScreens
@Composable
private fun ToggleTilesLightMaterialPreview() = FileTypePreviewSurface(dark = false, UiStyle.MATERIAL) { ToggleTileStates() }

@PreviewNarrowScreens
@Composable
private fun ToggleTilesDarkMaterialPreview() = FileTypePreviewSurface(dark = true, UiStyle.MATERIAL) { ToggleTileStates() }

@PreviewNarrowScreens
@Composable
private fun ToggleTilesLightGlassPreview() = FileTypePreviewSurface(dark = false, UiStyle.LIQUID_GLASS) { ToggleTileStates() }

@PreviewNarrowScreens
@Composable
private fun ToggleTilesDarkGlassPreview() = FileTypePreviewSurface(dark = true, UiStyle.LIQUID_GLASS) { ToggleTileStates() }

/** On, off, locked and a long German label, then a two-column pair as the options sheet lays them out. */
@Composable
private fun ToggleTileStates() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveToggleTile(checked = true, icon = Icons.Default.Language, label = "Web Search", stateLabel = "On", onClick = {})
        AdaptiveToggleTile(checked = false, icon = Icons.Default.Code, label = "Code", stateLabel = "Off", onClick = {})
        AdaptiveToggleTile(
            checked = true,
            icon = Icons.Default.Psychology,
            label = "Memory",
            stateLabel = "Set by Research Assistant Pro",
            onClick = {},
            locked = true,
        )
        AdaptiveToggleTile(
            checked = false,
            icon = Icons.Default.AutoAwesome,
            label = "Codeausführungsumgebung für Dateien",
            stateLabel = "Aus",
            onClick = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveToggleTile(
                checked = true,
                icon = Icons.Default.Language,
                label = "Websuche",
                stateLabel = "Suchmaschine und Scraper",
                onClick = {},
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AdaptiveToggleTile(
                checked = false,
                icon = Icons.Default.AutoAwesome,
                label = "Skills",
                stateLabel = "Off",
                onClick = {},
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
