package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

// A long key, so the masked dots and the revealed text both run under the reveal button.

@PreviewNarrowScreens
@Composable
private fun SecretMaskedMaterialPreview() = SecretHost(UiStyle.MATERIAL, revealed = false)

@PreviewNarrowScreens
@Composable
private fun SecretMaskedGlassPreview() = SecretHost(UiStyle.LIQUID_GLASS, revealed = false)

@PreviewNarrowScreens
@Composable
private fun SecretRevealedMaterialPreview() = SecretHost(UiStyle.MATERIAL, revealed = true)

@PreviewNarrowScreens
@Composable
private fun SecretRevealedGlassPreview() = SecretHost(UiStyle.LIQUID_GLASS, revealed = true)

@PreviewNarrowScreens
@Composable
private fun SecretErrorMaterialPreview() = SecretHost(UiStyle.MATERIAL, revealed = false, value = "", error = true)

@PreviewNarrowScreens
@Composable
private fun SecretErrorGlassPreview() = SecretHost(UiStyle.LIQUID_GLASS, revealed = false, value = "", error = true)

@Composable
private fun SecretHost(style: UiStyle, revealed: Boolean, value: String = PREVIEW_KEY, error: Boolean = false) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            SecretTextField(
                value = value,
                onValueChange = {},
                revealed = revealed,
                onRevealedChange = {},
                label = { Text("API key") },
                isError = error,
                supportingText = if (error) {
                    { Text("Required") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val PREVIEW_KEY = "preview-not-a-real-key-0123456789abcdefghijklmnop"
