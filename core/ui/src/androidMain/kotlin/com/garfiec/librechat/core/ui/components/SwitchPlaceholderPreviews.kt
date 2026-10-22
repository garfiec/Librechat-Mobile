package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun SwitchPlaceholderMaterialLightPreview() = SwitchPlaceholderGallery(UiStyle.MATERIAL, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun SwitchPlaceholderMaterialDarkPreview() = SwitchPlaceholderGallery(UiStyle.MATERIAL, darkTheme = true)

@PreviewNarrowScreens
@Composable
private fun SwitchPlaceholderGlassLightPreview() = SwitchPlaceholderGallery(UiStyle.LIQUID_GLASS, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun SwitchPlaceholderGlassDarkPreview() = SwitchPlaceholderGallery(UiStyle.LIQUID_GLASS, darkTheme = true)

/** The placeholder beside the off and on switches it stands in for, to compare their tracks. */
@Composable
private fun SwitchPlaceholderGallery(style: UiStyle, darkTheme: Boolean) {
    LibreChatTheme(darkTheme = darkTheme, uiStyle = style) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveSwitchPlaceholder()
            AdaptiveSwitch(checked = false, onCheckedChange = null)
            AdaptiveSwitch(checked = true, onCheckedChange = null)
        }
    }
}
