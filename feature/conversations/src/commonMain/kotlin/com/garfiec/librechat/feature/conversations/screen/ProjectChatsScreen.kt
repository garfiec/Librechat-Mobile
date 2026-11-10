package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.conversations.components.AvailableProjectFilesSheet
import com.garfiec.librechat.feature.conversations.components.BookmarkPickerHost
import com.garfiec.librechat.feature.conversations.components.ConversationActions
import com.garfiec.librechat.feature.conversations.components.ConversationItem
import com.garfiec.librechat.feature.conversations.components.DateGroupHeader
import com.garfiec.librechat.feature.conversations.components.ProjectEditorSheet
import com.garfiec.librechat.feature.conversations.components.ProjectFilesActions
import com.garfiec.librechat.feature.conversations.components.ProjectFilesCard
import com.garfiec.librechat.feature.conversations.components.ProjectInstructionsCard
import com.garfiec.librechat.feature.conversations.components.ProjectTile
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.export.ExportFormatPicker
import com.garfiec.librechat.feature.conversations.platform.FileSaver
import com.garfiec.librechat.feature.conversations.platform.rememberProjectFilePicker
import com.garfiec.librechat.feature.conversations.platform.showToast
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.conversation_exported
import com.garfiec.librechat.feature.conversations.resources.copy_of
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.resources.project_chats_load_failed
import com.garfiec.librechat.feature.conversations.resources.project_edit
import com.garfiec.librechat.feature.conversations.resources.project_empty_chats
import com.garfiec.librechat.feature.conversations.resources.project_files_at_limit
import com.garfiec.librechat.feature.conversations.resources.project_files_excess
import com.garfiec.librechat.feature.conversations.resources.project_new_chat_in
import com.garfiec.librechat.feature.conversations.resources.project_sort_created
import com.garfiec.librechat.feature.conversations.resources.project_sort_label
import com.garfiec.librechat.feature.conversations.resources.project_sort_updated
import com.garfiec.librechat.feature.conversations.resources.project_update_failed
import com.garfiec.librechat.feature.conversations.resources.save
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsSort
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectContextUi
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectFilesMessage
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val ExportFileNameSanitizer = Regex("[^a-zA-Z0-9._-]")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectChatsScreen(
    projectId: String,
    projectName: String,
    onConversationClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Closes this screen once its project is gone; it may not be on top, so not [onNavigateBack]. */
    onCloseSelf: () -> Unit = onNavigateBack,
    /** Starts a new chat filed into this project. Not offered for the Unassigned filter. */
    onNewChatInProject: (projectId: String, projectName: String) -> Unit = { _, _ -> },
    /** Opens the project's instructions editor (v0.8.9-rc1). */
    onEditInstructions: (projectId: String) -> Unit = {},
    viewModel: ProjectChatsViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectContext by viewModel.projectContext.collectAsStateWithLifecycle()
    val availableFiles by viewModel.projectFiles.available.collectAsStateWithLifecycle()
    val filePicker = rememberProjectFilePicker { picked -> viewModel.projectFiles.upload(picked) }
    // Spoken by a screen reader when a file is added or removed; nothing is drawn.
    var fileAnnouncement by remember { mutableStateOf("") }

    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showExportFormatPicker by remember { mutableStateOf(false) }
    var exportConversation by remember { mutableStateOf<Conversation?>(null) }

    var showEditSheet by remember { mutableStateOf(false) }
    val isRealProject = projectId != ChatProject.UNASSIGNED
    // The loaded project's name wins once it arrives: the route's copy is stale after an edit.
    val displayName = uiState.project?.name ?: projectName

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }
    val currentOnCloseSelf by rememberUpdatedState(onCloseSelf)
    LaunchedEffect(uiState.projectDeleted) {
        if (uiState.projectDeleted) currentOnCloseSelf()
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

    LaunchedEffect(Unit) {
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
                    pendingExportFileName = "${event.title.replace(ExportFileNameSanitizer, "_")}.$ext"
                }
                is ConversationListEvent.ImportSuccess -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.projectFiles.messages.collect { message ->
            when (message) {
                is ProjectFilesMessage.Error -> snackbarHostState.showSnackbar(getString(message.message))
                is ProjectFilesMessage.AtLimit ->
                    snackbarHostState.showSnackbar(getString(Res.string.project_files_at_limit, message.maxFiles))
                is ProjectFilesMessage.Excess ->
                    snackbarHostState.showSnackbar(getPluralString(Res.plurals.project_files_excess, message.count, message.count))
                is ProjectFilesMessage.Announce -> fileAnnouncement = getString(message.message)
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
                    title = BarTitle(displayName),
                    actions = listOfNotNull(
                        uiState.project?.let {
                            BarAction.Icon(
                                id = "edit",
                                icon = BarIcons.Edit,
                                label = stringResource(Res.string.project_edit),
                                onClick = { showEditSheet = true },
                            )
                        },
                        BarAction.Menu(
                            id = "sort",
                            icon = BarIcons.Sort,
                            label = stringResource(Res.string.project_sort_label),
                            sections = listOf(
                                BarMenuSection(
                                    listOf(
                                        BarMenuItem(
                                            id = "updated",
                                            label = stringResource(Res.string.project_sort_updated),
                                            icon = null,
                                            checked = uiState.sort == ProjectChatsSort.UPDATED,
                                            onClick = { viewModel.setSort(ProjectChatsSort.UPDATED) },
                                        ),
                                        BarMenuItem(
                                            id = "created",
                                            label = stringResource(Res.string.project_sort_created),
                                            icon = null,
                                            checked = uiState.sort == ProjectChatsSort.CREATED,
                                            onClick = { viewModel.setSort(ProjectChatsSort.CREATED) },
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
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            Box(Modifier.size(0.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = fileAnnouncement })
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (isRealProject) {
                    item(key = "header", contentType = "header") {
                        ProjectHeader(
                            projectId = projectId,
                            name = displayName,
                            description = uiState.project?.description,
                        )
                    }
                    item(key = "new_chat", contentType = "new_chat") {
                        NewChatInProjectButton(
                            projectName = displayName,
                            onClick = { onNewChatInProject(projectId, displayName) },
                        )
                    }
                    projectContext?.let { context ->
                        projectContextItems(
                            context = context,
                            onEditInstructions = { onEditInstructions(projectId) },
                            fileActions = ProjectFilesActions(
                                onUpload = filePicker::launch,
                                onChooseExisting = viewModel.projectFiles::openPicker,
                                onRemove = viewModel.projectFiles::remove,
                                onRetryUpload = viewModel.projectFiles::retry,
                                onDismissUpload = viewModel.projectFiles::dismiss,
                                onRetryLoad = viewModel.projectFiles::load,
                            ),
                        )
                    }
                }
                when {
                    uiState.isLoading && uiState.conversationCount == 0 -> item(
                        key = "loading",
                        contentType = "loading",
                    ) {
                        LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp))
                    }
                    uiState.loadFailed && uiState.conversationCount == 0 -> item(key = "error", contentType = "error") {
                        ErrorBanner(
                            message = stringResource(Res.string.project_chats_load_failed),
                            onRetry = viewModel::refresh,
                        )
                    }
                    uiState.conversationCount == 0 -> item(key = "empty", contentType = "empty") {
                        EmptyState(
                            title = stringResource(Res.string.project_empty_chats),
                            icon = Icons.Default.Forum,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                    }
                    else -> uiState.groupedConversations.forEach { (dateGroup, displayItems) ->
                        stickyHeader(key = "header_${dateGroup.key}", contentType = "date_header") {
                            DateGroupHeader(label = dateGroup.resolve())
                        }
                        items(
                            items = displayItems,
                            key = { it.conversationId },
                            contentType = { "conversation" },
                        ) { displayData ->
                            ConversationItem(
                                data = displayData,
                                onClick = { onConversationClick(displayData.conversationId) },
                                onActionsClick = {
                                    selectedConversation = viewModel.getConversation(displayData.conversationId)
                                },
                                bookmarksEnabled = uiState.bookmarksEnabled,
                            )
                            AdaptiveDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
                if (uiState.isLoading && uiState.conversationCount > 0) {
                    item(key = "loading_more", contentType = "loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveCircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        uiState.project?.let { project ->
            ProjectEditorSheet(
                title = stringResource(Res.string.project_edit),
                confirmLabel = stringResource(Res.string.save),
                initialName = project.name,
                initialDescription = project.description.orEmpty(),
                failureMessage = stringResource(Res.string.project_update_failed),
                maxDescriptionLength = uiState.descriptionLimit,
                onConfirm = viewModel::editProject,
                onDismiss = { showEditSheet = false },
            )
        }
    }

    if (selectedConversation != null) {
        ConversationActions(
            conversation = selectedConversation!!,
            onDismiss = { selectedConversation = null },
            isFavorite = SAVED_TAG in (selectedConversation?.tags ?: emptyList()),
            bookmarksEnabled = uiState.bookmarksEnabled,
            showShareAction = uiState.sharedLinksEnabled,
            onFavoriteToggle = {
                selectedConversation?.let { viewModel.toggleFavorite(it) }
                selectedConversation = null
            },
            onRename = { newTitle ->
                selectedConversation?.conversationId?.let { viewModel.renameConversation(it, newTitle) }
                selectedConversation = null
            },
            onArchive = {
                selectedConversation?.conversationId?.let { viewModel.archiveConversation(it) }
                selectedConversation = null
            },
            onDelete = {
                selectedConversation?.conversationId?.let { viewModel.deleteConversation(it) }
                selectedConversation = null
            },
            onBookmarks = {
                selectedConversation?.conversationId?.let { viewModel.openBookmarks(it) }
                selectedConversation = null
            },
            onShare = {
                selectedConversation?.conversationId?.let { viewModel.shareConversation(it) }
                selectedConversation = null
            },
            onDuplicate = {
                val convo = selectedConversation
                convo?.conversationId?.let { id ->
                    viewModel.duplicateConversation(id, copyOfPrefix.replace("%1\$s", convo.title ?: newChatLabel))
                }
                selectedConversation = null
            },
            onExport = {
                exportConversation = selectedConversation
                showExportFormatPicker = true
                selectedConversation = null
            },
        )
    }

    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)

    if (availableFiles.isOpen) {
        AvailableProjectFilesSheet(
            state = availableFiles,
            onQueryChange = viewModel.projectFiles::setPickerQuery,
            onLoadMore = viewModel.projectFiles::loadMoreAvailable,
            onRetry = viewModel.projectFiles::retryAvailable,
            onPick = viewModel.projectFiles::addExisting,
            onDismiss = viewModel.projectFiles::closePicker,
        )
    }
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

/** The Instructions card and, with a RAG API on the server, the Files card. */
private fun LazyListScope.projectContextItems(
    context: ProjectContextUi,
    onEditInstructions: () -> Unit,
    fileActions: ProjectFilesActions,
) {
    item(key = "instructions", contentType = "instructions") {
        ProjectInstructionsCard(instructions = context.instructions, onEdit = onEditInstructions)
    }
    context.files?.let { files ->
        item(key = "files", contentType = "files") {
            ProjectFilesCard(
                files = files,
                maxFiles = context.maxFiles,
                canUpload = context.canUploadFiles,
                atLimit = context.atFileLimit,
                actions = fileActions,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    item(key = "context_spacer", contentType = "spacer") { Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun ProjectHeader(projectId: String, name: String, description: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectTile(projectId = projectId, name = name, size = 48.dp)
        val text = description?.trim().orEmpty()
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NewChatInProjectButton(projectName: String, onClick: () -> Unit) {
    val shape = NewChatButtonShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.project_new_chat_in, projectName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val NewChatButtonShape = RoundedCornerShape(26.dp)
