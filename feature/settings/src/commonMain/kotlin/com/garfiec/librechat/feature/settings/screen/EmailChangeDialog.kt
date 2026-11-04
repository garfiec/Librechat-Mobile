package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.model.auth.EmailChangeError
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.IosTextContentType
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.rememberDraftField
import com.garfiec.librechat.core.ui.components.withIosTextContentType
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeDialogState
import org.jetbrains.compose.resources.stringResource

/**
 * "Change email address": the new address and the current password. Errors render inside the
 * dialog (a page snackbar would draw behind its scrim), under the field they concern. The draft
 * lives in the ViewModel, so it survives a rotation and a late answer can be dropped there; the
 * fields keep only their caret (see [rememberDraftField]).
 */
@Composable
internal fun EmailChangeDialog(
    draft: EmailChangeDialogState,
    currentEmail: String,
    coolingDown: Boolean,
    onNewEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val newEmail = rememberDraftField(draft.newEmail)
    val password = rememberDraftField(draft.password, resetKey = draft.passwordResets)
    val canSend = !draft.isSending && !coolingDown &&
        draft.newEmail.isNotBlank() && draft.password.isNotEmpty()

    LaunchedEffect(Unit) { emailFocus.requestFocus() }
    LaunchedEffect(draft.passwordResets) { if (draft.passwordResets > 0) passwordFocus.requestFocus() }
    LaunchedEffect(draft.emailError) { if (draft.emailError != null) emailFocus.requestFocus() }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clearFocusOnTap(),
        properties = DialogProperties(
            dismissOnBackPress = !draft.isSending,
            dismissOnClickOutside = !draft.isSending,
        ),
        title = { Text(stringResource(Res.string.email_change_title)) },
        text = {
            // AdaptiveAlertDialog scrolls its text slot itself, so a 320dp screen at 2x font with the
            // keyboard up can still reach both fields; no scroll of our own (it would nest).
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.email_change_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.email_change_current, currentEmail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdaptiveOutlinedTextField(
                    value = newEmail.value,
                    onValueChange = { newEmail.onValueChange(it, onNewEmailChange) },
                    label = { Text(stringResource(Res.string.email_change_new_email)) },
                    singleLine = true,
                    enabled = !draft.isSending,
                    isError = draft.emailError != null,
                    supportingText = draft.emailError?.let { error -> { Text(emailChangeErrorText(error)) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ).withIosTextContentType(IosTextContentType.EmailAddress),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocus)
                        .semantics { contentType = ContentType.EmailAddress },
                )
                // The login password, so a password manager may fill it: deliberately not SecretTextField.
                AdaptiveOutlinedTextField(
                    value = password.value,
                    onValueChange = { password.onValueChange(it, onPasswordChange) },
                    label = { Text(stringResource(Res.string.email_change_current_password)) },
                    singleLine = true,
                    enabled = !draft.isSending,
                    isError = draft.passwordError != null,
                    supportingText = draft.passwordError?.let { error -> { Text(emailChangeErrorText(error)) } },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Send,
                        autoCorrectEnabled = false,
                    ).withIosTextContentType(IosTextContentType.Password),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) Res.string.email_change_hide_password else Res.string.email_change_show_password,
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus)
                        .semantics { contentType = ContentType.Password },
                )
                val dialogError = draft.dialogError
                if (dialogError != null) {
                    Text(
                        text = emailChangeErrorText(dialogError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onSend, enabled = canSend) {
                if (draft.isSending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.email_change_sending))
                    }
                } else {
                    Text(stringResource(Res.string.email_change_send))
                }
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss, enabled = !draft.isSending) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun emailChangeErrorText(error: EmailChangeError): String = stringResource(
    when (error) {
        EmailChangeError.InvalidRequest -> Res.string.email_change_error_invalid_request
        EmailChangeError.SameEmail -> Res.string.email_change_error_same_email
        EmailChangeError.Disabled -> Res.string.email_change_error_disabled
        EmailChangeError.LocalAccountRequired -> Res.string.email_change_error_local_account_required
        EmailChangeError.CurrentPasswordInvalid -> Res.string.email_change_error_password_invalid
        EmailChangeError.DomainNotAllowed -> Res.string.email_change_error_domain_not_allowed
        EmailChangeError.EmailInUse -> Res.string.email_change_error_email_in_use
        EmailChangeError.AccountModified -> Res.string.email_change_error_account_modified
        EmailChangeError.RequestInProgress -> Res.string.email_change_error_request_in_progress
        EmailChangeError.DeliveryFailed -> Res.string.email_change_error_delivery_failed
        EmailChangeError.ServiceUnavailable -> Res.string.email_change_error_service_unavailable
        EmailChangeError.RateLimited -> Res.string.email_change_error_rate_limited
        EmailChangeError.Network -> Res.string.email_change_error_network
        EmailChangeError.Unknown -> Res.string.email_change_error_unknown
    },
)
