package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_delete
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.resources.cd_back
import com.garfiec.librechat.feature.settings.resources.dialog_delete_shared_link_message
import com.garfiec.librechat.feature.settings.resources.dialog_title_delete_shared_link
import com.garfiec.librechat.feature.settings.resources.shared_chats_clear_search
import com.garfiec.librechat.feature.settings.resources.shared_chats_delete_failed
import com.garfiec.librechat.feature.settings.resources.shared_chats_empty
import com.garfiec.librechat.feature.settings.resources.shared_chats_empty_description
import com.garfiec.librechat.feature.settings.resources.shared_chats_link_copied
import com.garfiec.librechat.feature.settings.resources.shared_chats_load_failed
import com.garfiec.librechat.feature.settings.resources.shared_chats_load_failed_description
import com.garfiec.librechat.feature.settings.resources.shared_chats_load_more_failed
import com.garfiec.librechat.feature.settings.resources.shared_chats_no_match
import com.garfiec.librechat.feature.settings.resources.shared_chats_refresh_failed
import com.garfiec.librechat.feature.settings.resources.shared_chats_search
import com.garfiec.librechat.feature.settings.resources.shared_chats_sort_label
import com.garfiec.librechat.feature.settings.resources.shared_chats_sort_newest
import com.garfiec.librechat.feature.settings.resources.shared_chats_sort_oldest
import com.garfiec.librechat.feature.settings.resources.shared_chats_sort_title
import com.garfiec.librechat.feature.settings.resources.shared_chats_title
import com.garfiec.librechat.feature.settings.resources.shared_chats_untitled
import com.garfiec.librechat.feature.settings.util.rememberClipboardWriter
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksMessage
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksUiState
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksViewModel
import com.garfiec.librechat.feature.settings.viewmodel.shouldLoadMore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val SharedLinkCardMinWidth = 300.dp
private const val PLACEHOLDER_COUNT = 4

/**
 * Settings → Data → Shared chats: every link the user has shared, as cards. A card opens the share
 * sheet on its link; its ⋮ opens the chat, copies the link or deletes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedLinksScreen(
    onNavigateBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedLinksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = rememberClipboardWriter()
    val clipLabel = stringResource(Res.string.shared_chats_title)
    var deleteTarget by remember { mutableStateOf<SharedLinkDisplayData?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(getString(message.text))
        }
    }

    val gridState = rememberLazyGridState()
    // Reads the current state on every layout pass. Capturing the first composition's values is
    // why scrolling once never loaded past the first page.
    val currentState by rememberUpdatedState(uiState)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            currentState.shouldLoadMore(
                lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItems = layout.totalItemsCount,
            )
        }
    }
    LaunchedEffect(viewModel) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    val linkCopied = stringResource(Res.string.shared_chats_link_copied)
    val onCopyLink: (SharedLinkDisplayData) -> Unit = { link ->
        clipboard.copy(link.url, clipLabel)
        scope.launch { snackbarHostState.showSnackbar(linkCopied) }
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.shared_chats_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            val minCardWidth = SharedLinkCardMinWidth * LocalDensity.current.fontScale
            LazyVerticalGrid(
                state = gridState,
                columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sharedLinksItems(
                    uiState = uiState,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onClearSearch = viewModel::clearSearch,
                    onSortChange = viewModel::setSort,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                    card = { link ->
                        SharedLinkCard(
                            link = link,
                            onClick = { viewModel.openLink(link) },
                            onOpenChat = { onOpenConversation(link.conversationId) },
                            onCopyLink = { onCopyLink(link) },
                            onDelete = { deleteTarget = link },
                            // "View shared version" arrives with the shared chat viewer.
                            onViewSharedVersion = null,
                        )
                    },
                )
            }
        }
    }

    SharedLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)

    deleteTarget?.let { target ->
        SharedLinkDeleteDialog(
            title = target.title ?: stringResource(Res.string.shared_chats_untitled),
            onConfirm = {
                viewModel.delete(target.shareId)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private val SharedLinksMessage.text: StringResource
    get() = when (this) {
        SharedLinksMessage.REFRESH_FAILED -> Res.string.shared_chats_refresh_failed
        SharedLinksMessage.DELETE_FAILED -> Res.string.shared_chats_delete_failed
    }

/**
 * The page's grid content for [uiState]: search and sort, then placeholders, the error, empty or
 * no-match state, or the cards, and the next-page row.
 */
@Suppress("LongParameterList")
internal fun LazyGridScope.sharedLinksItems(
    uiState: SharedLinksUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortChange: (SharedLinksSort) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    card: @Composable LazyGridItemScope.(SharedLinkDisplayData) -> Unit,
) {
    val isFirstLoad = uiState.isLoading && uiState.links.isEmpty()
    // From the search the shown list answers, not the typed one, which runs ahead of the results.
    val isSearching = uiState.shownQuery.isNotEmpty() || uiState.searchQuery.isNotEmpty()
    val hasNoLinks = uiState.links.isEmpty() && !uiState.isLoading && !uiState.loadFailed && !isSearching

    if (!hasNoLinks) {
        if (uiState.searchEnabled) {
            fullWidthItem(key = "search") {
                SharedLinksSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = onClearSearch,
                )
            }
        }
        fullWidthItem(key = "sort") { SharedLinksSortRow(sort = uiState.sort, onSortChange = onSortChange) }
    }

    when {
        isFirstLoad -> items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
            SharedLinkCardPlaceholder()
        }

        uiState.links.isEmpty() && uiState.loadFailed -> fullWidthItem(key = "error") {
            SharedLinksErrorState(onRetry = onRetry)
        }

        hasNoLinks -> fullWidthItem(key = "empty") { SharedLinksEmptyState() }

        uiState.links.isEmpty() && !uiState.isLoading -> fullWidthItem(key = "no_match") {
            SharedLinksNoMatch(query = uiState.shownQuery.ifEmpty { uiState.searchQuery.trim() }, onClearSearch = onClearSearch)
        }

        else -> items(uiState.links, key = { it.shareId }, contentType = { "shared_link" }) { link -> card(link) }
    }

    when {
        uiState.isLoadingMore || (uiState.isLoading && uiState.links.isNotEmpty()) -> fullWidthItem(key = "loading_more") {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        uiState.loadMoreFailed -> fullWidthItem(key = "load_more_failed") { LoadMoreFailedRow(onRetry = onLoadMore) }
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)

@Composable
private fun SharedLinksSearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    AdaptiveOutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.shared_chats_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.shared_chats_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
    )
}

private val SharedLinksSort.label: StringResource
    get() = when (this) {
        SharedLinksSort.NEWEST -> Res.string.shared_chats_sort_newest
        SharedLinksSort.OLDEST -> Res.string.shared_chats_sort_oldest
        SharedLinksSort.TITLE -> Res.string.shared_chats_sort_title
    }

@Composable
private fun SharedLinksSortRow(sort: SharedLinksSort, onSortChange: (SharedLinksSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            AdaptiveFilterChip(
                selected = true,
                onClick = { menuOpen = true },
                label = { Text(stringResource(sort.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(Res.string.shared_chats_sort_label),
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AdaptiveDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                SharedLinksSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label)) },
                        trailingIcon = { if (option == sort) Icon(Icons.Default.Check, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSortChange(option)
                        },
                    )
                }
            }
        }
    }
}

/** No links at all: says how to make one. */
@Composable
internal fun SharedLinksEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.shared_chats_empty),
        description = stringResource(Res.string.shared_chats_empty_description),
        icon = Icons.Default.Link,
        modifier = modifier.padding(vertical = 48.dp),
    )
}

/** The first page failed and there is nothing to show: not the empty state, which would be a lie. */
@Composable
internal fun SharedLinksErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.shared_chats_load_failed),
        description = stringResource(Res.string.shared_chats_load_failed_description),
        icon = Icons.Default.CloudOff,
        modifier = modifier.padding(vertical = 48.dp),
        action = { AdaptiveButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) } },
    )
}

@Composable
internal fun SharedLinksNoMatch(query: String, onClearSearch: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.shared_chats_no_match, query),
        icon = Icons.Default.Search,
        modifier = modifier.padding(vertical = 48.dp),
        action = {
            AdaptiveOutlinedButton(onClick = onClearSearch) { Text(stringResource(Res.string.shared_chats_clear_search)) }
        },
    )
}

/** A next page failed: the list ends here with a way to try again, instead of retrying on every scroll. */
@Composable
internal fun LoadMoreFailedRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.shared_chats_load_more_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}

@Composable
private fun SharedLinkDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_delete_shared_link)) },
        text = { Text(stringResource(Res.string.dialog_delete_shared_link_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
