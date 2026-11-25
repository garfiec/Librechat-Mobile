package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// Project cards at the widths and translations that squeeze them: a long name, a three-digit count,
// a card with no description.

@PreviewNarrowScreens
@Composable
private fun ProjectCardsPreview() = CardsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ProjectCardsGlassPreview() = CardsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun CardsHost(style: UiStyle) {
    val now = Clock.System.now()
    val projects = listOf(
        ChatProject(
            id = "p1",
            name = "Thesis research",
            description = "Literature notes and draft chapters for the groundwater recharge thesis",
            conversationCount = 24,
            lastConversationAt = (now - 2.hours).toString(),
        ),
        ChatProject(
            id = "p2",
            name = "Switchboard release notes and changelogs for every channel",
            conversationCount = 131,
            lastConversationAt = (now - 5.days).toString(),
        ),
        ChatProject(id = "p3", name = "Recipes", conversationCount = 1, createdAt = (now - 40.days).toString()),
    )
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        ProvideDateTimeFormat(DateTimeFormatPrefs()) {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                projects.forEach { ProjectCard(project = it, onClick = {}, onEdit = {}, onDelete = {}) }
            }
        }
    }
}
