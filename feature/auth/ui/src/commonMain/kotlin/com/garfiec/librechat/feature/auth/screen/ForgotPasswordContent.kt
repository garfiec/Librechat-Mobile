package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.ForgotPasswordUiState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onRequestReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The diagnostic-log export offered under a connection error; nothing by default. */
    diagnosticLogLink: @Composable () -> Unit = {},
) {
    AdaptiveScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onBack),
                    title = BarTitle(stringResource(Res.string.reset_password_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .imePadding(),
        ) {
            // The scaffold padding holds the bar column without consuming it; the banner pads that column itself.
            AuthBanner(uiState.banner, Modifier.consumeWindowInsets(padding))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (uiState.emailUnavailable) {
                    Text(
                        text = stringResource(Res.string.forgot_your_password),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.reset_password_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    AdaptiveButton(onClick = onBack) {
                        Text(stringResource(Res.string.back_to_sign_in))
                    }
                } else if (uiState.isSent) {
                    Text(
                        text = stringResource(Res.string.check_your_email),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.reset_link_sent, uiState.email),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    AdaptiveButton(onClick = onBack) {
                        Text(stringResource(Res.string.back_to_sign_in))
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.forgot_your_password),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.enter_email_for_reset),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    AdaptiveOutlinedTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(Res.string.email_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrectEnabled = false,
                        ),
                        isError = uiState.error != null,
                        supportingText = authErrorText(uiState.refusal, uiState.error)?.let { error ->
                            { Text(text = error, color = MaterialTheme.colorScheme.error) }
                        },
                        enabled = !uiState.isLoading,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AdaptiveButton(
                        onClick = onRequestReset,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.email.isNotBlank(),
                        loading = uiState.isLoading,
                    ) {
                        Text(stringResource(if (uiState.isLoading) Res.string.progress_sending else Res.string.send_reset_link))
                    }

                    if (uiState.error != null && uiState.offerDiagnosticLog) {
                        Spacer(modifier = Modifier.height(8.dp))
                        diagnosticLogLink()
                    }

                    PolicyLinksFooter(uiState.policyLinks, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
