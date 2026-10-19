package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.components.model.MarketplaceBuiltinLabel
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.MarketplaceKind
import com.garfiec.librechat.feature.agents.viewmodel.delegate.MarketplaceFilter

// State is hoisted the way the agent editor hoists it, so interactive mode searches, filters and
// toggles.

@PreviewNarrowScreens
@Composable
private fun ToolsMarketplacePreview() = PreviewSurface(UiStyle.MATERIAL) { ToolsMarketplaceHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun ToolsMarketplaceHost(initialQuery: String = "") {
    var query by remember { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(MarketplaceFilter.ALL) }
    var selected by remember { mutableStateOf(setOf(previewCatalog[0].itemKey)) }
    ToolsMarketplaceDialog(
        catalog = previewCatalog,
        selectedKeys = selected,
        favoriteKeys = emptySet(),
        favoritesSupported = true,
        query = query,
        filter = filter,
        onQueryChange = { query = it },
        onFilterChange = { filter = it },
        onToggleItem = { item -> selected = if (item.itemKey in selected) selected - item.itemKey else selected + item.itemKey },
        onToggleFavorite = {},
        onDismiss = {},
    )
}

private val previewCatalog = listOf(
    MarketplaceItem(MarketplaceKind.BUILTIN, "execute_code", labelRes = MarketplaceBuiltinLabel.CODE_INTERPRETER),
    MarketplaceItem(MarketplaceKind.BUILTIN, "file_search", labelRes = MarketplaceBuiltinLabel.FILE_SEARCH),
    MarketplaceItem(MarketplaceKind.BUILTIN, "web_search", labelRes = MarketplaceBuiltinLabel.WEB_SEARCH),
    MarketplaceItem(MarketplaceKind.TOOL, "dalle", name = "DALL-E 3", description = "Generates images from a prompt."),
    MarketplaceItem(MarketplaceKind.TOOL, "wolfram", name = "Wolfram", description = "Computes answers from curated data."),
    MarketplaceItem(MarketplaceKind.MCP, "jira_search", name = "search_issues", serverName = "Jira"),
    MarketplaceItem(MarketplaceKind.MCP, "jira_create", name = "create_issue", serverName = "Jira"),
    MarketplaceItem(MarketplaceKind.MCP, "github_pr", name = "create_pull_request", serverName = "GitHub"),
    MarketplaceItem(MarketplaceKind.SKILL, "review", name = "Code review", description = "Reviews a diff for correctness."),
    MarketplaceItem(MarketplaceKind.SKILL, "release", name = "Release notes", description = "Drafts notes from merged PRs."),
)
