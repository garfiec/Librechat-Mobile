package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.agents.AgentCardDisplayData
import com.garfiec.librechat.feature.agents.components.agentCategoryLabel
import com.garfiec.librechat.feature.agents.components.categoryValueLabel
import com.garfiec.librechat.feature.agents.components.displayLabel
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AgentChip
import com.garfiec.librechat.feature.agents.viewmodel.AgentListQuery
import com.garfiec.librechat.feature.agents.viewmodel.AgentMarketplaceUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentMarketplaceViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val AgentCardMinWidth = 300.dp
private val CardShape = RoundedCornerShape(16.dp)
private const val PLACEHOLDER_COUNT = 4

/** Room under the last card for the extended FAB. */
private val FAB_CLEARANCE = 96.dp

/** The list under a chip or search that is still loading: visible, but plainly not the answer yet. */
private const val STALE_LIST_ALPHA = 0.48f

@Composable
fun AgentMarketplaceScreen(
    onAgentClick: (String) -> Unit,
    onCreateAgent: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: AgentMarketplaceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Reload on return so a delete (or edit) made on the pushed detail screen is reflected;
    // without this the marketplace keeps a stale card that 404s on tap.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    AgentMarketplaceContent(
        uiState = uiState,
        actions = AgentMarketplaceActions(
            onAgentClick = onAgentClick,
            onCreateAgent = onCreateAgent,
            onBack = onBack,
            onSearchQueryChange = viewModel::onSearchQueryChanged,
            onChipSelect = viewModel::onChipSelected,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            onSearchAll = viewModel::searchAllAgents,
            onShowAll = viewModel::showAllAgents,
            onDismissError = viewModel::dismissError,
        ),
        modifier = modifier,
    )
}

/** What [AgentMarketplaceContent] can ask of its owner. */
internal class AgentMarketplaceActions(
    val onAgentClick: (String) -> Unit = {},
    val onCreateAgent: () -> Unit = {},
    val onBack: (() -> Unit)? = null,
    val onSearchQueryChange: (String) -> Unit = {},
    val onChipSelect: (AgentChip) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetryLoadMore: () -> Unit = {},
    val onSearchAll: () -> Unit = {},
    val onShowAll: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentMarketplaceContent(
    uiState: AgentMarketplaceUiState,
    actions: AgentMarketplaceActions,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    val retryLabel = stringResource(Res.string.agents_retry)
    val loadFailed = stringResource(Res.string.agents_load_failed)
    val currentActions by rememberUpdatedState(actions)

    // With agents on screen, a failed reload is a snackbar; with none, the page itself is the error.
    LaunchedEffect(uiState.loadError, uiState.agents.isEmpty()) {
        val error = uiState.loadError ?: return@LaunchedEffect
        if (uiState.agents.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = error.message ?: loadFailed, actionLabel = retryLabel)
        currentActions.onDismissError()
        if (result == SnackbarResult.ActionPerformed) currentActions.onRefresh()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    // Keyed on the laid-out item count too, so a page that lands with the end still in view asks for
    // the next one; read after layout, so it never judges a new page by the old count.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.totalItemsCount to shouldLoadMore }
            .collect { (_, nearEnd) -> if (nearEnd) currentActions.onLoadMore() }
    }

    val showsServerEmpty = uiState.isServerEmpty
    AdaptiveScaffold(
        // The search is typed here, so the grid and its empty state end where the keyboard begins.
        modifier = modifier.imePadding(),
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            val onBack = actions.onBack
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = onBack?.let { BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), it) },
                    title = BarTitle(stringResource(Res.string.agents)),
                ),
            )
        },
        floatingActionButton = {
            // The empty server's state carries its own New agent button; two would compete.
            if (uiState.canBrowse && uiState.agentsCreateEnabled && !showsServerEmpty) {
                AdaptiveExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.agents_new_agent)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = actions.onCreateAgent,
                    expanded = fabExpanded,
                )
            }
        },
    ) { innerPadding ->
        if (!uiState.canBrowse) {
            // The role denies AGENTS.USE or MARKETPLACE.USE. The drawer hides the entry then, so this
            // is reached only by a stale back stack or a role that changed while it was open.
            EmptyState(
                title = stringResource(Res.string.agents_not_available_title),
                description = stringResource(Res.string.agents_not_available_description),
                icon = Icons.Default.SmartToy,
                modifier = Modifier.padding(innerPadding).padding(horizontal = 32.dp),
            )
            return@AdaptiveScaffold
        }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            AgentGrid(uiState = uiState, actions = actions, gridState = gridState)
        }
    }
}

/** The server has no agents this user can see: All, no search, nothing back. */
private val AgentMarketplaceUiState.isServerEmpty: Boolean
    get() = !isLoading && !isRefreshing && loadError == null && agents.isEmpty() &&
        shownQuery == AgentListQuery(AgentChip.All, "")

@Composable
internal fun AgentGrid(
    uiState: AgentMarketplaceUiState,
    actions: AgentMarketplaceActions,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val categoriesByValue = remember(uiState.categories) { uiState.categories.associateBy { it.value } }
    // The card's text scales with the font, so its minimum width does too.
    val minCardWidth = AgentCardMinWidth * LocalDensity.current.fontScale
    // A pull on the error page has nothing to show either; placeholders, not a flash of "No agents yet".
    val isFirstLoad = (uiState.isLoading || uiState.isRefreshing) && uiState.agents.isEmpty()
    val showsServerEmpty = uiState.isServerEmpty
    LazyVerticalGrid(
        columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = FAB_CLEARANCE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (!showsServerEmpty) {
            fullWidthItem(key = "search") {
                AgentSearchField(query = uiState.searchQuery, onQueryChange = actions.onSearchQueryChange)
            }
            fullWidthItem(key = "chips") {
                AgentChipRow(
                    chips = uiState.chips,
                    selected = uiState.selectedChip,
                    categoriesByValue = categoriesByValue,
                    onSelect = actions.onChipSelect,
                )
            }
        }

        when {
            isFirstLoad -> items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                AgentCardPlaceholder()
            }

            uiState.loadError != null && uiState.agents.isEmpty() -> fullWidthItem(key = "error") {
                EmptyState(
                    title = stringResource(Res.string.agents_load_failed),
                    description = uiState.loadError.message,
                    icon = Icons.Default.CloudOff,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 48.dp),
                    action = { AdaptiveButton(onClick = actions.onRetry) { Text(stringResource(Res.string.agents_retry)) } },
                )
            }

            uiState.agents.isEmpty() -> fullWidthItem(key = "empty") {
                AgentListEmptyState(
                    shown = uiState.shownQuery,
                    canCreate = uiState.agentsCreateEnabled,
                    categoriesByValue = categoriesByValue,
                    actions = actions,
                )
            }

            else -> items(uiState.agents, key = { it.id }, contentType = { "agent" }) { agent ->
                AgentCard(
                    agent = agent,
                    categoryLabel = agentCategoryLabel(agent.categoryValue, categoriesByValue),
                    onClick = { actions.onAgentClick(agent.id) },
                    // A new chip or search is loading: the old answer stays readable but steps back.
                    modifier = Modifier.alpha(if (uiState.isLoading) STALE_LIST_ALPHA else 1f),
                )
            }
        }

        if (uiState.isLoadingMore) {
            fullWidthItem(key = "loading_more") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator()
                }
            }
        } else if (uiState.loadMoreFailed && uiState.agents.isNotEmpty()) {
            fullWidthItem(key = "load_more_failed") {
                LoadMoreFailedRow(onRetry = actions.onRetryLoadMore)
            }
        }
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)

@Composable
private fun AgentSearchField(query: String, onQueryChange: (String) -> Unit) {
    AdaptiveOutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.search_agents_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.agents_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
    )
}

/** The chip row. Tapping the selected chip does nothing: one chip is always selected. */
@Composable
internal fun AgentChipRow(
    chips: List<AgentChip>,
    selected: AgentChip?,
    categoriesByValue: Map<String, AgentCategory>,
    onSelect: (AgentChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = chips, key = { it.key }, contentType = { "chip" }) { chip ->
            AdaptiveFilterChip(
                selected = chip == selected,
                onClick = { if (chip != selected) onSelect(chip) },
                label = { Text(chip.label(categoriesByValue), maxLines = 1) },
            )
        }
    }
}

private val AgentChip.key: String
    get() = when (this) {
        AgentChip.TopPicks -> "top_picks"
        AgentChip.All -> "all"
        AgentChip.Editable -> "editable"
        // Prefixed: a category may be named like one of the chips above.
        is AgentChip.Category -> "category:$value"
    }

@Composable
private fun AgentChip.label(categoriesByValue: Map<String, AgentCategory>): String = when (this) {
    AgentChip.TopPicks -> stringResource(Res.string.agents_chip_top_picks)
    AgentChip.All -> stringResource(Res.string.agents_chip_all)
    AgentChip.Editable -> stringResource(Res.string.agents_chip_editable)
    is AgentChip.Category -> categoriesByValue[value]?.displayLabel() ?: categoryValueLabel(value)
}

/**
 * Nothing to list. An empty server says so and offers New agent; a chip or search that found
 * nothing names what it looked in and offers a way out to every agent.
 */
@Composable
private fun AgentListEmptyState(
    shown: AgentListQuery?,
    canCreate: Boolean,
    categoriesByValue: Map<String, AgentCategory>,
    actions: AgentMarketplaceActions,
) {
    val chip = shown?.chip ?: AgentChip.All
    val searching = !shown?.search.isNullOrEmpty()
    // While searching, Top picks searches everything, like All.
    val narrowed = chip != AgentChip.All && !(searching && chip == AgentChip.TopPicks)
    val modifier = Modifier.padding(horizontal = 16.dp, vertical = 48.dp)
    when {
        !narrowed && !searching -> EmptyState(
            title = stringResource(Res.string.agents_empty_title),
            description = stringResource(
                if (canCreate) Res.string.agents_empty_description_create else Res.string.check_back_later,
            ),
            icon = Icons.Default.SmartToy,
            modifier = modifier,
            action = if (canCreate) {
                { NewAgentButton(onClick = actions.onCreateAgent) }
            } else {
                null
            },
        )

        !narrowed -> EmptyState(
            title = stringResource(Res.string.no_agents_found),
            description = stringResource(Res.string.try_different_search),
            icon = Icons.Default.Search,
            modifier = modifier,
            action = { WayOutButton(stringResource(Res.string.agents_clear_search), actions.onShowAll) },
        )

        searching -> EmptyState(
            title = stringResource(Res.string.agents_no_matches_in, chip.label(categoriesByValue)),
            description = stringResource(Res.string.try_different_search),
            icon = Icons.Default.Search,
            modifier = modifier,
            action = { WayOutButton(stringResource(Res.string.agents_search_all), actions.onSearchAll) },
        )

        else -> EmptyState(
            title = stringResource(Res.string.agents_none_in, chip.label(categoriesByValue)),
            icon = Icons.Default.SmartToy,
            modifier = modifier,
            action = { WayOutButton(stringResource(Res.string.agents_show_all), actions.onShowAll) },
        )
    }
}

@Composable
private fun NewAgentButton(onClick: () -> Unit) {
    AdaptiveButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.agents_new_agent))
    }
}

@Composable
private fun WayOutButton(label: String, onClick: () -> Unit) {
    AdaptiveButton(onClick = onClick) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
private fun LoadMoreFailedRow(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.agents_load_more_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.agents_retry)) }
    }
}

/** One agent: tile, name, a two-line description, and a meta row (Top pick, category, contact). */
@Composable
internal fun AgentCard(
    agent: AgentCardDisplayData,
    categoryLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = agent.name ?: stringResource(Res.string.agent_unnamed)
    AdaptiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            InitialTile(seed = agent.id, label = name, imageUrl = agent.avatarUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = agent.description
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                AgentMetaRow(isPromoted = agent.isPromoted, details = listOfNotNull(categoryLabel, agent.contactName))
            }
        }
    }
}

@Composable
private fun AgentMetaRow(isPromoted: Boolean, details: List<String>) {
    if (!isPromoted && details.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        if (isPromoted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.agents_top_pick), style = style, color = color, maxLines = 1, softWrap = false)
            }
        }
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** First-load stand-in, sized like a card with a description, so the grid doesn't jump on arrival. */
@Composable
internal fun AgentCardPlaceholder() {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
        }
    }
}
