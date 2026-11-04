package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.OTP_LENGTH
import com.garfiec.librechat.core.ui.components.OtpCodeInput
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.platform.TextFileSaver
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupError
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupPhase
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupUiState
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupViewModel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val BACKUP_CODES_FILE_NAME = "backup-codes.txt"
private val QR_SIZE = 200.dp

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

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onBack),
                    title = BarTitle(stringResource(Res.string.two_factor_setup_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.mustSignInAgain) {
                ExpiredSetup(onBack = onBack)
                UseAnotherAccountButton(onUseAnotherAccount)
                return@Column
            }
            Text(
                text = stringResource(Res.string.two_factor_setup_required_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            when (uiState.phase) {
                TwoFactorSetupPhase.Start -> {
                    StartPhase(uiState.isLoading, viewModel::start)
                    // Not offered mid-setup, where it would compete with the step at hand; sign-in,
                    // one step back, still has it.
                    UseAnotherAccountButton(onUseAnotherAccount)
                }
                TwoFactorSetupPhase.Authenticator -> AuthenticatorPhase(uiState, viewModel::continueToVerify)
                TwoFactorSetupPhase.Verify -> VerifyPhase(
                    state = uiState,
                    onCodeChange = viewModel::onCodeChanged,
                    onVerify = viewModel::confirm,
                    onBackToAuthenticator = viewModel::backToAuthenticator,
                )
                TwoFactorSetupPhase.BackupCodes -> BackupCodesPhase(
                    state = uiState,
                    onSave = viewModel::onCodesSaved,
                    onAcknowledge = viewModel::setCodesAcknowledged,
                    onComplete = viewModel::complete,
                )
            }
            uiState.error?.let { error ->
                Text(
                    text = stringResource(error.message()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("twofa_setup_error"),
                )
            }
        }
    }
}

private fun TwoFactorSetupError.message(): StringResource = when (this) {
    TwoFactorSetupError.StartFailed -> Res.string.two_factor_setup_error_start
    TwoFactorSetupError.InvalidCode -> Res.string.two_factor_setup_error_invalid
    TwoFactorSetupError.RateLimited -> Res.string.two_factor_setup_error_rate_limited
    TwoFactorSetupError.FinalizeFailed -> Res.string.two_factor_setup_error_finalize
    TwoFactorSetupError.Connection -> Res.string.two_factor_setup_error_connection
}

/** One phase's content, as a single emitter. */
@Composable
private fun PhaseColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LoadingLabel(isLoading: Boolean, label: String) {
    if (isLoading) {
        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
    } else {
        Text(label)
    }
}

@Composable
private fun StartPhase(isLoading: Boolean, onStart: () -> Unit) {
    AdaptiveButton(
        onClick = onStart,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().testTag("twofa_setup_start"),
    ) {
        LoadingLabel(isLoading, stringResource(Res.string.two_factor_setup_begin))
    }
}

@Composable
private fun AuthenticatorPhase(state: TwoFactorSetupUiState, onNext: () -> Unit) {
    val otpauthUrl = state.otpauthUrl ?: return
    val uriHandler = LocalUriHandler.current
    var noAuthenticator by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }

    PhaseColumn {
        Heading(stringResource(Res.string.two_factor_setup_authenticator_heading))
        Text(
            text = stringResource(Res.string.two_factor_setup_authenticator_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // The phone is the second device, so it cannot scan its own screen: handing the otpauth:// URI
        // to the authenticator is the path that works here. The key and QR code are the fallbacks.
        AdaptiveFilledTonalButton(
            onClick = {
                noAuthenticator = !runCatching { uriHandler.openUri(otpauthUrl) }.isSuccess
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.two_factor_setup_open_authenticator))
        }
        if (noAuthenticator) {
            Text(
                text = stringResource(Res.string.two_factor_setup_no_authenticator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        state.setupKey?.let { key ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(Res.string.two_factor_setup_key),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(text = key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        AdaptiveTextButton(onClick = { showQr = !showQr }) {
            Text(stringResource(if (showQr) Res.string.two_factor_setup_hide_qr else Res.string.two_factor_setup_show_qr))
        }
        if (showQr) {
            // Black on transparent by default, which no camera reads off a dark surface.
            Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
                Image(
                    painter = rememberQrCodePainter(otpauthUrl),
                    contentDescription = stringResource(Res.string.two_factor_setup_qr_description),
                    modifier = Modifier.padding(16.dp).size(QR_SIZE),
                )
            }
        }
        AdaptiveButton(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("twofa_setup_next")) {
            Text(stringResource(Res.string.two_factor_setup_next))
        }
    }
}

@Composable
private fun VerifyPhase(
    state: TwoFactorSetupUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onBackToAuthenticator: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    PhaseColumn {
        Heading(stringResource(Res.string.two_factor_setup_verify_heading))
        OtpCodeInput(
            value = state.code,
            onValueChange = onCodeChange,
            enabled = !state.isLoading,
            modifier = Modifier.focusRequester(focusRequester).testTag("twofa_setup_code"),
        )
        LaunchedEffect(state.codeAttempt) { focusRequester.requestFocus() }
        AdaptiveButton(
            onClick = onVerify,
            enabled = !state.isLoading && state.code.length == OTP_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LoadingLabel(state.isLoading, stringResource(Res.string.verify))
        }
        AdaptiveTextButton(onClick = onBackToAuthenticator, enabled = !state.isLoading) {
            Text(stringResource(Res.string.back))
        }
    }
}

@Composable
private fun BackupCodesPhase(
    state: TwoFactorSetupUiState,
    onSave: () -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onComplete: () -> Unit,
) {
    var saveFileName by remember { mutableStateOf<String?>(null) }
    var saveContent by remember { mutableStateOf<String?>(null) }
    var savedToFile by rememberSaveable { mutableStateOf(false) }

    PhaseColumn {
        TextFileSaver(
            triggerFileName = saveFileName,
            content = saveContent,
            onComplete = { success, _ ->
                if (success) {
                    savedToFile = true
                    onSave()
                }
            },
            onReset = {
                saveFileName = null
                saveContent = null
            },
        )

        Heading(stringResource(Res.string.two_factor_setup_backup_heading))
        Text(
            text = stringResource(Res.string.two_factor_setup_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // From v0.8.9-rc1 each code is 32 hex characters by default: one per line, numbered, and left
        // to wrap rather than truncate on a narrow phone.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            SelectionContainer {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.backupCodes.forEachIndexed { index, code ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(text = code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        AdaptiveOutlinedButton(
            onClick = {
                saveContent = state.backupCodes.joinToString("\n")
                saveFileName = BACKUP_CODES_FILE_NAME
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.two_factor_setup_save_codes))
        }
        if (savedToFile) {
            Text(
                text = stringResource(Res.string.two_factor_setup_codes_saved),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isLoading) { onAcknowledge(!state.codesSaved) },
        ) {
            AdaptiveCheckbox(checked = state.codesSaved, onCheckedChange = onAcknowledge, enabled = !state.isLoading)
            Text(text = stringResource(Res.string.two_factor_setup_codes_acknowledged), style = MaterialTheme.typography.bodyMedium)
        }
        AdaptiveButton(
            onClick = onComplete,
            enabled = state.codesSaved && !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("twofa_setup_complete"),
        ) {
            LoadingLabel(state.isLoading, stringResource(Res.string.two_factor_setup_complete))
        }
    }
}

@Composable
private fun ExpiredSetup(onBack: () -> Unit) {
    PhaseColumn {
        Text(
            text = stringResource(Res.string.two_factor_setup_expired),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("twofa_setup_expired"),
        )
        AdaptiveButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.two_factor_setup_back_to_sign_in))
        }
    }
}
