package com.garfiec.librechat.feature.conversations.screen

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
import com.garfiec.librechat.feature.conversations.components.BookmarkPickerHost
import com.garfiec.librechat.feature.conversations.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.platform.FileSaver
import com.garfiec.librechat.feature.conversations.platform.showToast
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.conversation_exported
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val BookmarkExportFileNameSanitizer = Regex("[^a-zA-Z0-9._-]")

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

    BookmarkChatsScreenContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onConversationClick = onConversationClick,
        onNavigateBack = onNavigateBack,
        conversationFor = viewModel::getConversation,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onEdit = viewModel::openEdit,
        onSaveBookmark = viewModel::save,
        onCloseEditor = viewModel::closeEditor,
        onDeleteBookmark = viewModel::delete,
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
    BookmarkPickerHost(controller = viewModel.bookmarkPicker)
}
