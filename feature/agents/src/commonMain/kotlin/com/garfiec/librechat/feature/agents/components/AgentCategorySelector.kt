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
import org.jetbrains.compose.resources.stringResource

/**
 * Maps category values to human-readable English labels.
 * The server returns localization keys like "com_ui_idea" in the label field.
 * Since we don't have a localization system, we provide English labels inline.
 */
private val CATEGORY_DISPLAY_LABELS = mapOf(
    "idea" to "Ideas",
    "travel" to "Travel",
    "teach_or_explain" to "Learning",
    "write" to "Writing",
    "shop" to "Shopping",
    "code" to "Code",
    "misc" to "Misc.",
    "roleplay" to "Roleplay",
    "finance" to "Finance",
    "general" to "General",
)

private fun AgentCategory.displayLabel(): String {
    // If the label starts with "com_", it's a localization key -- look up the friendly name
    val rawLabel = label
    if (rawLabel != null && !rawLabel.startsWith("com_")) {
        return rawLabel
    }
    return CATEGORY_DISPLAY_LABELS[value] ?: value.replaceFirstChar { it.uppercase() }
}

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
        ?: CATEGORY_DISPLAY_LABELS[selectedCategory]
        ?: selectedCategory.ifEmpty { stringResource(Res.string.general) }

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
