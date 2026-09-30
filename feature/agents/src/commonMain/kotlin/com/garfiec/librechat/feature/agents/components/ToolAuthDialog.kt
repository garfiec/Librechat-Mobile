package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Single-field API-key dialog used when a user toggles a tool that requires
 * authentication (Code Interpreter, Web Search providers). Mirrors upstream
 * `Code/ApiKeyDialog.tsx` -- the upstream Search dialog is multi-provider, so
 * this dialog is presented for one provider key per invocation.
 *
 * [isAlreadyAuthenticated] = true switches the primary action to "Revoke" --
 * useful when the user opened the dialog via the key-icon affordance after
 * the tool was already enabled.
 */
@Composable
fun ToolAuthDialog(
    title: String,
    fieldLabel: String,
    onSubmit: (apiKey: String) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    initialValue: String = "",
    isAlreadyAuthenticated: Boolean = false,
    onRevoke: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            if (isAlreadyAuthenticated && onRevoke != null) {
                TextButton(onClick = onRevoke) {
                    Text(
                        stringResource(Res.string.tool_auth_revoke),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}
