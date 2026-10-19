package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LibreChatTopBar
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.endpointIconPainter
import com.garfiec.librechat.feature.agents.AgentCardDisplayData
import com.garfiec.librechat.feature.agents.components.displayLabel
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AgentMarketplaceViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentMarketplaceScreen(
    onAgentClick: (String) -> Unit,
    onCreateAgent: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: AgentMarketplaceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Reload on return so a delete (or edit) made on the pushed detail screen is
    // reflected — without this the marketplace keeps a stale card that 404s on tap.
    // Mirrors SkillsListScreen's ON_RESUME refresh.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOnReturn()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 &&
                uiState.hasMore &&
                !uiState.isLoadingMore &&
                !uiState.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    AdaptiveScaffold(
        // The search is typed here, so the grid and its empty state end where the keyboard begins.
        modifier = modifier.imePadding(),
        topBar = {
            LibreChatTopBar(
                title = stringResource(Res.string.agents),
                onNavigateBack = onBack,
            )
        },
        floatingActionButton = {
            if (uiState.agentsEnabled && uiState.agentsCreateEnabled) {
                AdaptiveFloatingActionButton(
                    onClick = onCreateAgent,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cd_create_agent),
                    )
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!uiState.agentsEnabled) {
                // Role denies AGENTS.USE — render degraded empty state. Hide search,
                // category chips, grid, and FAB. Matches the Settings MCP "not available"
                // pattern for consistency across gated surfaces.
                EmptyState(
                    title = stringResource(Res.string.agents_not_available_title),
                    description = stringResource(Res.string.agents_not_available_description),
                    icon = Icons.Default.SmartToy,
                )
                return@PullToRefreshBox
            }
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar
                AdaptiveOutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(Res.string.search_agents_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(Res.string.cd_search),
                        )
                    },
                    singleLine = true,
                )

                // Category chips
                if (uiState.categories.isNotEmpty()) {
                    AgentCategoryChips(
                        categories = uiState.categories,
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelect = viewModel::onCategorySelected,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when {
                    uiState.isLoading && uiState.agents.isEmpty() -> {
                        LoadingIndicator()
                    }

                    !uiState.isLoading && uiState.filteredAgents.isEmpty() && uiState.error == null -> {
                        EmptyState(
                            title = if (uiState.searchQuery.isNotBlank() || uiState.selectedCategory != null) {
                                stringResource(Res.string.no_agents_found)
                            } else {
                                stringResource(Res.string.no_agents_available)
                            },
                            description = if (uiState.searchQuery.isNotBlank()) {
                                stringResource(Res.string.try_different_search)
                            } else {
                                stringResource(Res.string.check_back_later)
                            },
                            icon = Icons.Default.SmartToy,
                        )
                    }

                    else -> {
                        if (uiState.error != null) {
                            ErrorBanner(
                                message = uiState.error ?: stringResource(Res.string.error_unknown),
                                onRetry = {
                                    viewModel.dismissError()
                                    viewModel.loadAgents()
                                },
                            )
                        }

                        AgentGrid(
                            agents = uiState.filteredAgents,
                            isLoadingMore = uiState.isLoadingMore,
                            gridState = gridState,
                            onAgentClick = onAgentClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgentGrid(
    agents: List<AgentCardDisplayData>,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    onAgentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The card's text scales with the font, so its minimum width does too.
    val minCardWidth = AgentCardMinWidth * LocalDensity.current.fontScale
    LazyVerticalGrid(
        columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = agents,
            key = { it.id },
            contentType = { "agent" },
        ) { agent ->
            AgentCard(
                agent = agent,
                onClick = { onAgentClick(agent.id) },
            )
        }

        if (isLoadingMore) {
            item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveCircularProgressIndicator()
                }
            }
        }
    }
}

// Two columns hold on a 320dp phone up to about 1.15x, and on a 360dp one up to 1.3x.
private val AgentCardMinWidth = 120.dp

/**
 * Two columns, as wide as they come, or one when two would be narrower than [minColumnWidth]: a
 * small phone at a large font scale can't fit a card's name and description in half the width.
 */
private class UpToTwoColumns(private val minColumnWidth: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = if ((availableSize - spacing) / 2 >= minColumnWidth.roundToPx()) 2 else 1
        // Split as GridCells.Fixed does: the remainder pixels go to the first columns.
        val usable = availableSize - spacing * (count - 1)
        return List(count) { usable / count + if (it < usable % count) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean = other is UpToTwoColumns && other.minColumnWidth == minColumnWidth

    override fun hashCode(): Int = minColumnWidth.hashCode()
}

@Composable
private fun AgentCard(
    agent: AgentCardDisplayData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarImage(
                    imageUrl = agent.avatarUrl,
                    size = 40.dp,
                    fallbackText = agent.name,
                    fallbackIconPainter = endpointIconPainter(EModelEndpoint.AGENTS),
                    tintIcon = true,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = agent.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = agent.description
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Only a human-readable author is worth a line; the raw author user ID is noise.
            val authorName = agent.authorName
            if (authorName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun AgentCategoryChips(
    categories: List<AgentCategory>,
    selectedCategory: String?,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = categories,
            key = { it.value },
            contentType = { "category" },
        ) { category ->
            AdaptiveFilterChip(
                selected = selectedCategory == category.value,
                onClick = { onCategorySelect(category.value) },
                label = { Text(category.displayLabel()) },
            )
        }
    }
}
