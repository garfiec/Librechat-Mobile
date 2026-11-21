package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.McpViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Manages MCP server connections: status, the action that fixes each, CRUD, and tool browsing. */
@Composable
fun McpServersScreen(
    onNavigateBack: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (serverName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(Res.string.action_retry)

    val oauthFailedMessage = stringResource(Res.string.mcp_oauth_failed)
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        // With nothing listed, the error banner owns the error; clearing it here would leave the
        // empty state claiming there are no servers, with no way to retry.
        if (uiState.servers.isEmpty()) return@LaunchedEffect
        val retry = uiState.errorRetry
        val result = snackbarHostState.showSnackbar(
            message = if (error == McpViewModel.OAUTH_FAILED_MARKER) oauthFailedMessage else error,
            actionLabel = retryLabel,
        )
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retry(retry)
        }
    }

    val deferredMessage = stringResource(Res.string.mcp_connection_deferred)
    val signedInMessage = stringResource(Res.string.mcp_oauth_signed_in)
    LaunchedEffect(uiState.successMessage) {
        val message = uiState.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = when (message) {
                McpViewModel.DEFERRED_MARKER -> deferredMessage
                McpViewModel.OAUTH_SIGNED_IN_MARKER -> signedInMessage
                else -> message
            },
        )
        viewModel.dismissSuccessMessage()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    // From v0.8.9-rc1 the callback needs the binding cookies, so the sign-in runs in app; an older
    // server keeps the system browser (McpOAuthLauncher decides).
    val launch = uiState.oauthLaunch
    if (launch is McpOAuthLaunch.External) {
        val uriHandler = LocalUriHandler.current
        LaunchedEffect(launch) {
            runCatching { uriHandler.openUri(launch.authorizationUrl) }
            viewModel.onOAuthLaunched()
        }
    }

    val appLocationConsents by viewModel.appLocationConsents.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        McpServersActions(
            refresh = viewModel::refresh,
            dismissError = viewModel::dismissError,
            showToolsSheet = viewModel::showToolsSheet,
            dismissToolsSheet = viewModel::dismissToolsSheet,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            setAttentionOnly = viewModel::setAttentionOnly,
            openServer = viewModel::openServer,
            dismissServer = viewModel::dismissServer,
            reinitializeServer = viewModel::reinitializeServer,
            deleteServer = viewModel::deleteServer,
            setAppLocationAllowed = viewModel::setAppLocationAllowed,
            openVariables = viewModel::openVariables,
            dismissVariables = viewModel::dismissVariables,
            userVarFlags = viewModel::userVarFlags,
            saveVariables = viewModel::saveVariables,
            removeVariables = viewModel::removeVariables,
            dismissOAuthPrompt = viewModel::dismissOAuthPrompt,
            continueOAuth = viewModel::continueOAuth,
            onOAuthOutcome = viewModel::onOAuthOutcome,
        )
    }
    McpServersContent(
        uiState = uiState,
        appLocationConsents = appLocationConsents,
        actions = actions,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onAddServer = onAddServer,
        onEditServer = onEditServer,
        modifier = modifier,
    )
}
