package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Reusable OTP verification dialog for 2FA-protected operations.
 *
 * Shows a 6-digit OTP input with visual digit boxes (paste-friendly, auto-submit on 6th digit, and
 * a Verify button to resend a kept code). Includes a "Use a backup code instead" toggle that
 * switches to a single text field for alphanumeric backup code input.
 *
 * @param title Dialog title (e.g. "Verify Identity", "Enter OTP")
 * @param description Optional description text shown below the title
 * @param isLoading Whether to disable inputs while an operation is in progress
 * @param onVerify Called with (token, backupCode) — one will be non-null
 * @param onDismiss Called when the dialog is dismissed
 * @param errorText Why the last code sent failed; shown under the input until the code is edited
 * @param verifyLabel Label for the verify button
 * @param cancelLabel Label for the cancel/dismiss button
 * @param backupCodeLabel Label for the backup code text field
 * @param useBackupToggleLabel Text for the "use backup code" toggle
 * @param useOtpToggleLabel Text for the "use OTP" toggle
 */
@Composable
fun OtpVerificationDialog(
    title: String,
    onVerify: (token: String?, backupCode: String?) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    isLoading: Boolean = false,
    errorText: String? = null,
    verifyLabel: String = "Verify",
    cancelLabel: String = "Cancel",
    backupCodeLabel: String = "Backup Code",
    useBackupToggleLabel: String = "Use a backup code instead",
    useOtpToggleLabel: String = "Use OTP code instead",
) {
    var otpValue by remember { mutableStateOf("") }
    var backupCodeValue by remember { mutableStateOf("") }
    var useBackupCode by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    // Every submit clears it, so a repeated rejection with the same text shows again.
    var edited by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            submitted = false
        }
    }

    LaunchedEffect(useBackupCode) {
        focusRequester.requestFocus()
    }

    AdaptiveAlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (useBackupCode) {
                    AdaptiveOutlinedTextField(
                        value = backupCodeValue,
                        onValueChange = {
                            backupCodeValue = it
                            edited = true
                        },
                        label = { Text(backupCodeLabel) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = VerbatimKeyboardOptions.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (backupCodeValue.isNotBlank()) {
                                    edited = false
                                    onVerify(null, backupCodeValue.trim())
                                }
                            },
                        ),
                    )
                } else {
                    OtpCodeInput(
                        value = otpValue,
                        onValueChange = { newValue ->
                            otpValue = newValue
                            edited = true
                            if (newValue.length == OTP_LENGTH && !submitted) {
                                submitted = true
                                edited = false
                                onVerify(newValue, null)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }

                val shownError = errorText.takeUnless { edited || isLoading }
                if (shownError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = shownError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("otp_error"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        edited = true
                        useBackupCode = !useBackupCode
                        otpValue = ""
                        backupCodeValue = ""
                    },
                    enabled = !isLoading,
                ) {
                    Text(
                        if (useBackupCode) useOtpToggleLabel else useBackupToggleLabel,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (useBackupCode) {
                TextButton(
                    onClick = {
                        edited = false
                        onVerify(null, backupCodeValue.trim())
                    },
                    enabled = !isLoading && backupCodeValue.isNotBlank(),
                ) {
                    Text(verifyLabel)
                }
            } else {
                TextButton(
                    onClick = {
                        submitted = true
                        edited = false
                        onVerify(otpValue, null)
                    },
                    enabled = !isLoading && otpValue.length == OTP_LENGTH,
                ) {
                    Text(verifyLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(cancelLabel)
            }
        },
    )
}
