package com.garfiec.librechat.feature.conversations.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// Collapsed with two tags picked: the bar's only text is then the active-tag count.
private val tags = listOf("work", "travel", "recipes").map { ConversationTag(id = it, tag = it) }

@PreviewNarrowScreens
@Composable
private fun TagFilterBarMaterialPreview() = TagFilterBarHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun TagFilterBarGlassPreview() = TagFilterBarHost(UiStyle.LIQUID_GLASS)

@Composable
private fun TagFilterBarHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            var selected by remember { mutableStateOf(setOf("work", "travel")) }
            TagFilterBar(
                tags = tags,
                selectedTags = selected,
                onTagToggle = { tag -> selected = if (tag in selected) selected - tag else selected + tag },
                onClearFilter = { selected = emptySet() },
            )
        }
    }
}
