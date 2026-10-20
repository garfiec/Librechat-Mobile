package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.statusColors

@PreviewNarrowScreens
@Composable
private fun ListPiecesLightMaterialPreview() = FileTypePreviewSurface(dark = false, UiStyle.MATERIAL) { ListPieces() }

@PreviewNarrowScreens
@Composable
private fun ListPiecesDarkMaterialPreview() = FileTypePreviewSurface(dark = true, UiStyle.MATERIAL) { ListPieces() }

@PreviewNarrowScreens
@Composable
private fun ListPiecesLightGlassPreview() = FileTypePreviewSurface(dark = false, UiStyle.LIQUID_GLASS) { ListPieces() }

@PreviewNarrowScreens
@Composable
private fun ListPiecesDarkGlassPreview() = FileTypePreviewSurface(dark = true, UiStyle.LIQUID_GLASS) { ListPieces() }

@PreviewNarrowScreens
@Composable
private fun EmptyStateActionMaterialPreview() = FileTypePreviewSurface(dark = false, UiStyle.MATERIAL) { EmptyWithAction() }

@PreviewNarrowScreens
@Composable
private fun EmptyStateActionGlassPreview() = FileTypePreviewSurface(dark = true, UiStyle.LIQUID_GLASS) { EmptyWithAction() }

@Composable
private fun ListPieces() {
    val status = statusColors()
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InitialTile(seed = "thesis", label = "Thesis research")
            InitialTile(seed = "linear", label = "Linear") { TileStatusDot(status.warning, ring) }
            InitialTile(seed = "home", label = "Home Assistant") { TileStatusDot(status.error, ring) }
            InitialTile(seed = "github", label = "GitHub") { TileStatusDot(status.success, ring) }
            InitialTile(seed = "docs", label = "東京", size = 48.dp) { TileStatusDot(status.neutral, ring) }
        }
        AdaptiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialTile(seed = "github", label = "GitHub") { TileStatusDot(status.success, ring) }
                Column {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Issues, pull requests and code search across your repositories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AdaptiveExtendedFloatingActionButton(
                text = { Text("Add server") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {},
            )
            AdaptiveExtendedFloatingActionButton(
                text = { Text("Add server") },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add server") },
                onClick = {},
                expanded = false,
            )
        }
    }
}

@Composable
private fun EmptyWithAction() {
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        EmptyState(
            title = "Keep related chats together",
            description = "A project groups chats on one topic. Move any chat into a project from its menu.",
            icon = Icons.Default.Folder,
            action = {
                AdaptiveButton(onClick = {}) { Text("Create project") }
            },
        )
    }
}
