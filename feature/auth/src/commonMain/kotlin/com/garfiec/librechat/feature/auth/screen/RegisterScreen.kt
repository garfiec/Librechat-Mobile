package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.RegisterOutcome
import com.garfiec.librechat.feature.auth.viewmodel.RegisterViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterScreen(
    onRegister: (RegisterOutcome) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnRegister by rememberUpdatedState(onRegister)
    val autofillManager = LocalAutofillManager.current

    LaunchedEffect(uiState.outcome) {
        uiState.outcome?.let { outcome ->
            // The next screen may have fields of its own that keep the autofill session open, so
            // it would never end on its own and no save prompt would appear.
            autofillManager?.commit()
            currentOnRegister(outcome)
        }
    }

    RegisterContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChanged,
        onUsernameChange = viewModel::onUsernameChanged,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
        onRegister = viewModel::register,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
        diagnosticLogLink = { DiagnosticLogLink() },
    )
}
