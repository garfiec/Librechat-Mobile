package com.garfiec.librechat.feature.agents.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.agents.viewmodel.AgentMarketplaceViewModel
import org.koin.compose.viewmodel.koinViewModel

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
            onSortSelect = viewModel::onSortSelected,
            onMineToggle = viewModel::onMineToggled,
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
