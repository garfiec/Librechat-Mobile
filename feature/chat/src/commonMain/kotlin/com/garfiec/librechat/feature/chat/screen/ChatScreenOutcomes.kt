package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garfiec.librechat.feature.chat.components.BookmarkPickerSheetHost
import com.garfiec.librechat.feature.chat.components.ForkOptionsBottomSheet
import com.garfiec.librechat.feature.chat.components.ShareLinkSheetHost
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.notice_branch_without_files_done
import com.garfiec.librechat.feature.chat.resources.notice_branch_without_files_draft
import com.garfiec.librechat.feature.chat.viewmodel.ChatNoticeHandoff
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.ForkNotice
import org.koin.compose.koinInject

/**
 * The ViewModel outcomes the chat screen acts on: lifecycle pause/resume, the share sheet, the
 * bookmark checklist, Fork and Duplicate. Each is easy to drop silently (the ViewModel never hears
 * a resume, a fork is made the user never lands in), which is why they sit together here rather
 * than in the screen.
 */
@Composable
internal fun ChatScreenOutcomes(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
    onNavigateToConversation: ((String) -> Unit)?,
    /** Read on each return: an MCP sign-in ends on it and re-reads the MCP statuses itself. */
    mcpSignInEnding: () -> Boolean,
) {
    // The effects below outlive a recomposition, so they read the navigation callbacks through
    // these rather than capturing whichever pair was current when they launched.
    val navigate by rememberUpdatedState(onNavigateToConversation)
    val start by rememberUpdatedState(onConversationStart)

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    val signInEnding by rememberUpdatedState(mcpSignInEnding)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume(signInEnding()) }

    val noticeHandoff = koinInject<ChatNoticeHandoff>()
    LaunchedEffect(uiState.forkedConversationId) {
        val forkId = uiState.forkedConversationId ?: return@LaunchedEffect
        // Read before the handled call clears it. Handed to the copy's screen, which shows it.
        val notice = uiState.forkNotice
        viewModel.onForkedConversationHandled()
        when (notice) {
            ForkNotice.COPIED_WITHOUT_FILES -> noticeHandoff.put(Res.string.notice_branch_without_files_done, forkId)
            ForkNotice.COPIED_WITHOUT_FILES_DRAFT -> noticeHandoff.put(Res.string.notice_branch_without_files_draft, forkId)
            null -> Unit
        }
        openConversation(forkId, navigate, start)
    }

    LaunchedEffect(uiState.duplicatedConversationId) {
        val dupId = uiState.duplicatedConversationId ?: return@LaunchedEffect
        viewModel.onDuplicatedConversationHandled()
        openConversation(dupId, navigate, start)
    }

    // Copy feedback and errors render inside the sheet: a snackbar would sit behind its scrim.
    ShareLinkSheetHost(controller = viewModel.shareLink, onDismiss = viewModel::closeShareSheet)

    BookmarkPickerSheetHost(controller = viewModel.bookmarkPicker)

    val forkMessageId = uiState.showForkOptionsForMessageId
    if (forkMessageId != null) {
        ForkOptionsBottomSheet(
            onDismiss = viewModel::dismissForkOptions,
            onFork = { option, splitAtTarget, excludeFiles ->
                viewModel.forkFromMessage(
                    messageId = forkMessageId,
                    option = option,
                    splitAtTarget = splitAtTarget,
                    excludeFiles = excludeFiles,
                )
            },
            excludeFilesSupported = uiState.forkExcludeFilesSupported,
        )
    }
}

/** Forks and duplicates are always real conversations, never temporary ones. */
private fun openConversation(
    conversationId: String,
    onNavigateToConversation: ((String) -> Unit)?,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
) {
    if (onNavigateToConversation != null) {
        onNavigateToConversation(conversationId)
    } else {
        onConversationStart?.invoke(conversationId, false)
    }
}
