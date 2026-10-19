package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.AgentCardDisplayData

@PreviewNarrowScreens
@Composable
private fun AgentGridPreview() = PreviewSurface(UiStyle.MATERIAL) { AgentGridHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun AgentGridHost() {
    AgentGrid(
        agents = previewAgents,
        isLoadingMore = false,
        gridState = rememberLazyGridState(),
        onAgentClick = {},
    )
}

private val previewAgents = listOf(
    AgentCardDisplayData(
        id = "travel",
        name = "Travel Planner",
        description = "Builds day-by-day itineraries with transit times, bookings and backup plans.",
        avatarUrl = null,
        authorName = "Operations team",
    ),
    AgentCardDisplayData(
        id = "review",
        name = "Code Reviewer",
        description = "Reviews pull requests for correctness and style.",
        avatarUrl = null,
        authorName = "Platform",
    ),
    AgentCardDisplayData(
        id = "support",
        name = "Customer Support Triage",
        description = "Sorts incoming tickets by urgency and drafts first replies.",
        avatarUrl = null,
        authorName = null,
    ),
    AgentCardDisplayData(id = "notes", name = "Meeting Notes", description = null, avatarUrl = null, authorName = "You"),
)
