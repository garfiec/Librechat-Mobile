package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.viewmodel.VerifyEmailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VerifyEmailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    email: String? = null,
    sendNow: Boolean = false,
    viewModel: VerifyEmailViewModel = koinViewModel { parametersOf(email, sendNow) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VerifyEmailContent(
        uiState = uiState,
        onResendVerification = viewModel::resendVerification,
        onBack = onBack,
        modifier = modifier,
    )
}
