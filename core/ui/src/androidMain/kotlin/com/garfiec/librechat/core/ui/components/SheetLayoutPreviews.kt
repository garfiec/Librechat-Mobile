package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The tag sheet with enough tags to fill a phone, and the empty state a search lands on. Both run
// out of room once the keyboard is up.

@PreviewNarrowScreens
@Composable
private fun TagSheetGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { TagSheetHost() }

@PreviewNarrowScreens
@Composable
private fun TagSheetMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { TagSheetHost() }

@PreviewNarrowScreens
@Composable
private fun EmptyStatePreview() = PreviewSurface(UiStyle.MATERIAL) { EmptyStateHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

@Composable
private fun TagSheetHost() {
    FilterChipBottomSheet(
        items = previewTags,
        selectedItems = setOf("work", "travel"),
        onSelectionChange = {},
        label = { it },
        onDismiss = {},
        title = "Tags",
        onAdd = { it },
        addPlaceholder = "New tag",
        addContentDescription = "Add tag",
    )
}

@Composable
private fun EmptyStateHost() {
    EmptyState(
        title = "No results",
        description = "Try a different search term",
        icon = Icons.Default.Search,
    )
}

private val previewTags = listOf(
    "work", "personal", "travel", "recipes", "kotlin", "compose", "taxes", "reading list",
    "health", "garden", "side project", "interview prep", "music", "home", "ideas",
)
