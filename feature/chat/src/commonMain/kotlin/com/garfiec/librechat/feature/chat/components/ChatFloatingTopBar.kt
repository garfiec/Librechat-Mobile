package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.components.mcpapp.LocalMcpApps
import com.garfiec.librechat.feature.chat.viewmodel.ChatActions
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * The chat screen's floating top bar, shared by Android and iOS. Chips (hamburger, the configurable
 * content bubble, optional temp-chat toggle, options) are drawn over a top-down [chatTopBarScrim]
 * so chat content scrolls up *behind* a gently dimmed status-bar region rather than being capped by
 * an opaque app bar — a ChatGPT/Telegram-style header. The in-conversation search bar is pinned
 * directly beneath the chips so the overlay measures as one unit.
 *
 * The bubble is configurable along two mobile-only axes ([ChatUiState.chatHeaderContent] /
 * [ChatUiState.chatHeaderAlignment]): it shows the conversation title (long-press to edit in place),
 * the selected model (tap to open the model selector), or nothing. Showing the model here is an
 * opt-in that partially reverses the default decluttering choice of keeping model/params on the
 * composer "+" menu; the title remains the default.
 *
 * Most actions are wired straight to [viewModel]; only the triggers whose dialog hosting differs by
 * platform (preset load/save, rename) and the navigation callbacks are passed in.
 *
 * On a [widePane] (the chat pane, not the window, at least 600dp) the chat's pull request is a chip
 * in the bar and leaves the ⋮ menu; on a narrow one it stays in the menu (and above the composer).
 *
 * In [railMode] (an `AdaptiveRailBox`'s `RailTopBar` with vertical bars), either style draws only the
 * title here and its controls go to the side rail, in [chatBarItems]' order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatFloatingTopBar(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onLoadPreset: () -> Unit,
    onSavePreset: () -> Unit,
    onRename: () -> Unit,
    contextRingDialogs: ContextRingDialogs,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    onShowAllMedia: (() -> Unit)? = null,
    onOpenPromptsLibrary: (() -> Unit)? = null,
    widePane: Boolean = false,
    railMode: Boolean = false,
    onNewChat: (() -> Unit)? = null,
) {
    // Apps live beside the chat state (McpAppsViewModel), so the count is read here, once.
    val mcpAppsViewModel = LocalMcpApps.current?.controller?.viewModel
    val openApps = mcpAppsViewModel?.state?.collectAsStateWithLifecycle()?.value
        ?.let { state -> if (state.panelId == null) state.sessions.size else 0 } ?: 0
    // Temporary chats are never persisted, so they have no server-side task list.
    val backgroundTasks =
        if (uiState.isTemporaryChat) null else rememberBackgroundTasks(uiState.conversationId, uiState.isStreaming)
    val actions: ChatActions = viewModel
    ChatFloatingTopBarContent(
        uiState = uiState,
        actions = actions,
        onLoadPreset = onLoadPreset,
        onSavePreset = onSavePreset,
        onRename = onRename,
        contextRingDialogs = contextRingDialogs,
        openApps = openApps,
        onOpenApps = { mcpAppsViewModel?.showApps() },
        backgroundTasks = backgroundTasks,
        traceViewerSheet = { conversationId, isStreaming, onDismiss ->
            TraceViewerSheet(conversationId = conversationId, isStreaming = isStreaming, onDismiss = onDismiss)
        },
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        onShowAllMedia = onShowAllMedia,
        onOpenPromptsLibrary = onOpenPromptsLibrary,
        widePane = widePane,
        railMode = railMode,
        onNewChat = onNewChat,
    )
}
