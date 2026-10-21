package com.garfiec.librechat.feature.agents.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.viewmodel.AgentChip
import com.garfiec.librechat.feature.agents.viewmodel.AgentListError
import com.garfiec.librechat.feature.agents.viewmodel.AgentListQuery
import com.garfiec.librechat.feature.agents.viewmodel.AgentMarketplaceUiState

// Every state of the index at the widths and translations that squeeze it.

@PreviewNarrowScreens
@Composable
private fun AgentIndexPreview() = IndexHost(UiStyle.MATERIAL, listed)

@PreviewNarrowScreens
@Composable
private fun AgentIndexGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, listed)

@PreviewNarrowScreens
@Composable
private fun AgentPlaceholdersPreview() = IndexHost(UiStyle.MATERIAL, AgentMarketplaceUiState())

@PreviewNarrowScreens
@Composable
private fun AgentPlaceholdersGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, AgentMarketplaceUiState())

@PreviewNarrowScreens
@Composable
private fun AgentEmptyCreatePreview() = IndexHost(UiStyle.MATERIAL, emptyServer)

@PreviewNarrowScreens
@Composable
private fun AgentEmptyCreateGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, emptyServer)

@PreviewNarrowScreens
@Composable
private fun AgentEmptyNoCreatePreview() = IndexHost(UiStyle.MATERIAL, emptyServer.copy(agentsCreateEnabled = false))

@PreviewNarrowScreens
@Composable
private fun AgentEmptyNoCreateGlassPreview() =
    IndexHost(UiStyle.LIQUID_GLASS, emptyServer.copy(agentsCreateEnabled = false))

@PreviewNarrowScreens
@Composable
private fun AgentNoMatchesPreview() = IndexHost(UiStyle.MATERIAL, noMatchesInCategory)

@PreviewNarrowScreens
@Composable
private fun AgentNoMatchesGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, noMatchesInCategory)

@PreviewNarrowScreens
@Composable
private fun AgentNoSearchResultsPreview() = IndexHost(UiStyle.MATERIAL, noSearchResults)

@PreviewNarrowScreens
@Composable
private fun AgentNoSearchResultsGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, noSearchResults)

@PreviewNarrowScreens
@Composable
private fun AgentLoadErrorPreview() = IndexHost(UiStyle.MATERIAL, loadFailed)

@PreviewNarrowScreens
@Composable
private fun AgentLoadErrorGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, loadFailed)

@PreviewNarrowScreens
@Composable
private fun AgentNotAvailablePreview() = IndexHost(UiStyle.MATERIAL, notAvailable)

@PreviewNarrowScreens
@Composable
private fun AgentNotAvailableGlassPreview() = IndexHost(UiStyle.LIQUID_GLASS, notAvailable)

@Composable
private fun IndexHost(style: UiStyle, state: AgentMarketplaceUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        AgentMarketplaceContent(uiState = state, actions = AgentMarketplaceActions(onBack = {}))
    }
}

private val categories = listOf(
    AgentCategory(value = "general", label = "com_agents_category_general"),
    AgentCategory(value = "rd", label = "com_agents_category_rd"),
    AgentCategory(value = "finance", label = "com_agents_category_finance"),
    AgentCategory(value = "promoted", label = "com_agents_top_picks"),
    AgentCategory(value = "all", label = "com_agents_all"),
)

private val listed = AgentMarketplaceUiState(
    agents = previewAgentCards,
    isLoading = false,
    selectedChip = AgentChip.TopPicks,
    shownQuery = AgentListQuery(AgentChip.TopPicks, ""),
    categories = categories,
)

private val emptyServer = AgentMarketplaceUiState(
    isLoading = false,
    selectedChip = AgentChip.All,
    shownQuery = AgentListQuery(AgentChip.All, ""),
)

private val noMatchesInCategory = AgentMarketplaceUiState(
    isLoading = false,
    selectedChip = AgentChip.Category("finance"),
    searchQuery = "payroll",
    shownQuery = AgentListQuery(AgentChip.Category("finance"), "payroll"),
    categories = categories,
)

private val noSearchResults = AgentMarketplaceUiState(
    isLoading = false,
    selectedChip = AgentChip.All,
    searchQuery = "quarterly forecast",
    shownQuery = AgentListQuery(AgentChip.All, "quarterly forecast"),
    categories = categories,
)

private val loadFailed = AgentMarketplaceUiState(
    isLoading = false,
    loadError = AgentListError(message = null),
    selectedChip = AgentChip.All,
    shownQuery = AgentListQuery(AgentChip.All, ""),
    categories = categories,
)

private val notAvailable = AgentMarketplaceUiState(isLoading = false, marketplaceEnabled = false)
