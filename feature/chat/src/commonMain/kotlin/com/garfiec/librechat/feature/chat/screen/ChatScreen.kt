package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.notifications.ReplyNotificationAsk
import com.garfiec.librechat.feature.chat.components.ChatComposer
import com.garfiec.librechat.feature.chat.components.ChatFloatingTopBar
import com.garfiec.librechat.feature.chat.components.ConversationSeenEffect
import com.garfiec.librechat.feature.chat.components.SubagentThreadsSheet
import com.garfiec.librechat.feature.chat.components.addToChatSelectionItem
import com.garfiec.librechat.feature.chat.components.mcpapp.LocalLegacyUiResources
import com.garfiec.librechat.feature.chat.components.mcpapp.LocalMcpApps
import com.garfiec.librechat.feature.chat.components.mcpapp.rememberLegacyUiResources
import com.garfiec.librechat.feature.chat.components.mcpapp.rememberMcpApps
import com.garfiec.librechat.feature.chat.components.rememberChatAttachmentActions
import com.garfiec.librechat.feature.chat.components.rememberMcpSignIn
import com.garfiec.librechat.feature.chat.viewmodel.ChatActions
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.McpAppsViewModel
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInViewModel
import com.garfiec.librechat.feature.chat.viewmodel.UpdateBannerViewModel
import com.garfiec.librechat.feature.chat.viewmodel.conversationUiResources
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChatScreen(
    /**
     * Deep-link CTA from the user-provided-key error snackbar and the
     * model-selector "Add key" action on greyed endpoint groups. Tap navigates to
     * Settings → Provider API Keys. When [endpointName] is non-null, the destination
     * screen auto-opens the Set Key bottom-sheet for that endpoint.
     */
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    modifier: Modifier = Modifier,
    conversationId: String? = null,
    /** True when this Chat(id) entry was created as (or restored as) a temporary chat. Seeds the
     *  VM temp-aware so it never persists the server-hidden conversation. See Chat.isTemporary. */
    isTemporaryRoute: Boolean = false,
    initialAgentId: String? = null,
    /** Explicit (endpoint, model) to pre-select on a new chat — set when launched from a
     *  home-screen model shortcut / quick action. Mutually exclusive with [initialAgentId]. */
    initialEndpoint: String? = null,
    initialModel: String? = null,
    /** The spec that model shortcut was recorded from, if any. */
    initialSpec: String? = null,
    /** Opens the chat at this message, highlighting [highlightQuery] (a search hit). */
    targetMessageId: String? = null,
    highlightQuery: String? = null,
    targetInReasoning: Boolean = false,
    /** A new chat started from a project: filed into [chatProjectId], named by [chatProjectName]. */
    chatProjectId: String? = null,
    chatProjectName: String? = null,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)? = null,
    onNavigateToConversation: ((String) -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    /** Starts a new chat, as the drawer's New chat does; shown in the side rail. Null leaves it out. */
    onNewChat: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    /** Opens the "Show all media" gallery for the current conversation. Null (and the menu item
     *  hidden) on a brand-new chat that has no conversation id yet. */
    onShowAllMedia: (() -> Unit)? = null,
    /** Opens the server-file picker so the user can attach an already-uploaded file by reference. */
    onAttachFromServer: () -> Unit = {},
    /** Opens What's new from the update banner shown on an empty chat. */
    onOpenWhatsNew: () -> Unit = {},
    /** Opens the Memories page from a memory card's Manage action. Null hides the action. */
    onOpenMemories: (() -> Unit)? = null,
) {
    val viewModel: ChatViewModel =
        koinViewModel {
            parametersOf(
                conversationId,
                initialAgentId,
                isTemporaryRoute,
                initialEndpoint,
                initialModel,
                targetMessageId,
                highlightQuery,
                targetInReasoning,
                chatProjectId,
                initialSpec,
            )
        }
    val updateBannerViewModel: UpdateBannerViewModel = koinViewModel()
    val updateBanner by updateBannerViewModel.pendingUpdate.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Nothing here cancels an OAuth flow: only a connecting chip's "Cancel sign-in" does.
    val mcpSignInViewModel: McpSignInViewModel = koinViewModel()
    val mcpSignIn = rememberMcpSignIn(mcpSignInViewModel, snackbarHostState, onSignInEnd = viewModel::onMcpSignInEnded)
    // MCP Apps: the panel and its approval dialogs live here; rows read the result.
    val mcpAppsViewModel: McpAppsViewModel = koinViewModel()
    val mcpApps = rememberMcpApps(mcpAppsViewModel, snackbarHostState, onSendMessage = viewModel::sendAppMessage)
    // Legacy MCP-UI resources the reply's markers name, from every shown message and the live reply.
    val conversationUiResources by remember(viewModel) { conversationUiResources(viewModel.uiState) }
        .collectAsStateWithLifecycle(emptyMap())
    val legacyUiResources = rememberLegacyUiResources(mcpAppsViewModel, conversationUiResources)
    val actions: ChatActions = viewModel
    val slots = ChatScreenSlots(
        replyNotificationAsk = { streaming -> ReplyNotificationAsk(streaming = streaming, snackbarHostState = snackbarHostState) },
        startRecording = { sttEngine, sttLanguage, coroutineScope ->
            rememberChatStartRecording(
                viewModel = viewModel,
                sttEngine = sttEngine,
                sttLanguage = sttLanguage,
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
            )
        },
        attachmentActions = { filePickerMimeTypes ->
            rememberChatAttachmentActions(
                onFilesSelect = viewModel::onFilesSelected,
                filePickerMimeTypes = filePickerMimeTypes,
                onCodeFilesSelect = viewModel::onCodeFilesSelected,
            )
        },
        effects = { uiState ->
            ChatScreenEffects(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onConversationStart = onConversationStart,
                onNavigateBack = onNavigateBack,
                onNavigateToProviderKeys = onNavigateToProviderKeys,
            )

            ChatScreenOutcomes(
                uiState = uiState,
                viewModel = viewModel,
                onConversationStart = onConversationStart,
                onNavigateToConversation = onNavigateToConversation,
                mcpSignInEnding = { mcpSignInViewModel.endsOnReturn },
            )
        },
        mcpProviders = { content ->
            CompositionLocalProvider(
                LocalMcpApps provides mcpApps,
                LocalLegacyUiResources provides legacyUiResources,
                content = content,
            )
        },
        subagentThreadsSheet = { parentConversationId, focusParentToolCallId, focusThreadId, onDismiss ->
            SubagentThreadsSheet(
                parentConversationId = parentConversationId,
                focusParentToolCallId = focusParentToolCallId,
                focusThreadId = focusThreadId,
                onDismiss = onDismiss,
            )
        },
        selectionItem = { enabled, onAddToChat -> Modifier.addToChatSelectionItem(enabled, onAddToChat) },
        conversationSeenEffect = { id, messages, streaming, nearBottom ->
            ConversationSeenEffect(id, messages, streaming, nearBottom)
        },
        composer = { p -> ChatComposer(
            inputText = p.inputText,
            isStreaming = p.isStreaming,
            onInputChange = p.onInputChange,
            onSend = p.onSend,
            onStop = p.onStop,
            onOpenTools = p.onOpenTools,
            onDuringRunSend = p.onDuringRunSend,
            onQueue = p.onQueue,
            canQueue = p.canQueue,
            onSteer = p.onSteer,
            onInterrupt = p.onInterrupt,
            canSteer = p.canSteer,
            builtInSearchOn = p.builtInSearchOn,
            duringRunAction = p.duringRunAction,
            duringRunSendTarget = p.duringRunSendTarget,
            chrome = p.chrome,
            onSetDuringRunAction = p.onSetDuringRunAction,
            onPasteFiles = p.onPasteFiles,
            pasteScope = p.pasteScope,
            promptSuggestions = p.promptSuggestions,
            skillSuggestions = p.skillSuggestions,
            isLoadingSkills = p.isLoadingSkills,
            skillsLoadFailed = p.skillsLoadFailed,
            canPickSkills = p.canPickSkills,
            onRetrySkills = p.onRetrySkills,
            onPickerSelect = p.onPickerSelect,
            isRecording = p.isRecording,
            isTranscribing = p.isTranscribing,
            onStartRecording = p.onStartRecording,
            onStopRecording = p.onStopRecording,
            selectedModelDisplay = p.selectedModelDisplay,
            isCodeInterpreterAvailable = p.isCodeInterpreterAvailable,
            gates = p.gates,
            queuedPausedCount = p.queuedPausedCount,
            queueHeldForRefusal = p.queueHeldForRefusal,
            isTemporaryChat = p.isTemporaryChat,
            onSendQueuedMessages = p.onSendQueuedMessages,
            isEditingQueued = p.isEditingQueued,
            onCommitEdit = p.onCommitEdit,
            onCancelEdit = p.onCancelEdit,
            isAwaitingUploadSend = p.isAwaitingUploadSend,
            arePicksUnsettled = p.arePicksUnsettled,
            comparison = p.comparison,
            comparisonHint = p.comparisonHint,
            onCancelPendingSend = p.onCancelPendingSend,
            fontSizeMultiplier = p.fontSizeMultiplier,
            modifier = p.modifier,
        ) },
        topBar = { p -> ChatFloatingTopBar(
            viewModel = viewModel,
            uiState = p.uiState,
            onLoadPreset = p.onLoadPreset,
            onSavePreset = p.onSavePreset,
            onRename = p.onRename,
            contextRingDialogs = p.contextRingDialogs,
            modifier = p.modifier,
            onOpenDrawer = p.onOpenDrawer,
            onShowAllMedia = p.onShowAllMedia,
            onOpenPromptsLibrary = p.onOpenPromptsLibrary,
            widePane = p.widePane,
            railMode = p.railMode,
            onNewChat = p.onNewChat,
        ) },
    )
    ChatScreenContent(
        actions = actions,
        uiStateFlow = viewModel.uiState,
        attachedFilesFlow = viewModel.attachedFiles,
        chatPreferences = viewModel.chatPreferences,
        promptLibraryRevisionFlow = viewModel.promptLibraryRevision,
        toolPartStatusesFlow = viewModel.toolPartStatuses,
        pdfPasswordPromptsFlow = viewModel.pdfPasswordPrompts,
        compareSyncScroll = viewModel.compareSyncScroll,
        mermaidRenderCache = viewModel.mermaidRenderCache,
        parsedMarkdownCache = viewModel.parsedMarkdownCache,
        codeRunHost = viewModel.codeRunHost,
        updateVersion = updateBanner?.version,
        onAcknowledgeUpdate = { updateBanner?.let(updateBannerViewModel::acknowledge) },
        snackbarHostState = snackbarHostState,
        mcpSignIn = mcpSignIn,
        slots = slots,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        modifier = modifier,
        chatProjectName = chatProjectName,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
        onNavigateToPromptsLibrary = onNavigateToPromptsLibrary,
        onShowAllMedia = onShowAllMedia,
        onAttachFromServer = onAttachFromServer,
        onOpenWhatsNew = onOpenWhatsNew,
        onOpenMemories = onOpenMemories,
    )
}

/**
 * [ChatScreen] with no conversation id. When a conversation starts streaming, [onConversationStart]
 * gets its id so the caller can navigate to the full chat route. [initialAgentId], when non-null,
 * pre-selects that agent (set when starting from an agent detail/card); [initialEndpoint] and
 * [initialModel] pre-select a concrete model (home-screen shortcut), and [initialSpec] the spec it
 * was recorded from.
 */
@Composable
fun NewChatScreen(
    onConversationStart: (conversationId: String, isTemporary: Boolean) -> Unit,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    modifier: Modifier = Modifier,
    initialAgentId: String? = null,
    initialEndpoint: String? = null,
    initialModel: String? = null,
    initialSpec: String? = null,
    chatProjectId: String? = null,
    chatProjectName: String? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
    onAttachFromServer: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenMemories: (() -> Unit)? = null,
) {
    ChatScreen(
        modifier = modifier,
        initialAgentId = initialAgentId,
        initialEndpoint = initialEndpoint,
        initialModel = initialModel,
        initialSpec = initialSpec,
        chatProjectId = chatProjectId,
        chatProjectName = chatProjectName,
        onConversationStart = onConversationStart,
        onOpenDrawer = onOpenDrawer,
        onNewChat = onNewChat,
        onNavigateToPromptsLibrary = onNavigateToPromptsLibrary,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onAttachFromServer = onAttachFromServer,
        onOpenWhatsNew = onOpenWhatsNew,
        onOpenMemories = onOpenMemories,
    )
}
