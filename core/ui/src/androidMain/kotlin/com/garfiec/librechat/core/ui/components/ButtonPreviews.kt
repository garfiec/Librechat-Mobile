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

@PreviewNarrowScreens
@Composable
private fun ButtonMaterialPreview() = ButtonGallery(UiStyle.MATERIAL, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun ButtonGlassPreview() = ButtonGallery(UiStyle.LIQUID_GLASS, darkTheme = true)

@Composable
private fun ButtonGallery(style: UiStyle, darkTheme: Boolean) {
    LibreChatTheme(darkTheme = darkTheme, uiStyle = style) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            AdaptiveButton(onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true) { Text("Connecting…") }
            AdaptiveButton(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) { Text("Connect") }
            AdaptiveOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Resend verification email") }
            AdaptiveOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true) { Text("Sending…") }
        }
    }
}
