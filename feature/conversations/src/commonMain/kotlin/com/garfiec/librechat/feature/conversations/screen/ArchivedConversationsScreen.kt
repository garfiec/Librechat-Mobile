package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.conversations.ArchivedConversationDisplayData
import com.garfiec.librechat.feature.conversations.components.endpointDisplayLabel
import com.garfiec.librechat.feature.conversations.resources.*
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedConversationsScreen(
    onNavigateBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedConversationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteConfirmation by remember { mutableStateOf<ArchivedConversationDisplayData?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ArchivedConversationsEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            // The loading flags make this drop to false during a load, so it re-fires after one even
            // when the page only re-delivers chats the cache already held and the list doesn't grow.
            lastVisibleIndex >= totalItems - 3 && uiState.hasMore && !uiState.loadMoreFailed &&
                !uiState.isLoading && !uiState.isLoadingMore && !uiState.isRefreshing
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.archived_conversations)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading && uiState.conversations.isEmpty() -> {
                    LoadingIndicator()
                }
                !uiState.isLoading && uiState.conversations.isEmpty() && uiState.error != null -> {
                    ErrorBanner(
                        message = uiState.error ?: stringResource(Res.string.could_not_load_archived),
                        onRetry = {
                            viewModel.dismissError()
                            viewModel.loadArchivedConversations()
                        },
                    )
                }
                !uiState.isLoading && uiState.conversations.isEmpty() -> {
                    EmptyState(
                        title = stringResource(Res.string.no_archived_conversations),
                        description = stringResource(Res.string.archived_will_appear_here),
                        icon = Icons.Default.Archive,
                    )
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.conversations,
                            key = { it.id },
                            contentType = { "archived_conversation" },
                        ) { displayData ->
                            ArchivedConversationItem(
                                data = displayData,
                                onClick = { onConversationClick(displayData.id) },
                                onUnarchive = {
                                    viewModel.unarchiveConversation(displayData.id)
                                },
                                onDelete = {
                                    deleteConfirmation = displayData
                                },
                            )
                            AdaptiveDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        when {
                            uiState.isLoadingMore -> item(key = "loading_more", contentType = "loading_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AdaptiveCircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            uiState.loadMoreFailed -> item(key = "load_more_failed", contentType = "load_more_failed") {
                                LoadMoreFailedRow(onRetry = viewModel::retryLoadMore)
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirmation != null) {
        AdaptiveAlertDialog(
            onDismissRequest = { deleteConfirmation = null },
            title = { Text(stringResource(Res.string.delete_conversation)) },
            text = {
                Text(
                    text = stringResource(
                        Res.string.delete_confirmation_message,
                        deleteConfirmation?.title ?: stringResource(Res.string.this_conversation),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmation?.id?.let { id ->
                        viewModel.deleteConversation(id)
                    }
                    deleteConfirmation = null
                }) {
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmation = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ArchivedConversationItem(
    data: ArchivedConversationDisplayData,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val time = remember(data.updatedAt, format, reference) {
        data.updatedAt?.listLabel(format, reference)
    }?.resolve() ?: ""
    val endpointLabel = endpointDisplayLabel(data.endpoint)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EndpointIcon(
            endpointName = data.endpoint,
            iconUrl = data.endpointIconUrl,
            size = 24.dp,
            contentDescription = endpointLabel,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The time is kept whole; endpoint and model share what it leaves.
                Text(
                    text = listOfNotNull(endpointLabel, data.modelLabel).joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (time.isNotEmpty()) {
                    Text(
                        text = " \u00B7 $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onUnarchive) {
            Icon(
                imageVector = Icons.Default.Unarchive,
                contentDescription = stringResource(Res.string.cd_unarchive),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.cd_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LoadMoreFailedRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.could_not_load_more_archived),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.archived_load_more_retry))
        }
    }
}
