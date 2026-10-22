package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.core.ui.theme.statusColors

@PreviewNarrowScreens
@Composable
private fun LinearProgressMaterialLightPreview() = LinearProgressGallery(UiStyle.MATERIAL, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun LinearProgressMaterialDarkPreview() = LinearProgressGallery(UiStyle.MATERIAL, darkTheme = true)

@PreviewNarrowScreens
@Composable
private fun LinearProgressGlassLightPreview() = LinearProgressGallery(UiStyle.LIQUID_GLASS, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun LinearProgressGlassDarkPreview() = LinearProgressGallery(UiStyle.LIQUID_GLASS, darkTheme = true)

@Composable
private fun LinearProgressGallery(style: UiStyle, darkTheme: Boolean) {
    LibreChatTheme(darkTheme = darkTheme, uiStyle = style) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0f, 0.5f, 1f).forEach { value ->
                Caption("${(value * 100).toInt()}%")
                AdaptiveLinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
            }
            // A caller's colour wins in both styles, as the memory usage bar passes a status colour.
            Caption("80% · warning colour")
            AdaptiveLinearProgressIndicator(
                progress = { 0.8f },
                modifier = Modifier.fillMaxWidth(),
                color = statusColors().warning,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
