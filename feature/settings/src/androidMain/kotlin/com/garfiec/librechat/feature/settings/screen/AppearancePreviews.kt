package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.DefaultAccentSeed
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsUiState

@PreviewNarrowScreens
@Composable
private fun ThemeSelectorMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ThemeSelectorHost(UiStyle.MATERIAL) }

@PreviewNarrowScreens
@Composable
private fun ThemeSelectorGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ThemeSelectorHost(UiStyle.LIQUID_GLASS) }

// Two-sided with bubbles at the large text size and the model in the title bar: the longest preview.
@PreviewNarrowScreens
@Composable
private fun ChatAppearanceMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ChatAppearanceHost() }

@PreviewNarrowScreens
@Composable
private fun ChatAppearanceGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ChatAppearanceHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun ThemeSelectorHost(style: UiStyle) {
    var selected by remember { mutableStateOf(ThemeMode.SYSTEM) }
    ThemeSelector(
        selected = selected,
        uiStyle = style,
        accentColor = DefaultAccentSeed,
        useDynamicColor = false,
        serverTheme = null,
        onSelect = { selected = it },
    )
}

@Composable
private fun ChatAppearanceHost() {
    var state by remember {
        mutableStateOf(
            ChatLayoutSettingsUiState(
                layoutStyle = ChatLayoutConstants.TWO_SIDED,
                showBubbles = true,
                fontSize = ChatFontSize.LARGE,
                headerContent = ChatHeaderContent.MODEL,
            ),
        )
    }
    ChatAppearanceContent(
        state = state,
        actions = ChatAppearanceActions(
            onLayoutStyle = { state = state.copy(layoutStyle = it) },
            onFontSize = { state = state.copy(fontSize = it) },
            onHeaderContent = { state = state.copy(headerContent = it) },
            onHeaderAlignment = { state = state.copy(headerAlignment = it) },
            onShowBubbles = { state = state.copy(showBubbles = it) },
            onShowAvatars = { state = state.copy(showAvatars = it) },
        ),
    )
}
