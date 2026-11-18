package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Single-line text input with label, optional placeholder, and description. A number past [max] is
 * pulled back to it as typed; one below [min] only when the field loses focus, so a number being
 * typed up to the floor is left alone.
 */
@Composable
fun DynamicInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    description: String? = null,
    min: Double? = null,
    max: Double? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val field = rememberDraftField(value)
        var focused by remember { mutableStateOf(false) }
        AdaptiveOutlinedTextField(
            value = field.value,
            onValueChange = { field.onValueChange(it.clampedTo(min = null, max = max), onValueChange) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) {
                        field.onValueChange(field.value.clampedTo(min, max), onValueChange)
                    }
                    focused = state.isFocused
                },
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else {
                null
            },
            singleLine = true,
        )
    }
}

private fun TextFieldValue.clampedTo(min: Double?, max: Double?): TextFieldValue {
    val clamped = clampNumericText(text, min, max)
    return if (clamped == text) this else TextFieldValue(clamped, TextRange(clamped.length))
}

/** [text] moved to the nearer bound when it is a number outside [min]..[max]; anything else unchanged. */
internal fun clampNumericText(text: String, min: Double?, max: Double?): String {
    val number = text.trim().toDoubleOrNull() ?: return text
    val clamped = when {
        max != null && number > max -> max
        min != null && number < min -> min
        else -> return text
    }
    return if (clamped % 1.0 == 0.0) clamped.toLong().toString() else clamped.toString()
}

@Preview
@Composable
private fun DynamicInputPreview() {
    var text by remember { mutableStateOf("") }
    DynamicInput(
        label = "System Prompt",
        value = text,
        onValueChange = { text = it },
        placeholder = "Enter a system prompt...",
        description = "Custom instructions for the model.",
    )
}
