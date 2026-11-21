package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.OtpVerificationDialog
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.mcp.McpOAuthSignInDialog
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.screen.sections.BackupCodesDialog
import com.garfiec.librechat.feature.settings.screen.sections.TwoFactorSetupDialog
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Suppress("LongParameterList")
@Immutable
class AccountSettingsActions(
    val retry: () -> Unit,
    val retryBalance: () -> Unit,
    val showAvatarDialog: () -> Unit,
    val dismissAvatarDialog: () -> Unit,
    val uploadAvatar: (ref: Any) -> Unit,
    val openEmailChange: () -> Unit,
    val onEmailChangeNewEmail: (String) -> Unit,
    val onEmailChangePassword: (String) -> Unit,
    val sendEmailChange: () -> Unit,
    val dismissEmailChange: () -> Unit,
    val toggleTwoFactor: () -> Unit,
    val confirmEnableTwoFactor: (code: String) -> Unit,
    val dismissTwoFactorSetupDialog: () -> Unit,
    val confirmDisableTwoFactor: (String?, String?) -> Unit,
    val dismissDisableTwoFactorDialog: () -> Unit,
    val enableTwoFactorWithOtp: (String?, String?) -> Unit,
    val dismissEnableTwoFactorOtpDialog: () -> Unit,
    val requestRegenerateBackupCodes: () -> Unit,
    val confirmRegenerateBackupCodes: () -> Unit,
    val dismissRegenerateBackupCodesConfirm: () -> Unit,
    val regenerateBackupCodesWithOtp: (String?, String?) -> Unit,
    val dismissBackupCodesOtpDialog: () -> Unit,
    val dismissBackupCodesDialog: () -> Unit,
    val logout: () -> Unit,
    val deleteAccount: (token: String?, backupCode: String?) -> Unit,
    val dismissDeleteAccountOtpDialog: () -> Unit,
    val pullRequestRowClick: () -> Unit,
    val pullRequestSignInOutcome: (McpOAuthOutcome) -> Unit,
    val confirmPullRequestDisconnect: () -> Unit,
    val dismissPullRequestDisconnect: () -> Unit,
)

/** Settings › Account's page: [uiState] and [pullRequestAccount] drawn, every change sent through [actions]. */
@Composable
fun AccountSettingsContent(
    uiState: AccountSettingsUiState,
    pullRequestAccount: PullRequestAccountUiState,
    actions: AccountSettingsActions,
    onNavigateToPasskeys: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    rememberAvatarPicker: @Composable (onPick: (PickedAvatar) -> Unit) -> () -> Unit = { {} },
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "account_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_profile))
            }
            adaptiveSection {
                row(key = "account_info") {
                    AccountInfoSection(
                        user = uiState.user,
                        profileLoadError = uiState.profileLoadError,
                        onAvatarClick = actions.showAvatarDialog,
                        onRetry = actions.retry,
                    )
                }
                val email = uiState.user?.email
                if (uiState.canChangeEmail && email != null) {
                    row(key = "change_email") {
                        SettingsRow(
                            icon = Icons.Default.Email,
                            title = stringResource(Res.string.email_change_row_title),
                            summary = email,
                            onClick = actions.openEmailChange,
                        )
                    }
                }
            }

            // Balance section — only when the server tracks balances
            if (uiState.balanceEnabled) {
                item(key = "balance_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_balance))
                }
                adaptiveSection {
                    balanceRows(uiState.balance, onRetry = actions.retryBalance)
                }
            }

            if (uiState.showTwoFactor || uiState.showPasskeys) {
                item(key = "security_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_security))
                }
            }
            adaptiveSection {
                // Federated accounts sign in through their identity provider, which never asks for this code.
                if (uiState.showTwoFactor) {
                    row(key = "security_settings") {
                        SecuritySection(
                            isTwoFactorEnabled = uiState.isTwoFactorEnabled,
                            isLoading = uiState.isTwoFactorLoading,
                            canDisableTwoFactor = uiState.canDisableTwoFactor,
                            onToggleTwoFactor = actions.toggleTwoFactor,
                            onRegenerateBackupCodes = actions.requestRegenerateBackupCodes,
                        )
                    }
                }
                if (uiState.showPasskeys) {
                    row(key = "passkeys_row") {
                        val count = uiState.passkeyCount
                        SettingsRow(
                            icon = Icons.Default.Fingerprint,
                            title = stringResource(Res.string.passkeys_title),
                            summary = if (count != null) {
                                pluralStringResource(Res.plurals.passkeys_row_count, count, count)
                            } else {
                                stringResource(Res.string.passkeys_row_subtitle)
                            },
                            onClick = onNavigateToPasskeys,
                        )
                    }
                }
            }

            // Connected accounts: web's Settings › Account › Connected accounts section.
            if (pullRequestAccount.visible) {
                item(key = "connections_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_connected_accounts))
                }
                adaptiveSection {
                    row(key = "pull_request_account") {
                        PullRequestAccountRow(
                            state = pullRequestAccount,
                            onClick = actions.pullRequestRowClick,
                        )
                    }
                }
            }

            item(key = "sign_out_gap") { Spacer(modifier = Modifier.height(24.dp)) }
            adaptiveSection {
                row(key = "sign_out") {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(Res.string.action_sign_out),
                        onClick = { showLogoutDialog = true },
                        enabled = !uiState.isDeletingAccount,
                        showChevron = false,
                    )
                }
            }

            // Danger zone — destructive, irreversible actions only.
            if (uiState.allowAccountDeletion) {
                item(key = "danger_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_danger_zone))
                }
                adaptiveSection {
                    row(key = "delete_account") {
                        SettingsRow(
                            icon = Icons.Default.DeleteForever,
                            title = stringResource(Res.string.delete_account),
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.isDeletingAccount,
                            destructive = true,
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        pullRequestAccount.signIn?.let { launch ->
            McpOAuthSignInDialog(launch = launch, onOutcome = actions.pullRequestSignInOutcome)
        }
        if (pullRequestAccount.confirmingDisconnect) {
            PullRequestDisconnectDialog(
                state = pullRequestAccount,
                onConfirm = actions.confirmPullRequestDisconnect,
                onDismiss = actions.dismissPullRequestDisconnect,
            )
        }

        uiState.emailChange?.let { draft ->
            EmailChangeDialog(
                draft = draft,
                currentEmail = uiState.user?.email.orEmpty(),
                coolingDown = uiState.emailChangeCoolingDown,
                onNewEmailChange = actions.onEmailChangeNewEmail,
                onPasswordChange = actions.onEmailChangePassword,
                onSend = actions.sendEmailChange,
                onDismiss = actions.dismissEmailChange,
            )
        }

        // Avatar upload dialog
        if (uiState.showAvatarDialog) {
            AvatarUploadDialog(
                currentAvatarUrl = uiState.user?.avatar,
                isUploading = uiState.isAvatarUploading,
                onPickImage = actions.uploadAvatar,
                onDismiss = actions.dismissAvatarDialog,
                rememberAvatarPicker = rememberAvatarPicker,
            )
        }

        // 2FA enable setup dialog
        if (uiState.showTwoFactorSetupDialog) {
            TwoFactorSetupDialog(
                otpauthUrl = uiState.twoFactorOtpauthUrl,
                isLoading = uiState.isTwoFactorLoading,
                onConfirm = actions.confirmEnableTwoFactor,
                onDismiss = actions.dismissTwoFactorSetupDialog,
            )
        }

        // 2FA disable dialog. Takes a backup code too: losing the authenticator is the usual
        // reason to turn 2FA off, and the server accepts either.
        if (uiState.showDisableTwoFactorDialog) {
            OtpVerificationDialog(
                title = stringResource(Res.string.dialog_title_disable_2fa),
                description = stringResource(Res.string.twofa_disable_instructions),
                isLoading = uiState.isTwoFactorLoading,
                onVerify = { token, backupCode ->
                    actions.confirmDisableTwoFactor(token, backupCode)
                },
                onDismiss = actions.dismissDisableTwoFactorDialog,
                verifyLabel = stringResource(Res.string.otp_verify),
                cancelLabel = stringResource(Res.string.otp_cancel),
                backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
                useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
                useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
            )
        }

        // Regenerating invalidates every existing code, so it is confirmed before the code prompt.
        if (uiState.showRegenerateBackupCodesConfirm) {
            AdaptiveAlertDialog(
                onDismissRequest = actions.dismissRegenerateBackupCodesConfirm,
                title = { Text(stringResource(Res.string.dialog_title_regenerate_backup_codes)) },
                text = { Text(stringResource(Res.string.regenerate_backup_codes_warning)) },
                confirmButton = {
                    AdaptiveTextButton(onClick = actions.confirmRegenerateBackupCodes) {
                        Text(stringResource(Res.string.action_regenerate))
                    }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = actions.dismissRegenerateBackupCodesConfirm) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }

        // Backup codes dialog
        if (uiState.showBackupCodesDialog) {
            BackupCodesDialog(
                backupCodes = uiState.backupCodes,
                onDismiss = actions.dismissBackupCodesDialog,
                warning = if (uiState.twoFactorSignInRequired) stringResource(Res.string.twofa_sessions_incomplete) else null,
                note = if (uiState.twoFactorOtherSessionsSignedOut) {
                    stringResource(Res.string.twofa_other_sessions_signed_out)
                } else {
                    null
                },
            )
        }

        // Logout confirmation dialog
        if (showLogoutDialog) {
            ConfirmDialog(
                title = Res.string.dialog_title_sign_out,
                message = Res.string.dialog_sign_out_message,
                confirmLabel = Res.string.action_sign_out,
                destructive = false,
                onConfirm = actions.logout,
                onDismiss = { showLogoutDialog = false },
            )
        }

        if (showDeleteDialog) {
            ConfirmDialog(
                title = Res.string.dialog_title_delete_account,
                message = Res.string.dialog_delete_account_message,
                confirmLabel = Res.string.action_delete,
                destructive = true,
                onConfirm = { actions.deleteAccount(null, null) },
                onDismiss = { showDeleteDialog = false },
            )
        }

        // OTP dialog for account deletion when 2FA is enabled
        if (uiState.showDeleteAccountOtpDialog) {
            OtpVerificationDialog(
                title = stringResource(Res.string.otp_title_verify_identity),
                description = stringResource(Res.string.otp_desc_delete_account),
                isLoading = uiState.isDeletingAccount,
                onVerify = { token, backupCode ->
                    actions.deleteAccount(token, backupCode)
                },
                onDismiss = actions.dismissDeleteAccountOtpDialog,
                verifyLabel = stringResource(Res.string.otp_verify),
                cancelLabel = stringResource(Res.string.otp_cancel),
                backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
                useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
                useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
            )
        }

        // OTP dialog for enabling 2FA when re-enrolling
        if (uiState.showEnableTwoFactorOtpDialog) {
            OtpVerificationDialog(
                title = stringResource(Res.string.otp_title_verify_identity),
                description = stringResource(Res.string.otp_desc_reenroll_2fa),
                isLoading = uiState.isTwoFactorLoading,
                onVerify = { token, backupCode ->
                    actions.enableTwoFactorWithOtp(token, backupCode)
                },
                onDismiss = actions.dismissEnableTwoFactorOtpDialog,
                verifyLabel = stringResource(Res.string.otp_verify),
                cancelLabel = stringResource(Res.string.otp_cancel),
                backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
                useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
                useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
            )
        }

        // OTP dialog for regenerating backup codes
        if (uiState.showBackupCodesOtpDialog) {
            OtpVerificationDialog(
                title = stringResource(Res.string.otp_title_verify_identity),
                description = stringResource(Res.string.otp_desc_regenerate_backup_codes),
                isLoading = uiState.isTwoFactorLoading,
                onVerify = { token, backupCode ->
                    actions.regenerateBackupCodesWithOtp(token, backupCode)
                },
                onDismiss = actions.dismissBackupCodesOtpDialog,
                verifyLabel = stringResource(Res.string.otp_verify),
                cancelLabel = stringResource(Res.string.otp_cancel),
                backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
                useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
                useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
            )
        }
    }
}
