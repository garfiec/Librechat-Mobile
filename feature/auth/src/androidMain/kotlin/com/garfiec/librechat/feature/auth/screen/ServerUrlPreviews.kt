package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.auth.viewmodel.KnownServer

private val servers = listOf(
    KnownServer("https://chat.acme-labs.io", "chat.acme-labs.io", "chat.acme-labs.io", accountCount = 2),
    KnownServer("https://home.example.net/librechat", "home.example.net/librechat", "home.example.net", 1),
    KnownServer(
        url = "https://librechat.research-computing.university.edu",
        label = "librechat.research-computing.university.edu",
        host = "librechat.research-computing.university.edu",
        accountCount = 1,
    ),
)

@PreviewNarrowScreens
@Composable
private fun KnownServersMaterialPreview() = KnownServersHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun KnownServersGlassPreview() = KnownServersHost(UiStyle.LIQUID_GLASS)

@Composable
private fun KnownServersHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            KnownServersSection(
                servers = servers,
                enabled = true,
                onSelect = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
