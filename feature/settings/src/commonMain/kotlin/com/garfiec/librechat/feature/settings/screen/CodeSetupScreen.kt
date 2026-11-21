package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.CodeSetupViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Approves or denies a machine's setup request (web's `/code/setup/:attemptId`): what the machine
 * asks for, how to check it is the one the user started, and Approve / Deny.
 */
@Composable
fun CodeSetupScreen(
    attemptId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeSetupViewModel = koinViewModel { parametersOf(attemptId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CodeSetupContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::load,
        onSelectPlane = viewModel::selectPlane,
        onApprove = viewModel::approve,
        onDeny = viewModel::deny,
        modifier = modifier,
    )
}
