package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The editor's top bar only: the dialog is a window of its own, which a preview cannot draw.

@PreviewNarrowScreens
@Composable
private fun AddActionMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { TopBarHost(isEditing = false) }

@PreviewNarrowScreens
@Composable
private fun AddActionGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { TopBarHost(isEditing = false) }

@PreviewNarrowScreens
@Composable
private fun EditActionMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { TopBarHost(isEditing = true) }

@PreviewNarrowScreens
@Composable
private fun EditActionGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { TopBarHost(isEditing = true) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun TopBarHost(isEditing: Boolean) {
    ActionEditorTopBar(isEditing = isEditing, saveEnabled = true, onDismiss = {}, onSave = {})
}
