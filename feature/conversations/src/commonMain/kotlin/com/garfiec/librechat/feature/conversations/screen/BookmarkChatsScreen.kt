package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkEditorSheet
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.core.ui.resources.bookmark_chat_count
import com.garfiec.librechat.core.ui.resources.bookmark_edit
import com.garfiec.librechat.feature.conversations.components.BookmarkDeleteDialog
import com.garfiec.librechat.feature.conversations.components.BookmarkPickerHost
import com.garfiec.librechat.feature.conversations.components.ConversationActions
import com.garfiec.librechat.feature.conversations.components.ConversationItem
import com.garfiec.librechat.feature.conversations.components.DateGroupHeader
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.export.ExportFormatPicker
import com.garfiec.librechat.feature.conversations.platform.FileSaver
import com.garfiec.librechat.feature.conversations.platform.showToast
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.bookmark_chats_empty
import com.garfiec.librechat.feature.conversations.resources.bookmark_chats_empty_description
import com.garfiec.librechat.feature.conversations.resources.bookmark_chats_load_error
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete
import com.garfiec.librechat.feature.conversations.resources.bookmark_options
import com.garfiec.librechat.feature.conversations.resources.conversation_exported
import com.garfiec.librechat.feature.conversations.resources.copy_of
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsContent
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsUiState
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

private val BookmarkExportFileNameSanitizer = Regex("[^a-zA-Z0-9._-]")
private val HeaderTileSize = 48.dp

/**
 * One bookmark's chats, from its card on the Bookmarks index: the bookmark's tile and description,
 * then its chats by date. Edit and Delete sit in the top bar; each row's ⋮ has the same actions as
 * a project's chats. [onCloseSelf] runs once the bookmark is gone (deleted here or elsewhere) or
 * bookmarks are turned off for the account; the entry may not be on top, so not [onNavigateBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkChatsScreen(
    tag: String,
    onConversationClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseSelf: () -> Unit = onNavigateBack,
    viewModel: BookmarkChatsViewModel = koinViewModel { parametersOf(tag) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showExportFormatPicker by remember { mutableStateOf(false) }
    var exportConversation by remember { mutableStateOf<Conversation?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }
    val currentOnCloseSelf by rememberUpdatedState(onCloseSelf)
    val gone = uiState.deleted || !uiState.bookmarksEnabled
    LaunchedEffect(gone) {
        if (gone) currentOnCloseSelf()
    }

    var pendingExportFileName by remember { mutableStateOf<String?>(null) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnConversationClick by rememberUpdatedState(onConversationClick)

    val copyOfPrefix = stringResource(Res.string.copy_of)
    val newChatLabel = stringResource(Res.string.new_chat)
    val conversationExportedMsg = stringResource(Res.string.conversation_exported)

    FileSaver(
        triggerFileName = pendingExportFileName,
        content = pendingExportContent,
        onComplete = { success, errorMessage ->
            if (success) showToast(conversationExportedMsg) else if (errorMessage != null) showToast(errorMessage)
        },
        onReset = {
            pendingExportFileName = null
            pendingExportContent = null
        },
    )

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationListEvent.NavigateToConversation -> currentOnConversationClick(event.conversationId)
                // Only the drawer deletes with active-conversation awareness, so this never fires here.
                is ConversationListEvent.NavigateToNewChat -> Unit
                is ConversationListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is ConversationListEvent.ShowErrorRes -> snackbarHostState.showSnackbar(getString(event.message))
                is ConversationListEvent.ExportReady -> {
                    pendingExportContent = event.content
                    val ext = when (event.format) {
                        ExportFormat.JSON -> "json"
                        ExportFormat.MARKDOWN -> "md"
                    }
                    pendingExportFileName = "${event.title.replace(BookmarkExportFileNameSanitizer, "_")}.$ext"
                }
                is ConversationListEvent.ImportSuccess -> Unit
            }
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 3 && uiState.hasMore && !uiState.isLoading
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onNavigateBack),
                    title = BarTitle(uiState.name),
                    actions = listOf(
                        BarAction.Icon(
                            id = "edit",
                            icon = BarIcons.Edit,
                            label = stringResource(CoreRes.string.bookmark_edit),
                            onClick = viewModel::openEdit,
                        ),
                        BarAction.Menu(
                            id = "more",
                            icon = BarIcons.More,
                            label = stringResource(Res.string.bookmark_options),
                            sections = listOf(
                                BarMenuSection(
                                    listOf(
                                        BarMenuItem(
                                            id = "delete",
                                            label = stringResource(Res.string.bookmark_delete),
                                            icon = BarIcons.Delete,
                                            destructive = true,
                                            onClick = { confirmDelete = true },
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            BookmarkChatsList(
                state = uiState,
                listState = listState,
                onRetry = viewModel::retry,
                onConversationClick = onConversationClick,
                onActionsClick = { id -> selectedConversation = viewModel.getConversation(id) },
            )
        }
    }

    uiState.editor?.let { editor ->
        BookmarkEditorSheet(
            existingNames = uiState.allNames,
            onConfirm = { name, description, _ -> viewModel.save(name, description) },
            onDismiss = viewModel::closeEditor,
            editing = editor.editing?.tag,
            initialDescription = editor.editing?.description.orEmpty(),
            saving = editor.saving,
            error = editor.error?.let { stringResource(it.message) },
        )
    }

    if (confirmDelete) {
        BookmarkDeleteDialog(
            name = uiState.name,
            chatCount = uiState.bookmark?.count ?: 0,
            onConfirm = {
                viewModel.delete()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }

    selectedConversation?.let { conversation ->
        ConversationActions(
            conversation = conversation,
            onDismiss = { selectedConversation = null },
            isFavorite = SAVED_TAG in conversation.tags,
            bookmarksEnabled = uiState.bookmarksEnabled,
            showShareAction = uiState.sharedLinksEnabled,
            onFavoriteToggle = {
                viewModel.toggleFavorite(conversation)
                selectedConversation = null
            },
            onRename = { newTitle ->
                conversation.conversationId?.let { viewModel.renameConversation(it, newTitle) }
                selectedConversation = null
            },
            onArchive = {
                conversation.conversationId?.let { viewModel.archiveConversation(it) }
                selectedConversation = null
            },
            onDelete = {
                conversation.conversationId?.let { viewModel.deleteConversation(it) }
                selectedConversation = null
            },
            onBookmarks = {
                conversation.conversationId?.let { viewModel.openBookmarks(it) }
                selectedConversation = null
            },
            onShare = {
                conversation.conversationId?.let { viewModel.shareConversation(it) }
                selectedConversation = null
            },
            onDuplicate = {
                conversation.conversationId?.let { id ->
                    viewModel.duplicateConversation(id, copyOfPrefix.replace("%1\$s", conversation.title ?: newChatLabel))
                }
                selectedConversation = null
            },
            onExport = {
                exportConversation = conversation
                showExportFormatPicker = true
                selectedConversation = null
            },
        )
    }

    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)
    BookmarkPickerHost(controller = viewModel.bookmarkPicker)

    if (showExportFormatPicker && exportConversation != null) {
        ExportFormatPicker(
            onFormatSelect = { format ->
                exportConversation?.conversationId?.let { viewModel.exportConversation(it, exportConversation?.title, format) }
                showExportFormatPicker = false
                exportConversation = null
            },
            onDismiss = {
                showExportFormatPicker = false
                exportConversation = null
            },
        )
    }
}

/** The page's body: the bookmark's header, then its chats by date or the state standing in for them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarkChatsList(
    state: BookmarkChatsUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onConversationClick: (String) -> Unit,
    onActionsClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item(key = "header", contentType = "header") {
            BookmarkChatsHeader(
                name = state.name,
                description = state.bookmark?.description,
                count = state.bookmark?.count ?: 0,
            )
        }
        when (state.content) {
            BookmarkChatsContent.LOADING -> item(key = "loading", contentType = "loading") {
                LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp))
            }
            BookmarkChatsContent.ERROR -> item(key = "error", contentType = "error") {
                ErrorBanner(
                    message = stringResource(Res.string.bookmark_chats_load_error),
                    onRetry = onRetry,
                    modifier = Modifier.padding(16.dp),
                )
            }
            BookmarkChatsContent.EMPTY -> item(key = "empty", contentType = "empty") {
                EmptyState(
                    title = stringResource(Res.string.bookmark_chats_empty),
                    description = stringResource(Res.string.bookmark_chats_empty_description),
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.padding(vertical = 48.dp),
                )
            }
            BookmarkChatsContent.CHATS -> state.groupedConversations.forEach { (dateGroup, displayItems) ->
                stickyHeader(key = "header_${dateGroup.key}", contentType = "date_header") {
                    DateGroupHeader(label = dateGroup.resolve())
                }
                items(items = displayItems, key = { it.conversationId }, contentType = { "conversation" }) { displayData ->
                    ConversationItem(
                        data = displayData,
                        onClick = { onConversationClick(displayData.conversationId) },
                        onActionsClick = { onActionsClick(displayData.conversationId) },
                        bookmarksEnabled = state.bookmarksEnabled,
                    )
                    AdaptiveDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        if (state.isLoading && state.conversationCount > 0) {
            item(key = "loading_more", contentType = "loading_more") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The bookmark's tile beside its description and how many chats carry it. */
@Composable
private fun BookmarkChatsHeader(name: String, description: String?, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialTile(seed = name, label = name, size = HeaderTileSize)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val text = description?.trim().orEmpty()
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (count > 0) {
                Text(
                    text = pluralStringResource(CoreRes.plurals.bookmark_chat_count, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (text.isNotEmpty()) Modifier.padding(top = 4.dp) else Modifier,
                )
            }
        }
    }
}
