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
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.DefaultAccentSeed
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun ThemeSelectorMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ThemeSelectorHost(UiStyle.MATERIAL) }

@PreviewNarrowScreens
@Composable
private fun ThemeSelectorGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ThemeSelectorHost(UiStyle.LIQUID_GLASS) }

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
        onSelect = { selected = it },
    )
}
