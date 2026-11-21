package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.UnderBarsPullToRefreshBox
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.skills.components.SkillCard
import com.garfiec.librechat.feature.skills.components.SkillCardPlaceholder
import com.garfiec.librechat.feature.skills.components.SkillImportFailureDialog
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skills_back
import com.garfiec.librechat.feature.skills.resources.skills_empty
import com.garfiec.librechat.feature.skills.resources.skills_empty_body
import com.garfiec.librechat.feature.skills.resources.skills_empty_body_no_create
import com.garfiec.librechat.feature.skills.resources.skills_import
import com.garfiec.librechat.feature.skills.resources.skills_importing
import com.garfiec.librechat.feature.skills.resources.skills_load_more_failed
import com.garfiec.librechat.feature.skills.resources.skills_more_options
import com.garfiec.librechat.feature.skills.resources.skills_new
import com.garfiec.librechat.feature.skills.resources.skills_no_results
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.resources.skills_search_clear
import com.garfiec.librechat.feature.skills.resources.skills_search_hint
import com.garfiec.librechat.feature.skills.resources.skills_title
import com.garfiec.librechat.feature.skills.text
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListUiState
import org.jetbrains.compose.resources.stringResource

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val SkillCardMinWidth = 300.dp

/** Room under the last card for the extended FAB. */
private val FabClearance = 96.dp
private const val PLACEHOLDER_COUNT = 3

/** What the skills list asks of its owner; `SkillsListScreen` binds each to its ViewModel and the import picker. */
class SkillsListActions(
    val onSkillClick: (skillId: String) -> Unit = {},
    val onCreateSkill: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetryLoadMore: () -> Unit = {},
    val onLoadFirstPage: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onDismissImportFailure: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsListScreenContent(
    uiState: SkillsListUiState,
    actions: SkillsListActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val currentUiState by rememberUpdatedState(uiState)
    val currentActions by rememberUpdatedState(actions)
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    // Infinite scroll: load more when within 3 of the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val state = currentUiState
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            last >= total - 3 && state.hasMore && !state.isLoadingMore && !state.loadMoreFailed
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }.collect { if (it) currentActions.onLoadMore() }
    }

    uiState.importFailure?.let { failure ->
        SkillImportFailureDialog(failure = failure, onDismiss = actions.onDismissImportFailure)
    }

    val content = listContent(uiState)
    val onImport = actions.onImport

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), actions.onBack),
                    title = BarTitle(stringResource(Res.string.skills_title)),
                    actions = if (uiState.canCreate) {
                        listOf(
                            BarAction.Menu(
                                id = "more",
                                icon = BarIcons.More,
                                label = stringResource(Res.string.skills_more_options),
                                sections = listOf(
                                    BarMenuSection(
                                        listOf(
                                            BarMenuItem(
                                                id = "import",
                                                label = stringResource(Res.string.skills_import),
                                                icon = BarIcons.Import,
                                                enabled = !uiState.isImporting,
                                                onClick = onImport,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
                materialStyle = MaterialBarStyle(translucent = true),
            )
        },
        floatingActionButton = {
            // The empty state carries its own New skill button; two would compete.
            if (uiState.canCreate && (content == ListContent.CARDS || content == ListContent.NO_RESULTS)) {
                AdaptiveExtendedFloatingActionButton(
                    text = stringResource(Res.string.skills_new),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = actions.onCreateSkill,
                    expanded = fabExpanded,
                )
            }
        },
    ) { padding ->
        UnderBarsPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            barPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            val minCardWidth = SkillCardMinWidth * LocalDensity.current.fontScale
            // Every state is a grid item, so pull-to-refresh always has something to pull and the
            // search field keeps its focus while the results under it change.
            LazyVerticalGrid(
                state = gridState,
                columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listHeader(uiState, actions.onSearchQueryChange)
                when (content) {
                    ListContent.PLACEHOLDERS -> items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                        SkillCardPlaceholder()
                    }
                    ListContent.ERROR -> fullWidthItem(key = "error") {
                        ErrorBanner(
                            message = uiState.error?.text().orEmpty(),
                            onRetry = actions.onLoadFirstPage,
                            retryLabel = stringResource(Res.string.skills_retry),
                        )
                    }
                    ListContent.EMPTY -> fullWidthItem(key = "empty") {
                        SkillsEmptyState(
                            canCreate = uiState.canCreate,
                            onCreate = actions.onCreateSkill,
                            onImport = onImport,
                            importEnabled = !uiState.isImporting,
                        )
                    }
                    ListContent.NO_RESULTS -> fullWidthItem(key = "no_results") {
                        SkillsNoResults(query = uiState.shownQuery)
                    }
                    ListContent.CARDS -> items(uiState.rows, key = { "skill:${it.summary.id}" }, contentType = { "skill" }) { row ->
                        SkillCard(
                            row = row,
                            categories = uiState.categories,
                            onClick = { actions.onSkillClick(row.summary.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                listFooter(uiState, actions.onRetryLoadMore)
            }
        }
    }
}

/** What the body of the list shows. */
internal enum class ListContent { PLACEHOLDERS, ERROR, EMPTY, NO_RESULTS, CARDS }

internal fun listContent(state: SkillsListUiState): ListContent = when {
    state.skills.isNotEmpty() -> ListContent.CARDS
    state.error != null && !state.isLoading -> ListContent.ERROR
    // A search keeps "no results" up while the next query runs, rather than flashing placeholders.
    state.isSearching && (!state.isLoading || state.shownQuery.isNotEmpty()) -> ListContent.NO_RESULTS
    state.isLoading -> ListContent.PLACEHOLDERS
    else -> ListContent.EMPTY
}

private fun LazyGridScope.listHeader(state: SkillsListUiState, onQueryChange: (String) -> Unit) {
    if (state.isImporting) {
        fullWidthItem(key = "importing") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.skills_importing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (state.showSearch) {
        fullWidthItem(key = "search") {
            AdaptiveOutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.skills_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.skills_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
        }
    }
}

private fun LazyGridScope.listFooter(state: SkillsListUiState, onRetry: () -> Unit) {
    if (state.isLoadingMore) {
        fullWidthItem(key = "loading_more") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator()
            }
        }
    }
    if (state.loadMoreFailed && state.skills.isNotEmpty()) {
        fullWidthItem(key = "load_more_failed") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.skills_load_more_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                AdaptiveOutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.skills_retry)) }
            }
        }
    }
}

/** No skills at all. New skill and Import with CREATE; without it, nothing to offer but what shows up here. */
@Composable
internal fun SkillsEmptyState(
    canCreate: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while an import runs, as the top bar's Import: a second pick then would be dropped unsent. */
    importEnabled: Boolean = true,
) {
    EmptyState(
        title = stringResource(Res.string.skills_empty),
        description = stringResource(if (canCreate) Res.string.skills_empty_body else Res.string.skills_empty_body_no_create),
        icon = Icons.Default.Extension,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 48.dp),
        action = if (canCreate) {
            {
                // Stacked: side by side they overflow a 320dp screen in a long translation.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveButton(onClick = onCreate) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.skills_new), modifier = Modifier.padding(start = 8.dp))
                    }
                    AdaptiveOutlinedButton(onClick = onImport, enabled = importEnabled) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.skills_import), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else {
            null
        },
    )
}

/** A search that found nothing, echoing the query the empty result answers. */
@Composable
internal fun SkillsNoResults(query: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.skills_no_results, query),
        icon = Icons.Default.Search,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 48.dp),
    )
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
