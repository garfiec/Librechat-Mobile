package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.ProviderKeysViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProviderKeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderKeysViewModel = koinViewModel(),
    /**
     * If non-null, auto-opens the [SetProviderKeyDialog] for this endpoint on
     * first composition. Used by the chat model-selector "Set API Key" CTA and
     * the chat-send `UserKeyError` snackbar to deep-link the user directly to
     * the right form. The keyed [LaunchedEffect] only fires when the value
     * changes, so a back/forward navigation re-opens the dialog as expected
     * without looping on dismissal.
     */
    pendingDialogEndpoint: String? = null,
    /**
     * The screen was opened from chat to set a key: once a save or revoke goes through, go back
     * there instead of staying on the list.
     */
    returnAfterSave: Boolean = false,
    /**
     * Goes back to the chat after a save or revoke when [returnAfterSave], carrying the
     * confirmation: this screen's snackbar leaves with it.
     */
    onReturnAfterSave: (message: StringResource) -> Unit = { onNavigateBack() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Quiet re-read of every row on return (a key set on the web while the app was away); it
    // never touches an open sheet.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    // Track whether the route arg has already triggered a dialog open. `rememberSaveable`
    // keyed on `pendingDialogEndpoint` survives rotation: same landing -> consumed flag
    // preserved (no reopen if user dismissed). Fresh deep-link to the same endpoint produces
    // a new NavBackStackEntry, which gives a fresh `rememberSaveable` slot and reopens.
    var consumed by rememberSaveable(pendingDialogEndpoint) { mutableStateOf(false) }
    LaunchedEffect(pendingDialogEndpoint) {
        if (pendingDialogEndpoint != null && !consumed) {
            viewModel.openDialog(pendingDialogEndpoint)
            consumed = true
        }
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = getString(error))
        viewModel.dismissError()
    }
    LaunchedEffect(uiState.transientMessage) {
        val msg = uiState.transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = getString(msg))
        viewModel.consumeTransientMessage()
    }

    val setKeyDialog: @Composable (String) -> Unit = { pending ->
        val row = uiState.dialogEntry
        SetProviderKeyDialog(
            endpointName = pending,
            openRequest = uiState.dialogOpenRequest,
            rowKeyState = row?.keyState ?: KeyState.Loading,
            rowFetchFailed = row?.fetchFailed ?: false,
            rowRetrying = row?.retrying ?: false,
            onRetryKeyState = { viewModel.retry(pending) },
            onDismiss = viewModel::dismissDialog,
            onMutationSuccess = { endpointName, keyState, message ->
                viewModel.onChildKeyChanged(endpointName, keyState)
                if (returnAfterSave) onReturnAfterSave(message) else viewModel.emitTransientMessage(message)
            },
            onShowMessage = viewModel::emitTransientMessage,
        )
    }
    ProviderKeysScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onOpen = viewModel::openDialog,
        onRetryRow = viewModel::retry,
        onRetryLoad = viewModel::retry,
        onRevokeAll = viewModel::showRevokeAllConfirm,
        onConfirmRevokeAll = viewModel::revokeAll,
        onDismissRevokeAll = viewModel::dismissRevokeAllConfirm,
        setKeyDialog = setKeyDialog,
        modifier = modifier,
    )
}
