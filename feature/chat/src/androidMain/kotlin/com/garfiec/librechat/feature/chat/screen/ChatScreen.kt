package com.garfiec.librechat.feature.chat.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.model.response.pickerMimeTypes
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.PdfPasswordDialog
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.feature.chat.components.AskUserQuestionPanel
import com.garfiec.librechat.feature.chat.components.ChatComposer
import com.garfiec.librechat.feature.chat.components.ChatFloatingTopBar
import com.garfiec.librechat.feature.chat.components.ChatRoot
import com.garfiec.librechat.feature.chat.components.ToolApprovalPanel
import com.garfiec.librechat.feature.chat.components.UpdateAvailableBanner
import com.garfiec.librechat.feature.chat.components.UploadRoutingSheet
import com.garfiec.librechat.feature.chat.components.addToChatSelectionItem
import com.garfiec.librechat.feature.chat.components.rememberChatAttachmentActions
import com.garfiec.librechat.feature.chat.components.rememberChatOptionsSheetController
import com.garfiec.librechat.feature.chat.prompts.components.VariableInputDialog
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.UpdateBannerViewModel
import com.garfiec.librechat.feature.chat.viewmodel.asString
import com.garfiec.librechat.feature.chat.viewmodel.isEmptyLanding
import com.garfiec.librechat.feature.chat.viewmodel.neutralizeStreamingChurn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
// The Scaffold's content padding is deliberately unused: the thread draws under both bars (the
// floating top bar applies its own statusBarsPadding, the composer its own nav-bar padding) and the
// list reserves its insets from the measured bar heights instead. contentWindowInsets is still set
// so the snackbar clears the navigation bar.
@Suppress("LambdaParameterEventTrailing") // actual: the defaults live on the expect, which the rule can't see
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
actual fun ChatScreen(
    modifier: Modifier,
    conversationId: String?,
    isTemporaryRoute: Boolean,
    initialAgentId: String?,
    initialEndpoint: String?,
    initialModel: String?,
    onConversationStart: ((conversationId: String, isTemporary: Boolean) -> Unit)?,
    onNavigateToConversation: ((String) -> Unit)?,
    onOpenDrawer: (() -> Unit)?,
    onNavigateToPromptsLibrary: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    onShowAllMedia: (() -> Unit)?,
    onAttachFromServer: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val viewModel: ChatViewModel =
        koinViewModel {
            parametersOf(conversationId, initialAgentId, isTemporaryRoute, initialEndpoint, initialModel)
        }
    // Chrome-rate state; ChatContent collects at full rate. Never read a neutralized field here.
    val chromeFlow = remember(viewModel) {
        viewModel.uiState.map { it.neutralizeStreamingChurn() }.distinctUntilChanged()
    }
    val initialChrome = remember(viewModel) { viewModel.uiState.value.neutralizeStreamingChurn() }
    val uiState by chromeFlow.collectAsStateWithLifecycle(initialChrome)
    val updateBannerViewModel: UpdateBannerViewModel = koinViewModel()
    val updateBanner by updateBannerViewModel.pendingUpdate.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.chatPreferences.collectAsStateWithLifecycle()
    val promptLibraryRevision by viewModel.promptLibraryRevision.collectAsStateWithLifecycle()
    val showImageDescriptions = prefs.showImageDescriptions
    val dismissKeyboardOnSend = prefs.dismissKeyboardOnSend
    val chatLayoutStyle = prefs.chatLayoutStyle
    val showAvatars = prefs.showAvatars
    val showBubbles = prefs.showBubbles
    val useKatex = prefs.latexRenderer == LatexRenderer.KATEX
    val sttEngine = prefs.sttEngine
    val sttLanguage = prefs.sttLanguage
    val keyboardController = LocalSoftwareKeyboardController.current
    val fontSizeMultiplier = when (uiState.chatFontSize) {
        ChatFontSize.SMALL -> 0.85f
        ChatFontSize.MEDIUM -> 1.0f
        ChatFontSize.LARGE -> 1.2f
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showPresetPicker by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showSecondaryModelSheet by remember { mutableStateOf(false) }
    var activeComparisonTab by remember { mutableIntStateOf(0) }

    // Header/composer model labels (agent name vs model name, with the "never a raw
    // model string under agents" rule). Shared with iOS via rememberChatModelLabel.
    val (agentName, displayModel) = rememberChatModelLabel(
        selectedEndpoint = uiState.selectedEndpoint,
        selectedModel = uiState.selectedModel,
        agents = uiState.agents,
    )

    val onStartRecordingWithPermission = rememberChatStartRecording(
        viewModel = viewModel,
        sttEngine = sttEngine,
        sttLanguage = sttLanguage,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
    )

    // Options sheet ("+" menu, model selector/parameters as pages). Opened from the composer's "+"
    // and the pull-up, hence the controller. Independent of uiState.showModelSheet (the standalone
    // selector), which keeps dismissing straight to the chat.
    val optionsController = rememberChatOptionsSheetController()
    // On comparison tab 1 the composer edits the secondary model, so the selector page and label
    // both follow the active tab.
    val isSecondaryTab = uiState.comparisonState.isEnabled && activeComparisonTab == 1
    val effectiveSelectedModelDisplay = if (isSecondaryTab) {
        viewModel.getSecondaryModelDisplayName()
            ?: uiState.comparisonState.secondaryModel
            ?: displayModel
    } else {
        displayModel
    }
    // One launcher set, registered here and shared by both the composer "+" sheet (ChatInput) and
    // the pull-up sheet: a launcher stays usable from descendant compositions while the one that
    // registered it (this screen) is alive, so a second registration would be redundant.
    // Narrow the file picker to what this endpoint's `supportedMimeTypes` allows (web parity:
    // useUploadOptions). Recomputed only when the config or endpoint changes — the translation
    // compiles the server's regexes.
    // The text route is validated against `fileConfig.text`, not against this allowlist, so the
    // picker has to admit what it can extract too — otherwise a narrowed endpoint allowlist makes
    // routing-to-text unreachable, with no file even pickable to route.
    val filePickerMimeTypes = remember(
        uiState.fileUploadConfig,
        uiState.selectedEndpoint,
        uiState.isFileContextAvailable,
        uiState.gates.backendVersion,
    ) {
        uiState.fileUploadConfig?.pickerMimeTypes(
            endpoint = uiState.selectedEndpoint,
            includeTextRoute = uiState.isFileContextAvailable,
            serverVersion = uiState.gates.backendVersion,
        ).orEmpty()
    }
    val attachmentActions = rememberChatAttachmentActions(
        onFilesSelect = viewModel::onFilesSelected,
        filePickerMimeTypes = filePickerMimeTypes,
    )

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
        snackbarHostState = snackbarHostState,
        onConversationStart = onConversationStart,
        onNavigateToConversation = onNavigateToConversation,
    )

    val sendBlockMessage = uiState.sendBlockReason?.asString()

    ChatRoot(
        inlineArtifactPrefs = prefs.inlineArtifactPrefs,
        mermaidRenderCache = viewModel.mermaidRenderCache,
        parsedMarkdownCache = viewModel.parsedMarkdownCache,
        subagentProgress = uiState.subagentProgress,
        conversationId = uiState.conversationId,
        subagentThreadsSupported = uiState.gates.subagentThreadsSupported,
        mediaPreview = uiState.mediaPreview,
        onOpenMedia = viewModel::openMedia,
        onCloseMedia = viewModel::closeMedia,
        onDownloadAttachment = viewModel::downloadFileBytes,
        promptLibraryRevision = promptLibraryRevision,
        onRefreshPrompts = viewModel::refreshPromptsIfStale,
        siteIcons = prefs.siteIcons,
        onSiteIconsChoice = viewModel::setShowSiteIcons,
        onSiteIconsPromptDismiss = viewModel::dismissSiteIconPrompt,
    ) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        // The floating top bar draws behind the status bar itself (statusBarsPadding), so the body
        // must extend under the status bar — only reserve the navigation-bar inset here (for the
        // snackbar); the composer handles its own nav-bar padding.
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
    ) { _ ->
        // The composer overlays the message list at the bottom; the list reserves a scrollable
        // bottom inset so its latest content rests above the bar. We measure the bar's actual
        // height (which grows as queued ghost rows stack above it) so streaming text never hides
        // behind the ghosts, while the list still scrolls *behind* the translucent overlay. The
        // floating top bar is measured the same way so the first message clears it while still
        // scrolling up behind its dimming scrim.
        var inputBarHeightPx by remember { mutableIntStateOf(0) }
        var topBarHeightPx by remember { mutableIntStateOf(0) }
        val bottomContentPadding = with(LocalDensity.current) {
            maxOf(160.dp, inputBarHeightPx.toDp() + 16.dp)
        }
        // Floor the top inset at status bar + one chip row so content clears the bar on the first
        // frame (before onSizeChanged reports the real height), avoiding a content-under-bar flash
        // on each screen entry. Mirrors the bottomContentPadding floor.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topContentPadding = with(LocalDensity.current) {
            maxOf(statusBarTop + 56.dp, topBarHeightPx.toDp())
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Pull-up tools sheet; see ChatPullUpSheetState.
            val pullUp = rememberChatPullUpSheetState()
            Box(Modifier.fillMaxSize().glassBackdropSource(pullUp.backdrop.takeIf { pullUp.visible })) {
                Column(
                    // "Add to chat" on the selection toolbar (v0.8.7 quotes), contributed from above
                    // every message's SelectionContainer — which is where foundation collects a
                    // menu's components from. Gated: a pre-0.8.7 server ignores the request field and
                    // would silently drop the excerpts.
                    modifier = Modifier
                        .fillMaxSize()
                        .addToChatSelectionItem(
                            enabled = uiState.quoteCaptureAvailable,
                            onAddToChat = viewModel::addPendingQuote,
                        ),
                ) {
                    ChatContent(
                        listPullUpModifier = pullUp.listModifier,
                        pullUpModifier = pullUp.landingModifier,
                        viewModel = viewModel,
                        agentName = agentName,
                        displayModel = displayModel,
                        fontSizeMultiplier = fontSizeMultiplier,
                        showImageDescriptions = showImageDescriptions,
                        chatLayoutStyle = chatLayoutStyle,
                        showAvatars = showAvatars,
                        showBubbles = showBubbles,
                        useKatex = useKatex,
                        bottomContentPadding = bottomContentPadding,
                        topContentPadding = topContentPadding,
                        onShowSecondaryModelSheet = { showSecondaryModelSheet = true },
                        onComparisonTabChange = { activeComparisonTab = it },
                    )
                }

                // ChatInput overlays at the bottom so gradient shows content behind
                val isAnyStreaming = uiState.isStreaming ||
                    uiState.comparisonState.primaryIsStreaming ||
                    uiState.comparisonState.secondaryIsStreaming
                // A question pause takes the composer's place until it resolves (see
                // AskUserQuestionPanel); a tool-approval pause docks above it (see ToolApprovalPanel).
                // Everything here is measured together so the list's bottom inset clears it.
                val askPause = uiState.renderablePendingAction?.takeIf { it.isAskUserQuestion }
                val toolPause = uiState.renderablePendingAction?.takeIf { it.isToolApproval }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { inputBarHeightPx = it.height },
                ) {
                    Column {
                        if (uiState.isEmptyLanding()) {
                            updateBanner?.let { update ->
                                UpdateAvailableBanner(
                                    version = update.version,
                                    onOpen = {
                                        updateBannerViewModel.acknowledge(update)
                                        onOpenWhatsNew()
                                    },
                                    onDismiss = { updateBannerViewModel.acknowledge(update) },
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                )
                            }
                        }
                        toolPause?.let { pause ->
                            ToolApprovalPanel(
                                pendingAction = pause,
                                isResolving = uiState.isResolvingPendingAction,
                                drafts = uiState.toolDecisionDrafts,
                                activeCallId = uiState.toolActiveCallId,
                                collapsed = uiState.toolPanelCollapsed,
                                onDraftChange = viewModel::updateToolDecisionDraft,
                                onPickDecision = viewModel::pickToolDecision,
                                onSelectCall = viewModel::selectToolCall,
                                onCollapsedChange = viewModel::setToolPanelCollapsed,
                                onCancelAutoAdvance = viewModel::cancelPausePanelAutoAdvance,
                                onSubmit = viewModel::resolveToolApproval,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            )
                        }
                        if (askPause != null) {
                            AskUserQuestionPanel(
                                pendingAction = askPause,
                                isResolving = uiState.isResolvingPendingAction,
                                drafts = uiState.askAnswerDrafts,
                                activeQuestionId = uiState.askActiveQuestionId,
                                collapsed = uiState.askPanelCollapsed,
                                onDraftChange = viewModel::updateAskAnswerDraft,
                                onPickOption = viewModel::pickAskOption,
                                onSelectQuestion = viewModel::selectAskQuestion,
                                onCollapsedChange = viewModel::setAskPanelCollapsed,
                                onCancelAutoAdvance = viewModel::cancelPausePanelAutoAdvance,
                                onSubmitAnswer = viewModel::answerPendingQuestion,
                                onSubmitAnswers = viewModel::answerPendingQuestions,
                                // The composer's Stop is hidden with it, and the run stays live across the pause.
                                onStop = viewModel::stopGeneration.takeIf { isAnyStreaming },
                                resumeFailed = uiState.pendingActionResumeFailed,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        } else {
                            ChatComposer(
                                inputText = uiState.inputText,
                                isStreaming = isAnyStreaming,
                                onInputChange = viewModel::onInputChanged,
                                onSend = {
                                    viewModel.sendMessage()
                                    if (dismissKeyboardOnSend) {
                                        keyboardController?.hide()
                                    }
                                },
                                onStop = viewModel::stopGeneration,
                                onOpenTools = { optionsController.open() },
                                // The mid-stream send button: the ViewModel resolves steer-vs-queue from the
                                // user's preference and what this run can actually take, so the composer never
                                // has to. `onQueue` stays the picker's explicit "add to queue".
                                onDuringRunSend = {
                                    viewModel.sendDuringRun()
                                    if (dismissKeyboardOnSend) {
                                        keyboardController?.hide()
                                    }
                                },
                                onQueue = {
                                    viewModel.queueMessage()
                                    if (dismissKeyboardOnSend) {
                                        keyboardController?.hide()
                                    }
                                },
                                canQueue = uiState.canQueueFollowUp,
                                // Explicit "steer this one", from the during-run picker or the send button when
                                // steering is the standing default.
                                onSteer = {
                                    viewModel.steerMessage()
                                    if (dismissKeyboardOnSend) {
                                        keyboardController?.hide()
                                    }
                                },
                                canSteer = uiState.canSteerNow,
                                duringRunAction = uiState.effectiveDuringRunAction,
                                duringRunSendTarget = uiState.duringRunSendTarget,
                                pendingSteers = uiState.pendingSteers,
                                pendingQuotes = uiState.pendingQuotes,
                                onRemoveQuote = viewModel::removePendingQuote,
                                onCancelSteer = viewModel::cancelSteer,
                                onSetDuringRunAction = viewModel::setDuringRunAction,
                                attachedFiles = attachedFiles,
                                onRemoveFile = viewModel::removeFile,
                                onPasteFiles = viewModel::onFilesSelected,
                                promptSuggestions = uiState.availablePrompts,
                                onSlashCommandSelect = viewModel::handleSlashCommand,
                                isRecording = uiState.isRecording,
                                isTranscribing = uiState.isTranscribing,
                                onStartRecording = onStartRecordingWithPermission,
                                onStopRecording = viewModel::stopRecording,
                                enabledTools = uiState.effectiveEnabledTools,
                                pinnedToolKeys = uiState.pinnedToolChips,
                                onToggleTool = viewModel::toggleTool,
                                mcpServers = uiState.mcpServers,
                                selectedMcpServerNames = uiState.selectedMcpServerNames,
                                selectedModelDisplay = effectiveSelectedModelDisplay,
                                isCodeInterpreterAvailable = uiState.isCodeInterpreterAvailable,
                                gates = uiState.chatInputGates,
                                contextGauge = uiState.contextGaugeDetails,
                                contextUsageEnabled = uiState.contextUsageEnabled,
                                onCompact = viewModel::compactConversation.takeIf { uiState.canCompactNow },
                                onSnoozeCompact = viewModel::snoozeCompactNudge,
                                compactSuggested = uiState.compactNudgeOnToolsButton,
                                contextBarPlacement = uiState.contextBarPlacement,
                                // After a Stop/error pause, the queue waits for an explicit nudge.
                                queuedPausedCount = uiState.pausedQueueCount,
                                onSendQueuedMessages = viewModel::sendQueuedNow,
                                isEditingQueued = uiState.isEditingQueued,
                                onCommitEdit = viewModel::commitQueuedEdit,
                                onCancelEdit = viewModel::cancelQueuedEdit,
                                isAwaitingUploadSend = uiState.isAwaitingUploadSend,
                                arePicksUnsettled = uiState.arePicksUnsettled,
                                onCancelPendingSend = viewModel::cancelPendingUploadSend,
                                queuedMessages = uiState.messageQueue,
                                onEditQueuedMessage = viewModel::editQueued,
                                onCancelQueuedMessage = viewModel::cancelQueued,
                                onReorderQueuedMessages = viewModel::reorderQueue,
                                fontSizeMultiplier = fontSizeMultiplier,
                            )
                        }
                    }
                }

                // Floating top bar overlays the message list (drawn last so it sits above content),
                // measured so ChatContent can reserve a matching scrollable top inset.
                ChatFloatingTopBar(
                    uiState = uiState,
                    viewModel = viewModel,
                    onLoadPreset = { showPresetPicker = true },
                    onSavePreset = { showSavePresetDialog = true },
                    onRename = viewModel::showRenameDialog,
                    onOpenDrawer = onOpenDrawer,
                    onShowAllMedia = onShowAllMedia,
                    onOpenPromptsLibrary = onNavigateToPromptsLibrary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topBarHeightPx = it.height },
                )
            }

            ChatPullUpSheetOverlay(
                state = pullUp,
                topContentPadding = topContentPadding,
                uiState = uiState,
                viewModel = viewModel,
                optionsController = optionsController,
                selectedModelDisplay = effectiveSelectedModelDisplay,
                onAttachFiles = attachmentActions.onAttachFiles,
                onTakePhoto = attachmentActions.onTakePhoto,
                onPickPhotos = attachmentActions.onPickPhotos,
                onAttachFromServer = onAttachFromServer,
            )

            val pendingRouting = uiState.composer.pendingUploadRouting
            if (pendingRouting != null) {
                UploadRoutingSheet(
                    files = pendingRouting.files,
                    onRouteChange = viewModel::setPendingUploadRoute,
                    onApplyToAll = viewModel::setAllPendingUploadRoutes,
                    onConfirm = viewModel::confirmPendingUploadRouting,
                    onDismiss = viewModel::cancelPendingUploadRouting,
                )
            }
        }
    }

    ChatOptionsSheetHost(
        controller = optionsController,
        uiState = uiState,
        viewModel = viewModel,
        isSecondaryTab = isSecondaryTab,
        selectedModelDisplay = effectiveSelectedModelDisplay,
        onAttachFiles = attachmentActions.onAttachFiles,
        onTakePhoto = attachmentActions.onTakePhoto,
        onPickPhotos = attachmentActions.onPickPhotos,
        onAttachFromServer = onAttachFromServer,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onShowSavePresetDialog = { showSavePresetDialog = true },
    )

    ChatScreenDialogs(
        uiState = uiState,
        viewModel = viewModel,
        sendBlockMessage = sendBlockMessage,
        showPresetPicker = showPresetPicker,
        showSavePresetDialog = showSavePresetDialog,
        showSecondaryModelSheet = showSecondaryModelSheet,
        onSetShowPresetPicker = { showPresetPicker = it },
        onSetShowSavePresetDialog = { showSavePresetDialog = it },
        onSetShowSecondaryModelSheet = { showSecondaryModelSheet = it },
        onNavigateToProviderKeys = onNavigateToProviderKeys,
    )

    LaunchedEffect(Unit) { viewModel.consumePendingPromptInsertion() }

    val pdfPasswordPrompts by viewModel.pdfPasswordPrompts.collectAsStateWithLifecycle()
    pdfPasswordPrompts.firstOrNull()?.let { prompt ->
        // Keyed so a re-prompt after a wrong password starts from an empty field.
        key(prompt) {
            PdfPasswordDialog(
                filename = prompt.file.name,
                incorrectPassword = prompt.incorrectPassword,
                onSubmit = viewModel::submitPdfPassword,
                onDismiss = viewModel::dismissPdfPassword,
            )
        }
    }

    uiState.pendingVariablePrompt?.let { pending ->
        VariableInputDialog(
            promptTemplate = pending.template,
            variables = pending.variables,
            onInsert = viewModel::confirmVariablePrompt,
            onDismiss = viewModel::dismissVariablePrompt,
        )
    }
    }
}
