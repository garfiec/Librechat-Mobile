package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.MCP_SEARCH_THRESHOLD
import com.garfiec.librechat.feature.settings.viewmodel.McpServerRow
import com.garfiec.librechat.feature.settings.viewmodel.McpUiState
import com.garfiec.librechat.feature.settings.viewmodel.McpViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Manages MCP server connections: status, the action that fixes each, CRUD, and tool browsing. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    var pendingDelete by remember { mutableStateOf<McpServerRow?>(null) }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        // With nothing listed, the error banner owns the error; clearing it here would leave the
        // empty state claiming there are no servers, with no way to retry.
        if (uiState.servers.isEmpty()) return@LaunchedEffect
        val retry = uiState.errorRetry
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = retryLabel,
        )
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retry(retry)
        }
    }

    val deferredMessage = stringResource(Res.string.mcp_connection_deferred)
    LaunchedEffect(uiState.successMessage) {
        val message = uiState.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = if (message == McpViewModel.DEFERRED_MARKER) deferredMessage else message,
        )
        viewModel.dismissSuccessMessage()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_mcp_servers)),
                    actions = if (uiState.tools.isNotEmpty()) {
                        listOf(
                            BarAction.Icon(
                                id = "tools",
                                icon = BarIcons.Tools,
                                label = stringResource(Res.string.cd_view_all_tools),
                                onClick = { viewModel.showToolsSheet() },
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        },
        floatingActionButton = {
            // The empty state carries its own Add button; two of them would compete.
            if (uiState.canCreateServer && uiState.servers.isNotEmpty()) {
                AdaptiveExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.cd_add_server)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddServer,
                    expanded = fabExpanded,
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.servers.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.servers.isEmpty() -> ErrorBanner(
                message = uiState.error ?: stringResource(Res.string.error_failed_to_load_servers),
                modifier = Modifier.padding(innerPadding),
                // Everything, not just the list: a first load that failed lost status, tools and permissions too.
                onRetry = {
                    viewModel.dismissError()
                    viewModel.refresh()
                },
            )
            uiState.servers.isEmpty() -> McpEmptyState(
                canCreate = uiState.canCreateServer,
                onAdd = onAddServer,
                modifier = Modifier.padding(innerPadding),
            )
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                BoxWithConstraints {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (maxWidth >= TWO_COLUMN_MIN_WIDTH) 2 else 1),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FAB_CLEARANCE),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listHeader(uiState, viewModel::onSearchQueryChange, viewModel::setAttentionOnly)
                        // Prefixed: a server may be named like a header item ("search").
                        items(items = uiState.rows, key = { "server:${it.name}" }, contentType = { "mcp_server" }) { row ->
                            McpServerCard(
                                row = row,
                                onOpen = { viewModel.openServer(row.name) },
                                onReconnect = { viewModel.reinitializeServer(row.name) },
                                onEdit = { onEditServer(row.name) },
                                onDelete = { pendingDelete = row },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AdaptiveAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.dialog_title_delete_server)) },
            text = { Text(stringResource(Res.string.dialog_delete_server_message, row.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteServer(row.name)
                    },
                ) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // A server that answered "authorize me first" gets an explicit consent step rather than an
    // automatic browser launch: the redirect hands a third-party provider this session, and that
    // has to be the user's decision, not a side effect of tapping reconnect.
    val pendingOAuth = uiState.pendingOAuth
    if (pendingOAuth != null) {
        val uriHandler = LocalUriHandler.current
        AdaptiveAlertDialog(
            onDismissRequest = viewModel::dismissOAuthPrompt,
            title = { Text(stringResource(Res.string.mcp_oauth_title, pendingOAuth.serverName)) },
            text = { Text(stringResource(Res.string.mcp_oauth_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri(pendingOAuth.authorizationUrl)
                        viewModel.onOAuthLaunched()
                    },
                ) {
                    Text(stringResource(Res.string.mcp_oauth_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOAuthPrompt) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    uiState.selectedRow?.let { row ->
        McpServerSheet(
            row = row,
            tools = uiState.selectedTools,
            onDismiss = viewModel::dismissServer,
            onReconnect = { viewModel.reinitializeServer(row.name) },
            onEdit = {
                viewModel.dismissServer()
                onEditServer(row.name)
            },
            onDelete = { pendingDelete = row },
        )
    }

    if (uiState.showToolsSheet) {
        McpToolsSheet(
            tools = uiState.tools,
            serverTitles = uiState.serverTitles,
            onDismiss = viewModel::dismissToolsSheet,
        )
    }
}

/** Search (once there are enough servers to need it), the attention filter, and the no-match line. */
private fun LazyGridScope.listHeader(
    uiState: McpUiState,
    onQueryChange: (String) -> Unit,
    onAttentionOnlyChange: (Boolean) -> Unit,
) {
    if (uiState.allRows.size >= MCP_SEARCH_THRESHOLD || uiState.searchQuery.isNotEmpty()) {
        item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
            AdaptiveOutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.mcp_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
        }
    }
    if (uiState.attentionCount > 0) {
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveFilterChip(
                    selected = !uiState.attentionOnly,
                    onClick = { onAttentionOnlyChange(false) },
                    label = { Text(stringResource(Res.string.mcp_filter_all)) },
                )
                AdaptiveFilterChip(
                    selected = uiState.attentionOnly,
                    onClick = { onAttentionOnlyChange(true) },
                    label = { Text(stringResource(Res.string.mcp_filter_needs_attention, uiState.attentionCount)) },
                )
            }
        }
    }
    if (uiState.rows.isEmpty()) {
        item(key = "no_results", span = { GridItemSpan(maxLineSpan) }, contentType = "no_results") {
            Text(
                text = stringResource(Res.string.mcp_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        }
    }
}

@Composable
private fun McpEmptyState(canCreate: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.mcp_empty_title),
        description = stringResource(if (canCreate) Res.string.mcp_empty_body else Res.string.mcp_empty_body_no_create),
        icon = Icons.Default.Extension,
        modifier = modifier.padding(horizontal = 32.dp),
        action = if (canCreate) {
            {
                AdaptiveButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(Res.string.cd_add_server), modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            null
        },
    )
}

/** Same breakpoint the Projects index uses for its second column. */
private val TWO_COLUMN_MIN_WIDTH = 600.dp

/** Room under the last card for the extended FAB. */
private val FAB_CLEARANCE = 96.dp
