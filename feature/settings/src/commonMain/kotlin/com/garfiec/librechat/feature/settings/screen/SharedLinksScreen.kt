package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.shared_chats_delete_failed
import com.garfiec.librechat.feature.settings.resources.shared_chats_refresh_failed
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksMessage
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksViewModel
import com.garfiec.librechat.feature.settings.viewmodel.shouldLoadMore
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Data → Shared chats: every link the user has shared, as cards. A card opens the share
 * sheet on its link; its ⋮ opens the chat or its shared version, copies the link or deletes it.
 */
@Composable
fun SharedLinksScreen(
    onNavigateBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedLinksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(getString(message.text))
        }
    }

    val gridState = rememberLazyGridState()
    // Read the current state on every pass: a captured first-composition state stops paging after page one.
    val currentState by rememberUpdatedState(uiState)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            currentState.shouldLoadMore(
                lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItems = layout.totalItemsCount,
            )
        }
    }
    LaunchedEffect(viewModel) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    val actions = remember(viewModel) {
        SharedLinksActions(
            refresh = viewModel::refresh,
            retry = viewModel::retry,
            loadMore = viewModel::loadMore,
            setSort = viewModel::setSort,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            clearSearch = viewModel::clearSearch,
            openLink = viewModel::openLink,
            delete = viewModel::delete,
        )
    }
    SharedLinksContent(
        uiState = uiState,
        actions = actions,
        snackbarHostState = snackbarHostState,
        gridState = gridState,
        onNavigateBack = onNavigateBack,
        onOpenConversation = onOpenConversation,
        shareSheet = { SharedLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet) },
        modifier = modifier,
    )
}

private val SharedLinksMessage.text: StringResource
    get() = when (this) {
        SharedLinksMessage.REFRESH_FAILED -> Res.string.shared_chats_refresh_failed
        SharedLinksMessage.DELETE_FAILED -> Res.string.shared_chats_delete_failed
    }
