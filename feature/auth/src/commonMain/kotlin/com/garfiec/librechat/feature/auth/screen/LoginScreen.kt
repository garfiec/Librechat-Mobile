package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.IosTextContentType
import com.garfiec.librechat.core.ui.components.SecretRevealButton
import com.garfiec.librechat.core.ui.components.withIosTextContentType
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.LoginInputError
import com.garfiec.librechat.feature.auth.viewmodel.LoginViewModel
import com.garfiec.librechat.feature.auth.viewmodel.MAX_PASSWORD_LENGTH
import org.jetbrains.compose.resources.stringResource
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No scaffold here: the screen keeps itself clear of the system bars and the keyboard.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .then(if (onBack != null) Modifier.padding(top = BackAffordanceClearance) else Modifier),
        ) {
            AuthBanner(uiState.banner)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = uiState.appTitle?.let { stringResource(Res.string.login_title_server, it) }
                        ?: stringResource(Res.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )

                uiState.serverLabel?.let { label ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("login_server_label"),
                    )
                }

                if (uiState.trustedViaUserCa) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.user_ca_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("login_user_ca_notice"),
                    )
                }

                uiState.twoFactorSetupAccounts?.let { accounts ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.login_two_factor_setup_required, accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("login_two_factor_setup_notice"),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // The email/password form is hidden when the server disables email login
                // (ALLOW_EMAIL_LOGIN=false, #14180) — it 403s the login POST, so offer only the
                // social providers below. Fail-open: the flag defaults true until config resolves.
                if (uiState.emailLoginEnabled) {
                    val usernameLogin = uiState.usernameLogin
                    AdaptiveOutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text(stringResource(if (usernameLogin) Res.string.username_label else Res.string.email_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("login_email")
                            .semantics {
                                contentType = if (usernameLogin) ContentType.Username else ContentType.Username + ContentType.EmailAddress
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (usernameLogin) KeyboardType.Text else KeyboardType.Email,
                            autoCorrectEnabled = false,
                        ).withIosTextContentType(IosTextContentType.Username),
                        enabled = !uiState.isLoading,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Plain remember scoped to the field, so it always first composes masked: Compose keeps a text
                    // field's first Password semantics, so one first composed revealed is never a password again.
                    var passwordRevealed by remember { mutableStateOf(false) }
                    // ON_PAUSE, not ON_STOP, so the app-switcher snapshot never shows it (see SecretTextField).
                    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { passwordRevealed = false }
                    AdaptiveOutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text(stringResource(Res.string.password_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("login_password"),
                        singleLine = true,
                        visualTransformation = if (passwordRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            SecretRevealButton(
                                revealed = passwordRevealed,
                                onRevealedChange = { passwordRevealed = it },
                                modifier = Modifier.testTag("login_password_reveal"),
                                enabled = !uiState.isLoading,
                                showDescription = Res.string.cd_show_password,
                                hideDescription = Res.string.cd_hide_password,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                        enabled = !uiState.isLoading,
                    )
                }

                val errorText = uiState.inputError?.let { loginInputErrorText(it, uiState.usernameLogin, uiState.minPasswordLength) }
                    ?: authErrorText(uiState.refusal, uiState.error, uiState.usernameLogin)
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("login_error"),
                    )
                    if (uiState.offerDiagnosticLog) DiagnosticLogLink()
                }

                if (uiState.emailLoginEnabled) {
                    Spacer(modifier = Modifier.height(24.dp))

                    AdaptiveButton(
                        onClick = viewModel::login,
                        modifier = Modifier.fillMaxWidth().testTag("login_submit"),
                        loading = uiState.isLoading,
                    ) {
                        Text(stringResource(if (uiState.isLoading) Res.string.progress_signing_in else Res.string.continue_button))
                    }

                    if (uiState.passwordResetEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onNavigateToForgotPassword) {
                            Text(stringResource(Res.string.forgot_password))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.registrationEnabled) {
                    TextButton(onClick = onNavigateToRegister) {
                        Text(stringResource(Res.string.no_account_sign_up))
                    }
                }

                val socialProviders = uiState.socialProviders
                if (socialProviders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.emailLoginEnabled) {
                        AdaptiveDivider(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(Res.string.or_continue_with),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    socialProviders.forEach { provider ->
                        AdaptiveOutlinedButton(
                            onClick = { viewModel.onSsoProviderSelected(provider.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                        ) {
                            Text(provider.label ?: oAuthProviderLabel(provider.id))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                UseAnotherAccountButton(onUseAnotherAccount, modifier = Modifier.padding(top = 8.dp))

                PolicyLinksFooter(uiState.policyLinks, modifier = Modifier.padding(top = 16.dp))
            }
        }

        uiState.pendingSsoProvider?.let {
            AdaptiveAlertDialog(
                onDismissRequest = viewModel::onSsoRiskDismissed,
                title = { Text(stringResource(Res.string.sso_risk_title)) },
                text = { Text(stringResource(Res.string.sso_risk_body)) },
                confirmButton = {
                    TextButton(onClick = viewModel::onSsoRiskAccepted) {
                        Text(stringResource(Res.string.continue_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onSsoRiskDismissed) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        BackAffordanceOverlay(onBack)
    }
}

@Composable
internal fun oAuthProviderLabel(provider: String): String {
    val name = when (provider.lowercase()) {
        "google" -> "Google"
        "github" -> "GitHub"
        "discord" -> "Discord"
        "facebook" -> "Facebook"
        "apple" -> "Apple"
        "openid" -> "OpenID"
        "saml" -> "SAML"
        else -> provider.replaceFirstChar { it.uppercase() }
    }
    return stringResource(Res.string.continue_with_provider, name)
}

@Composable
private fun loginInputErrorText(error: LoginInputError, usernameLogin: Boolean, minPasswordLength: Int): String = when (error) {
    LoginInputError.MissingFields -> stringResource(
        if (usernameLogin) Res.string.login_error_missing_fields_username else Res.string.login_error_missing_fields,
    )
    LoginInputError.InvalidEmail -> stringResource(Res.string.login_error_invalid_email)
    LoginInputError.PasswordTooShort -> stringResource(Res.string.login_error_password_too_short, minPasswordLength)
    LoginInputError.PasswordTooLong -> stringResource(Res.string.login_error_password_too_long, MAX_PASSWORD_LENGTH)
}
