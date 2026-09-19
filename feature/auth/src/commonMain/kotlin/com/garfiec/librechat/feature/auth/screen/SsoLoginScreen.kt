package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.SsoLoginError
import com.garfiec.librechat.feature.auth.viewmodel.SsoLoginViewModel
import org.jetbrains.compose.resources.stringResource
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

    Box(modifier = modifier.fillMaxSize()) {
        SsoWebView(
            serverUrl = viewModel.serverUrl,
            provider = provider,
            onTokenCapture = viewModel::onTokenCapture,
            onOAuthError = viewModel::onOAuthError,
            onCaptureFail = viewModel::onCaptureFailed,
            modifier = Modifier.fillMaxSize(),
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.error?.let { error ->
            if (!uiState.isLoading) {
                SsoErrorPane(error = error, onBack = onBack)
            }
        }

        BackAffordanceOverlay(onBack)
    }
}

@Composable
private fun SsoErrorPane(error: SsoLoginError, onBack: (() -> Unit)?) {
    val fallback = stringResource(Res.string.sso_sign_in_failed)
    val message = when (error) {
        is SsoLoginError.Exchange -> error.message ?: fallback
        is SsoLoginError.Provider, SsoLoginError.CaptureFailed -> fallback
    }
    val code = (error as? SsoLoginError.Provider)?.code

    // Opaque, because this pane is drawn OVER the WebView: Android leaves a blank white page
    // under it and iOS leaves the stopped provider page. Without a surface behind it the
    // onSurface-coloured text is white-on-white in dark theme — the reason for the failure is
    // simply unreadable — and on iOS the abandoned page underneath stays interactive.
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (code != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onBack?.invoke() }) {
                    Text(stringResource(Res.string.back_to_sign_in))
                }
            }
        }
    }
}
