package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.screen.AgentChipRow
import com.garfiec.librechat.feature.agents.viewmodel.AgentChip
import com.garfiec.librechat.feature.agents.viewmodel.availableChips

// What /api/agents/categories serves: the built-in categories carry a localization key as their
// label, a custom one carries the admin's own label.
private val categories = listOf(
    AgentCategory(value = "general", label = "com_agents_category_general"),
    AgentCategory(value = "hr", label = "com_agents_category_hr"),
    AgentCategory(value = "rd", label = "com_agents_category_rd"),
    AgentCategory(value = "aftersales", label = "com_agents_category_aftersales"),
    AgentCategory(value = "legal", label = "Legal"),
    AgentCategory(value = "promoted", label = "com_agents_top_picks"),
    AgentCategory(value = "all", label = "com_agents_all"),
)

@PreviewNarrowScreens
@Composable
private fun AgentCategoryMaterialPreview() = AgentCategoryHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun AgentCategoryGlassPreview() = AgentCategoryHost(UiStyle.LIQUID_GLASS)

@Composable
private fun AgentCategoryHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                var editorCategory by remember { mutableStateOf("rd") }
                AgentCategorySelector(
                    selectedCategory = editorCategory,
                    categories = categories,
                    onCategorySelect = { editorCategory = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                var chip by remember { mutableStateOf<AgentChip>(AgentChip.Category("hr")) }
                AgentChipRow(
                    chips = availableChips(categories, canCreate = true),
                    selected = chip,
                    categoriesByValue = categories.associateBy { it.value },
                    onSelect = { chip = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
