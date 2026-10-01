package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ParameterDefinition
import com.garfiec.librechat.core.model.ParameterType
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Dispatches a list of ParameterDefinitions to typed controls (slider, dropdown, checkbox, input, textarea) by ParameterType. */
@Composable
fun DynamicParameterPanel(
    definitions: List<ParameterDefinition>,
    values: Map<String, String>,
    onValueChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Endpoint Parameters",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        definitions.forEach { definition ->
            val currentValue = values[definition.key] ?: definition.default.orEmpty()

            when (definition.type) {
                ParameterType.SLIDER -> {
                    val min = definition.min?.toFloat() ?: 0f
                    val max = definition.max?.toFloat() ?: 1f
                    val step = definition.step?.toFloat() ?: 0.1f
                    val floatValue = currentValue.toFloatOrNull() ?: min

                    DynamicSlider(
                        label = definition.label,
                        value = floatValue,
                        onValueChange = { onValueChange(definition.key, it.toString()) },
                        min = min,
                        max = max,
                        step = step,
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.DROPDOWN -> {
                    DynamicDropdown(
                        label = definition.label,
                        selectedValue = currentValue,
                        options = definition.options ?: emptyList(),
                        onValueChange = { onValueChange(definition.key, it) },
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.ENUM_SLIDER -> {
                    DynamicEnumSlider(
                        label = definition.label,
                        selectedValue = currentValue,
                        options = definition.options ?: emptyList(),
                        onValueChange = { onValueChange(definition.key, it) },
                        description = definition.localizedDescription(),
                        optionLabels = definition.optionLabels,
                    )
                }

                ParameterType.CHECKBOX -> {
                    val checked = currentValue.toBooleanStrictOrNull() ?: false
                    DynamicCheckbox(
                        label = definition.label,
                        checked = checked,
                        onCheckedChange = { onValueChange(definition.key, it.toString()) },
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.TEXT -> {
                    DynamicInput(
                        label = definition.label,
                        value = currentValue,
                        onValueChange = { onValueChange(definition.key, it) },
                        placeholder = definition.default,
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.SWITCH -> {
                    val checked = currentValue.toBooleanStrictOrNull() ?: false
                    DynamicCheckbox(
                        label = definition.label,
                        checked = checked,
                        onCheckedChange = { onValueChange(definition.key, it.toString()) },
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.TEXTAREA -> {
                    DynamicTextarea(
                        label = definition.label,
                        value = currentValue,
                        onValueChange = { onValueChange(definition.key, it) },
                        placeholder = definition.default,
                        description = definition.localizedDescription(),
                    )
                }

                ParameterType.TAGS -> {
                    DynamicTagsInput(
                        label = definition.label,
                        tags = if (currentValue.isBlank()) {
                            emptyList()
                        } else {
                            currentValue.split("\n").filter { it.isNotBlank() }
                        },
                        onTagsChange = { tags ->
                            onValueChange(definition.key, tags.joinToString("\n"))
                        },
                        description = definition.localizedDescription(),
                        maxTags = definition.max?.toInt() ?: 4,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DynamicParameterPanelPreview() {
    val definitions = listOf(
        ParameterDefinition(
            key = "temperature",
            label = "Temperature",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 2.0,
            step = 0.1,
            default = "1.0",
            description = "Controls randomness of the output.",
        ),
        ParameterDefinition(
            key = "model",
            label = "Model",
            type = ParameterType.DROPDOWN,
            options = listOf("gpt-4", "gpt-3.5-turbo"),
            default = "gpt-4",
        ),
        ParameterDefinition(
            key = "stream",
            label = "Stream",
            type = ParameterType.SWITCH,
            default = "true",
            description = "Enable streaming output.",
        ),
        ParameterDefinition(
            key = "stop",
            label = "Stop Sequence",
            type = ParameterType.TEXT,
            description = "Sequence where the model stops generating.",
        ),
    )
    var values by remember { mutableStateOf(emptyMap<String, String>()) }
    DynamicParameterPanel(
        definitions = definitions,
        values = values,
        onValueChange = { key, value ->
            values = values.toMutableMap().apply { this[key] = value }
        },
    )
}
