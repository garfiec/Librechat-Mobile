package com.garfiec.librechat.feature.conversations.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.drawer.AccountSwitcherSheet
import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel

// The conversation sheets with as many rows as a busy account has: every action, a handful of
// projects, a roster of accounts. Large fonts and the keyboard push them past the screen.

@PreviewNarrowScreens
@Composable
private fun ProjectPickerGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ProjectPickerHost() }

@PreviewNarrowScreens
@Composable
private fun ConversationActionsPreview() = PreviewSurface(UiStyle.MATERIAL) { ConversationActionsHost() }

@PreviewNarrowScreens
@Composable
private fun AccountSwitcherPreview() = PreviewSurface(UiStyle.MATERIAL) { AccountSwitcherHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

@Composable
private fun ProjectPickerHost(projectCount: Int = 4) {
    ProjectPicker(
        projects = previewProjects.take(projectCount),
        currentProjectId = previewProjects.first().id,
        onSelect = {},
        onCreate = {},
        onDismiss = {},
    )
}

@Composable
private fun ConversationActionsHost() {
    ConversationActions(
        conversation = Conversation(conversationId = "lisbon", title = "Trip to Lisbon in May"),
        onDismiss = {},
        onRename = {},
        onArchive = {},
        onDelete = {},
        showShareAction = true,
    )
}

@Composable
private fun AccountSwitcherHost(accountCount: Int = 5) {
    AccountSwitcherSheet(
        accounts = previewAccounts.take(accountCount),
        onSwitchAccount = {},
        onRemoveAccountRequest = {},
        onAddAccount = {},
        onDismiss = {},
    )
}

private val previewProjects = listOf(
    ChatProject(id = "home", name = "Home renovation"),
    ChatProject(id = "trip", name = "Lisbon trip"),
    ChatProject(id = "work", name = "Quarterly planning"),
    ChatProject(id = "book", name = "Book club"),
    ChatProject(id = "garden", name = "Vegetable garden"),
    ChatProject(id = "taxes", name = "Taxes 2026"),
)

private val previewAccounts = listOf(
    AccountUiModel("a", "Garfie", "chat.example.com", avatarUrl = null, isActive = true),
    AccountUiModel("b", "Work", "librechat.acme.corp", avatarUrl = null, isActive = false),
    AccountUiModel("c", "Family", "home.example.net", avatarUrl = null, isActive = false),
    AccountUiModel("d", "Test server", "staging.example.com", avatarUrl = null, isActive = false, needsSignIn = true),
    AccountUiModel("e", "Club", "club.example.org", avatarUrl = null, isActive = false),
    AccountUiModel("f", "Lab", "lab.example.edu", avatarUrl = null, isActive = false),
    AccountUiModel("g", "Side project", "side.example.dev", avatarUrl = null, isActive = false),
    AccountUiModel("h", "Old server", "old.example.com", avatarUrl = null, isActive = false),
)
