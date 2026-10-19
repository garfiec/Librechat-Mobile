package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

@PreviewNarrowScreens
@Composable
private fun RenameGlassKeyboardPreview() = RenameHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@Composable
private fun RenameHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    RenameDialog(currentTitle = "Planning the spring garden layout", onDismiss = {}, onConfirm = {})
}
