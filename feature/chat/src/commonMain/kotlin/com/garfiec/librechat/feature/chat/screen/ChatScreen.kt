package com.garfiec.librechat.feature.chat.screen

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.garfiec.librechat.feature.chat.components.ChatOptionsPage
import com.garfiec.librechat.feature.chat.components.ChatRoot
import com.garfiec.librechat.feature.chat.components.CodeWorkspaceComposer
import com.garfiec.librechat.feature.chat.components.ComposerChrome
import com.garfiec.librechat.feature.chat.components.ComposerComparison
import com.garfiec.librechat.feature.chat.components.ComposerComparisonHint
import com.garfiec.librechat.feature.chat.components.LandingStarters
import com.garfiec.librechat.feature.chat.components.LocalCodeRunHost
import com.garfiec.librechat.feature.chat.components.LocalCodeWorkspaceComposer
import com.garfiec.librechat.feature.chat.components.LocalOpenFileActions
import com.garfiec.librechat.feature.chat.components.LocalOpenPdf
import com.garfiec.librechat.feature.chat.components.LocalPullRequestComposer
import com.garfiec.librechat.feature.chat.components.LocalThreadScrollHandle
import com.garfiec.librechat.feature.chat.components.PullRequestComposer
import com.garfiec.librechat.feature.chat.components.ThinkingSheet
import com.garfiec.librechat.feature.chat.components.ToolApprovalPanel
import com.garfiec.librechat.feature.chat.components.ToolContentHost
import com.garfiec.librechat.feature.chat.components.UpdateAvailableBanner
import com.garfiec.librechat.feature.chat.components.UploadRoutingSheet
import com.garfiec.librechat.feature.chat.components.addToChatSelectionItem
import com.garfiec.librechat.feature.chat.components.mcpapp.rememberLegacyUiResources
import com.garfiec.librechat.feature.chat.components.mcpapp.rememberMcpApps
import com.garfiec.librechat.feature.chat.components.rememberChatAttachmentActions
import com.garfiec.librechat.feature.chat.components.rememberChatOptionsSheetController
import com.garfiec.librechat.feature.chat.components.rememberDraftAdditions
import com.garfiec.librechat.feature.chat.components.rememberMcpSignIn
import com.garfiec.librechat.feature.chat.components.rememberThreadScrollHandle
import com.garfiec.librechat.feature.chat.components.veil.COMPOSER_SWELL
import com.garfiec.librechat.feature.chat.components.veil.LocalComposerFlare
import com.garfiec.librechat.feature.chat.components.veil.LocalModeVeil
import com.garfiec.librechat.feature.chat.components.veil.VeilReaction
import com.garfiec.librechat.feature.chat.components.veil.rememberModeVeilModifier
import com.garfiec.librechat.feature.chat.components.veil.rememberModeVeilState
import com.garfiec.librechat.feature.chat.components.veil.rememberVeilTarget
import com.garfiec.librechat.feature.chat.prompts.components.VariableInputDialog
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_cancel_sign_in_failed
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_failed
import com.garfiec.librechat.feature.chat.util.DraftItemTap
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.util.draftItemsOf
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.McpAppsViewModel
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInViewModel
import com.garfiec.librechat.feature.chat.viewmodel.UpdateBannerViewModel
import com.garfiec.librechat.feature.chat.viewmodel.asString
import com.garfiec.librechat.feature.chat.viewmodel.conversationUiResources
import com.garfiec.librechat.feature.chat.viewmodel.isEmptyLanding
import com.garfiec.librechat.feature.chat.viewmodel.neutralizeStreamingChurn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The chat screen, shared by Android and iOS; platform differences sit behind the expects it calls. */
@OptIn(ExperimentalMaterial3Api::class)
// The Scaffold's content padding is deliberately unused: the thread draws under both bars (the
// floating top bar applies its own statusBarsPadding, the composer its own nav-bar padding) and the
// list reserves its insets from the measured bar heights instead. contentWindowInsets is still set
// so the snackbar clears the navigation bar.
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
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
    // Chrome-rate state; ChatContent collects at full rate. Never read a neutralized field here.
    val chromeFlow = remember(viewModel) {
        viewModel.uiState.map { it.neutralizeStreamingChurn() }.distinctUntilChanged()
    }
    val initialChrome = remember(viewModel) { viewModel.uiState.value.neutralizeStreamingChurn() }
    val uiState by chromeFlow.collectAsStateWithLifecycle(initialChrome)
    val updateBannerViewModel: UpdateBannerViewModel = koinViewModel()
    val updateBanner by updateBannerViewModel.pendingUpdate.collectAsStateWithLifecycle()
    val attachedFiles = viewModel.attachedFiles.collectAsStateWithLifecycle()
    // The files without their upload progress: a progress tick changes nothing here, so nothing below
    // re-maps or recomposes for it. The rings read it as they draw (draftUploadProgress).
    val draftFiles by remember {
        derivedStateOf(structuralEqualityPolicy()) { attachedFiles.value.map { it.copy(uploadProgress = null) } }
    }
    val draftUploadProgress = remember { DraftUploadProgress { attachedFiles.value } }
    // Mapped here, not in the uiState combine: the files are their own flow.
    val draftItems = remember(draftFiles, uiState.pendingManualSkills, uiState.pendingQuotes, uiState.serverUrl) {
        draftItemsOf(draftFiles, uiState.pendingManualSkills, uiState.pendingQuotes, uiState.serverUrl)
    }
    // Above the strip, which leaves composition while the draft is empty, so the first add pops too.
    val draftAdditions = rememberDraftAdditions(draftItems)
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
    var showThinkingSheet by rememberSaveable { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showSecondaryModelSheet by remember { mutableStateOf(false) }
    var activeComparisonTab by remember { mutableIntStateOf(0) }
    val compareSetupPick = rememberCompareSetupPick(uiState.comparisonState.isSettingUp)
    val compareSetupActions = remember(viewModel) { viewModel.compareSetupActions() }
    // A wide setup's "Browse all models" opens the setup sheet over the panes; it closes with setup.
    var showCompareBrowseAll by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.comparisonState.isSettingUp) {
        if (!uiState.comparisonState.isSettingUp) showCompareBrowseAll = false
    }
    // The chat's own width (not the window's: a tablet sidebar takes some) decides whether Compare
    // shows its panes side by side, and with it whether setup is a sheet or the empty second pane.
    // Until onSizeChanged reports for this window size, the window's width stands in: a fold,
    // unfold or rotation then lays out right from its first frame instead of flashing the other
    // layout. Keyed on the window width so a measurement from the previous posture is never reused.
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    var chatWidthPx by remember(windowWidthPx) { mutableIntStateOf(0) }
    val effectiveChatWidthPx = if (chatWidthPx > 0) chatWidthPx else windowWidthPx
    val comparisonWide = with(LocalDensity.current) { effectiveChatWidthPx.toDp() } >= ComparisonWideBreakpoint
    val setupInPanes = uiState.comparisonState.isSettingUp && uiState.showsComparisonPanes(comparisonWide)

    // Header/composer model labels (agent name vs model name, with the "never a raw
    // model string under agents" rule).
    val (agentName, displayModel) = rememberChatModelLabel(
        selectedEndpoint = uiState.selectedEndpoint,
        selectedModel = uiState.selectedModel,
        agents = uiState.agents,
        selectedSpec = uiState.selectedSpec,
        modelSpecs = uiState.modelSpecs,
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
    val thinkingChip = uiState.thinkingChip
    // Nothing here cancels an OAuth flow: only a connecting chip's "Cancel sign-in" does.
    val mcpSignInViewModel: McpSignInViewModel = koinViewModel()
    val mcpSignIn = rememberMcpSignIn(mcpSignInViewModel, snackbarHostState, onSignInEnd = viewModel::onMcpSignInEnded)
    val mcpSignInFailed = stringResource(Res.string.mcp_sign_in_failed)
    val mcpCancelSignInFailed = stringResource(Res.string.mcp_cancel_sign_in_failed)
    val showNotice: (String) -> Unit = { message -> coroutineScope.launch { snackbarHostState.showNotice(message) } }
    val startMcpSignIn: (String) -> Unit = { serverName ->
        viewModel.signInMcpServer(
            serverName = serverName,
            openSignIn = mcpSignIn.signIn,
            onFailed = { showNotice(mcpSignInFailed) },
        )
    }
    // A refused cancel leaves the chip reading connecting, so only the snackbar says it didn't land.
    val cancelMcpSignIn: (String) -> Unit = { serverName ->
        viewModel.cancelMcpSignIn(serverName, onFailed = { showNotice(mcpCancelSignInFailed) })
    }
    val onSettingChipTap = rememberSettingChipTap(
        chips = uiState.settingChips,
        snackbarHostState = snackbarHostState,
        onToggle = viewModel::toggleSettingChip,
        onShowInSheet = { optionsController.open(ChatOptionsPage.Options, it) },
        onOpenThinking = { showThinkingSheet = true },
        onSignIn = startMcpSignIn,
    )
    // On a phone's comparison tab 1 the composer edits the secondary model, so the selector page and
    // label both follow the active tab. Side by side there is no current tab (the remembered index
    // can be stale from before an unfold), so it is never the secondary one.
    val isSecondaryTab = uiState.comparisonState.isEnabled && activeComparisonTab == 1 && !comparisonWide
    val secondaryModelLabel = if (uiState.comparisonState.isEnabled) {
        comparisonLaneLabel(
            uiState.comparisonState.secondaryEndpoint,
            uiState.comparisonState.secondaryModel,
            uiState.agents,
            uiState.laneBSpec,
            uiState.modelSpecs,
        )
    } else {
        null
    }
    val composerComparisonHint = when {
        uiState.comparisonState.isSettingUp -> ComposerComparisonHint.SETTING_UP
        uiState.comparisonState.isEnabled -> ComposerComparisonHint.BOTH
        else -> ComposerComparisonHint.NONE
    }
    // Both models, as the A·B badge pair inside the field, whose menu opens either model's selector.
    // Compact on a phone, where the tab bar already names both models, and dots once a draft is typed.
    // Lane A without a model reads "Select model", never B's name.
    val primaryLaneLabel = comparisonLaneLabel(
        uiState.selectedEndpoint,
        uiState.selectedModel,
        uiState.agents,
        uiState.selectedSpec,
        uiState.modelSpecs,
    )
    val composerComparison = remember(
        secondaryModelLabel,
        primaryLaneLabel,
        comparisonWide,
        viewModel,
    ) {
        secondaryModelLabel?.let { secondaryLabel ->
            ComposerComparison(
                primaryLabel = primaryLaneLabel,
                secondaryLabel = secondaryLabel,
                onChangePrimary = viewModel::openModelSheet,
                onChangeSecondary = { showSecondaryModelSheet = true },
                compact = !comparisonWide,
            )
        }
    }
    val effectiveSelectedModelDisplay = if (isSecondaryTab) {
        secondaryModelLabel ?: displayModel
    } else {
        displayModel
    }
    // The picker's skill rows, narrowed for this chat. Recomputed only when an input changes, not
    // on every chrome emission.
    val pickerSkills = remember(
        uiState.skillCatalog,
        uiState.canPickSkills,
        uiState.activeSpecSkills,
        uiState.selectedEndpoint,
        uiState.selectedModel,
        uiState.agents,
        uiState.pendingManualSkills,
    ) { uiState.pickerSkills }
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
        uiState.fileConfigEndpoint,
        uiState.isFileContextAvailable,
        uiState.gates.backendVersion,
    ) {
        uiState.fileUploadConfig?.pickerMimeTypes(
            endpoint = uiState.fileConfigEndpoint,
            includeTextRoute = uiState.isFileContextAvailable,
            serverVersion = uiState.gates.backendVersion,
        ).orEmpty()
    }
    val attachmentActions = rememberChatAttachmentActions(
        onFilesSelect = viewModel::onFilesSelected,
        filePickerMimeTypes = filePickerMimeTypes,
        onCodeFilesSelect = viewModel::onCodeFilesSelected,
    )
    val onAttachToCode = attachmentActions.onAttachToCode.takeIf { uiState.codeUploadOffered }

    DraftUndoSnackbarEffect(
        undo = uiState.pendingDraftUndo,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoDraftRemoval,
        onDrop = viewModel::dropDraftUndo,
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
        onConversationStart = onConversationStart,
        onNavigateToConversation = onNavigateToConversation,
        mcpSignInEnding = { mcpSignInViewModel.endsOnReturn },
    )

    val sendBlockMessage = uiState.sendBlockReason?.asString()

    // MCP Apps: the panel and its approval dialogs live here; rows read the result.
    val mcpAppsViewModel: McpAppsViewModel = koinViewModel()
    val mcpApps = rememberMcpApps(mcpAppsViewModel, snackbarHostState, onSendMessage = viewModel::sendAppMessage)
    // Legacy MCP-UI resources the reply's markers name, from every shown message and the live reply.
    val conversationUiResources by remember(viewModel) { conversationUiResources(viewModel.uiState) }
        .collectAsStateWithLifecycle(emptyMap())
    val legacyUiResources = rememberLegacyUiResources(mcpAppsViewModel, conversationUiResources)

    // Collected apart from uiState: it changes only when a preview's fetch starts, lands or fails.
    val toolPartStatuses by viewModel.toolPartStatuses.collectAsStateWithLifecycle()
    val toolContent = remember(toolPartStatuses, viewModel) {
        ToolContentHost(
            statuses = toolPartStatuses,
            onRequest = viewModel::requestToolPart,
            onRetain = viewModel::retainToolPart,
            onRelease = viewModel::releaseToolPart,
        )
    }

    ChatRoot(
        inlineArtifactPrefs = prefs.inlineArtifactPrefs,
        mermaidRenderCache = viewModel.mermaidRenderCache,
        parsedMarkdownCache = viewModel.parsedMarkdownCache,
        subagentProgress = uiState.subagentProgress,
        ptcTraces = uiState.ptcTraces,
        previewOverrides = uiState.previewOverrides,
        conversationId = uiState.conversationId,
        subagentThreadsSupported = uiState.gates.subagentThreadsSupported,
        mediaPreview = uiState.mediaPreview,
        onOpenMedia = viewModel::openMedia,
        onCloseMedia = viewModel::closeMedia,
        onDownloadAttachment = viewModel::downloadFileBytes,
        onFileNotice = showNotice,
        promptLibraryRevision = promptLibraryRevision,
        onRefreshPrompts = viewModel::refreshPromptsIfStale,
        siteIcons = prefs.siteIcons,
        onSiteIconsChoice = viewModel::setShowSiteIcons,
        onSiteIconsPromptDismiss = viewModel::dismissSiteIconPrompt,
        onOpenMemories = onOpenMemories?.takeIf { uiState.gates.memoriesPageEnabled },
        mcpSignIn = mcpSignIn,
        mcpApps = mcpApps,
        legacyUiResources = legacyUiResources,
        thinkingOpenByDefault = prefs.thinkingOpenByDefault,
        collapseLongUserMessages = prefs.collapseLongUserMessages,
        toolContent = toolContent,
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
        // Side-by-side comparison panes are cards that stop above the composer, not lists that
        // scroll under it, so they sit on the measured bar itself. The list floor above would
        // leave a band of empty space, widest with the keyboard up (the bar loses its nav-bar inset).
        val paneBottomInset = with(LocalDensity.current) {
            if (inputBarHeightPx > 0) inputBarHeightPx.toDp() + 8.dp else bottomContentPadding
        }
        // Floor the top inset at status bar + one chip row so content clears the bar on the first
        // frame (before onSizeChanged reports the real height), avoiding a content-under-bar flash
        // on each screen entry. Mirrors the bottomContentPadding floor.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val (fallbackToggleEnd, fallbackToggleBottom) = with(LocalDensity.current) {
            NativeToggleFromEnd.toPx() to NativeToggleFromBottom.toPx()
        }
        val topContentPadding = with(LocalDensity.current) {
            maxOf(statusBarTop + 56.dp, topBarHeightPx.toDp())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { chatWidthPx = it.width },
        ) {
            val pullUp = rememberChatPullUpSheetState()
            // Temporary chat's transition: the band and (Material) the retone draw over and behind
            // the landing, the composer and the top bar, which all react to it through LocalModeVeil.
            val modeVeil = rememberModeVeilState(uiState.isTemporaryChat)
            // Created here, above both the thread and the composer: the composer's chrome hands its
            // vertical drags to whichever list ChatContent binds.
            val threadScroll = rememberThreadScrollHandle()
            CompositionLocalProvider(
                LocalModeVeil provides modeVeil,
                LocalThreadScrollHandle provides threadScroll,
                // Run Code on reply fences, behind RUN_CODE.USE (web `MarkdownComponents` `canRunCode`).
                LocalCodeRunHost provides viewModel.codeRunHost.takeIf { uiState.runCodeEnabled },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdropSource(pullUp.backdrop.takeIf { pullUp.visible })
                        .then(rememberModeVeilModifier(modeVeil)),
                ) {
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
                            fontSizeMultiplier = fontSizeMultiplier,
                            showImageDescriptions = showImageDescriptions,
                            chatLayoutStyle = chatLayoutStyle,
                            showAvatars = showAvatars,
                            showBubbles = showBubbles,
                            useKatex = useKatex,
                            bottomContentPadding = bottomContentPadding,
                            paneBottomInset = paneBottomInset,
                            topContentPadding = topContentPadding,
                            onShowSecondaryModelSheet = { showSecondaryModelSheet = true },
                            onComparisonTabChange = { activeComparisonTab = it },
                            activeComparisonTab = activeComparisonTab,
                            projectName = chatProjectName,
                            comparisonWide = comparisonWide,
                            compareSetupPick = compareSetupPick,
                            compareSetupActions = compareSetupActions,
                            primaryLaneLabel = primaryLaneLabel,
                            onBrowseAllModels = { showCompareBrowseAll = true },
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
                    val openPdf = LocalOpenPdf.current
                    val openFileActions = LocalOpenFileActions.current
                    val onDraftItemTap: (DraftItemTap) -> Unit = remember(viewModel, openPdf, openFileActions) {
                        { tap ->
                            when (tap) {
                                is DraftItemTap.Retry -> viewModel.retryUpload(tap.file)
                                is DraftItemTap.ViewImage -> viewModel.openDraftMedia(tap.preview)
                                is DraftItemTap.OpenPdf -> openPdf(tap.fileId, tap.filename)
                                is DraftItemTap.OpenFile -> openFileActions(tap.attachment)
                            }
                        }
                    }
                    val composerChrome = ComposerChrome(
                        pendingSteers = uiState.pendingSteers,
                        onCancelSteer = viewModel::cancelSteer,
                        onInterruptSteer = viewModel::armSteer,
                        queuedMessages = uiState.displayedMessageQueue,
                        onEditQueuedMessage = viewModel::editQueued,
                        onCancelQueuedMessage = viewModel::cancelQueued,
                        onReorderQueuedMessages = viewModel::reorderQueue,
                        draftItems = draftItems,
                        draftUploadProgress = draftUploadProgress,
                        draftAdditions = draftAdditions,
                        onRemoveDraftItem = viewModel::removeDraftItem,
                        onDraftItemTap = onDraftItemTap,
                        enabledTools = uiState.effectiveEnabledTools,
                        selectedMcpServerNames = uiState.effectiveMcpServerNames,
                        settingChips = uiState.settingChips,
                        onSettingChipTap = onSettingChipTap,
                        onCancelMcpSignIn = cancelMcpSignIn,
                        onStartMcpSignInOver = startMcpSignIn,
                        contextGauge = uiState.contextGaugeDetails,
                        contextUsageEnabled = uiState.contextUsageEnabled,
                        onCompact = viewModel::compactConversation.takeIf { uiState.canCompactNow },
                        onSnoozeCompact = viewModel::snoozeCompactNudge,
                        compactSuggested = uiState.compactNudgeOnToolsButton,
                        contextBarPlacement = uiState.contextBarPlacement,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // The panels size their caps from the height they are given: only what the
                            // floating top bar leaves visible. Before onSizeChanged, which measures content.
                            .padding(top = topContentPadding)
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
                            if (askPause == null && !uiState.showsComparisonPanes(comparisonWide)) {
                                LandingStarters(
                                    starters = uiState.landingStarters,
                                    visible = uiState.showsLandingStarters,
                                    onSend = viewModel::sendStarter,
                                    onEdit = viewModel::editStarter,
                                )
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
                                    // Weighted, so the composer below is measured first and the panel
                                    // gets only the height it leaves.
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
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
                                val composerVeil = rememberVeilTarget(
                                    isTemporary = uiState.isTemporaryChat,
                                    reaction = VeilReaction.NUDGE,
                                    swell = COMPOSER_SWELL,
                                )
                                Box(composerVeil.modifier) {
                                    val codeWorkspace = uiState.codeWorkspace
                                    val codeWorkspaceComposer = remember(codeWorkspace, viewModel) {
                                        CodeWorkspaceComposer(
                                            state = codeWorkspace,
                                            onOpenSheet = viewModel::openCodeWorkspaceSheet,
                                            onDismissSheet = viewModel::dismissCodeWorkspaceSheet,
                                            onSelectWorkspace = viewModel::selectCodeWorkspace,
                                            onSetCheckout = viewModel::setCodeCheckout,
                                            onSelectNoWorkspace = viewModel::selectNoCodeWorkspace,
                                            onRefresh = viewModel::refreshCodeWorkspaces,
                                            onSelectTransitionWorkspace = viewModel::selectTransitionCodeWorkspace,
                                            onSetTransitionCheckout = viewModel::setTransitionCodeCheckout,
                                            onConfirmTransition = viewModel::confirmCodeWorkspaceTransition,
                                            onDetach = viewModel::detachCodeWorkspace,
                                            onRetryReconcile = viewModel::retryCodeWorkspaceCheck,
                                            onSelectApprovalMode = viewModel::selectCodeApprovalMode,
                                        )
                                    }
                                    // The pull request row: narrow panes only (a wide one has the bar chip),
                                    // out of the way of a tool-approval pause, and folded to its glyph
                                    // while a run streams.
                                    val pullRequestState = uiState.pullRequest
                                    val toolPausePending = toolPause != null
                                    val pullRequestComposer = remember(
                                        pullRequestState,
                                        comparisonWide,
                                        toolPausePending,
                                        isAnyStreaming,
                                        viewModel,
                                    ) {
                                        val pr = pullRequestState.pullRequest
                                        val presentation = pullRequestState.presentation
                                        if (comparisonWide || toolPausePending || pr == null || presentation == null) {
                                            null
                                        } else {
                                            PullRequestComposer(
                                                pullRequest = pr,
                                                presentation = presentation,
                                                expanded = pullRequestState.unseen && !isAnyStreaming,
                                                onOpen = viewModel::openPullRequestSheet,
                                                onSeen = viewModel::markPullRequestSeen,
                                            )
                                        }
                                    }

                                    fun send(action: () -> Unit): () -> Unit = {
                                        action()
                                        if (dismissKeyboardOnSend) keyboardController?.hide()
                                    }
                                    CompositionLocalProvider(
                                        LocalComposerFlare provides composerVeil.flare,
                                        LocalCodeWorkspaceComposer provides codeWorkspaceComposer,
                                        LocalPullRequestComposer provides pullRequestComposer,
                                    ) {
                                        ChatComposer(
                                            inputText = uiState.inputText,
                                            isStreaming = isAnyStreaming,
                                            onInputChange = viewModel::onInputChanged,
                                            onSend = send(viewModel::sendMessage),
                                            onStop = viewModel::stopGeneration,
                                            onOpenTools = { optionsController.open() },
                                            // The mid-stream send button: the ViewModel resolves steer-vs-queue from the
                                            // user's preference and what this run can actually take, so the composer never
                                            // has to. `onQueue` stays the picker's explicit "add to queue".
                                            onDuringRunSend = send(viewModel::sendDuringRun),
                                            onQueue = send(viewModel::queueMessage),
                                            canQueue = uiState.canQueueFollowUp,
                                            // Explicit "steer this one", from the during-run picker or the send button when
                                            // steering is the standing default.
                                            onSteer = send(viewModel::steerMessage),
                                            onInterrupt = send(viewModel::interruptMessage),
                                            canSteer = uiState.canSteerNow,
                                            // Built-in search can't be interrupted (provider-hosted tool), so the
                                            // menu says so instead of promising to stop it.
                                            builtInSearchOn = uiState.webSearchRoute == WebSearchRoute.BUILT_IN,
                                            duringRunAction = uiState.effectiveDuringRunAction,
                                            duringRunSendTarget = uiState.duringRunSendTarget,
                                            chrome = composerChrome,
                                            onSetDuringRunAction = viewModel::setDuringRunAction,
                                            onPasteFiles = viewModel::onFilesSelected,
                                            pasteScope = coroutineScope,
                                            promptSuggestions = uiState.availablePrompts,
                                            skillSuggestions = pickerSkills,
                                            isLoadingSkills = uiState.isLoadingSkills && uiState.canPickSkills,
                                            skillsLoadFailed = uiState.skillsLoadFailed && uiState.canPickSkills,
                                            canPickSkills = uiState.canPickSkills,
                                            onRetrySkills = viewModel::retrySkillCatalog,
                                            onPickerSelect = viewModel::selectPickerItem,
                                            isRecording = uiState.isRecording,
                                            isTranscribing = uiState.isTranscribing,
                                            onStartRecording = onStartRecordingWithPermission,
                                            onStopRecording = viewModel::stopRecording,
                                            selectedModelDisplay = effectiveSelectedModelDisplay,
                                            isCodeInterpreterAvailable = uiState.isCodeInterpreterAvailable,
                                            gates = uiState.chatInputGates,
                                            // After a Stop/error pause, the queue waits for an explicit nudge.
                                            queuedPausedCount = uiState.pausedQueueCount,
                                            queueHeldForRefusal = uiState.isQueueHeldForRefusal,
                                            // The band, not the flag, tints the composer: it turns violet as the band reaches it.
                                            isTemporaryChat = composerVeil.crossed,
                                            onSendQueuedMessages = viewModel::sendQueuedNow,
                                            isEditingQueued = uiState.isEditingQueued,
                                            onCommitEdit = viewModel::commitQueuedEdit,
                                            onCancelEdit = viewModel::cancelQueuedEdit,
                                            isAwaitingUploadSend = uiState.isAwaitingUploadSend,
                                            arePicksUnsettled = uiState.arePicksUnsettled,
                                            comparison = composerComparison,
                                            comparisonHint = composerComparisonHint,
                                            onCancelPendingSend = viewModel::cancelPendingUploadSend,
                                            fontSizeMultiplier = fontSizeMultiplier,
                                        )
                                    }
                                }
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
                        // The same 600dp of chat pane that splits Compare and the pause panels.
                        widePane = comparisonWide,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .onSizeChanged { topBarHeightPx = it.height }
                            .onGloballyPositioned { bar ->
                                // The native iOS bar's toggle has no Compose layout: aim at its usual slot.
                                val bounds = bar.boundsInRoot()
                                modeVeil.reportFallbackOrigin(
                                    Offset(bounds.right - fallbackToggleEnd, bounds.bottom - fallbackToggleBottom),
                                )
                            },
                    )
                }
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
                onAttachToCode = onAttachToCode,
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
        onAttachToCode = onAttachToCode,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onShowSavePresetDialog = { showSavePresetDialog = true },
        compareSetupPick = compareSetupPick,
    )

    // Closes itself when the chip goes away (a model switch, a spec, a comparison).
    if (showThinkingSheet && thinkingChip != null) {
        ThinkingSheet(
            chip = thinkingChip,
            onSelect = viewModel::selectThinkingLevel,
            onOpenChatSetting = {
                showThinkingSheet = false
                optionsController.open(ChatOptionsPage.ModelParameters)
            },
            onDismiss = { showThinkingSheet = false },
        )
    }

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
        // The sheet whenever the panes can't hold setup (narrow, or a wide chat still loading or
        // failed to load), and over them for "Browse all models".
        showCompareSetupSheet = uiState.comparisonState.isSettingUp && (!setupInPanes || showCompareBrowseAll),
        onCompareSetupSheetDismiss = if (setupInPanes) {
            { showCompareBrowseAll = false }
        } else {
            null
        },
        compareSetupPick = compareSetupPick,
        primaryModelLabel = primaryLaneLabel,
        compareSetupActions = compareSetupActions,
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
        onNavigateToPromptsLibrary = onNavigateToPromptsLibrary,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onAttachFromServer = onAttachFromServer,
        onOpenWhatsNew = onOpenWhatsNew,
        onOpenMemories = onOpenMemories,
    )
}

/** The native iOS bar's toggle centre, from the bar's bottom-end corner: before the ⋯ button. */
private val NativeToggleFromEnd = 82.dp
private val NativeToggleFromBottom = 28.dp
