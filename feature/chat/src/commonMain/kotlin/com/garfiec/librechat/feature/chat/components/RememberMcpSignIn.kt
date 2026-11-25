package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.ui.mcp.McpOAuthSignInDialog
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_failed
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Hosts what [viewModel] opens — the in-app sign-in dialog, or the system browser for a server
 * before v0.8.9-rc1 — reports a failed sign-in on [snackbarHostState], calls [onSignInEnd] once each
 * sign-in ends (a browser one when the app is back in front), and returns the action the cards and
 * chips read through [LocalMcpSignIn].
 */
@Composable
fun rememberMcpSignIn(
    viewModel: McpSignInViewModel,
    snackbarHostState: SnackbarHostState,
    onSignInEnd: () -> Unit = {},
): McpSignIn {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val launch = state.launch) {
        is McpOAuthLaunch.InApp -> McpOAuthSignInDialog(launch = launch, onOutcome = viewModel::onOutcome)
        is McpOAuthLaunch.External -> {
            val uriHandler = LocalUriHandler.current
            LaunchedEffect(launch) {
                val opened = runCatching { uriHandler.openUri(launch.authorizationUrl) }.isSuccess
                viewModel.onLaunched(opened)
            }
        }
        null -> Unit
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturned() }
    val failedMessage = stringResource(Res.string.mcp_sign_in_failed)
    LaunchedEffect(state.failed) {
        if (!state.failed) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(failedMessage)
        viewModel.dismissFailure()
    }
    val currentOnSignInEnd by rememberUpdatedState(onSignInEnd)
    LaunchedEffect(state.ended) {
        if (!state.ended) return@LaunchedEffect
        viewModel.consumeEnded()
        currentOnSignInEnd()
    }
    return remember(viewModel, state.preparingServer) {
        McpSignIn(state.preparingServer, viewModel::signIn, viewModel::signInAction)
    }
}
