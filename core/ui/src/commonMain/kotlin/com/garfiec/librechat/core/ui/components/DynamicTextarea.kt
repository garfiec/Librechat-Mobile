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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Multi-line text input with configurable minLines/maxLines, label, and optional description. */
@Composable
fun DynamicTextarea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    description: String? = null,
    minLines: Int = 3,
    maxLines: Int = 6,
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
        AdaptiveOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else {
                null
            },
            minLines = minLines,
            maxLines = maxLines,
        )
    }
}

@Preview
@Composable
private fun DynamicTextareaPreview() {
    var text by remember { mutableStateOf("") }
    DynamicTextarea(
        label = "Custom Instructions",
        value = text,
        onValueChange = { text = it },
        placeholder = "Enter detailed instructions...",
        description = "Provide additional context for the model.",
        minLines = 3,
        maxLines = 6,
    )
}
