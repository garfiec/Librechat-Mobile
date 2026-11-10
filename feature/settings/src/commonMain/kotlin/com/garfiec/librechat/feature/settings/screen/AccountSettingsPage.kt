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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountNotice
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountViewModel
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeNotice
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Account: profile, balance, security, connected accounts, sign out and deletion. */
@Composable
fun AccountSettingsPage(
    onLogout: () -> Unit,
    onNavigateToPasskeys: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: AccountSettingsViewModel = koinViewModel(),
    pullRequestAccountViewModel: PullRequestAccountViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRequestAccount by pullRequestAccountViewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLogout by rememberUpdatedState(onLogout)
    val retryLabel = stringResource(Res.string.action_retry)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Each resume after the first (back from a sub-page, or from another app) re-reads the profile,
    // so an email confirmed in the browser (or an avatar or name changed on the web) shows up, along
    // with the passkey count.
    OnResume {
        viewModel.refreshOnReturn()
        // A connect made on the web, or one whose page was closed, shows on return.
        pullRequestAccountViewModel.refresh()
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = retryLabel,
        )
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retry()
        }
    }

    val emailNotice = uiState.emailChangeNotice
    val emailNoticeText = when (emailNotice) {
        is EmailChangeNotice.LinkSent -> stringResource(Res.string.email_change_link_sent, emailNotice.newEmail)
        EmailChangeNotice.Updated -> stringResource(Res.string.email_change_updated)
        null -> null
    }
    LaunchedEffect(emailNotice) {
        val text = emailNoticeText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text)
        // Consumed after showing: consuming first would change this effect's key and cancel the snackbar.
        viewModel.consumeEmailChangeNotice()
    }

    val pullRequestNotice = when (pullRequestAccount.notice) {
        PullRequestAccountNotice.CONNECT_FAILED -> stringResource(Res.string.pr_account_connect_error)
        PullRequestAccountNotice.NOT_AVAILABLE -> stringResource(Res.string.pr_account_not_available)
        null -> null
    }
    LaunchedEffect(pullRequestNotice) {
        val text = pullRequestNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text)
        pullRequestAccountViewModel.consumeNotice()
    }

    LaunchedEffect(uiState.isLoggedOut, uiState.isAccountDeleted) {
        if (uiState.isLoggedOut || uiState.isAccountDeleted) {
            currentOnLogout()
        }
    }

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
                        onAvatarClick = viewModel::showAvatarDialog,
                        onRetry = viewModel::retry,
                    )
                }
                val email = uiState.user?.email
                if (uiState.canChangeEmail && email != null) {
                    row(key = "change_email") {
                        SettingsRow(
                            icon = Icons.Default.Email,
                            title = stringResource(Res.string.email_change_row_title),
                            summary = email,
                            onClick = viewModel::openEmailChange,
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
                    balanceRows(uiState.balance, onRetry = viewModel::retryBalance)
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
                            onToggleTwoFactor = viewModel::toggleTwoFactor,
                            onRegenerateBackupCodes = viewModel::requestRegenerateBackupCodes,
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
                            onClick = pullRequestAccountViewModel::onRowClick,
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
            McpOAuthSignInDialog(launch = launch, onOutcome = pullRequestAccountViewModel::onSignInOutcome)
        }
        if (pullRequestAccount.confirmingDisconnect) {
            PullRequestDisconnectDialog(
                state = pullRequestAccount,
                onConfirm = pullRequestAccountViewModel::confirmDisconnect,
                onDismiss = pullRequestAccountViewModel::dismissDisconnect,
            )
        }

        uiState.emailChange?.let { draft ->
            EmailChangeDialog(
                draft = draft,
                currentEmail = uiState.user?.email.orEmpty(),
                coolingDown = uiState.emailChangeCoolingDown,
                onNewEmailChange = viewModel::onEmailChangeNewEmail,
                onPasswordChange = viewModel::onEmailChangePassword,
                onSend = viewModel::sendEmailChange,
                onDismiss = viewModel::dismissEmailChange,
            )
        }

        // Avatar upload dialog
        if (uiState.showAvatarDialog) {
            AvatarUploadDialog(
                currentAvatarUrl = uiState.user?.avatar,
                isUploading = uiState.isAvatarUploading,
                onPickImage = viewModel::uploadAvatar,
                onDismiss = viewModel::dismissAvatarDialog,
            )
        }

        // 2FA enable setup dialog
        if (uiState.showTwoFactorSetupDialog) {
            TwoFactorSetupDialog(
                otpauthUrl = uiState.twoFactorOtpauthUrl,
                isLoading = uiState.isTwoFactorLoading,
                onConfirm = viewModel::confirmEnableTwoFactor,
                onDismiss = viewModel::dismissTwoFactorSetupDialog,
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
                    viewModel.confirmDisableTwoFactor(token = token, backupCode = backupCode)
                },
                onDismiss = viewModel::dismissDisableTwoFactorDialog,
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
                onDismissRequest = viewModel::dismissRegenerateBackupCodesConfirm,
                title = { Text(stringResource(Res.string.dialog_title_regenerate_backup_codes)) },
                text = { Text(stringResource(Res.string.regenerate_backup_codes_warning)) },
                confirmButton = {
                    AdaptiveTextButton(onClick = viewModel::confirmRegenerateBackupCodes) {
                        Text(stringResource(Res.string.action_regenerate))
                    }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = viewModel::dismissRegenerateBackupCodesConfirm) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }

        // Backup codes dialog
        if (uiState.showBackupCodesDialog) {
            BackupCodesDialog(
                backupCodes = uiState.backupCodes,
                onDismiss = viewModel::dismissBackupCodesDialog,
            )
        }

        // Logout confirmation dialog
        if (showLogoutDialog) {
            ConfirmDialog(
                title = Res.string.dialog_title_sign_out,
                message = Res.string.dialog_sign_out_message,
                confirmLabel = Res.string.action_sign_out,
                destructive = false,
                onConfirm = viewModel::logout,
                onDismiss = { showLogoutDialog = false },
            )
        }

        if (showDeleteDialog) {
            ConfirmDialog(
                title = Res.string.dialog_title_delete_account,
                message = Res.string.dialog_delete_account_message,
                confirmLabel = Res.string.action_delete,
                destructive = true,
                onConfirm = { viewModel.deleteAccount() },
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
                    viewModel.deleteAccount(token = token, backupCode = backupCode)
                },
                onDismiss = viewModel::dismissDeleteAccountOtpDialog,
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
                    viewModel.enableTwoFactorWithOtp(token = token, backupCode = backupCode)
                },
                onDismiss = viewModel::dismissEnableTwoFactorOtpDialog,
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
                    viewModel.regenerateBackupCodesWithOtp(token = token, backupCode = backupCode)
                },
                onDismiss = viewModel::dismissBackupCodesOtpDialog,
                verifyLabel = stringResource(Res.string.otp_verify),
                cancelLabel = stringResource(Res.string.otp_cancel),
                backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
                useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
                useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
            )
        }
    }
}
