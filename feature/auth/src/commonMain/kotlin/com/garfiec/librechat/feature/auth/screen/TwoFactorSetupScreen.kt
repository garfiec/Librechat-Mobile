package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Required 2FA setup (v0.8.9-rc1), reached when sign-in, a refresh or any request reports that the
 * server enforces 2FA and this account has none. Mirrors upstream's four phases: start, add to an
 * authenticator, verify a code, keep the backup codes. Completing it signs the user in.
 */
@Composable
fun TwoFactorSetupScreen(
    tempToken: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the switcher on the other signed-in accounts; null hides it. Offered before setup starts. */
    onUseAnotherAccount: (() -> Unit)? = null,
    viewModel: TwoFactorSetupViewModel = koinViewModel { parametersOf(tempToken) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) currentOnComplete()
    }

    TwoFactorSetupContent(
        uiState = uiState,
        actions = TwoFactorSetupActions(
            onStart = viewModel::start,
            onContinueToVerify = viewModel::continueToVerify,
            onCodeChange = viewModel::onCodeChanged,
            onConfirm = viewModel::confirm,
            onBackToAuthenticator = viewModel::backToAuthenticator,
            onCodesSaved = viewModel::onCodesSaved,
            onCodesAcknowledged = viewModel::setCodesAcknowledged,
            onComplete = viewModel::complete,
            onLeave = {
                viewModel.leave()
                onBack()
            },
            onBack = onBack,
        ),
        modifier = modifier,
        onUseAnotherAccount = onUseAnotherAccount,
    )
}
