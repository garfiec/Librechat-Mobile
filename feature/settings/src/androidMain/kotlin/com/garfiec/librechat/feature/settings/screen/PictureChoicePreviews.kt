package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.artifacts_open_in
import com.garfiec.librechat.feature.settings.resources.context_settings_where
import com.garfiec.librechat.feature.settings.resources.starred_models_title
import org.jetbrains.compose.resources.stringResource

// The picture choices in grouped cells as their pages place them, at the widths and text sizes that
// decide how many cards share a row.

@PreviewNarrowScreens
@Composable
private fun ArtifactViewerChoicePreview() = PreviewSurface(UiStyle.MATERIAL) { ArtifactViewerHost() }

@PreviewNarrowScreens
@Composable
private fun ArtifactViewerChoiceGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ArtifactViewerHost() }

@PreviewNarrowScreens
@Composable
private fun ContextPlacementChoicePreview() = PreviewSurface(UiStyle.MATERIAL) { ContextPlacementHost() }

@PreviewNarrowScreens
@Composable
private fun ContextPlacementChoiceGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ContextPlacementHost() }

@PreviewNarrowScreens
@Composable
private fun StarredModelsChoicePreview() = PreviewSurface(UiStyle.MATERIAL) { StarredModelsHost() }

@PreviewNarrowScreens
@Composable
private fun StarredModelsChoiceGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { StarredModelsHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Composable
private fun ArtifactViewerHost() {
    var selected by remember { mutableStateOf(ArtifactDisplayMode.AUTOMATIC) }
    InCell(stringResource(Res.string.artifacts_open_in)) {
        ArtifactViewerChoice(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun ContextPlacementHost() {
    var selected by remember { mutableStateOf(ContextBarPlacement.OPTIONS_SHEET) }
    InCell(stringResource(Res.string.context_settings_where)) {
        ContextPlacementChoice(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun StarredModelsHost() {
    var selected by remember { mutableStateOf(StarredModelsDisplay.GROUPED) }
    InCell(stringResource(Res.string.starred_models_title)) {
        StarredModelsChoice(selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun InCell(header: String, content: @Composable () -> Unit) {
    AdaptiveGroupedPage {
        LazyColumn {
            item(key = "header") { AdaptiveSectionHeader(header) }
            adaptiveSection { row(key = "choice") { content() } }
        }
    }
}
