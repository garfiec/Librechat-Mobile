package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.LoginViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToTwoFactor: (String) -> Unit = {},
    onNavigateToTwoFactorSetup: (String) -> Unit = {},
    onNavigateToSso: (String) -> Unit = {},
    /** Opens the check-your-email screen, sending a fresh link to the given address. */
    onNavigateToVerifyEmail: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    /** Opens the switcher on the other signed-in accounts; null hides it. */
    onUseAnotherAccount: (() -> Unit)? = null,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)
    val currentOnNavigateToTwoFactor by rememberUpdatedState(onNavigateToTwoFactor)
    val currentOnNavigateToTwoFactorSetup by rememberUpdatedState(onNavigateToTwoFactorSetup)

    LaunchedEffect(uiState.twoFactorSetupTempToken) {
        val tempToken = uiState.twoFactorSetupTempToken
        if (tempToken != null) {
            currentOnNavigateToTwoFactorSetup(tempToken)
            viewModel.consumeTwoFactorSetupNavigation()
        }
    }

    val currentOnNavigateToSso by rememberUpdatedState(onNavigateToSso)
    LaunchedEffect(uiState.ssoProvider) {
        uiState.ssoProvider?.let { provider ->
            viewModel.consumeSsoNavigation()
            currentOnNavigateToSso(provider)
        }
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            currentOnLoginSuccess()
        }
    }

    LaunchedEffect(uiState.twoFactorTempToken) {
        val tempToken = uiState.twoFactorTempToken
        if (tempToken != null) {
            currentOnNavigateToTwoFactor(tempToken)
            // Consume the signal so backing out of the 2FA screen returns here instead of
            // re-triggering this effect against the retained ViewModel's stale token.
            viewModel.consumeTwoFactorNavigation()
        }
    }

    LoginContent(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onLogin = viewModel::login,
        onSsoProviderSelect = viewModel::onSsoProviderSelected,
        onSsoRiskAccept = viewModel::onSsoRiskAccepted,
        onSsoRiskDismiss = viewModel::onSsoRiskDismissed,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier,
        onNavigateToForgotPassword = onNavigateToForgotPassword,
        onNavigateToVerifyEmail = onNavigateToVerifyEmail,
        onBack = onBack,
        onUseAnotherAccount = onUseAnotherAccount,
        diagnosticLogLink = { DiagnosticLogLink() },
    )
}
