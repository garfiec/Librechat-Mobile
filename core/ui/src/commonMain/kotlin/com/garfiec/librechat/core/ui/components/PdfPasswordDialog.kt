package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.pdf_password_cancel
import com.garfiec.librechat.core.ui.resources.pdf_password_confirm
import com.garfiec.librechat.core.ui.resources.pdf_password_explanation
import com.garfiec.librechat.core.ui.resources.pdf_password_field
import com.garfiec.librechat.core.ui.resources.pdf_password_hide
import com.garfiec.librechat.core.ui.resources.pdf_password_incorrect
import com.garfiec.librechat.core.ui.resources.pdf_password_sensitive_warning
import com.garfiec.librechat.core.ui.resources.pdf_password_show
import com.garfiec.librechat.core.ui.resources.pdf_password_title
import org.jetbrains.compose.resources.stringResource

/**
 * A picked PDF waiting on its password, as a screen's UI state carries it. [id] tells apart two
 * prompts that would otherwise be equal (same name, same flag), so a dialog keyed on the prompt
 * starts from an empty field for each file rather than keeping the last one's password.
 */
@Immutable
data class PdfPasswordPromptUi(val filename: String, val incorrectPassword: Boolean, val id: Long = 0)

/**
 * Asks for the password of a PDF the upload path refused, explaining that it is decrypted on this
 * device before upload and warning that the decrypted contents leave the device. Callers key it on
 * the prompt, so a re-prompt after a wrong password starts from an empty field.
 */
@Composable
fun PdfPasswordDialog(
    filename: String,
    incorrectPassword: Boolean,
    onSubmit: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (password.isNotEmpty()) onSubmit(password) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pdf_password_dialog"),
        title = { Text(stringResource(Res.string.pdf_password_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.pdf_password_explanation, filename),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(Res.string.pdf_password_sensitive_warning),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.pdf_password_field)) },
                    singleLine = true,
                    isError = incorrectPassword,
                    supportingText = if (incorrectPassword) {
                        { Text(stringResource(Res.string.pdf_password_incorrect)) }
                    } else {
                        null
                    },
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    // Keeps the IME from learning, suggesting or autocorrecting the password.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (revealed) Res.string.pdf_password_hide else Res.string.pdf_password_show,
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("pdf_password_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = password.isNotEmpty(),
                modifier = Modifier.testTag("pdf_password_confirm"),
            ) {
                Text(stringResource(Res.string.pdf_password_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.pdf_password_cancel)) }
        },
    )
}
