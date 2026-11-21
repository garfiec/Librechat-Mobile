package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TwoFactorScreen(
    onVerify: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tempToken: String? = null,
    viewModel: TwoFactorViewModel = koinViewModel { parametersOf(tempToken) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnVerify by rememberUpdatedState(onVerify)

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) currentOnVerify()
    }

    TwoFactorContent(
        uiState = uiState,
        onCodeChange = viewModel::onCodeChanged,
        onBackupCodeChange = viewModel::onBackupCodeChanged,
        onSubmit = viewModel::submit,
        onToggleBackupMode = viewModel::toggleBackupMode,
        onBack = onBack,
        modifier = modifier,
        diagnosticLogLink = { DiagnosticLogLink() },
    )
}
