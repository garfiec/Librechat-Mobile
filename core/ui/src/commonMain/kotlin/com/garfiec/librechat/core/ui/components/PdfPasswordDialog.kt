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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        scrollableText = false,
        modifier = Modifier.testTag("pdf_password_dialog").clearFocusOnTap(),
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
                SecretTextField(
                    value = password,
                    onValueChange = { password = it },
                    revealed = revealed,
                    onRevealedChange = { revealed = it },
                    label = { Text(stringResource(Res.string.pdf_password_field)) },
                    isError = incorrectPassword,
                    supportingText = if (incorrectPassword) {
                        { Text(stringResource(Res.string.pdf_password_incorrect)) }
                    } else {
                        null
                    },
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    showDescription = Res.string.pdf_password_show,
                    hideDescription = Res.string.pdf_password_hide,
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
