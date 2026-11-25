package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.ResetPasswordViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ResetPasswordScreen(
    onResetComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    userId: String? = null,
    token: String? = null,
    viewModel: ResetPasswordViewModel = koinViewModel { parametersOf(userId, token) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ResetPasswordContent(
        uiState = uiState,
        onPasswordChange = viewModel::onPasswordChanged,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
        onResetPassword = viewModel::resetPassword,
        onResetComplete = onResetComplete,
        onBack = onBack,
        modifier = modifier,
        diagnosticLogLink = { DiagnosticLogLink() },
    )
}
