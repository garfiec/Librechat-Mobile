package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountNotice
import com.garfiec.librechat.feature.settings.viewmodel.PullRequestAccountViewModel
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeNotice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Account: profile, balance, security, connected accounts, sign out and deletion. */
@Composable
fun AccountSettingsPage(
    onLogout: () -> Unit,
    onSignInAgain: () -> Unit,
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
    val currentOnSignInAgain by rememberUpdatedState(onSignInAgain)
    val retryLabel = stringResource(Res.string.action_retry)

    // Each resume after the first (back from a sub-page, or from another app) re-reads the profile,
    // so an email confirmed in the browser (or an avatar or name changed on the web) shows up, along
    // with the passkey count and balance. The first resume re-reads the balance too: in the two-pane
    // home this page shares the home's ViewModel, so re-entering it builds no fresh one.
    OnResume(onFirst = viewModel::refreshBalance) {
        viewModel.refreshOnReturn()
        // A connect made on the web, or one whose page was closed, shows on return.
        pullRequestAccountViewModel.refresh()
    }

    val errorResText = uiState.errorRes?.let { stringResource(it) }
    LaunchedEffect(uiState.error, errorResText) {
        val error = uiState.error ?: errorResText ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = retryLabel.takeIf { uiState.error != null },
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
    LaunchedEffect(uiState.isSignInAgainRequested) {
        if (uiState.isSignInAgainRequested) currentOnSignInAgain()
    }

    val actions = remember(viewModel, pullRequestAccountViewModel) {
        AccountSettingsActions(
            retry = viewModel::retry,
            retryBalance = viewModel::retryBalance,
            showAvatarDialog = viewModel::showAvatarDialog,
            dismissAvatarDialog = viewModel::dismissAvatarDialog,
            uploadAvatar = viewModel::uploadAvatar,
            openEmailChange = viewModel::openEmailChange,
            onEmailChangeNewEmail = viewModel::onEmailChangeNewEmail,
            onEmailChangePassword = viewModel::onEmailChangePassword,
            sendEmailChange = viewModel::sendEmailChange,
            dismissEmailChange = viewModel::dismissEmailChange,
            toggleTwoFactor = viewModel::toggleTwoFactor,
            confirmEnableTwoFactor = viewModel::confirmEnableTwoFactor,
            dismissTwoFactorSetupDialog = viewModel::dismissTwoFactorSetupDialog,
            confirmDisableTwoFactor = viewModel::confirmDisableTwoFactor,
            dismissDisableTwoFactorDialog = viewModel::dismissDisableTwoFactorDialog,
            enableTwoFactorWithOtp = viewModel::enableTwoFactorWithOtp,
            dismissEnableTwoFactorOtpDialog = viewModel::dismissEnableTwoFactorOtpDialog,
            requestRegenerateBackupCodes = viewModel::requestRegenerateBackupCodes,
            confirmRegenerateBackupCodes = viewModel::confirmRegenerateBackupCodes,
            dismissRegenerateBackupCodesConfirm = viewModel::dismissRegenerateBackupCodesConfirm,
            regenerateBackupCodesWithOtp = viewModel::regenerateBackupCodesWithOtp,
            dismissBackupCodesOtpDialog = viewModel::dismissBackupCodesOtpDialog,
            dismissBackupCodesDialog = viewModel::dismissBackupCodesDialog,
            logout = viewModel::logout,
            deleteAccount = viewModel::deleteAccount,
            dismissDeleteAccountOtpDialog = viewModel::dismissDeleteAccountOtpDialog,
            pullRequestRowClick = pullRequestAccountViewModel::onRowClick,
            pullRequestSignInOutcome = pullRequestAccountViewModel::onSignInOutcome,
            confirmPullRequestDisconnect = pullRequestAccountViewModel::confirmDisconnect,
            dismissPullRequestDisconnect = pullRequestAccountViewModel::dismissDisconnect,
        )
    }
    AccountSettingsContent(
        uiState = uiState,
        pullRequestAccount = pullRequestAccount,
        actions = actions,
        onNavigateToPasskeys = onNavigateToPasskeys,
        rememberAvatarPicker = { onPick -> rememberAvatarPicker(onPick) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
