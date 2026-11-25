package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.SsoLoginViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SsoLoginScreen(
    provider: String,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SsoLoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            currentOnLoginSuccess()
        }
    }

    SsoLoginContent(
        uiState = uiState,
        modifier = modifier,
        onBack = onBack,
        diagnosticLogLink = { DiagnosticLogLink() },
        webView = { webViewModifier ->
            SsoWebView(
                serverUrl = viewModel.serverUrl,
                provider = provider,
                keepProviderSession = viewModel.keepProviderSession,
                signedIn = uiState.isLoggedIn,
                onTokenCapture = viewModel::onTokenCapture,
                onOAuthError = viewModel::onOAuthError,
                onCaptureFail = viewModel::onCaptureFailed,
                modifier = webViewModifier,
            )
        },
    )
}
