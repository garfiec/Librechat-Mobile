package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.prompts.components.PromptShareDialog
import com.garfiec.librechat.feature.chat.prompts.components.VariableInputDialog
import com.garfiec.librechat.feature.chat.screen.ChatRenameDialog

@PreviewNarrowScreens
@Composable
private fun SiteIconsGlassPreview() = SiteIconsHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun CompactConfirmGlassPreview() = CompactConfirmHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun CompactConfirmMaterialPreview() = CompactConfirmHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SavePresetGlassKeyboardPreview() = SavePresetHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun PromptShareGlassPreview() = PromptShareHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun VariableInputGlassKeyboardPreview() = VariableInputHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun ChatRenameGlassKeyboardPreview() = ChatRenameHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@Composable
private fun SiteIconsHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SiteIconsPromptDialog(onChoice = {}, onDismiss = {})
}

@Composable
private fun CompactConfirmHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    CompactConfirmDialog(percent = 92, onDismiss = {}, onNotNow = {}, onCompact = {})
}

@Composable
private fun SavePresetHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SavePresetDialog(currentEndpoint = "openAI", currentModel = "gpt-4o-mini", onSave = {}, onDismiss = {})
}

/** Shared, so the permission radios and the copy-link button are part of the height. */
@Composable
private fun PromptShareHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    PromptShareDialog(promptName = "Weekly status summary", isCurrentlyShared = true, onShareToggle = { _, _ -> }, onDismiss = {})
}

@Composable
private fun VariableInputHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    VariableInputDialog(
        promptTemplate = "Summarize {{topic}} for {{audience}} in {{length}} words.",
        variables = listOf("topic", "audience", "length"),
        onInsert = {},
        onDismiss = {},
    )
}

@Composable
private fun ChatRenameHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    ChatRenameDialog(currentTitle = "Planning the spring garden layout", onDismiss = {}, onConfirm = {})
}
