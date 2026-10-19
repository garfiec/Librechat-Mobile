package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ApiKey
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.dialog_title_disable_2fa
import com.garfiec.librechat.feature.settings.resources.server_headers_discard_confirm
import com.garfiec.librechat.feature.settings.resources.server_headers_discard_message
import com.garfiec.librechat.feature.settings.resources.server_headers_discard_title
import com.garfiec.librechat.feature.settings.resources.server_headers_keep_editing
import com.garfiec.librechat.feature.settings.resources.twofa_disable_instructions
import com.garfiec.librechat.feature.settings.screen.sections.BackupCodesDialog
import com.garfiec.librechat.feature.settings.screen.sections.TwoFactorCodeDialog
import org.jetbrains.compose.resources.stringResource

@PreviewNarrowScreens
@Composable
private fun ServerHeadersDiscardGlassPreview() = ServerHeadersDiscardHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun DisableTwoFactorGlassKeyboardPreview() = DisableTwoFactorHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun BackupCodesGlassPreview() = BackupCodesHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun MemoryEditGlassKeyboardPreview() = MemoryEditHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun ApiKeyCreateMaterialPreview() = ApiKeyCreateHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ApiKeyCreatedMaterialPreview() = ApiKeyCreatedHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ForkSettingsMaterialPreview() = ForkSettingsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun AvatarUploadMaterialPreview() = AvatarUploadHost(UiStyle.MATERIAL)

/** The confirmation `ServerHeadersDialog` raises over itself on a dirty close, with its strings. */
@Composable
private fun ServerHeadersDiscardHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    AdaptiveAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.server_headers_discard_title)) },
        text = { Text(stringResource(Res.string.server_headers_discard_message)) },
        confirmButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.server_headers_discard_confirm)) } },
        dismissButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.server_headers_keep_editing)) } },
    )
}

@Composable
private fun DisableTwoFactorHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    TwoFactorCodeDialog(
        title = stringResource(Res.string.dialog_title_disable_2fa),
        description = stringResource(Res.string.twofa_disable_instructions),
        isLoading = false,
        onConfirm = {},
        onDismiss = {},
    )
}

@Composable
private fun BackupCodesHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    BackupCodesDialog(backupCodes = List(10) { "${1000 + it * 1111}-${9000 - it * 777}" }, onDismiss = {})
}

@Composable
private fun MemoryEditHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    MemoryEditDialog(memories = emptyList(), enforceKeyPattern = true, editingMemory = null, onDismiss = {}, onSave = { _, _ -> })
}

@Composable
private fun ApiKeyCreateHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    ApiKeyCreateDialog(createdKey = null, isCreating = false, onCreateKey = {}, onDismiss = {})
}

@Composable
private fun ApiKeyCreatedHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    ApiKeyCreateDialog(
        createdKey = ApiKey(name = "Home automation", keyValue = "sk-lc-9f3a7c21e4b84d0a96f1c2e7b5d8a4f0"),
        isCreating = false,
        onCreateKey = {},
        onDismiss = {},
    )
}

@Composable
private fun ForkSettingsHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    ForkSettingsDialog(selectedMode = ForkMode.entries.first(), onModeSelect = {}, onDismiss = {})
}

@Composable
private fun AvatarUploadHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    // Uploading, so the spinner under the hint is part of the height.
    AvatarUploadDialog(currentAvatarUrl = null, isUploading = true, onPickImage = {}, onDismiss = {})
}
