package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.TitleMatch
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.core.ui.theme.searchMatchColor
import com.garfiec.librechat.feature.conversations.components.ConversationActionDialogs
import com.garfiec.librechat.feature.conversations.components.ConversationActionEffects
import com.garfiec.librechat.feature.conversations.components.ConversationActionsMenu
import com.garfiec.librechat.feature.conversations.components.ProjectDeleteDialog
import com.garfiec.librechat.feature.conversations.components.ProjectEditorSheet
import com.garfiec.librechat.feature.conversations.components.ProjectMenuButton
import com.garfiec.librechat.feature.conversations.components.ProjectPicker
import com.garfiec.librechat.feature.conversations.components.ProjectTile
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.conversations.components.TagPicker
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.export.ExportFormatPicker
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.agents
import com.garfiec.librechat.feature.conversations.resources.bookmark
import com.garfiec.librechat.feature.conversations.resources.cd_close_search
import com.garfiec.librechat.feature.conversations.resources.cd_collapse_section
import com.garfiec.librechat.feature.conversations.resources.cd_conversation_actions
import com.garfiec.librechat.feature.conversations.resources.cd_expand_section
import com.garfiec.librechat.feature.conversations.resources.cd_open_search
import com.garfiec.librechat.feature.conversations.resources.cd_search
import com.garfiec.librechat.feature.conversations.resources.drawer_running_chats
import com.garfiec.librechat.feature.conversations.resources.favorites
import com.garfiec.librechat.feature.conversations.resources.files
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.resources.no_conversations_found
import com.garfiec.librechat.feature.conversations.resources.pinned
import com.garfiec.librechat.feature.conversations.resources.project_create_confirm
import com.garfiec.librechat.feature.conversations.resources.project_edit
import com.garfiec.librechat.feature.conversations.resources.project_new
import com.garfiec.librechat.feature.conversations.resources.project_new_chat_in
import com.garfiec.librechat.feature.conversations.resources.project_unassigned
import com.garfiec.librechat.feature.conversations.resources.projects
import com.garfiec.librechat.feature.conversations.resources.projects_all
import com.garfiec.librechat.feature.conversations.resources.projects_see_all
import com.garfiec.librechat.feature.conversations.resources.remove_bookmark
import com.garfiec.librechat.feature.conversations.resources.save
import com.garfiec.librechat.feature.conversations.resources.schedules
import com.garfiec.librechat.feature.conversations.resources.search_open_all
import com.garfiec.librechat.feature.conversations.resources.search_title
import com.garfiec.librechat.feature.conversations.resources.settings
import com.garfiec.librechat.feature.conversations.resources.show_less
import com.garfiec.librechat.feature.conversations.resources.show_more
import com.garfiec.librechat.feature.conversations.resources.skills
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Pre-computed shapes to avoid creating new ones per item per frame
private val ItemShape = RoundedCornerShape(8.dp)

/**
 * A sticky header in Material; a plain item in Liquid Glass, where the opaque band a sticky header
 * needs (so rows don't show through it) would cut across the glass panel.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.drawerHeader(glass: Boolean, key: Any, content: @Composable LazyItemScope.() -> Unit) {
    if (glass) item(key = key, content = content) else stickyHeader(key = key) { content() }
}
private val ActiveIndicatorShape = RoundedCornerShape(2.dp)

// Pulls a tappable drawer row in from the edges and clips its ripple to [ItemShape], so every row
// reads as the same inset, rounded button instead of a full-bleed rectangular highlight. Apply
// before .clickable so the indication is bounded by the rounded shape.
private fun Modifier.drawerRowShape(): Modifier =
    padding(horizontal = 4.dp, vertical = 1.dp).clip(ItemShape)

private const val STAR_SWELL = 0.35f

// Gap between the conversation row's bottom edge and the long-press menu's top edge.
private val MenuVerticalGap = 4.dp

// Favorites shown before the "Show more" toggle reveals the rest.
private const val FavoritesPreviewCount = 5

/** Title hits the drawer lists while searching; the rest are in Search. */
private const val TITLE_HIT_PREVIEW = 5

/**
 * Stateful DrawerContent that collects its own state from the ViewModel.
 */
@Composable
fun DrawerContent(
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSkillsClick: () -> Unit,
    onSchedulesClick: () -> Unit,
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
    viewModel: DrawerViewModel = koinViewModel(),
) {
    val uiState by viewModel.drawerUiState.collectAsStateWithLifecycle()
    // The field reads the query straight from its own state flow: the combined UI state carries it
    // a frame or more later, and a field showing that copy drops keystrokes typed in between.
    val searchFieldText by viewModel.searchQuery.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val hasMoreProjects by viewModel.hasMoreProjects.collectAsStateWithLifecycle()
    val inlineProjectChats by viewModel.inlineProjectChats.collectAsStateWithLifecycle()
    val libraryTab by viewModel.drawerLibraryTab.collectAsStateWithLifecycle()

    // Drives the Running group's active-jobs poll: on only while this surface is open and started.
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
        footerContent = { dragSelection ->
            val active = accounts.firstOrNull { it.isActive }
            Spacer(modifier = Modifier.height(8.dp))
            // Footer row: Settings (icon + label) on the left takes the width; the account avatar
            // (icon only, tap to switch) sits on the right.
            //
            // Settings must render even with an empty roster: it is the only route to the
            // server/account screens, sign-out and the log export, and an empty roster is exactly
            // the state a user needs them in. Only AccountChip needs a resolved account — do not
            // fold Settings back under it. See issue #360.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Inset for the chip only, so Settings still aligns with the rows above it
                    // when there is no chip.
                    .padding(end = if (active != null) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerFooterItem(
                    icon = Icons.Default.Settings,
                    label = stringResource(Res.string.settings),
                    onClick = onSettingsClick,
                    dragSelection = dragSelection,
                    modifier = Modifier.weight(1f),
                )
                if (active != null) {
                    AccountChip(
                        account = active,
                        onClick = { showAccountSheet = true },
                        // Gmail/YouTube-style: swipe the avatar up/down to round-robin accounts
                        // without opening the sheet. Switches in place via the ViewModel so the user
                        // can swipe through several accounts against the same avatar. The sheet path
                        // keeps the drawer open too (the host no longer closes it on switch).
                        // Disabled (null) with nothing to switch to: a single account, or every
                        // other one signed out.
                        onSwitchAdjacent = if (accounts.any { !it.isActive && !it.needsSignIn }) {
                            { delta -> adjacentAccountId(accounts, delta)?.let(onSwitchAccountInPlace) }
                        } else {
                            null
                        },
                    )
                }
            }
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
        onAgentsClick = onAgentsClick,
        onFilesClick = onFilesClick,
        onSkillsClick = onSkillsClick,
        onSchedulesClick = onSchedulesClick,
        onToggleFavorite = { data -> viewModel.toggleFavorite(data.conversationId, data.tags) },
        onRefresh = viewModel::refreshConversations,
        onLoadMore = viewModel::loadMoreConversations,
        onRename = { id, newTitle -> viewModel.renameConversation(id, newTitle) },
        onArchive = viewModel::archiveConversation,
        onDelete = viewModel::deleteConversation,
        onPin = viewModel::pinConversation,
        projects = projects,
        hasMoreProjects = hasMoreProjects,
        onLoadProjects = { viewModel.reloadProjects() },
        onMoveToProject = viewModel::moveConversationToProject,
        onCreateProjectAndAssign = viewModel::createProjectAndAssign,
        onOpenProjectsIndex = onOpenProjectsIndex,
        selectedTab = libraryTab ?: DrawerTab.Chats,
        onSelectTab = viewModel::setDrawerLibraryTab,
        inlineProjectChats = inlineProjectChats,
        onToggleProject = viewModel::toggleProjectExpanded,
        onCreateProject = viewModel::createProject,
        onEditProject = viewModel::editProject,
        onNewChatInProject = onNewChatInProject,
        onDeleteProject = viewModel::deleteProject,
        onShare = viewModel::shareConversation,
        onDuplicate = { id, title -> viewModel.duplicateConversation(id, title) },
        onUpdateTags = { data, tags -> viewModel.updateConversationTags(data.conversationId, data.tags, tags) },
        onExportFormat = { data, format -> viewModel.exportConversation(data.conversationId, data.title, format) },
        modifier = modifier,
        isOpen = isOpen,
    )

    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    uiState: DrawerUiState,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSkillsClick: () -> Unit,
    onSchedulesClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Slot below the footer links (Files, Agents, …) — the stateful wrapper puts the Settings row
    // and the account avatar here, at the bottom of the drawer. Its rows join the footer links'
    // drag-to-select through the [ListDragSelection] it's given.
    footerContent: (@Composable (ListDragSelection?) -> Unit)? = null,
    onMessageHitClick: (MessageSearchHit) -> Unit = {},
    onOpenSearch: ((String) -> Unit)? = null,
    onToggleFavorite: (DrawerConversationDisplayData) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onArchive: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onPin: (String, Boolean) -> Unit = { _, _ -> },
    projects: List<ChatProject> = emptyList(),
    hasMoreProjects: Boolean = false,
    onLoadProjects: () -> Unit = {},
    onMoveToProject: (String, String?) -> Unit = { _, _ -> },
    onCreateProjectAndAssign: (String, String) -> Unit = { _, _ -> },
    // Projects tab (segmented toggle above the list): the folder list + inline chat accordion, plus
    // an escape hatch to the full-page projects index for advanced controls.
    onOpenProjectsIndex: () -> Unit = {},
    // Persisted Chats/Projects toggle selection (controlled by the caller).
    selectedTab: DrawerTab = DrawerTab.Chats,
    onSelectTab: (DrawerTab) -> Unit = {},
    inlineProjectChats: InlineProjectChatsState = InlineProjectChatsState(),
    onToggleProject: (String) -> Unit = {},
    onCreateProject: (String, String) -> Unit = { _, _ -> },
    onEditProject: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteProject: (String) -> Unit = {},
    onNewChatInProject: (projectId: String, projectName: String) -> Unit = { _, _ -> },
    onShare: (String) -> Unit = {},
    onDuplicate: (String, String) -> Unit = { _, _ -> },
    onUpdateTags: (DrawerConversationDisplayData, List<String>) -> Unit = { _, _ -> },
    onExportFormat: (DrawerConversationDisplayData, ExportFormat) -> Unit = { _, _ -> },
    /** What the search field shows: the query as typed, ahead of [uiState]'s copy of it. */
    searchFieldText: String = uiState.searchQuery,
    /** Cursor and scroll position, kept where the next drawer composed (fold ↔ unfold) finds them. */
    viewMemory: DrawerViewMemory = remember { DrawerViewMemory() },
    /** Whether the drawer is showing; closing it drops the search field's focus and keyboard. */
    isOpen: Boolean = true,
) {
    // Which row's long-press action menu is currently open (null = none). Keyed by the row's
    // LazyColumn key, not the conversation id: a favorited conversation appears in BOTH the
    // favorites section and its date group, so an id-keyed menu would open in both rows at once.
    var menuRowKey by remember { mutableStateOf<String?>(null) }
    // Targets for the dialogs/pickers opened from the action menu. Hoisted here (single instance)
    // so they survive the per-row menu — which is composed only for the open row — leaving the tree.
    var renameTarget by remember { mutableStateOf<DrawerConversationDisplayData?>(null) }
    var deleteTarget by remember { mutableStateOf<DrawerConversationDisplayData?>(null) }
    var tagPickerTarget by remember { mutableStateOf<DrawerConversationDisplayData?>(null) }
    var exportPickerTarget by remember { mutableStateOf<DrawerConversationDisplayData?>(null) }
    var projectPickerTarget by remember { mutableStateOf<DrawerConversationDisplayData?>(null) }

    // Favorites section view state: whether the whole section is collapsed, and (when expanded)
    // whether it shows all favorites or just the top [FavoritesPreviewCount]. Saveable so the
    // choice survives config changes and the drawer's carousel mode switches.
    var favoritesCollapsed by rememberSaveable { mutableStateOf(false) }
    var showAllFavorites by rememberSaveable { mutableStateOf(false) }

    // Invisible anchor that claims initial focus so the search field below
    // doesn't auto-focus and pop the keyboard when the drawer opens. Tapping
    // the search field still focuses it normally. See Android focus docs
    // ("Change focus behavior"): redirect initial focus to a non-input element.
    val focusAnchor = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusAnchor.requestFocus() }
    }

    val glass = isLiquidGlass
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            // In Liquid Glass the floating panel around the drawer paints it and keeps it inside the
            // safe area (the keyboard included), so the drawer itself is transparent and unpadded.
            // Material pads itself, the keyboard too, so its search results end above it.
            .then(
                if (glass) {
                    Modifier
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                },
            )
            .padding(top = 16.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusAnchor)
                .focusable(),
        )

        // Search field is hidden by default and revealed by the toggle beside "New Chat". Seed the
        // toggle from the current query so a restored search stays visible across recompositions.
        var searchExpanded by remember { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
        val searchFocusRequester = remember { FocusRequester() }
        var searchFocused by remember { mutableStateOf(false) }
        // Derived, so the drawer recomposes when the keyboard comes or goes, not on every frame of its
        // slide.
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val keyboardUp by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        // Search takes the query over: the drawer's copy is cleared and the box collapsed, so the
        // drawer is back to its normal list when the user returns to it.
        val openSearchAndReset: ((String) -> Unit)? = onOpenSearch?.let { open ->
            { query ->
                searchExpanded = false
                onSearchQueryChange("")
                open(query)
            }
        }

        // Focus the field (and pop the keyboard) only when the user opens search explicitly — the
        // focusAnchor above still steals initial focus so opening the drawer doesn't do this.
        LaunchedEffect(searchExpanded) {
            if (searchExpanded) {
                runCatching { searchFocusRequester.requestFocus() }
            }
        }

        // New Chat and the search box share one slot: the search toggle turns the button into the
        // field, and the toggle becomes close, which turns the slot back into New Chat. The field
        // carries "open in Search". Material moves: the glyph flies from the toggle into the field,
        // the label slides out to the left as the field slides in behind it. Liquid Glass morphs in
        // place, as iOS controls do: the + fades into the search glyph where it sits, the label
        // into the field, the tint into the grey fill.
        // The open folder is the selected project: on the Projects tab, New chat files the chat there.
        val selectedProject = projects.firstOrNull { it.id == inlineProjectChats.expandedProjectId }
            ?.takeIf { uiState.projectsEnabled && selectedTab == DrawerTab.Projects }
        val newChatBounce = rememberPressBounce()
        val searchBounce = rememberPressBounce()
        val focusManager = LocalFocusManager.current
        val collapseSearch = {
            focusManager.clearFocus()
            searchExpanded = false
            onSearchQueryChange("")
        }
        // The closed drawer stays composed, so a search field that still holds focus keeps the
        // keyboard up over the screen behind it (closing by scrim tap, swipe or back). The query is
        // kept: a restored search stays visible.
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(isOpen) {
            if (!isOpen && searchFocused) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
        val slotColor by animateColorAsState(
            targetValue = when {
                !searchExpanded && glass -> GlassControlColors.tint
                !searchExpanded -> MaterialTheme.colorScheme.primary
                glass -> GlassControlColors.fill
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = MorphColorSpec,
        )
        SharedTransitionLayout {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(SlotHeight)
                        .pressBounce(newChatBounce, enabled = !glass && !searchExpanded),
                    // Round in both styles: the header's buttons are pills, as in glass.
                    shape = CircleShape,
                    color = slotColor,
                    contentColor = if (glass) Color.White else MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        // Qualified: inside the Row, the RowScope overload would win and can't be called here.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !searchExpanded,
                            enter = morphIn(glass, fromStart = true),
                            exit = morphOut(glass, toStart = true),
                        ) {
                            NewChatLabel(
                                project = selectedProject,
                                onClick = {
                                    if (selectedProject != null) {
                                        onNewChatInProject(selectedProject.id, selectedProject.name)
                                    } else {
                                        onNewChat()
                                    }
                                },
                            )
                        }
                        // Qualified: inside the Row, the RowScope overload would win and can't be called here.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchExpanded,
                            enter = morphIn(glass, fromStart = false),
                            exit = morphOut(glass, toStart = false),
                        ) {
                            DrawerSearchField(
                                query = searchFieldText,
                                onQueryChange = onSearchQueryChange,
                                initialSelection = viewMemory.searchSelection,
                                onSelectionChange = { viewMemory.searchSelection = it },
                                // Enter opens the full Search screen with the query: the keyboard's way
                                // to "see everything", and what a search box's Enter is expected to do.
                                onSubmit = {
                                    val query = searchFieldText.trim()
                                    if (openSearchAndReset != null && query.isNotEmpty()) openSearchAndReset(query)
                                },
                                onOpenInSearch = openSearchAndReset?.let { open -> { open(searchFieldText.trim()) } },
                                focusRequester = searchFocusRequester,
                                onFocusChange = { searchFocused = it },
                                // In glass the glyph takes the +'s place, so it starts where the + does.
                                startPadding = if (glass) NewChatStartPadding else 12.dp,
                                glyph = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .then(
                                                if (glass) {
                                                    Modifier
                                                } else {
                                                    Modifier.sharedElement(
                                                        rememberSharedContentState(SearchGlyphKey),
                                                        animatedVisibilityScope = this@AnimatedVisibility,
                                                    )
                                                },
                                            )
                                            .size(20.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                // Search, then close once the field is out: closing turns the slot back into New Chat.
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = { if (searchExpanded) collapseSearch() else searchExpanded = true },
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .pressBounce(searchBounce, enabled = !glass),
                    shape = CircleShape,
                    color = if (glass) GlassControlColors.fill else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    AnimatedContent(
                        targetState = searchExpanded,
                        transitionSpec = {
                            (scaleIn(MorphScaleSpec, initialScale = 0.6f) + fadeIn(MorphFadeInSpec))
                                .togetherWith(scaleOut(MorphScaleSpec, targetScale = 0.6f) + fadeOut(MorphFadeOutSpec))
                        },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                    ) { expanded ->
                        val tint = if (glass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (expanded) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.cd_close_search),
                                    tint = tint,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(Res.string.cd_search),
                                    tint = tint,
                                    modifier = Modifier
                                        .then(
                                            if (glass) {
                                                Modifier
                                            } else {
                                                Modifier.sharedElement(
                                                    rememberSharedContentState(SearchGlyphKey),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                )
                                            },
                                        )
                                        .size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pinned under the slot, outside the list: the bottom "See all" link sits past every title and
        // message hit, where a long result list hides it.
        AnimatedVisibility(
            visible = searchExpanded && openSearchAndReset != null && FtsQuery.plan(searchFieldText) != FtsQueryPlan.Empty,
        ) {
            SearchLinkRow(
                text = stringResource(Res.string.search_open_all),
                onClick = { openSearchAndReset?.invoke(searchFieldText.trim()) },
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
        }

        // Chats / Projects toggle above the list — shown only where projects are supported. When
        // hidden (older server / no permission) the drawer is always the recents list.
        val projectsTabAvailable = uiState.projectsEnabled
        val librarySwitch = rememberLibrarySwitch(selectedTab)
        // Hidden while the drawer's search has the keyboard, as the footer links below are: the results
        // need what room the keyboard leaves.
        val searchHasKeyboard = searchFocused && keyboardUp
        if (projectsTabAvailable && !searchHasKeyboard) {
            Spacer(modifier = Modifier.height(8.dp))
            LibraryHeader(switch = librarySwitch, selectedTab = selectedTab, onSelect = onSelectTab)
        }
        if (projectsTabAvailable) {
            // Keep the folder counts fresh whenever the Projects panel comes into view, a drag included.
            val currentOnLoadProjects by rememberUpdatedState(onLoadProjects)
            val projectsInView = librarySwitch.projectsShown
            LaunchedEffect(projectsInView) {
                if (projectsInView) currentOnLoadProjects()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Both panels are composed only mid-swap; at rest it's the selected one.
        val showChats = !projectsTabAvailable || librarySwitch.chatsShown
        val showProjects = projectsTabAvailable && librarySwitch.projectsShown
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val panel = { tab: DrawerTab ->
            if (projectsTabAvailable) Modifier.libraryPanel(librarySwitch, tab, rtl) else Modifier
        }

        // Conversation list with favorites section and date groups
        // Not rememberLazyListState: that is saveable per place in the tree, so after the activity is
        // recreated (fold ↔ unfold) each layout's drawer would restore its own old position over this one.
        val listState = remember(viewMemory) { LazyListState(viewMemory.listIndex, viewMemory.listOffset) }
        LaunchedEffect(listState, viewMemory) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewMemory.listIndex = index
                    viewMemory.listOffset = offset
                }
        }
        val currentOnLoadMore by rememberUpdatedState(onLoadMore)

        // Single item renderer shared by the favorites section and the date groups so the
        // long-press action menu wiring isn't duplicated across both call sites. rowKey is the
        // row's LazyColumn key (unique per rendered row, unlike the conversation id).
        val renderConversationItem: @Composable (String, DrawerConversationDisplayData) -> Unit = { rowKey, data ->
            DrawerConversationItem(
                data = data,
                searchQuery = uiState.searchQuery.takeIf { it.isNotBlank() },
                onClick = { onConversationClick(data.conversationId) },
                onToggleFavorite = { onToggleFavorite(data) },
                showBookmarkToggle = uiState.bookmarksEnabled,
                onLongPress = { menuRowKey = rowKey },
                onMenuCancel = { menuRowKey = null },
                menuContent = { menuOffset, menuDrag ->
                    // Only the open row materializes the menu, so there's one menu in the tree at
                    // a time (and the dialogs it triggers are hoisted below, outside this row).
                    if (menuRowKey == rowKey) {
                        ConversationActionsMenu(
                            expanded = true,
                            onDismiss = { menuRowKey = null },
                            title = data.title,
                            offset = menuOffset,
                            isBookmarked = data.isFavorite,
                            bookmarksEnabled = uiState.bookmarksEnabled,
                            isPinned = data.isPinned,
                            showPinAction = uiState.pinEnabled,
                            onPinToggle = { onPin(data.conversationId, !data.isPinned) },
                            showMoveToProject = uiState.projectsEnabled,
                            onMoveToProject = {
                                onLoadProjects()
                                projectPickerTarget = data
                            },
                            showShareAction = uiState.sharedLinksEnabled,
                            onBookmarkToggle = { onToggleFavorite(data) },
                            onRenameRequest = { renameTarget = data },
                            onArchive = { onArchive(data.conversationId) },
                            onDeleteRequest = { deleteTarget = data },
                            onShare = { onShare(data.conversationId) },
                            onDuplicate = { newTitle -> onDuplicate(data.conversationId, newTitle) },
                            onTags = { tagPickerTarget = data },
                            onExport = { exportPickerTarget = data },
                            dragSelection = menuDrag,
                        )
                    }
                },
            )
        }

        val shouldLoadMore = remember {
            derivedStateOf {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisibleItem >= totalItems - 8 && totalItems > 0
            }
        }

        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && uiState.hasMore && !uiState.isLoadingMore) {
                currentOnLoadMore()
            }
        }

        Box(modifier = Modifier.weight(1f)) {
        if (showChats) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().then(panel(DrawerTab.Chats)),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                // Pinned section (v0.8.7) — pinned conversations surfaced above favorites. This is
                // their canonical home: when shown, they're filtered out of the date-grouped buckets
                // (see ConversationListStateHolder.withoutPinned) so they don't appear twice. The
                // section is hidden during search, where pinned rows instead surface in the results.
                if (uiState.pinnedConversations.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                    item(key = "pinned_header") {
                        SectionHeader(
                            icon = Icons.Default.PushPin,
                            title = stringResource(Res.string.pinned),
                        )
                    }

                    items(
                        items = uiState.pinnedConversations,
                        key = { "pin_${it.conversationId}" },
                        contentType = { "conversation" },
                    ) { data ->
                        renderConversationItem("pin_${data.conversationId}", data)
                    }

                    item(key = "pinned_divider") {
                        AdaptiveDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                // Favorites section — hidden entirely when BOOKMARKS.USE is denied so
                // any locally-cached favorites from a prior permissive session don't leak.
                // The header collapses the whole section; when expanded, only the top
                // [FavoritesPreviewCount] show until "Show more" reveals the rest.
                if (uiState.bookmarksEnabled && uiState.favoriteConversations.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                    val favorites = uiState.favoriteConversations
                    drawerHeader(glass, key = "favorites_header") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            SectionHeader(
                                icon = Icons.Default.Star,
                                title = stringResource(Res.string.favorites),
                                collapsed = favoritesCollapsed,
                                onToggle = { favoritesCollapsed = !favoritesCollapsed },
                            )
                        }
                    }

                    // The whole body (preview rows + show-more + divider) lives in one item so it can
                    // expand/collapse as a unit; the extra rows past the preview get their own nested
                    // reveal. Favorites are a small curated set, so composing them eagerly is cheap.
                    item(key = "favorites_body") {
                        Column {
                            AnimatedVisibility(
                                visible = !favoritesCollapsed,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    favorites.take(FavoritesPreviewCount).forEach { data ->
                                        renderConversationItem("fav_${data.conversationId}", data)
                                    }

                                    if (favorites.size > FavoritesPreviewCount) {
                                        AnimatedVisibility(
                                            visible = showAllFavorites,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            Column {
                                                favorites.drop(FavoritesPreviewCount).forEach { data ->
                                                    renderConversationItem("fav_${data.conversationId}", data)
                                                }
                                            }
                                        }
                                        ShowMoreLessRow(
                                            expanded = showAllFavorites,
                                            onClick = { showAllFavorites = !showAllFavorites },
                                        )
                                    }

                                    AdaptiveDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                val searching = uiState.searchQuery.isNotBlank()
                val openSearch = openSearchAndReset?.let { open -> { open(uiState.searchQuery.trim()) } }
                if (searching) {
                    // Title hits only, flat and capped: the full list would push the message hits
                    // (and the way into Search) off the bottom of a long drawer.
                    val titleHits = uiState.groupedConversations.flatMap { it.second }
                    conversationResultsHeader(count = titleHits.size)
                    if (titleHits.isEmpty()) {
                        item(key = "empty_search") {
                            Text(
                                text = stringResource(Res.string.no_conversations_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(
                        items = titleHits.take(TITLE_HIT_PREVIEW),
                        key = { it.conversationId },
                        contentType = { "conversation" },
                    ) { data ->
                        renderConversationItem(data.conversationId, data)
                    }
                    if (titleHits.size > TITLE_HIT_PREVIEW && openSearch != null) {
                        moreChatsLink(titleHits.size - TITLE_HIT_PREVIEW, openSearch)
                    }
                } else {
                    uiState.groupedConversations.forEach { (group, displayItems) ->
                        drawerHeader(glass, key = "header_${group.key}") {
                            Text(
                                text = when (group) {
                                    DrawerGroupKey.Running -> stringResource(Res.string.drawer_running_chats)
                                    is DrawerGroupKey.Date -> group.group.resolve()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 12.dp,
                                        bottom = 4.dp,
                                    ),
                            )
                        }

                        items(
                            items = displayItems,
                            key = { it.conversationId },
                            contentType = { "conversation" },
                        ) { data ->
                            renderConversationItem(data.conversationId, data)
                        }
                    }
                }

                if (searching) {
                    messageResultsSection(
                        search = uiState.messageSearch,
                        onHitClick = onMessageHitClick,
                        onSeeAll = openSearch,
                    )
                }

                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveCircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                }
            }
        }
        if (showProjects) {
            DrawerProjectsList(
                projects = projects,
                hasMoreProjects = hasMoreProjects,
                inlineProjectChats = inlineProjectChats,
                onToggleProject = onToggleProject,
                onOpenProjectsIndex = onOpenProjectsIndex,
                onCreateProject = onCreateProject,
                onEditProject = onEditProject,
                onNewChatInProject = onNewChatInProject,
                onDeleteProject = onDeleteProject,
                renderChat = renderConversationItem,
                modifier = Modifier.fillMaxSize().then(panel(DrawerTab.Projects)),
            )
        }
        }

        // Bottom section: divider + footer links. Hidden while the keyboard is up for the drawer's search:
        // the drawer shrinks above it, and the links would take what room is left from the results. Only
        // for this field: a tablet's sidebar stays open while the chat composer has the keyboard.
        if (!searchHasKeyboard) {
            AdaptiveDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            val dragSelection = rememberListDragSelection()
            val footerScroll = rememberScrollState()
            // Only once the links overflow do they scroll; until then a vertical drag is drag-select.
            val footerScrolls = footerScroll.maxValue > 0
            Column(
                modifier = Modifier
                    .atMostHalfTheRoomLeft()
                    .verticalScroll(footerScroll, enabled = footerScrolls)
                    .listDragSelection(dragSelection, holdOnly = footerScrolls),
            ) {
                if (uiState.agentsEntryVisible) {
                    DrawerFooterItem(
                        icon = Icons.Default.SmartToy,
                        label = stringResource(Res.string.agents),
                        onClick = onAgentsClick,
                        dragSelection = dragSelection,
                    )
                }
                if (uiState.skillsEnabled) {
                    DrawerFooterItem(
                        icon = Icons.Default.Extension,
                        label = stringResource(Res.string.skills),
                        onClick = onSkillsClick,
                        dragSelection = dragSelection,
                    )
                }
                if (uiState.schedulesEnabled) {
                    DrawerFooterItem(
                        icon = Icons.Default.Schedule,
                        label = stringResource(Res.string.schedules),
                        onClick = onSchedulesClick,
                        dragSelection = dragSelection,
                    )
                }
                DrawerFooterItem(
                    icon = Icons.Default.Folder,
                    label = stringResource(Res.string.files),
                    onClick = onFilesClick,
                    dragSelection = dragSelection,
                )

                footerContent?.invoke(dragSelection)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Rename/Delete confirmation dialogs for the long-press action menu. Single hoisted instance
    // (a non-null target shows the dialog) so it outlives the per-row menu that requested it.
    ConversationActionDialogs(
        renameTitle = renameTarget?.title,
        deleteTitle = deleteTarget?.title,
        onDismissRename = { renameTarget = null },
        onConfirmRename = { newTitle ->
            renameTarget?.let { onRename(it.conversationId, newTitle) }
            renameTarget = null
        },
        onDismissDelete = { deleteTarget = null },
        onConfirmDelete = {
            deleteTarget?.let { onDelete(it.conversationId) }
            deleteTarget = null
        },
    )

    // Secondary pickers opened from the long-press action menu. Rendered as overlay bottom
    // sheets, so their position in the tree doesn't matter.
    tagPickerTarget?.let { target ->
        TagPicker(
            availableTags = uiState.availableTags,
            currentTags = target.tags.filterNot { it == SAVED_TAG },
            onTagsChange = { newTags -> onUpdateTags(target, newTags) },
            onDismiss = { tagPickerTarget = null },
        )
    }

    exportPickerTarget?.let { target ->
        ExportFormatPicker(
            onFormatSelect = { format ->
                onExportFormat(target, format)
                exportPickerTarget = null
            },
            onDismiss = { exportPickerTarget = null },
        )
    }

    projectPickerTarget?.let { target ->
        ProjectPicker(
            projects = projects,
            currentProjectId = target.chatProjectId,
            onSelect = { projectId -> onMoveToProject(target.conversationId, projectId) },
            onCreate = { name -> onCreateProjectAndAssign(target.conversationId, name) },
            onDismiss = { projectPickerTarget = null },
        )
    }
}

/**
 * New Chat's label, filling the shared slot so the whole slot is the button. With a [project]
 * selected it says where the chat goes, the project's tile standing in for the +.
 */
@Composable
private fun NewChatLabel(onClick: () -> Unit, project: ChatProject? = null) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NewChatStartPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (project != null) {
            ProjectTile(projectId = project.id, name = project.name, size = 20.dp)
        } else {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (project != null) {
                stringResource(Res.string.project_new_chat_in, project.name)
            } else {
                stringResource(Res.string.new_chat)
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The drawer's search box, drawn inside New Chat's slot. A bare text field, because the slot is
 * New Chat's height and an outlined field is taller; the slot supplies the fill. [glyph] is the
 * leading search icon: in Material, the shared element the toggle's glyph flies into. [onOpenInSearch] opens
 * the full Search screen with the query; null hides it.
 *
 * The field keeps its own [TextFieldValue], so the cursor is its own: [initialSelection] places it
 * when the field is created (end of the text when null), and [onSelectionChange] reports it.
 */
@Composable
private fun DrawerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    initialSelection: TextRange?,
    onSelectionChange: (TextRange) -> Unit,
    onSubmit: () -> Unit,
    onOpenInSearch: (() -> Unit)?,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    startPadding: Dp,
    glyph: @Composable () -> Unit,
) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = MaterialTheme.typography.bodyMedium
    // The String overload would start a new field's cursor at 0, which is where it landed on unfold.
    var value by remember {
        val selection = initialSelection?.takeIf { it.max <= query.length } ?: TextRange(query.length)
        mutableStateOf(TextFieldValue(query, selection))
    }
    // Only a clear comes from outside (close, open in Search, account switch). Syncing every change
    // would race the query's state flow, a frame behind the keys, and drop what was typed meanwhile.
    LaunchedEffect(query) {
        if (query.isEmpty() && value.text.isNotEmpty()) value = TextFieldValue()
    }
    Row(
        modifier = Modifier.fillMaxSize().padding(start = startPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph()
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val changed = next.text != value.text
                value = next
                onSelectionChange(next.selection)
                if (changed) onQueryChange(next.text)
            },
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(text = stringResource(Res.string.search_title), style = textStyle, color = hint, maxLines = 1)
                    }
                    field()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
        if (onOpenInSearch != null) {
            IconButton(onClick = onOpenInSearch, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = stringResource(Res.string.cd_open_search),
                    tint = hint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** New Chat's height; the search box takes the same slot, so nothing below moves when it opens. */
private val SlotHeight = 44.dp
private const val SearchGlyphKey = "drawer_search_glyph"
private val MorphColorSpec = tween<Color>(durationMillis = 250)
private val MorphSlideSpec =
    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val MorphScaleSpec = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
private val MorphFadeInSpec = tween<Float>(durationMillis = 180, delayMillis = 70)
private val MorphFadeOutSpec = tween<Float>(durationMillis = 90)

/** Liquid Glass's morph: both sides fade at once, so the slot crossfades where it is. */
private val InPlaceFadeSpec = tween<Float>(durationMillis = 220)

/** How one side of the slot arrives: sliding a quarter in from its side (Material), or fading in place (glass). */
private fun morphIn(glass: Boolean, fromStart: Boolean): EnterTransition =
    if (glass) {
        fadeIn(InPlaceFadeSpec)
    } else {
        slideInHorizontally(MorphSlideSpec) { if (fromStart) -it / 4 else it / 4 } + fadeIn(MorphFadeInSpec)
    }

/** How one side of the slot leaves; see [morphIn]. */
private fun morphOut(glass: Boolean, toStart: Boolean): ExitTransition =
    if (glass) {
        fadeOut(InPlaceFadeSpec)
    } else {
        slideOutHorizontally(MorphSlideSpec) { if (toStart) -it / 4 else it / 4 } + fadeOut(MorphFadeOutSpec)
    }

/** Where New Chat's + sits; the glass field's glyph starts there too, to take its place. */
private val NewChatStartPadding = 16.dp

/**
 * The Projects tab of the drawer — an inline folder browser reached via the pill toggle. Lists the
 * Unassigned bucket + project folders; tapping a folder
 * expands its chats inline (single-expand accordion via [onToggleProject]) rendered through
 * [renderChat] so the rows and their long-press actions match the recents list. Owns its own
 * create/rename/delete dialog state; [onOpenProjectsIndex] is the escape hatch to the full-page index.
 */
@Composable
private fun DrawerProjectsList(
    projects: List<ChatProject>,
    hasMoreProjects: Boolean,
    inlineProjectChats: InlineProjectChatsState,
    onToggleProject: (String) -> Unit,
    onOpenProjectsIndex: () -> Unit,
    onCreateProject: (name: String, description: String) -> Unit,
    onEditProject: (projectId: String, name: String, description: String) -> Unit,
    onNewChatInProject: (projectId: String, projectName: String) -> Unit,
    onDeleteProject: (String) -> Unit,
    renderChat: @Composable (String, DrawerConversationDisplayData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ChatProject?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatProject?>(null) }

    // Expanded body for the open folder: a spinner while its page loads, an empty note, or the chat
    // rows. Single-expand, so inlineProjectChats always holds the currently open folder's chats.
    val expandedChats: @Composable (String) -> Unit = { projectId ->
        when {
            inlineProjectChats.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            inlineProjectChats.conversations.isEmpty() -> {
                val project = projects.firstOrNull { it.id == projectId }
                if (project != null) {
                    EmptyFolderNewChatRow(
                        projectName = project.name,
                        onClick = { onNewChatInProject(project.id, project.name) },
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.no_conversations_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            else -> {
                inlineProjectChats.conversations.forEach { data ->
                    renderChat("projchat_${projectId}_${data.conversationId}", data)
                }
            }
        }
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        // Action row: link to the full-page index (advanced controls) + create a new folder.
        item(key = "projects_actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Weighted, so a long translation is cut short rather than squeezing out the +.
                TextButton(onClick = onOpenProjectsIndex, modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(Res.string.projects_all),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.project_new),
                    )
                }
            }
        }

        item(key = "proj_unassigned") {
            val unassignedLabel = stringResource(Res.string.project_unassigned)
            ProjectFolderAccordion(
                name = unassignedLabel,
                conversationCount = null,
                leading = {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                expanded = inlineProjectChats.expandedProjectId == ChatProject.UNASSIGNED,
                onToggle = { onToggleProject(ChatProject.UNASSIGNED) },
                menuContent = null,
                expandedContent = { expandedChats(ChatProject.UNASSIGNED) },
            )
        }

        item(key = "projects_divider") {
            AdaptiveDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        items(items = projects, key = { it.id }) { folder ->
            ProjectFolderAccordion(
                name = folder.name,
                conversationCount = folder.conversationCount,
                leading = { ProjectTile(projectId = folder.id, name = folder.name, size = 20.dp) },
                expanded = inlineProjectChats.expandedProjectId == folder.id,
                highlighted = inlineProjectChats.expandedProjectId == folder.id,
                onToggle = { onToggleProject(folder.id) },
                menuContent = {
                    // Laid out at the slot's size; M3 still gives it a full-size touch target.
                    ProjectMenuButton(
                        buttonSize = ProjectMenuSlot,
                        onNewChat = { onNewChatInProject(folder.id, folder.name) },
                        onEdit = { editTarget = folder },
                        onDelete = { deleteTarget = folder },
                    )
                },
                expandedContent = { expandedChats(folder.id) },
            )
        }

        if (hasMoreProjects) {
            item(key = "projects_more") {
                TextButton(
                    onClick = onOpenProjectsIndex,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.projects_see_all),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ProjectEditorSheet(
            title = stringResource(Res.string.project_new),
            confirmLabel = stringResource(Res.string.project_create_confirm),
            onConfirm = { name, description ->
                onCreateProject(name, description)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editTarget?.let { target ->
        ProjectEditorSheet(
            title = stringResource(Res.string.project_edit),
            confirmLabel = stringResource(Res.string.save),
            initialName = target.name,
            initialDescription = target.description.orEmpty(),
            onConfirm = { name, description ->
                onEditProject(target.id, name, description)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ProjectDeleteDialog(
            projectName = target.name,
            onConfirm = {
                onDeleteProject(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/**
 * A project folder row that expands/collapses its chats inline (accordion) in the Projects tab. The
 * chevron points down when open and right when collapsed; [menuContent] is the optional trailing
 * overflow (null for the Unassigned bucket), and [expandedContent] renders the chats when open.
 */
@Composable
private fun ProjectFolderAccordion(
    name: String,
    conversationCount: Int?,
    leading: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: (@Composable () -> Unit)? = null,
    /** The selected project (the open folder), which the drawer's New chat files into. */
    highlighted: Boolean = false,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "ProjectChevronRotation",
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawerRowShape()
                .background(
                    if (highlighted) adaptiveSelectedRowColor else Color.Transparent,
                )
                .clickable(onClick = onToggle)
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The menu leads the row, in the thumb's path from the drawer's edge. A row without one
            // (Not in a project) keeps the slot, so every icon lines up.
            Box(modifier = Modifier.size(ProjectMenuSlot), contentAlignment = Alignment.Center) {
                menuContent?.invoke()
            }
            Spacer(modifier = Modifier.width(4.dp))
            leading()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (conversationCount != null) {
                Text(
                    text = conversationCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // Slight indent nests the chats under their folder.
            Column(modifier = Modifier.padding(start = 12.dp)) {
                expandedContent()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerConversationItem(
    data: DrawerConversationDisplayData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onMenuCancel: () -> Unit = {},
    showBookmarkToggle: Boolean = true,
    // While searching: the query, so the title's matching words are marked.
    searchQuery: String? = null,
    menuContent: @Composable (DpOffset, MenuDragSelection) -> Unit = { _, _ -> },
) {
    val glass = isLiquidGlass
    val rowShape = if (glass) CircleShape else ItemShape
    val backgroundColor = if (data.isActive) adaptiveSelectedRowColor else Color.Transparent

    // Horizontal pixel position of the last press, so the long-press menu can open with its
    // left edge under the finger. Kept in pixels (no density captured in the gesture) and
    // converted to dp at use-site to stay correct across density changes.
    var pressXpx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // onLongClickLabel announces the long-press action to TalkBack — without it the action menu
    // (the only way to reach rename/delete/share/etc. from the drawer) is undiscoverable.
    val longPressLabel = stringResource(Res.string.cd_conversation_actions)

    val menuDrag = rememberMenuDragSelection()

    // Box wraps the row so the long-press action menu (a DropdownMenu) anchors to this row.
    Box(modifier = modifier) {
        Row(
            // pointerInput is outermost so the captured x shares the Box's coordinate space
            // (the menu anchor), and recorded without consuming the down so combinedClickable
            // still handles tap + long-press normally.
            modifier = Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressXpx = down.position.x
                    }
                }
                .menuDragAnchor(
                    menuDrag,
                    opensOnDrag = false,
                    onOpen = onLongPress,
                    onCancel = onMenuCancel,
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .fillMaxWidth()
                .background(backgroundColor, rowShape)
                .clip(rowShape)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = longPressLabel,
                    onLongClick = onLongPress,
                    // menuDragAnchor already fires the hold haptic; on here it buzzes twice.
                    hapticFeedbackEnabled = false,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.isActive && !glass) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary, ActiveIndicatorShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (data.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                EndpointIcon(
                    endpointName = data.endpoint,
                    iconUrl = data.endpointIconUrl,
                    size = 18.dp,
                    glyphTint = if (data.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                val matchColor = searchMatchColor(isSurfaceDark())
                val title = remember(data.title, searchQuery, matchColor) {
                    val ranges = searchQuery?.let { TitleMatch.of(data.title, it) }.orEmpty()
                    buildAnnotatedString {
                        append(data.title)
                        ranges.forEach { addStyle(SpanStyle(background = matchColor), it.first, it.last + 1) }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (data.isActive) {
                        if (glass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Relative time is formatted here rather than in the ViewModel mapping: it depends
                // on the clock, so a pre-formatted value in immutable state goes stale. The
                // reference is a key, not just an input — it is the thing that advances, and
                // without it this memo would never recompute. Same shape as ConversationItem.
                val format = LocalDateTimeFormat.current
                val reference = LocalRelativeTimeReference.current
                val timeLabel = remember(data.updatedAt, format, reference) {
                    data.updatedAt?.listLabel(format, reference)
                }
                val relativeTime = timeLabel?.resolve()
                val subtitle = remember(data.modelLabel, relativeTime) {
                    buildString {
                        data.modelLabel?.let { model ->
                            append(model.take(20))
                        }
                        if (!relativeTime.isNullOrEmpty()) {
                            if (isNotEmpty()) append(" \u00B7 ")
                            append(relativeTime)
                        }
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (showBookmarkToggle) {
                val starBounce = rememberPressBounce()
                Icon(
                    imageVector = if (data.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (data.isFavorite) {
                        stringResource(Res.string.remove_bookmark)
                    } else {
                        stringResource(Res.string.bookmark)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .pressBounce(starBounce, depth = STAR_SWELL)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFavorite)
                        .padding(8.dp),
                    tint = if (data.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Open the menu just below the row, with its left edge under the press point. The
        // DropdownMenu position provider flips it above the row near the screen bottom and
        // clamps it within a margin, so it never clips off-screen.
        menuContent(with(density) { DpOffset(x = pressXpx.toDp(), y = MenuVerticalGap) }, menuDrag)
    }
}

/**
 * Drawer section header (icon + title), shared by the Pinned and Favorites sections. With a non-null
 * [onToggle] the whole row is tappable (a full 48dp touch target) and shows a trailing chevron that
 * points down when expanded and right when collapsed; with a null [onToggle] it renders as a plain,
 * non-interactive, compact header.
 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "SectionChevronRotation",
    )
    // The interactive header keeps its label/icon visually small but claims a 48dp-tall hit area so
    // it's comfortably tappable; the static header stays compact so it doesn't waste vertical space.
    val rowModifier = if (onToggle != null) {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawerRowShape()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (collapsed) Res.string.cd_expand_section else Res.string.cd_collapse_section,
                ),
                onClick = onToggle,
            )
            .padding(horizontal = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onToggle != null) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Tappable "Show more"/"Show less" row that toggles a section between its preview and full list. */
@Composable
private fun ShowMoreLessRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawerRowShape()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (expanded) Res.string.show_less else Res.string.show_more),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Caps the footer links at half the height the drawer's header and list leave them (the Column
 * measures them before the weighted list), so a short drawer (a phone on its side) still shows rows
 * of the list, and the links scroll in their half.
 */
private fun Modifier.atMostHalfTheRoomLeft(): Modifier = layout { measurable, constraints ->
    val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight / 2 else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = maxHeight))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Composable
internal fun DrawerFooterItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragSelection: ListDragSelection? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dragSelectRow(dragSelection, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp).dragSelectRowIcon(),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ProjectMenuSlot = 32.dp

@Composable
private fun EmptyFolderNewChatRow(projectName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .drawerRowShape()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AddComment,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.project_new_chat_in, projectName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
