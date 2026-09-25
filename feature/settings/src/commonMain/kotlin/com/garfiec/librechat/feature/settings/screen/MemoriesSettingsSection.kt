package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.model.memoryKeyProblem
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MemoriesSettingsSection(
    memories: List<Memory>,
    memoriesEnabled: Boolean,
    showMemoryDialog: Boolean,
    editingMemory: Memory?,
    enforceKeyPattern: Boolean,
    onToggleEnable: (Boolean) -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (Memory) -> Unit,
    onDeleteMemory: (Memory) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveMemory: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Enable/disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.enable_memories),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.enable_memories_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val toggleMemoriesCd = stringResource(Res.string.cd_toggle_memories)
                Switch(
                    checked = memoriesEnabled,
                    onCheckedChange = onToggleEnable,
                    modifier = Modifier.semantics {
                        contentDescription = toggleMemoriesCd
                    },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Memory list
            if (memories.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_memories_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                memories.forEach { memory ->
                    MemoryItem(
                        memory = memory,
                        onEdit = { onEditMemory(memory) },
                        onDelete = { onDeleteMemory(memory) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onAddMemory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.add_memory))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        // Create/edit dialog
        if (showMemoryDialog) {
            MemoryDialog(
                memories = memories,
                enforceKeyPattern = enforceKeyPattern,
                editingMemory = editingMemory,
                onDismiss = onDismissDialog,
                onSave = onSaveMemory,
            )
        }

        // Delete confirmation is handled inline via the delete icon
    }
}

@Composable
private fun MemoryItem(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Same contract as the full Memories screen: a content-filtered entry arrives with its fields
    // BLANKED rather than omitted, so it must say so, and editing it would PATCH that blank back
    // over the real content through a key the server has also blanked.
    val redacted = memory.contentFilterBlocked == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (redacted) Modifier else Modifier.clickable(onClick = onEdit))
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.key.ifBlank {
                        if (redacted) stringResource(Res.string.memory_redacted_key) else ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (redacted) {
                        stringResource(Res.string.memory_redacted_value)
                    } else {
                        memory.value
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!redacted) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.cd_edit),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.cd_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.dialog_title_delete_memory)) },
            text = { Text(stringResource(Res.string.dialog_delete_memory_message, memory.key)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryDialog(
    memories: List<Memory>,
    enforceKeyPattern: Boolean,
    editingMemory: Memory?,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = editingMemory != null
    var key by remember { mutableStateOf(editingMemory?.key ?: "") }
    var value by remember { mutableStateOf(editingMemory?.value ?: "") }
    // Only CREATE carries a key: the update path sends a value alone, so the server's rename arm
    // never fires and there is nothing here to validate.
    val keyProblem = if (isEditing) {
        null
    } else {
        memoryKeyProblem(key = key, memories = memories, enforcePattern = enforceKeyPattern)
    }
    // A blank key is already what disables the button; surfacing it as an error would put one
    // under the field the moment the dialog opens.
    val keyError = when (keyProblem) {
        MemoryKeyProblem.PATTERN -> Res.string.memory_key_invalid
        MemoryKeyProblem.DUPLICATE -> Res.string.memory_key_exists
        else -> null
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditing) Res.string.edit_memory else Res.string.add_memory))
        },
        text = {
            Column(modifier = Modifier.imePadding()) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(Res.string.memory_key_label)) },
                    singleLine = true,
                    enabled = !isEditing,
                    isError = keyError != null,
                    // The hint rides on every server — it costs nothing where the shape is not
                    // enforced, and it is the only thing that explains the refusal where it is.
                    supportingText = if (isEditing) {
                        null
                    } else {
                        { Text(stringResource(keyError ?: Res.string.memory_key_hint)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(Res.string.memory_value_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(key.trim(), value.trim())
                },
                enabled = key.isNotBlank() && value.isNotBlank() && keyError == null,
            ) {
                Text(stringResource(if (isEditing) Res.string.action_save else Res.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
