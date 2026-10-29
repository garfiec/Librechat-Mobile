package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData
import com.garfiec.librechat.feature.conversations.components.ConversationItem
import com.garfiec.librechat.feature.conversations.components.ConversationSearchBar
import com.garfiec.librechat.feature.conversations.components.ProjectRow
import com.garfiec.librechat.feature.conversations.drawer.DrawerContent
import com.garfiec.librechat.feature.conversations.drawer.DrawerConversationDisplayData
import com.garfiec.librechat.feature.conversations.drawer.DrawerFooterItem
import com.garfiec.librechat.feature.conversations.drawer.DrawerGroupKey
import com.garfiec.librechat.feature.conversations.drawer.DrawerMessageSearch
import com.garfiec.librechat.feature.conversations.drawer.DrawerTab
import com.garfiec.librechat.feature.conversations.drawer.DrawerUiState
import com.garfiec.librechat.feature.conversations.drawer.messageResultsSection
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.settings
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// The conversation list's rows, the drawer and the Search screen at the widths and translations that
// squeeze them.

@PreviewNarrowScreens
@Composable
private fun DrawerProjectsPreview() = PreviewSurface(UiStyle.MATERIAL) { DrawerProjectsHost() }

@PreviewNarrowScreens
@Composable
private fun DrawerProjectsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { DrawerProjectsHost() }

@PreviewNarrowScreens
@Composable
private fun DrawerHitsPreview() = PreviewSurface(UiStyle.MATERIAL) { DrawerHitsHost() }

@PreviewNarrowScreens
@Composable
private fun SearchResultsPreview() = PreviewSurface(UiStyle.MATERIAL) { SearchResultsHost() }

@PreviewNarrowScreens
@Composable
private fun SearchAdvancedPreview() = PreviewSurface(UiStyle.MATERIAL) { SearchAdvancedHost() }

@PreviewNarrowScreens
@Composable
private fun ConversationRowsPreview() = PreviewSurface(UiStyle.MATERIAL) { ConversationRowsHost() }

@PreviewNarrowScreens
@Composable
private fun ConversationRowsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ConversationRowsHost() }

@PreviewNarrowScreens
@Composable
private fun ProjectRowsPreview() = PreviewSurface(UiStyle.MATERIAL) { ProjectRowsHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            ProvideDateTimeFormat(DateTimeFormatPrefs(), content)
        }
    }
}

/** The drawer on its Projects view, with the Settings row the stateful wrapper puts in its footer. */
@Composable
private fun DrawerProjectsHost() {
    DrawerContent(
        uiState = DrawerUiState(groupedConversations = drawerChats, projectsEnabled = true),
        onSearchQueryChange = {},
        onNewChat = {},
        onConversationClick = {},
        onAgentsClick = {},
        onFilesClick = {},
        onSkillsClick = {},
        onSchedulesClick = {},
        onBookmarksClick = {},
        footerContent = { dragSelection ->
            Spacer(modifier = Modifier.height(8.dp))
            DrawerFooterItem(
                icon = Icons.Default.Settings,
                label = stringResource(Res.string.settings),
                onClick = {},
                dragSelection = dragSelection,
            )
        },
        projects = listOf(
            ChatProject(id = "p1", name = "Home renovation", conversationCount = 12),
            ChatProject(id = "p2", name = "Work", conversationCount = 4),
        ),
        selectedTab = DrawerTab.Projects,
    )
}

/** The drawer's Messages section, whose rows lead with the conversation's title. */
@Composable
private fun DrawerHitsHost() {
    LazyColumn {
        messageResultsSection(
            search = DrawerMessageSearch(preview = hits, totalCount = hits.size, searched = true),
            onHitClick = {},
            onSeeAll = {},
        )
    }
}

@Composable
private fun SearchResultsHost() {
    SearchResultsList(
        state = searchState,
        twoPane = false,
        onHitClick = {},
        onOpenSelection = {},
        onMoveSelection = {},
        onConversationClick = {},
        onToggleGroup = {},
        onScanMore = {},
        listState = rememberLazyListState(),
    )
}

@Composable
private fun SearchAdvancedHost() {
    var options by remember { mutableStateOf(SearchOptions(matchCase = true, role = RoleFilter.User)) }
    AdvancedSearchRow(
        state = searchState.copy(advancedEnabled = true, options = options),
        actions = AdvancedActions(
            onOptionsChange = { change -> options = change(options) },
            onDateRangeChange = {},
            onGroupingChange = {},
            onOpenFilters = {},
            onResetFilters = {},
        ),
    )
}

@Composable
private fun ConversationRowsHost() {
    val now = Clock.System.now()
    Column {
        ConversationSearchBar(query = "", onQueryChange = {})
        ConversationSearchBar(query = "lisbon tram", onQueryChange = {})
        ConversationItem(
            data = ConversationDisplayData(
                "a",
                "Trip to Lisbon in May",
                "anthropic",
                "claude-sonnet-4-5",
                now - 2.hours,
                isFavorite = true,
            ),
            onClick = {},
            onActionsClick = {},
        )
        ConversationItem(
            data = ConversationDisplayData("b", "Refactor the sync worker", "azureOpenAI", null, now - 3.days, isFavorite = false),
            onClick = {},
            onActionsClick = {},
        )
    }
}

@Composable
private fun ProjectRowsHost() {
    Column {
        ProjectRow(label = "No project", selected = false, onClick = {})
        ProjectRow(label = "Kitchen renovation quotes", selected = true, onClick = {})
        ProjectRow(label = "Work", selected = false, onClick = {})
    }
}

private fun drawerChat(id: String, title: String) = DrawerConversationDisplayData(
    conversationId = id,
    title = title,
    model = "gpt-5",
    endpoint = "openAI",
    updatedAt = null,
    isActive = false,
    isFavorite = false,
    isPinned = false,
    tags = emptyList(),
)

private val drawerChats = listOf(
    DrawerGroupKey.Date(DateGroup.Today) to listOf(drawerChat("a", "Trip to Lisbon in May")),
)

private fun snippet(text: String, match: String) = HighlightedSnippet(
    text = text,
    highlights = text.indexOf(match).let { if (it < 0) emptyList() else listOf(it until it + match.length) },
    truncatedStart = false,
    truncatedEnd = false,
)

private val hitTime = Clock.System.now() - 3.days

private fun hit(id: String, text: String, user: Boolean, reasoning: Boolean = false, source: HitSource = HitSource.Local) =
    MessageSearchHit(
        accountId = "preview",
        messageId = id,
        conversationId = "lisbon",
        conversationTitle = "Trip to Lisbon",
        createdAt = hitTime.toEpochMilliseconds(),
        isCreatedByUser = user,
        model = "gpt-5",
        endpoint = "openAI",
        snippet = snippet(text, "tram"),
        source = source,
        inReasoning = reasoning,
    )

private val hits = listOf(
    hit("m1", "Which tram goes up to the castle, and how early should we get on it?", user = true),
    hit("m2", "Take tram 28 from Martim Moniz before 9am: it fills up fast.", user = false, reasoning = true),
    hit("m3", "Is the tram worth it with a stroller, or should we walk?", user = true, source = HitSource.Remote),
)

private val searchState = SearchUiState(
    query = "tram",
    searched = true,
    groups = listOf(
        SearchResultGroup(conversationId = "lisbon", title = "Weekend trip to Lisbon and Sintra", hits = hits, fromServer = false),
        SearchResultGroup(conversationId = "porto", title = "Porto day trip by train", hits = emptyList(), fromServer = true),
    ),
    messageCount = hits.size,
)
