package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.model.memoryKeyProblem
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Add/edit memory dialog; key field is immutable when editing an existing memory.
 *
 * Validation follows the server's memory-key rule, enforced only where the server is known to
 * enforce it (see `MemoriesUiState.keyPatternEnforced`).
 */
@Composable
internal fun MemoryEditDialog(
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
    val keyProblem = remember(key, memories, enforceKeyPattern, isEditing) {
        if (isEditing) {
            null
        } else {
            memoryKeyProblem(key = key, memories = memories, enforcePattern = enforceKeyPattern)
        }
    }
    // A blank key is already what disables the button; surfacing it as an error would put one
    // under the field the moment the dialog opens.
    val keyError = when (keyProblem) {
        MemoryKeyProblem.PATTERN -> Res.string.memory_key_invalid
        MemoryKeyProblem.DUPLICATE -> Res.string.memory_key_exists
        else -> null
    }

    AdaptiveAlertDialog(
        modifier = modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditing) Res.string.edit_memory else Res.string.add_memory))
        },
        text = {
            Column(modifier = Modifier.imePadding()) {
                AdaptiveOutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(Res.string.memory_key_label)) },
                    singleLine = true,
                    enabled = !isEditing,
                    isError = keyError != null,
                    supportingText = if (isEditing) {
                        null
                    } else {
                        { Text(stringResource(keyError ?: Res.string.memory_key_hint)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AdaptiveOutlinedTextField(
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
                onClick = { onSave(key.trim(), value.trim()) },
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
