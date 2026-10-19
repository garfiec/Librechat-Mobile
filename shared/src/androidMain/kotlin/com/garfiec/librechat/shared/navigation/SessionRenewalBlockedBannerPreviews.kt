package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun SessionRenewalBlockedMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { BannerHost() }

@PreviewNarrowScreens
@Composable
private fun SessionRenewalBlockedGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { BannerHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun BannerHost() {
    SessionRenewalBlockedBanner(visible = true, onSignInAgain = {})
}
