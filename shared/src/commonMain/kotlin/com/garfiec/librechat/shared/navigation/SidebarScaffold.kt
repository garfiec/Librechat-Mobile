package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.drawer.DrawerContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * The sidebar: the Conversations drawer, wired to the nav shell's account list. Projects live inline
 * in the drawer (a segmented toggle).
 */
@Composable
fun SidebarScaffold(
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSkillsClick: () -> Unit,
    onSchedulesClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProjectsIndex: () -> Unit = {},
    // Opens the Search screen with the drawer's query; see DrawerContent's param.
    onOpenSearch: ((String) -> Unit)? = null,
    // A drawer message hit was tapped; opens the chat at that message. Defaults to the conversation.
    onMessageClick: (conversationId: String, messageId: String, query: String, inReasoning: Boolean) -> Unit =
        { conversationId, _, _, _ -> onConversationClick(conversationId) },
    onSwitchAccount: (String) -> Unit = {},
    onAddAccount: () -> Unit = {},
    // Deleting the open conversation navigates the pane off it; see DrawerContent's param for the
    // onNewChat distinction. Defaults to [onNewChat].
    onActiveConversationDelete: () -> Unit = onNewChat,
    // Whether the host is showing the sidebar (phone drawer open / tablet sidebar expanded); gates
    // the drawer's active-jobs poll.
    isOpen: Boolean = true,
    viewModel: NavHostViewModel = koinViewModel(),
) {
    // Account list + switching stay in the nav shell; the drawer only renders the footer chip/sheet,
    // so the list and account callbacks are hoisted down into DrawerContent (which owns only its own
    // DrawerViewModel now).
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    DrawerContent(
        onNewChat = onNewChat,
        onActiveConversationDelete = onActiveConversationDelete,
        onConversationClick = onConversationClick,
        onSettingsClick = onSettingsClick,
        onAgentsClick = onAgentsClick,
        onFilesClick = onFilesClick,
        onSkillsClick = onSkillsClick,
        onSchedulesClick = onSchedulesClick,
        accounts = accounts,
        onOpenProjectsIndex = onOpenProjectsIndex,
        onOpenSearch = onOpenSearch,
        onMessageClick = onMessageClick,
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        // Swipe round-robins in place; the sheet's remove asks the nav shell directly.
        onSwitchAccountInPlace = viewModel::switchAccount,
        onRemoveAccount = viewModel::removeAccount,
        isOpen = isOpen,
        modifier = modifier,
    )
}
