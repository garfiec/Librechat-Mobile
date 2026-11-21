package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// Empty above typed: a placeholder that wraps shows as the empty bar standing taller.

@PreviewNarrowScreens
@Composable
private fun InConvoSearchBarMaterialPreview() = SearchBarSurface(UiStyle.MATERIAL) { SearchBarHost() }

@PreviewNarrowScreens
@Composable
private fun InConvoSearchBarGlassPreview() = SearchBarSurface(UiStyle.LIQUID_GLASS) { SearchBarHost() }

@Composable
private fun SearchBarSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun SearchBarHost() {
    Column {
        listOf("", "latency").forEach { initial ->
            var query by remember { mutableStateOf(initial) }
            InConvoSearchBar(
                query = query,
                onQueryChange = { query = it },
                currentMatchIndex = 0,
                totalMatches = if (query.isEmpty()) 0 else 3,
                onPreviousMatch = {},
                onNextMatch = {},
                onClose = {},
            )
        }
    }
}
