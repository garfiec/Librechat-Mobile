package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ApiKey
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.util.copyToClipboard
import org.jetbrains.compose.resources.stringResource

/**
 * Two-phase API key creation dialog.
 *
 * Phase 1: Name input form. Phase 2: Shows the newly created key value
 * with a copy-to-clipboard button. The key value is only available in
 * the creation response and cannot be retrieved again.
 */
@Composable
fun ApiKeyCreateDialog(
    createdKey: ApiKey?,
    isCreating: Boolean,
    onCreateKey: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (createdKey != null) {
        CreatedKeyDialog(
            apiKey = createdKey,
            onDismiss = onDismiss,
        )
    } else {
        CreateKeyFormDialog(
            isCreating = isCreating,
            onCreateKey = onCreateKey,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CreateKeyFormDialog(
    isCreating: Boolean,
    onCreateKey: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_create_api_key)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.api_key_name_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.hint_key_name)) },
                    placeholder = { Text(stringResource(Res.string.hint_key_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateKey(name.trim()) },
                enabled = name.isNotBlank() && !isCreating,
            ) {
                Text(stringResource(if (isCreating) Res.string.action_creating else Res.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CreatedKeyDialog(
    apiKey: ApiKey,
    onDismiss: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_api_key_created)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.api_key_created_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = apiKey.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = apiKey.keyValue ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                copyToClipboard(apiKey.keyValue ?: "", "API Key")
                                copied = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.cd_copy_api_key),
                            )
                        }
                    }
                }
                if (copied) {
                    Text(
                        text = stringResource(Res.string.copied_to_clipboard),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_done))
            }
        },
    )
}
