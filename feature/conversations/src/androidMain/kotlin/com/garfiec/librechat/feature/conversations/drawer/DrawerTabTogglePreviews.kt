package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun LibraryHeaderMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { LibraryHeaderHost() }

@PreviewNarrowScreens
@Composable
private fun LibraryHeaderGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { LibraryHeaderHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
    }
}

/** The drawer's Library heading beside the Chats/Projects toggle. */
@Composable
private fun LibraryHeaderHost() {
    var selected by remember { mutableStateOf(DrawerTab.Chats) }
    val switch = rememberLibrarySwitch(selected)
    LibraryHeader(
        switch = switch,
        selectedTab = selected,
        onSelect = { selected = it },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
