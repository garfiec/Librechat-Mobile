package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.settings
import org.jetbrains.compose.resources.stringResource

// A phone on its side: 360dp tall less the status bar.
@Preview(name = "landscape · default", widthDp = 300, heightDp = 336, showBackground = true)
@Composable
private fun DrawerLandscapePreview() = PreviewSurface(UiStyle.MATERIAL) { DrawerHost(defaultGates) }

@Preview(name = "landscape · every gate", widthDp = 300, heightDp = 336, showBackground = true)
@Composable
private fun DrawerLandscapeAllGatesPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { DrawerHost(allGates) }

@PreviewNarrowScreens
@Composable
private fun DrawerSearchingPreview() = PreviewSurface(UiStyle.MATERIAL) { DrawerHost(allGates.copy(searchQuery = "trip")) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        // Liquid Glass leaves painting the drawer to the panel around it.
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
    }
}

/** The stateless drawer with the Settings row the stateful wrapper puts in its footer slot. */
@Composable
private fun DrawerHost(state: DrawerUiState) = ProvideDateTimeFormat(DateTimeFormatPrefs()) {
    DrawerContent(
        uiState = state,
        onSearchQueryChange = {},
        onNewChat = {},
        onConversationClick = {},
        onWorkspaceClick = {},
        footerContent = {
            Spacer(modifier = Modifier.height(8.dp))
            DrawerFooterItem(
                icon = Icons.Default.Settings,
                label = stringResource(Res.string.settings),
                onClick = {},
            )
        },
    )
}

private fun chat(id: String, title: String) = DrawerConversationDisplayData(
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

private val chats = listOf(
    DrawerGroupKey.Date(DateGroup.Today) to listOf(
        chat("a", "Trip to Lisbon in May"),
        chat("b", "Refactor the sync worker"),
        chat("c", "Birthday dinner ideas"),
    ),
    DrawerGroupKey.Date(DateGroup.Yesterday) to listOf(
        chat("d", "Quarterly report outline"),
        chat("e", "Fix the flaky upload test"),
    ),
)

/** What a fresh server shows: Agents, Skills and Files, no Schedules, no Projects. */
private val defaultGates = DrawerUiState(groupedConversations = chats, skillsEnabled = true)

private val allGates = defaultGates.copy(schedulesEnabled = true, projectsEnabled = true)
