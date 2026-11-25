package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.components.AvailableProjectFilesSheet
import com.garfiec.librechat.feature.conversations.components.BookmarkPickerHost
import com.garfiec.librechat.feature.conversations.components.ProjectFilesActions
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.platform.FileSaver
import com.garfiec.librechat.feature.conversations.platform.rememberProjectFilePicker
import com.garfiec.librechat.feature.conversations.platform.showToast
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.conversation_exported
import com.garfiec.librechat.feature.conversations.resources.project_files_at_limit
import com.garfiec.librechat.feature.conversations.resources.project_files_excess
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsViewModel
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }
    val currentOnCloseSelf by rememberUpdatedState(onCloseSelf)
    LaunchedEffect(uiState.projectDeleted) {
        if (uiState.projectDeleted) currentOnCloseSelf()
    }

    var pendingExportFileName by remember { mutableStateOf<String?>(null) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnConversationClick by rememberUpdatedState(onConversationClick)

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

    ProjectChatsScreenContent(
        state = uiState,
        projectId = projectId,
        projectName = projectName,
        projectContext = projectContext,
        fileActions = ProjectFilesActions(
            onUpload = filePicker::launch,
            onChooseExisting = viewModel.projectFiles::openPicker,
            onRemove = viewModel.projectFiles::remove,
            onRetryUpload = viewModel.projectFiles::retry,
            onDismissUpload = viewModel.projectFiles::dismiss,
            onRetryLoad = viewModel.projectFiles::load,
        ),
        fileAnnouncement = fileAnnouncement,
        snackbarHostState = snackbarHostState,
        onConversationClick = onConversationClick,
        onNavigateBack = onNavigateBack,
        onNewChatInProject = onNewChatInProject,
        onEditInstructions = onEditInstructions,
        conversationFor = viewModel::getConversation,
        onLoadMore = viewModel::loadMore,
        onRefresh = { viewModel.refresh() },
        onSortChange = viewModel::setSort,
        onEditProject = viewModel::editProject,
        onToggleFavorite = viewModel::toggleFavorite,
        onRename = viewModel::renameConversation,
        onArchive = viewModel::archiveConversation,
        onDelete = viewModel::deleteConversation,
        onOpenBookmarks = viewModel::openBookmarks,
        onShare = viewModel::shareConversation,
        onDuplicate = viewModel::duplicateConversation,
        onExport = viewModel::exportConversation,
        modifier = modifier,
    )

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
}
