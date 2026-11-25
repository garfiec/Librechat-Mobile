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
import com.garfiec.librechat.feature.conversations.components.ConversationActions
import com.garfiec.librechat.feature.conversations.components.ConversationItem
import com.garfiec.librechat.feature.conversations.components.DateGroupHeader
import com.garfiec.librechat.feature.conversations.components.ProjectEditorSheet
import com.garfiec.librechat.feature.conversations.components.ProjectFilesActions
import com.garfiec.librechat.feature.conversations.components.ProjectFilesCard
import com.garfiec.librechat.feature.conversations.components.ProjectInstructionsCard
import com.garfiec.librechat.feature.conversations.components.ProjectTile
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.export.ExportFormatPicker
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.copy_of
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.resources.project_chats_load_failed
import com.garfiec.librechat.feature.conversations.resources.project_edit
import com.garfiec.librechat.feature.conversations.resources.project_empty_chats
import com.garfiec.librechat.feature.conversations.resources.project_new_chat_in
import com.garfiec.librechat.feature.conversations.resources.project_sort_created
import com.garfiec.librechat.feature.conversations.resources.project_sort_label
import com.garfiec.librechat.feature.conversations.resources.project_sort_updated
import com.garfiec.librechat.feature.conversations.resources.project_update_failed
import com.garfiec.librechat.feature.conversations.resources.save
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsSort
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsUiState
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectContextUi
import org.jetbrains.compose.resources.stringResource

/**
 * `ProjectChatsScreen`'s page, from its state: the project's header, its Instructions and Files cards
 * ([projectContext], acted on through [fileActions]), then its chats by date. [fileAnnouncement] is
 * spoken by a screen reader; [conversationFor] looks up a row's chat for its menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectChatsScreenContent(
    state: ProjectChatsUiState,
    projectId: String,
    projectName: String,
    projectContext: ProjectContextUi?,
    fileActions: ProjectFilesActions,
    fileAnnouncement: String,
    snackbarHostState: SnackbarHostState,
    onConversationClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNewChatInProject: (projectId: String, projectName: String) -> Unit,
    onEditInstructions: (projectId: String) -> Unit,
    conversationFor: (String) -> Conversation?,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (ProjectChatsSort) -> Unit,
    onEditProject: (name: String, description: String, onResult: (saved: Boolean) -> Unit) -> Unit,
    onToggleFavorite: (Conversation) -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenBookmarks: (String) -> Unit,
    onShare: (String) -> Unit,
    onDuplicate: (id: String, title: String) -> Unit,
    onExport: (id: String, title: String?, format: ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by rememberUpdatedState(state)
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showExportFormatPicker by remember { mutableStateOf(false) }
    var exportConversation by remember { mutableStateOf<Conversation?>(null) }

    var showEditSheet by remember { mutableStateOf(false) }
    val isRealProject = projectId != ChatProject.UNASSIGNED
    // The loaded project's name wins once it arrives: the route's copy is stale after an edit.
    val displayName = uiState.project?.name ?: projectName

    val copyOfPrefix = stringResource(Res.string.copy_of)
    val newChatLabel = stringResource(Res.string.new_chat)

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 3 && uiState.hasMore && !uiState.isLoading
        }
    }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }.collect { if (it) currentOnLoadMore() }
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
                                            onClick = { onSortChange(ProjectChatsSort.UPDATED) },
                                        ),
                                        BarMenuItem(
                                            id = "created",
                                            label = stringResource(Res.string.project_sort_created),
                                            icon = null,
                                            checked = uiState.sort == ProjectChatsSort.CREATED,
                                            onClick = { onSortChange(ProjectChatsSort.CREATED) },
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
            onRefresh = onRefresh,
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
                            fileActions = fileActions,
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
                            onRetry = onRefresh,
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
                                    selectedConversation = conversationFor(displayData.conversationId)
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
                onConfirm = onEditProject,
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
                selectedConversation?.let { onToggleFavorite(it) }
                selectedConversation = null
            },
            onRename = { newTitle ->
                selectedConversation?.conversationId?.let { onRename(it, newTitle) }
                selectedConversation = null
            },
            onArchive = {
                selectedConversation?.conversationId?.let { onArchive(it) }
                selectedConversation = null
            },
            onDelete = {
                selectedConversation?.conversationId?.let { onDelete(it) }
                selectedConversation = null
            },
            onBookmarks = {
                selectedConversation?.conversationId?.let { onOpenBookmarks(it) }
                selectedConversation = null
            },
            onShare = {
                selectedConversation?.conversationId?.let { onShare(it) }
                selectedConversation = null
            },
            onDuplicate = {
                val convo = selectedConversation
                convo?.conversationId?.let { id ->
                    onDuplicate(id, copyOfPrefix.replace("%1\$s", convo.title ?: newChatLabel))
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

    if (showExportFormatPicker && exportConversation != null) {
        ExportFormatPicker(
            onFormatSelect = { format ->
                exportConversation?.conversationId?.let { onExport(it, exportConversation?.title, format) }
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
