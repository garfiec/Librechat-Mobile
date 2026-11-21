package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.diagnostics.DiagnosticLogLink
import com.garfiec.librechat.feature.auth.viewmodel.ServerUrlViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ServerUrlScreen(
    onServerValidate: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Opens the switcher on the other signed-in accounts; null hides it. */
    onUseAnotherAccount: (() -> Unit)? = null,
    viewModel: ServerUrlViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnServerValidated by rememberUpdatedState(onServerValidate)

    LaunchedEffect(uiState.isValidated) {
        if (uiState.isValidated) {
            currentOnServerValidated()
            // Consume it, or backing out of the next screen lands here and is immediately thrown
            // forward again — this entry's ViewModel outlives the forward navigation.
            viewModel.consumeValidated()
        }
    }

    ServerUrlContent(
        uiState = uiState,
        actions = ServerUrlActions(
            onUrlChange = viewModel::onUrlChanged,
            onConnect = viewModel::validateAndConnect,
            onConfirmHttp = viewModel::confirmHttpConnection,
            onDismissHttpWarning = viewModel::dismissHttpWarning,
            onToggleAdvanced = viewModel::toggleAdvanced,
            onHeaderNameChange = viewModel::onHeaderNameChanged,
            onHeaderValueChange = viewModel::onHeaderValueChanged,
            onAddHeader = viewModel::addHeaderRow,
            onRemoveHeader = viewModel::removeHeaderRow,
            onSelectKnownServer = viewModel::connectTo,
            onGateClientIdChange = viewModel::onGateClientIdChanged,
            onSignInToGate = viewModel::signInToGate,
            onOpenGatePage = viewModel::openGatePage,
            onDismissGatePage = viewModel::dismissGatePage,
        ),
        modifier = modifier,
        onBack = onBack,
        onUseAnotherAccount = onUseAnotherAccount,
        diagnosticLogLink = { DiagnosticLogLink() },
        gatePage = { gate ->
            GatePageSignInOverlay(
                portalUrl = gate.portalUrl,
                serverUrl = gate.serverUrl,
                onSignInComplete = viewModel::onGatePageSignedIn,
                onClose = viewModel::dismissGatePage,
            )
        },
    )
}
