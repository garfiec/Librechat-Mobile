package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.prompts.components.PromptFilterSheetContent
import com.garfiec.librechat.feature.chat.prompts.components.PromptSortOrder

// The prompts editor's two titles, the library's two empty states, and the filter sheet's body with
// enough categories to wrap. The empty states fill their parent, so they are held at their own height.

private val categories = listOf("Writing", "Coding", "Customer support", "Marketing", "Research", "Translation")

@PreviewNarrowScreens
@Composable
private fun PromptLabelsMaterialPreview() = PromptLabelsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun PromptLabelsGlassPreview() = PromptLabelsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun PromptLabelsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PromptEditorTopBar(isNewPrompt = true, showVersions = false, onBack = {}, onShowVersions = {})
                PromptEditorTopBar(isNewPrompt = false, showVersions = true, onBack = {}, onShowVersions = {})
                PromptsEmptyState(isCategoryFiltered = false, modifier = Modifier.height(IntrinsicSize.Min))
                PromptsEmptyState(isCategoryFiltered = true, modifier = Modifier.height(IntrinsicSize.Min))
                PromptFilterSheetContent(
                    categories = categories,
                    selectedCategory = "Coding",
                    onCategorySelect = {},
                    sortOrder = PromptSortOrder.POPULAR,
                    onSortOrderChange = {},
                )
            }
        }
    }
}
