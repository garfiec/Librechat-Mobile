package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.components.BookmarkPickerHost
import com.garfiec.librechat.feature.conversations.components.ConversationActionEffects
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful DrawerContent that collects its own state from the ViewModel.
 */
@Composable
fun DrawerContent(
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onWorkspaceClick: (WorkspaceDestination) -> Unit,
    accounts: List<AccountUiModel>,
    modifier: Modifier = Modifier,
    onOpenProjectsIndex: () -> Unit = {},
    onNewChatInProject: (projectId: String, projectName: String) -> Unit = { _, _ -> },
    onSwitchAccount: (String) -> Unit = {},
    onAddAccount: () -> Unit = {},
    // Round-robin swipe switch (in place, drawer stays open) + sheet remove — both are nav-shell
    // account operations, hoisted in because DrawerViewModel owns only drawer data now.
    onSwitchAccountInPlace: (String) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    // Fired when the user deletes the conversation currently open in the pane: move the pane off the
    // now-gone thread. Distinct from [onNewChat] because that also closes the phone drawer — here the
    // drawer stays open so the user can keep browsing (defaults to [onNewChat] if a host doesn't wire it).
    onActiveConversationDelete: () -> Unit = onNewChat,
    // Whether the host is actually showing this surface (phone drawer open / tablet sidebar expanded).
    // The phone drawer stays composed while closed, so the host must say so.
    isOpen: Boolean = true,
    // A message search hit was tapped. The default opens just its conversation.
    onMessageClick: (conversationId: String, messageId: String, query: String, inReasoning: Boolean) -> Unit =
        { conversationId, _, _, _ -> onConversationClick(conversationId) },
    // Opens the full Search screen with the drawer's query; null hides both ways in (the field's
    // expand button and "See all").
    onOpenSearch: ((String) -> Unit)? = null,
    // The Workspace destination open in the tablet's content pane, highlighted in the footer. Null on
    // a phone, and while a chat is open.
    currentWorkspace: WorkspaceDestination? = null,
    viewModel: DrawerViewModel = koinViewModel(),
    pinsViewModel: WorkspacePinsViewModel = koinViewModel(),
) {
    val uiState by viewModel.drawerUiState.collectAsStateWithLifecycle()
    // The field reads the query straight from its own state flow: the combined UI state carries it
    // a frame or more later, and a field showing that copy drops keystrokes typed in between.
    val searchFieldText by viewModel.searchQuery.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val inlineProjectChats by viewModel.inlineProjectChats.collectAsStateWithLifecycle()
    val projectsLoadFailed by viewModel.projectsLoadFailed.collectAsStateWithLifecycle()
    val projectLimits by viewModel.projectLimits.collectAsStateWithLifecycle()
    val libraryTab by viewModel.drawerLibraryTab.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()

    // Drives the Running group's active-jobs poll and the stale-list reload: on only while this
    // surface is open and started.
    LifecycleStartEffect(viewModel, isOpen) {
        viewModel.setDrawerVisible(isOpen)
        onStopOrDispose { viewModel.setDrawerVisible(false) }
    }

    // Account switcher: the header chip opens the roster sheet; remove asks for confirmation.
    // Switch/add callbacks come from the host (they also close the drawer); remove goes straight to
    // the ViewModel against the tapped row's id — no captured drawer state (Nav3 stale-closure rule).
    var showAccountSheet by remember { mutableStateOf(false) }
    var removeAccountTarget by remember { mutableStateOf<AccountUiModel?>(null) }
    // The sheet and its confirm are popups, so they outlive the drawer closing under them — on a
    // session ending, over the sign-in screen.
    LaunchedEffect(isOpen) {
        if (!isOpen) {
            showAccountSheet = false
            removeAccountTarget = null
            viewModel.closeShareSheet()
            viewModel.bookmarkPicker.close()
        }
    }
    LaunchedEffect(isOpen, libraryTab) {
        if (isOpen && libraryTab == DrawerTab.Projects) viewModel.reloadProjects()
    }

    // Side-effects for the long-press action menu (export file-save, navigate to a duplicated
    // conversation, error toasts). Lives in feature/conversations so it owns the toast/file-save
    // plumbing and its localized strings.
    ConversationActionEffects(
        events = viewModel.events,
        onNavigateToConversation = onConversationClick,
        onNavigateToNewChat = onActiveConversationDelete,
    )

    DrawerContent(
        uiState = uiState,
        searchFieldText = searchFieldText,
        viewMemory = viewModel.viewMemory,
        footerContent = {
            DrawerAccountFooter(
                accounts = accounts,
                onSettingsClick = onSettingsClick,
                onAccountClick = { showAccountSheet = true },
                onSwitchAccountInPlace = onSwitchAccountInPlace,
            )
        },
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onNewChat = onNewChat,
        onConversationClick = onConversationClick,
        // A hit drawn before an account switch belongs to the previous account: ignore the tap
        // rather than open a conversation id under the wrong account.
        onMessageHitClick = { hit ->
            if (viewModel.isActiveAccount(hit.accountId)) {
                onMessageClick(hit.conversationId, hit.messageId, uiState.searchQuery.trim(), hit.inReasoning)
            }
        },
        onOpenSearch = onOpenSearch,
        onWorkspaceClick = onWorkspaceClick,
        workspace = workspace,
        currentWorkspace = currentWorkspace,
        onPinWorkspace = pinsViewModel::pin,
        onUnpinWorkspace = pinsViewModel::unpin,
        onReorderWorkspacePins = pinsViewModel::reorder,
        onToggleFavorite = { data -> viewModel.setFavorite(data.conversationId, favorite = !data.isFavorite) },
        onRefresh = viewModel::refreshConversations,
        onLoadMore = viewModel::loadMoreConversations,
        onRename = { id, newTitle -> viewModel.renameConversation(id, newTitle) },
        onArchive = viewModel::archiveConversation,
        onDelete = viewModel::deleteConversation,
        onPin = viewModel::pinConversation,
        projects = projects,
        onLoadProjects = { viewModel.reloadProjects() },
        projectsLoadFailed = projectsLoadFailed,
        onRetryProjects = { viewModel.reloadProjects(force = true) },
        onMoveToProject = viewModel::moveConversationToProject,
        onCreateProjectAndAssign = viewModel::createProjectAndAssign,
        onOpenProjectsIndex = onOpenProjectsIndex,
        selectedTab = libraryTab ?: DrawerTab.Chats,
        onSelectTab = viewModel::setDrawerLibraryTab,
        inlineProjectChats = inlineProjectChats,
        onToggleProject = viewModel::toggleProjectExpanded,
        onPickUpProject = viewModel::closeExpandedProject,
        onReorderProjects = viewModel::reorderProjects,
        onCreateProject = viewModel::createProject,
        projectDescriptionLimit = projectLimits.maxDescriptionLength,
        onEditProject = viewModel::editProject,
        onNewChatInProject = onNewChatInProject,
        onDeleteProject = viewModel::deleteProject,
        onShare = viewModel::shareConversation,
        onDuplicate = { id, title -> viewModel.duplicateConversation(id, title) },
        onOpenBookmarks = { data -> viewModel.openBookmarks(data.conversationId) },
        onExportFormat = { data, format -> viewModel.exportConversation(data.conversationId, data.title, format) },
        onMarkRead = viewModel::markRead,
        onMarkUnread = viewModel::markUnread,
        onFiltersChange = viewModel::setFilters,
        modifier = modifier,
        isOpen = isOpen,
    )

    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)
    BookmarkPickerHost(controller = viewModel.bookmarkPicker)

    if (showAccountSheet) {
        AccountSwitcherSheet(
            accounts = accounts,
            onSwitchAccount = { accountId ->
                showAccountSheet = false
                onSwitchAccount(accountId)
            },
            onRemoveAccountRequest = { removeAccountTarget = it },
            onAddAccount = {
                showAccountSheet = false
                onAddAccount()
            },
            onDismiss = { showAccountSheet = false },
        )
    }

    removeAccountTarget?.let { target ->
        RemoveAccountDialog(
            account = target,
            onConfirm = {
                onRemoveAccount(target.accountId)
                removeAccountTarget = null
                // Removing the active account can end the session (the last one, or none left signed
                // in), and the sheet would otherwise stay up over the sign-in screen. Any other removal
                // keeps it open for the next.
                if (target.isActive) showAccountSheet = false
            },
            onDismiss = { removeAccountTarget = null },
        )
    }
}
