package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Tag input that renders chips and allows adding/removing text tags up to a max count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicTagsInput(
    label: String,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    maxTags: Int = 4,
) {
    var inputText by remember { mutableStateOf("") }

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

        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { tag ->
                    AdaptiveAssistChip(
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onTagsChange(tags - tag) },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $tag",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (tags.size < maxTags) {
            AdaptiveOutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add stop sequence\u2026") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty() && tags.size < maxTags) {
                            onTagsChange(tags + trimmed)
                            inputText = ""
                        }
                    },
                ),
            )
        }
    }
}

@Preview
@Composable
private fun DynamicTagsInputPreview() {
    var tags by remember { mutableStateOf(listOf("END", "STOP")) }
    DynamicTagsInput(
        label = "Stop Sequences",
        tags = tags,
        onTagsChange = { tags = it },
        description = "Up to 4 sequences where the model will stop generating.",
        maxTags = 4,
    )
}
