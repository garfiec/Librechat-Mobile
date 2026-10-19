package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The built-in categories, by value. The server labels these with a web localization key
 * ("com_agents_category_hr"), which names a string this app carries rather than text to show.
 */
private val CATEGORY_LABELS: Map<String, StringResource> = mapOf(
    "general" to Res.string.general,
    "hr" to Res.string.agent_category_hr,
    "rd" to Res.string.agent_category_rd,
    "finance" to Res.string.agent_category_finance,
    "it" to Res.string.agent_category_it,
    "sales" to Res.string.agent_category_sales,
    "aftersales" to Res.string.agent_category_aftersales,
    "idea" to Res.string.agent_category_idea,
    "travel" to Res.string.agent_category_travel,
    "teach_or_explain" to Res.string.agent_category_learning,
    "write" to Res.string.agent_category_writing,
    "shop" to Res.string.agent_category_shopping,
    "code" to Res.string.agent_category_code,
    "misc" to Res.string.agent_category_misc,
    "roleplay" to Res.string.agent_category_roleplay,
)

/** A category's own label when an admin wrote one, else the translated built-in, else its value. */
@Composable
internal fun AgentCategory.displayLabel(): String {
    val rawLabel = label
    if (rawLabel != null && !rawLabel.startsWith("com_")) {
        return rawLabel
    }
    return categoryValueLabel(value)
}

@Composable
private fun categoryValueLabel(value: String): String =
    CATEGORY_LABELS[value]?.let { stringResource(it) } ?: value.replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCategorySelector(
    selectedCategory: String,
    categories: List<AgentCategory>,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Filter out special categories (promoted, all) used only in the marketplace
    val editorCategories = remember(categories) {
        categories.filter { it.value != "promoted" && it.value != "all" }
    }

    val displayValue = editorCategories.find { it.value == selectedCategory }?.displayLabel()
        ?: selectedCategory.takeIf { it.isNotEmpty() }?.let { categoryValueLabel(it) }
        ?: stringResource(Res.string.general)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        AdaptiveOutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.label_category)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            editorCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayLabel()) },
                    onClick = {
                        onCategorySelect(category.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
