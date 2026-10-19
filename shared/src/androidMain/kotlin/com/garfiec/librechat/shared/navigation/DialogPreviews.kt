package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

@PreviewNarrowScreens
@Composable
private fun BannedGlassPreview() = BannedHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun BannedMaterialPreview() = BannedHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun VersionMismatchGlassPreview() = VersionMismatchHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun VersionMismatchMaterialPreview() = VersionMismatchHost(UiStyle.MATERIAL)

@Composable
private fun BannedHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SessionExpiredDialog(notice = SessionExpiredNotice(accountLabel = "sam@example.com", banned = true), onDismiss = {})
}

@Composable
private fun VersionMismatchHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    VersionMismatchDialog(supportedVersion = "0.8.8", backendVersion = "0.9.1", onDismiss = {}, onDismissPermanently = {})
}
