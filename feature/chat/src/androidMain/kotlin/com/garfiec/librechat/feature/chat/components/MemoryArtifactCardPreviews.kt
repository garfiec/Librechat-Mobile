package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The inline memory card: a saved write with its Manage action, the same write for a role that may
// not read memories (no action), and a refusal the tool reported in prose.

@PreviewNarrowScreens
@Composable
internal fun MemoryArtifactCardLightMaterialPreview() = MemoryArtifactCardsPreview(false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
internal fun MemoryArtifactCardDarkGlassPreview() = MemoryArtifactCardsPreview(true, UiStyle.LIQUID_GLASS)

@Composable
private fun MemoryArtifactCardsPreview(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryArtifactCard(
                    artifact = MemoryArtifact(
                        title = "travel_preferences_for_long_haul_flights",
                        content = "Prefers a window seat, an early departure and a vegetarian meal on long-haul flights.",
                    ),
                    onManage = {},
                )
                MemoryArtifactCard(
                    artifact = MemoryArtifact(title = "tone", content = "Keep replies short."),
                )
                MemoryArtifactCard(
                    artifact = MemoryArtifact(
                        title = null,
                        content = "Invalid key \"Travel Prefs\". Must be one of: travel_preferences, tone",
                        kind = MemoryChangeKind.ERROR,
                    ),
                    onManage = {},
                )
            }
        }
    }
}
