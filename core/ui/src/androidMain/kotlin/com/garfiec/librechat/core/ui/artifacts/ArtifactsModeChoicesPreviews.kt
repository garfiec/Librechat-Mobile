package com.garfiec.librechat.core.ui.artifacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun ArtifactsModeChoicesMaterialPreview() = ChoicesSurface(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ArtifactsModeChoicesGlassPreview() = ChoicesSurface(UiStyle.LIQUID_GLASS)

@Composable
private fun ChoicesSurface(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            ArtifactsModeChoices(selected = ArtifactsMode.SHADCN_UI, onSelect = {})
        }
    }
}
