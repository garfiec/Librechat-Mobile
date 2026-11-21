package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.code.CodePairingError
import com.garfiec.librechat.core.model.code.CodeRemovalError
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.CodeExecutionEvent
import com.garfiec.librechat.feature.settings.viewmodel.CodeExecutionViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › Data › Code execution (web's Data controls › Code execution): the default stateful
 * workspace for new agents, the user's machines, pairing a new one, and opening a worker's setup
 * link for approval.
 */
@Composable
fun CodeExecutionScreen(
    onNavigateBack: () -> Unit,
    onOpenSetup: (attemptId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeExecutionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Machine status is polled only while the page is in front, as web stops in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    val openSetup by rememberUpdatedState(onOpenSetup)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is CodeExecutionEvent.OpenSetup) {
                openSetup(event.attemptId)
                return@collect
            }
            snackbarHostState.showSnackbar(getString(eventMessage(event)))
        }
    }

    val actions = remember(viewModel) {
        CodeExecutionActions(
            loadScope = viewModel::loadScope,
            selectScope = viewModel::selectScope,
            load = viewModel::load,
            setPairName = viewModel::setPairName,
            selectControlPlane = viewModel::selectControlPlane,
            selectShell = viewModel::selectShell,
            pair = viewModel::pair,
            setPermission = viewModel::setPermission,
            requestRemoval = viewModel::requestRemoval,
            dismissRemoval = viewModel::dismissRemoval,
            confirmRemoval = viewModel::confirmRemoval,
            setSetupLink = viewModel::setSetupLink,
            openSetupLink = viewModel::openSetupLink,
        )
    }
    CodeExecutionContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = actions,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

private fun eventMessage(event: CodeExecutionEvent): StringResource = when (event) {
    CodeExecutionEvent.PairingCreated -> Res.string.code_environment_pairing_created
    is CodeExecutionEvent.PairingFailed -> when (event.error) {
        CodePairingError.INVALID_NAME -> Res.string.code_environment_pairing_error_name
        CodePairingError.DISABLED -> Res.string.code_environment_pairing_error_disabled
        CodePairingError.CONTROL_PLANE_GONE -> Res.string.code_environment_pairing_error_plane
        CodePairingError.LIMIT_REACHED -> Res.string.code_environment_pairing_error_limit
        CodePairingError.RATE_LIMITED -> Res.string.code_environment_pairing_error_rate
        CodePairingError.REFUSED, CodePairingError.UNAVAILABLE -> Res.string.code_environment_pairing_error
    }
    CodeExecutionEvent.PermissionsSaved -> Res.string.code_environment_permissions_saved
    CodeExecutionEvent.PermissionsFailed -> Res.string.code_environment_permissions_error
    CodeExecutionEvent.Removed -> Res.string.code_environment_removed
    is CodeExecutionEvent.RemoveFailed -> when (event.error) {
        CodeRemovalError.IN_USE -> Res.string.code_environment_remove_in_use
        CodeRemovalError.GONE -> Res.string.code_environment_removed
        CodeRemovalError.FAILED -> Res.string.code_environment_remove_error
    }
    CodeExecutionEvent.PreferencesUpdated -> Res.string.code_preferences_updated
    CodeExecutionEvent.PreferencesFailed -> Res.string.code_preferences_error
    is CodeExecutionEvent.OpenSetup -> Res.string.code_setup_title
}
