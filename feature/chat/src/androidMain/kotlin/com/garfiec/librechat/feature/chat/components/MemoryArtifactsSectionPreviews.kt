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

// The "Updated saved memory" line for writes no tool card accounts for (the background memory
// agent's): with its Manage action, the same line for a role that may not read memories (no
// action), and the red "Memory error" line with Manage.

@PreviewNarrowScreens
@Composable
internal fun MemoryArtifactsSectionLightMaterialPreview() = MemoryArtifactsSectionsPreview(false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
internal fun MemoryArtifactsSectionDarkGlassPreview() = MemoryArtifactsSectionsPreview(true, UiStyle.LIQUID_GLASS)

@Composable
private fun MemoryArtifactsSectionsPreview(dark: Boolean, style: UiStyle) {
    val written = listOf(
        MemoryArtifact(title = "travel_preferences", content = "Prefers window seats."),
        MemoryArtifact(title = "old_address", content = null, kind = MemoryChangeKind.DELETE),
    )
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryArtifactsSection(artifacts = written, stateKey = "manage", onManage = {})
                MemoryArtifactsSection(artifacts = written, stateKey = "no-manage")
                MemoryArtifactsSection(
                    artifacts = written + MemoryArtifact(
                        title = null,
                        content = null,
                        kind = MemoryChangeKind.ERROR,
                        error = MemoryErrorInfo(errorType = null, tokenCount = null),
                    ),
                    stateKey = "error",
                    onManage = {},
                )
            }
        }
    }
}
