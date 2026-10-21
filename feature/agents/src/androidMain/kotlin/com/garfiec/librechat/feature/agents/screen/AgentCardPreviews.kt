package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.AgentCardDisplayData

// Agent cards at the widths and translations that squeeze them: a Top pick with a long category
// and contact, a long name, a card with no name or description, and the first-load placeholder.

@PreviewNarrowScreens
@Composable
private fun AgentCardsPreview() = CardsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun AgentCardsGlassPreview() = CardsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun CardsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            previewAgentCards.forEach { agent ->
                AgentCard(agent = agent, categoryLabel = agent.categoryValue?.let { "Research & Development" }, onClick = {})
            }
            AgentCardPlaceholder()
        }
    }
}

internal val previewAgentCards = listOf(
    AgentCardDisplayData(
        id = "release",
        name = "Release Notes Writer",
        description = "Turns a list of merged PRs into user-facing release notes in the house voice.",
        avatarUrl = null,
        categoryValue = "rd",
        isPromoted = true,
        contactName = "Dana Okafor, Developer Experience",
    ),
    AgentCardDisplayData(
        id = "onboarding",
        name = "Onboarding Buddy for New Engineers Joining the Platform Team",
        description = "Answers questions about our repos, CI and on-call.",
        avatarUrl = null,
        contactName = "Platform",
    ),
    AgentCardDisplayData(id = "notes", name = null, description = null, avatarUrl = null),
)
