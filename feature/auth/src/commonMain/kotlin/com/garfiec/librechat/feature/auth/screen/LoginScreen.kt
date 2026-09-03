package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.brand.TchatBrand
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.auth.oauth.OAuthWebView
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.LoginViewModel
import com.garfiec.librechat.feature.auth.viewmodel.OAuthWebViewState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToTwoFactor: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)
    val currentOnNavigateToTwoFactor by rememberUpdatedState(onNavigateToTwoFactor)

    // Belt-and-braces fallback — see the comment on LoginViewModel.checkOAuthResult(). The primary
    // path is the OAuth WebView overlay's own onComplete below.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkOAuthResult()
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

    // TCHAT: honour the server's OPENID_AUTO_REDIRECT — fires once per screen instance (the
    // rememberSaveable latch, not just a LaunchedEffect key, because config fields don't change
    // mid-visit but the user backing out of the browser must land on a *usable* screen rather than
    // being thrown straight back into it). Only when there is genuinely nothing else to do: email
    // login is off, OIDC is on, and no error is already showing (an error means the last attempt —
    // possibly this very auto-redirect — didn't work, and relaunching over it would loop).
    var autoRedirectFired by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.openidAutoRedirect, uiState.emailLoginEnabled, uiState.socialLogins, uiState.error) {
        if (!autoRedirectFired &&
            uiState.openidAutoRedirect &&
            !uiState.emailLoginEnabled &&
            TchatBrand.OIDC_PROVIDER in uiState.socialLogins &&
            uiState.error == null
        ) {
            autoRedirectFired = true
            viewModel.launchOAuth(TchatBrand.OIDC_PROVIDER)
        }
    }

    // TCHAT: the in-app OAuth WebView, full-screen over everything else on this screen while a
    // flow is in progress. Rendered as an early return (not a sibling overlay) so the login form
    // underneath is never composed on top of / interactable behind it — same shape as
    // ServerUrlScreen's autoConnecting branch.
    val oAuthWebView = uiState.oAuthWebView
    if (oAuthWebView != null) {
        OAuthWebViewOverlay(
            state = oAuthWebView,
            onComplete = viewModel::onOAuthWebViewComplete,
            onDismiss = viewModel::dismissOAuthWebView,
            modifier = modifier,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(32.dp))

            // The email/password form is hidden when the server disables email login
            // (ALLOW_EMAIL_LOGIN=false, #14180) — it 403s the login POST, so offer only the
            // social providers below. Fail-open: the flag defaults true until config resolves.
            if (uiState.emailLoginEnabled) {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text(stringResource(Res.string.email_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("login_email"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                    ),
                    enabled = !uiState.isLoading,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text(stringResource(Res.string.password_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("login_password"),
                    singleLine = true,
                    visualTransformation = passwordMaskTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    enabled = !uiState.isLoading,
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("login_error"),
                )
            }

            if (uiState.emailLoginEnabled) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::login,
                    modifier = Modifier.fillMaxWidth().testTag("login_submit"),
                    enabled = !uiState.isLoading,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(Res.string.continue_button))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onNavigateToForgotPassword) {
                    Text(stringResource(Res.string.forgot_password))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.registrationEnabled) {
                TextButton(onClick = onNavigateToRegister) {
                    Text(stringResource(Res.string.no_account_sign_up))
                }
            }

            val socialLogins = uiState.socialLogins
            if (uiState.socialLoginEnabled && socialLogins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.or_continue_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                socialLogins.forEach { provider ->
                    OutlinedButton(
                        onClick = { viewModel.launchOAuth(provider) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                    ) {
                        Text(oAuthProviderLabel(provider, uiState.openidLabel))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        BackAffordanceOverlay(onBack)
    }
}

/**
 * TCHAT: full-screen host for the in-app OAuth WebView. A slim top bar carries just a close
 * button — system back dismisses on Android via [PlatformBackHandler]; the explicit close covers
 * iOS (which has no system back). Mirrors feature/chat's `PdfPreviewOverlay` shape.
 */
@Composable
private fun OAuthWebViewOverlay(
    state: OAuthWebViewState,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = true, onBack = onDismiss)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.oauth_webview_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            OAuthWebView(
                startUrl = state.startUrl,
                serverUrl = state.serverUrl,
                onComplete = onComplete,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * TCHAT: `openidLabel` is the server's own `OPENID_BUTTON_LABEL` (StartupConfig.openidLabel,
 * surfaced via LoginUiState) — deploy/tchat/env.example already sets it to "Continue with
 * TensorGrid", so that's what renders. [TchatBrand.OIDC_FALLBACK_LABEL] only covers a server that
 * enables OIDC without setting a label.
 */
private fun oAuthProviderLabel(provider: String, openidLabel: String?): String = when (provider.lowercase()) {
    "google" -> "Continue with Google"
    "github" -> "Continue with GitHub"
    "discord" -> "Continue with Discord"
    "facebook" -> "Continue with Facebook"
    "apple" -> "Continue with Apple"
    "openid" -> openidLabel?.takeIf { it.isNotBlank() } ?: TchatBrand.OIDC_FALLBACK_LABEL
    else -> "Continue with ${provider.replaceFirstChar { it.uppercase() }}"
}
