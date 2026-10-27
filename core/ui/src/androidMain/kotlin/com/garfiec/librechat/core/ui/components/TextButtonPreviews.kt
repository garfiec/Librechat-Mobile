package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
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
private fun TextButtonMaterialPreview() = TextButtonGallery(UiStyle.MATERIAL, darkTheme = false)

@PreviewNarrowScreens
@Composable
private fun TextButtonGlassPreview() = TextButtonGallery(UiStyle.LIQUID_GLASS, darkTheme = true)

/** The default accent label, a destructive one in the caller's colour, and a disabled one. */
@Composable
private fun TextButtonGallery(style: UiStyle, darkTheme: Boolean) {
    LibreChatTheme(darkTheme = darkTheme, uiStyle = style) {
        FlowRow(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveTextButton(onClick = {}) { Text("Retry") }
            AdaptiveTextButton(
                onClick = {},
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Revoke all keys")
            }
            AdaptiveTextButton(onClick = {}, enabled = false) { Text("Cancel") }
        }
    }
}
