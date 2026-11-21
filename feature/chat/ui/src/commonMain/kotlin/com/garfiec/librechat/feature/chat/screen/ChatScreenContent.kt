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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.garfiec.librechat.core.model.preferences.ChatFontSize
import com.garfiec.librechat.core.model.preferences.LatexRenderer
import com.garfiec.librechat.core.model.response.pickerMimeTypes
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.PdfPasswordDialog
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveRailBox
import com.garfiec.librechat.core.ui.components.topbar.RailTopBar
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.core.ui.mcp.McpVariablesSheet
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.platform.verticalBarColumn
import com.garfiec.librechat.core.ui.theme.chatColors
import com.garfiec.librechat.core.ui.websearch.WebSearchKeysSheet
import com.garfiec.librechat.feature.chat.components.AskUserQuestionPanel
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.CONTEXT_RING_ACTION_ID
import com.garfiec.librechat.feature.chat.components.ChatOptionsPage
import com.garfiec.librechat.feature.chat.components.ChatRootContent
import com.garfiec.librechat.feature.chat.components.CodeRunHost
import com.garfiec.librechat.feature.chat.components.CodeWorkspaceComposer
import com.garfiec.librechat.feature.chat.components.ComposerChrome
import com.garfiec.librechat.feature.chat.components.ComposerComparison
import com.garfiec.librechat.feature.chat.components.ComposerComparisonHint
import com.garfiec.librechat.feature.chat.components.ContextRingDialogs
import com.garfiec.librechat.feature.chat.components.LandingStarters
import com.garfiec.librechat.feature.chat.components.LocalCodeRunHost
import com.garfiec.librechat.feature.chat.components.LocalCodeWorkspaceComposer
import com.garfiec.librechat.feature.chat.components.LocalOpenFileActions
import com.garfiec.librechat.feature.chat.components.LocalOpenPdf
import com.garfiec.librechat.feature.chat.components.LocalPullRequestComposer
import com.garfiec.librechat.feature.chat.components.LocalThreadScrollHandle
import com.garfiec.librechat.feature.chat.components.McpSignIn
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.PickerScope
import com.garfiec.librechat.feature.chat.components.PullRequestComposer
import com.garfiec.librechat.feature.chat.components.ThinkingSheet
import com.garfiec.librechat.feature.chat.components.ToolApprovalPanel
import com.garfiec.librechat.feature.chat.components.ToolContentHost
import com.garfiec.librechat.feature.chat.components.UpdateAvailableBanner
import com.garfiec.librechat.feature.chat.components.UploadRoutingSheet
import com.garfiec.librechat.feature.chat.components.UserKeyErrors
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache
import com.garfiec.librechat.feature.chat.components.namesById
import com.garfiec.librechat.feature.chat.components.parsePickerQuery
import com.garfiec.librechat.feature.chat.components.rememberChatOptionsSheetController
import com.garfiec.librechat.feature.chat.components.rememberDraftAdditions
import com.garfiec.librechat.feature.chat.components.rememberThreadScrollHandle
import com.garfiec.librechat.feature.chat.components.sendflight.LocalSendFlight
import com.garfiec.librechat.feature.chat.components.sendflight.SendFlightEffect
import com.garfiec.librechat.feature.chat.components.sendflight.SendFlightState
import com.garfiec.librechat.feature.chat.components.veil.COMPOSER_SWELL
import com.garfiec.librechat.feature.chat.components.veil.LocalComposerFlare
import com.garfiec.librechat.feature.chat.components.veil.LocalModeVeil
import com.garfiec.librechat.feature.chat.components.veil.VeilReaction
import com.garfiec.librechat.feature.chat.components.veil.rememberModeVeilModifier
import com.garfiec.librechat.feature.chat.components.veil.rememberModeVeilState
import com.garfiec.librechat.feature.chat.components.veil.rememberVeilTarget
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.McpPick
import com.garfiec.librechat.feature.chat.model.pickerMcpServersOf
import com.garfiec.librechat.feature.chat.prompts.components.VariableInputDialog
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_cancel_sign_in_failed
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_failed
import com.garfiec.librechat.feature.chat.util.DraftItemTap
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import com.garfiec.librechat.feature.chat.util.ModelPick
import com.garfiec.librechat.feature.chat.util.ModelPickerBlock
import com.garfiec.librechat.feature.chat.util.PickerInputs
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.util.draftItemsOf
import com.garfiec.librechat.feature.chat.util.pickerModelsOf
import com.garfiec.librechat.feature.chat.viewmodel.ChatActions
import com.garfiec.librechat.feature.chat.viewmodel.ChatPreferences
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.asString
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PdfPasswordPrompt
import com.garfiec.librechat.feature.chat.viewmodel.isEmptyLanding
import com.garfiec.librechat.feature.chat.viewmodel.neutralizeStreamingChurn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
// The Scaffold's content padding is deliberately unused: the thread draws under both bars (the
// floating top bar applies its own statusBarsPadding, the composer its own nav-bar padding) and the
// list reserves its insets from the measured bar heights instead. contentWindowInsets is still set
// so the snackbar clears the navigation bar.
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ChatScreenContent(
    actions: ChatActions,
    /** The ViewModel's state; the screen reads it at chrome rate, the thread at full rate. */
    uiStateFlow: StateFlow<ChatUiState>,
    attachedFilesFlow: StateFlow<List<AttachedFile>>,
    chatPreferences: StateFlow<ChatPreferences>,
    promptLibraryRevisionFlow: StateFlow<Long>,
    toolPartStatusesFlow: StateFlow<Map<ToolPartKey, ToolPartStatus>>,
    pdfPasswordPromptsFlow: StateFlow<List<PdfPasswordPrompt>>,
    compareSyncScroll: StateFlow<Boolean>,
    mermaidRenderCache: MermaidRenderCache,
    parsedMarkdownCache: ParsedMarkdownCache,
    codeRunHost: CodeRunHost,
    /** The app update an empty chat offers, by version; null shows nothing. */
    updateVersion: String?,
    onAcknowledgeUpdate: () -> Unit,
    snackbarHostState: SnackbarHostState,
    mcpSignIn: McpSignIn,
    slots: ChatScreenSlots,
    /**
     * Deep-link CTA from the user-provided-key error snackbar and the
     * model-selector "Add key" action on greyed endpoint groups. Tap navigates to
     * Settings → Provider API Keys. When [endpointName] is non-null, the destination
     * screen auto-opens the Set Key bottom-sheet for that endpoint.
     */
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    modifier: Modifier = Modifier,
    chatProjectName: String? = null,
    onOpenDrawer: (() -> Unit)? = null,
    /** Starts a new chat, as the drawer's New chat does; shown in the side rail. Null leaves it out. */
    onNewChat: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
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
    // Chrome-rate state; ChatContent collects at full rate. Never read a neutralized field here.
    val chromeFlow = remember(uiStateFlow) {
        uiStateFlow.map { it.neutralizeStreamingChurn() }.distinctUntilChanged()
    }
    val initialChrome = remember(uiStateFlow) { uiStateFlow.value.neutralizeStreamingChurn() }
    val uiState by chromeFlow.collectAsStateWithLifecycle(initialChrome)
    val attachedFiles = attachedFilesFlow.collectAsStateWithLifecycle()
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
    val prefs by chatPreferences.collectAsStateWithLifecycle()
    val promptLibraryRevision by promptLibraryRevisionFlow.collectAsStateWithLifecycle()
    val showImageDescriptions = prefs.showImageDescriptions
    val dismissKeyboardOnSend = prefs.dismissKeyboardOnSend
    val chatLayoutStyle = prefs.chatLayoutStyle
    val showAvatars = prefs.showAvatars
    val showBubbles = prefs.showBubbles
    val useKatex = prefs.latexRenderer == LatexRenderer.KATEX
    val sttEngine = prefs.sttEngine
    val sttLanguage = prefs.sttLanguage
    val keyboardController = LocalSoftwareKeyboardController.current
    val reduceMotion = rememberReduceMotion()
    val fontSizeMultiplier = when (uiState.chatFontSize) {
        ChatFontSize.SMALL -> 0.85f
        ChatFontSize.MEDIUM -> 1.0f
        ChatFontSize.LARGE -> 1.2f
    }
    slots.replyNotificationAsk(uiState.isStreaming)
    val coroutineScope = rememberCoroutineScope()
    var showPresetPicker by remember { mutableStateOf(false) }
    var showThinkingSheet by rememberSaveable { mutableStateOf(false) }
    var showWebSearchKeys by rememberSaveable { mutableStateOf(false) }
    var mcpVariablesServer by rememberSaveable { mutableStateOf<String?>(null) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showSecondaryModelSheet by remember { mutableStateOf(false) }
    val contextRingDialogs = remember { ContextRingDialogs() }
    var activeComparisonTab by remember { mutableIntStateOf(0) }
    val compareSetupPick = rememberCompareSetupPick(uiState.comparisonState.isSettingUp)
    val compareSetupActions = remember(actions) { actions.compareSetupActions() }
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

    val onStartRecordingWithPermission = slots.startRecording(sttEngine, sttLanguage, coroutineScope)

    // Options sheet ("+" menu, model selector/parameters as pages). Opened from the composer's "+"
    // and the pull-up, hence the controller. Independent of uiState.showModelSheet (the standalone
    // selector), which keeps dismissing straight to the chat.
    val optionsController = rememberChatOptionsSheetController()
    val thinkingChip = uiState.settingChips.thinking
    // Nothing here cancels an OAuth flow: only a connecting chip's "Cancel sign-in" does.
    val mcpSignInFailed = stringResource(Res.string.mcp_sign_in_failed)
    val mcpCancelSignInFailed = stringResource(Res.string.mcp_cancel_sign_in_failed)
    val showNotice: (String) -> Unit = { message -> coroutineScope.launch { snackbarHostState.showNotice(message) } }
    val signInMcpServer: (String, Boolean) -> Unit = { serverName, selectWhenConnected ->
        actions.signInMcpServer(
            serverName = serverName,
            openSignIn = mcpSignIn.signIn,
            onFailed = { showNotice(mcpSignInFailed) },
            selectWhenConnected = selectWhenConnected,
        )
    }
    val startMcpSignIn: (String) -> Unit = { signInMcpServer(it, false) }
    // The "+" sheet's sign-in turns the server on once it connects (web's sign in, then select).
    val startSheetMcpSignIn: (String) -> Unit = { signInMcpServer(it, true) }
    // A refused cancel leaves the chip reading connecting, so only the snackbar says it didn't land.
    val cancelMcpSignIn: (String) -> Unit = { serverName ->
        actions.cancelMcpSignIn(serverName, onFailed = { showNotice(mcpCancelSignInFailed) })
    }
    val onSettingChipTap = rememberSettingChipTap(
        chips = uiState.settingChips,
        snackbarHostState = snackbarHostState,
        onToggle = actions::toggleSettingChip,
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
        actions,
    ) {
        secondaryModelLabel?.let { secondaryLabel ->
            ComposerComparison(
                primaryLabel = primaryLaneLabel,
                secondaryLabel = secondaryLabel,
                onChangePrimary = actions::openModelSheet,
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
    val attachmentActions = slots.attachmentActions(filePickerMimeTypes)
    val onAttachToCode = attachmentActions.onAttachToCode.takeIf { uiState.codeUploadOffered }

    DraftUndoSnackbarEffect(
        undo = uiState.pendingDraftUndo,
        snackbarHostState = snackbarHostState,
        onUndo = actions::undoDraftRemoval,
        onDrop = actions::dropDraftUndo,
    )

    slots.effects(uiState)

    val sendBlockMessage = uiState.sendBlockReason?.asString()

    // Collected apart from uiState: it changes only when a preview's fetch starts, lands or fails.
    val toolPartStatuses by toolPartStatusesFlow.collectAsStateWithLifecycle()
    val toolContent = remember(toolPartStatuses, actions) {
        ToolContentHost(
            statuses = toolPartStatuses,
            onRequest = actions::requestToolPart,
            onRetain = actions::retainToolPart,
            onRelease = actions::releaseToolPart,
        )
    }

    val currentOnNavigateToProviderKeys by rememberUpdatedState(onNavigateToProviderKeys)
    val userKeyErrors = remember(uiState.selection.endpointConfigs, uiState.agents) {
        UserKeyErrors(uiState.selection.endpointConfigs, uiState.agents) { currentOnNavigateToProviderKeys(it) }
    }
    val agentNames = remember(uiState.agents) { uiState.agents.namesById() }
    slots.mcpProviders {
    ChatRootContent(
        inlineArtifactPrefs = prefs.inlineArtifactPrefs,
        mermaidRenderCache = mermaidRenderCache,
        parsedMarkdownCache = parsedMarkdownCache,
        subagentProgress = uiState.subagentProgress,
        ptcTraces = uiState.ptcTraces,
        previewOverrides = uiState.previewOverrides,
        conversationId = uiState.conversationId,
        subagentThreadsSupported = uiState.gates.subagentThreadsSupported,
        mediaPreview = uiState.mediaPreview,
        onOpenMedia = actions::openMedia,
        onCloseMedia = actions::closeMedia,
        onDownloadAttachment = actions::downloadFileBytes,
        onFileNotice = showNotice,
        promptLibraryRevision = promptLibraryRevision,
        onRefreshPrompts = actions::refreshPromptsIfStale,
        siteIcons = prefs.siteIcons,
        onSiteIconsChoice = actions::setShowSiteIcons,
        onSiteIconsPromptDismiss = actions::dismissSiteIconPrompt,
        onOpenMemories = onOpenMemories?.takeIf { uiState.gates.memoriesPageEnabled },
        userKeyErrors = userKeyErrors,
        agentNames = agentNames,
        mcpSignIn = mcpSignIn,
        thinkingOpenByDefault = prefs.thinkingOpenByDefault,
        collapseLongUserMessages = prefs.collapseLongUserMessages,
        toolContent = toolContent,
        subagentThreadsSheet = slots.subagentThreadsSheet,
    ) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        // The floating top bar draws behind the status bar itself (statusBarsPadding), so the body
        // must extend under the status bar — only reserve the navigation-bar inset here (for the
        // snackbar); the composer handles its own nav-bar padding.
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = chatColors().canvas,
        snackbarHost = {
            AdaptiveSnackbarHost(snackbarHostState, modifier = Modifier.windowInsetsPadding(WindowInsets.verticalBarColumn))
        },
    ) { _ ->
        // The composer overlays the message list at the bottom; the list reserves a scrollable
        // bottom inset so its latest content rests above the bar. We measure the bar's actual
        // height (which grows as queued ghost rows stack above it) so streaming text never hides
        // behind the ghosts, while the list still scrolls *behind* the translucent overlay. The
        // floating top bar is measured the same way so the first message clears it while still
        // scrolling up behind its dimming scrim.
        var inputBarHeightPx by remember { mutableIntStateOf(0) }
        var topBarHeightPx by remember { mutableIntStateOf(0) }
        // Floor the top inset at status bar + one chip row so content clears the bar on the first
        // frame (before onSizeChanged reports the real height), avoiding a content-under-bar flash
        // on each screen entry.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val (fallbackToggleEnd, fallbackToggleBottom) = with(LocalDensity.current) {
            NativeToggleFromEnd.toPx() to NativeToggleFromBottom.toPx()
        }
        val topContentPadding = with(LocalDensity.current) {
            maxOf(statusBarTop + 56.dp, topBarHeightPx.toDp())
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val pullUp = rememberChatPullUpSheetState()
            // Temporary chat's transition: the band and (Material) the retone draw over and behind
            // the landing, the composer and the top bar, which all react to it through LocalModeVeil.
            val modeVeil = rememberModeVeilState(uiState.isTemporaryChat)
            // Created here, above both the thread and the composer: the composer's chrome hands its
            // vertical drags to whichever list ChatContent binds.
            val threadScroll = rememberThreadScrollHandle()
            val sendFlight = remember { SendFlightState() }
            sendFlight.enabled = !reduceMotion && !uiState.comparisonState.isEnabled
            CompositionLocalProvider(
                LocalModeVeil provides modeVeil,
                LocalThreadScrollHandle provides threadScroll,
                LocalSendFlight provides sendFlight,
                // Run Code on reply fences, behind RUN_CODE.USE (web `MarkdownComponents` `canRunCode`).
                LocalCodeRunHost provides codeRunHost.takeIf { uiState.runCodeEnabled },
            ) {
                AdaptiveRailBox(
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
                            // The thread keeps out of the bar column (iPhone Duo) while the backdrop around
                            // it fills under it; the panes' width gates measure what's left.
                            .windowInsetsPadding(WindowInsets.verticalBarColumn)
                            .onSizeChanged { chatWidthPx = it.width }
                            .then(slots.selectionItem(uiState.quoteCaptureAvailable, actions::addPendingQuote)),
                    ) {
                        ChatContent(
                            listPullUpModifier = pullUp.listModifier,
                            pullUpModifier = pullUp.landingModifier,
                            actions = actions,
                            uiStateFlow = uiStateFlow,
                            compareSyncScroll = compareSyncScroll,
                            conversationSeenEffect = slots.conversationSeenEffect,
                            agentName = agentName,
                            fontSizeMultiplier = fontSizeMultiplier,
                            showImageDescriptions = showImageDescriptions,
                            chatLayoutStyle = chatLayoutStyle,
                            showAvatars = showAvatars,
                            showBubbles = showBubbles,
                            useKatex = useKatex,
                            bottomBarHeightPx = { inputBarHeightPx },
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
                    val onDraftItemTap: (DraftItemTap) -> Unit = remember(actions, openPdf, openFileActions) {
                        { tap ->
                            when (tap) {
                                is DraftItemTap.Retry -> actions.retryUpload(tap.file)
                                is DraftItemTap.ViewImage -> actions.openDraftMedia(tap.preview)
                                is DraftItemTap.OpenPdf -> openPdf(tap.fileId, tap.filename)
                                is DraftItemTap.OpenFile -> openFileActions(tap.attachment)
                            }
                        }
                    }
                    val pickerMcpServers = remember(
                        uiState.offeredMcpServers,
                        uiState.effectiveMcpServerNames,
                        uiState.lockedMcpServers,
                        uiState.toolbarShown,
                        uiState.mcpServersEnabled,
                    ) {
                        pickerMcpServersOf(
                            servers = uiState.offeredMcpServers,
                            on = uiState.effectiveMcpServerNames,
                            locked = uiState.lockedMcpServers,
                            toolbarShown = uiState.toolbarShown,
                            mcpAllowed = uiState.mcpServersEnabled,
                        )
                    }
                    val pickerModelBlock = when {
                        !uiState.modelPickerShown -> ModelPickerBlock.NO_PICKER
                        uiState.comparisonState.isEnabled || uiState.comparisonState.isSettingUp ->
                            ModelPickerBlock.COMPARING
                        else -> null
                    }
                    // Mapped only while `/model ` is open, so a closed list maps nothing.
                    val pickerModelInputs = if (
                        pickerModelBlock == null &&
                        parsePickerQuery(uiState.inputText, uiState.canPickSkills)?.scope == PickerScope.MODEL
                    ) {
                        PickerInputs(
                            endpointConfigs = uiState.endpointConfigs,
                            availableModels = uiState.availableModels,
                            agents = uiState.agents,
                            agentsLoaded = uiState.agentsLoaded,
                            modelSpecs = uiState.modelSpecs,
                            modelSelect = uiState.modelSelectEnabled,
                            selectedEndpoint = uiState.selectedEndpoint,
                            selectedModel = uiState.selectedModel,
                            selectedSpec = uiState.selectedSpec,
                            favoriteAgentIds = uiState.favoriteAgentIds,
                            favoriteModelKeys = uiState.favoriteModelKeys,
                            favoriteSpecNames = uiState.favoriteSpecNames,
                            endpointKeyStates = uiState.endpointKeyStates,
                        )
                    } else {
                        null
                    }
                    val pickerModels = remember(pickerModelInputs, uiState.serverUrl) {
                        pickerModelInputs?.let { pickerModelsOf(it, uiState.serverUrl) }.orEmpty()
                    }
                    val composerChrome = ComposerChrome(
                        pendingSteers = uiState.pendingSteers,
                        onCancelSteer = actions::cancelSteer,
                        onInterruptSteer = actions::armSteer,
                        queuedMessages = uiState.displayedMessageQueue,
                        onEditQueuedMessage = actions::editQueued,
                        onCancelQueuedMessage = actions::cancelQueued,
                        onReorderQueuedMessages = actions::reorderQueue,
                        draftItems = draftItems,
                        draftUploadProgress = draftUploadProgress,
                        draftAdditions = draftAdditions,
                        onRemoveDraftItem = actions::removeDraftItem,
                        onDraftItemTap = onDraftItemTap,
                        enabledTools = uiState.effectiveEnabledTools,
                        selectedMcpServerNames = uiState.effectiveMcpServerNames,
                        settingChips = uiState.settingChips,
                        onSettingChipTap = onSettingChipTap,
                        pickerMcpServers = pickerMcpServers,
                        pickerModels = pickerModels,
                        pickerModelBlock = pickerModelBlock,
                        onCancelMcpSignIn = cancelMcpSignIn,
                        onStartMcpSignInOver = startMcpSignIn,
                        contextGauge = uiState.contextGaugeDetails,
                        contextUsageEnabled = uiState.contextUsageEnabled,
                        onCompact = actions::compactConversation.takeIf { uiState.canCompactNow },
                        onSnoozeCompact = actions::snoozeCompactNudge,
                        compactSuggested = uiState.compactNudgeOnToolsButton,
                        contextBarPlacement = uiState.contextBarPlacement,
                        // A rail shortened by the keyboard drops the ring; the composer then draws it.
                        contextRingInRail = railShowsBottomAction(CONTEXT_RING_ACTION_ID),
                        contextRingDialogs = contextRingDialogs,
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
                                updateVersion?.let { version ->
                                    UpdateAvailableBanner(
                                        version = version,
                                        onOpen = {
                                            onAcknowledgeUpdate()
                                            onOpenWhatsNew()
                                        },
                                        onDismiss = onAcknowledgeUpdate,
                                        modifier = Modifier
                                            .windowInsetsPadding(WindowInsets.verticalBarColumn)
                                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                    )
                                }
                            }
                            if (askPause == null && !uiState.showsComparisonPanes(comparisonWide)) {
                                LandingStarters(
                                    // Hidden rows keep their space, so outside the landing they would
                                    // sit invisibly over the thread and inflate the measured bar.
                                    starters = if (uiState.hasLandingStarters) uiState.landingStarters else emptyList(),
                                    visible = uiState.showsLandingStarters,
                                    onSend = actions::sendStarter,
                                    onEdit = actions::editStarter,
                                    // Weighted, so the composer and its `/` picker are measured first and
                                    // the starters' kept space never squeezes the field. They cover the
                                    // landing, so they carry its pull-up drag too.
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .windowInsetsPadding(WindowInsets.verticalBarColumn)
                                        .then(pullUp.landingModifier),
                                )
                            }
                            toolPause?.let { pause ->
                                ToolApprovalPanel(
                                    pendingAction = pause,
                                    isResolving = uiState.isResolvingPendingAction,
                                    drafts = uiState.toolDecisionDrafts,
                                    activeCallId = uiState.toolActiveCallId,
                                    collapsed = uiState.toolPanelCollapsed,
                                    onDraftChange = actions::updateToolDecisionDraft,
                                    onPickDecision = actions::pickToolDecision,
                                    onSelectCall = actions::selectToolCall,
                                    onCollapsedChange = actions::setToolPanelCollapsed,
                                    onCancelAutoAdvance = actions::cancelPausePanelAutoAdvance,
                                    onSubmit = actions::resolveToolApproval,
                                    // Weighted, so the composer below is measured first and the panel
                                    // gets only the height it leaves.
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .windowInsetsPadding(WindowInsets.verticalBarColumn)
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
                                    onDraftChange = actions::updateAskAnswerDraft,
                                    onPickOption = actions::pickAskOption,
                                    onSelectQuestion = actions::selectAskQuestion,
                                    onCollapsedChange = actions::setAskPanelCollapsed,
                                    onCancelAutoAdvance = actions::cancelPausePanelAutoAdvance,
                                    onSubmitAnswer = actions::answerPendingQuestion,
                                    onSubmitAnswers = actions::answerPendingQuestions,
                                    // The composer's Stop is hidden with it, and the run stays live across the pause.
                                    onStop = actions::stopGeneration.takeIf { isAnyStreaming },
                                    resumeFailed = uiState.pendingActionResumeFailed,
                                    modifier = Modifier
                                        .navigationBarsPadding()
                                        .windowInsetsPadding(WindowInsets.verticalBarColumn)
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
                                    val codeWorkspaceComposer = remember(codeWorkspace, actions) {
                                        CodeWorkspaceComposer(
                                            state = codeWorkspace,
                                            onOpenSheet = actions::openCodeWorkspaceSheet,
                                            onDismissSheet = actions::dismissCodeWorkspaceSheet,
                                            onSelectWorkspace = actions::selectCodeWorkspace,
                                            onSetCheckout = actions::setCodeCheckout,
                                            onSelectNoWorkspace = actions::selectNoCodeWorkspace,
                                            onRefresh = actions::refreshCodeWorkspaces,
                                            onSelectTransitionWorkspace = actions::selectTransitionCodeWorkspace,
                                            onSetTransitionCheckout = actions::setTransitionCodeCheckout,
                                            onConfirmTransition = actions::confirmCodeWorkspaceTransition,
                                            onDetach = actions::detachCodeWorkspace,
                                            onRetryReconcile = actions::retryCodeWorkspaceCheck,
                                            onSelectApprovalMode = actions::selectCodeApprovalMode,
                                        )
                                    }
                                    // The pull request row: narrow panes and the rail only (a wide bar has the
                                    // chip), out of the way of a tool-approval pause, and folded to its glyph
                                    // while a run streams.
                                    val pullRequestState = uiState.pullRequest
                                    val toolPausePending = toolPause != null
                                    val barHasPullRequestChip = comparisonWide && !this@AdaptiveRailBox.inRail
                                    val pullRequestComposer = remember(
                                        pullRequestState,
                                        barHasPullRequestChip,
                                        toolPausePending,
                                        isAnyStreaming,
                                        actions,
                                    ) {
                                        val pr = pullRequestState.pullRequest
                                        val presentation = pullRequestState.presentation
                                        if (barHasPullRequestChip || toolPausePending || pr == null || presentation == null) {
                                            null
                                        } else {
                                            PullRequestComposer(
                                                pullRequest = pr,
                                                presentation = presentation,
                                                expanded = pullRequestState.unseen && !isAnyStreaming,
                                                onOpen = actions::openPullRequestSheet,
                                                onSeen = actions::markPullRequestSeen,
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
                                        slots.composer(
ChatComposerParams(
                                            inputText = uiState.inputText,
                                            isStreaming = isAnyStreaming,
                                            onInputChange = actions::onInputChanged,
                                            onSend = send {
                                                sendFlight.capture(uiState.inputText)
                                                actions.sendMessage()
                                            },
                                            onStop = actions::stopGeneration,
                                            onOpenTools = { optionsController.open() },
                                            // The mid-stream send button: the ViewModel resolves steer-vs-queue from the
                                            // user's preference and what this run can actually take, so the composer never
                                            // has to. `onQueue` stays the picker's explicit "add to queue".
                                            onDuringRunSend = send(actions::sendDuringRun),
                                            onQueue = send(actions::queueMessage),
                                            canQueue = uiState.canQueueFollowUp,
                                            // Explicit "steer this one", from the during-run picker or the send button when
                                            // steering is the standing default.
                                            onSteer = send(actions::steerMessage),
                                            onInterrupt = send(actions::interruptMessage),
                                            canSteer = uiState.canSteerNow,
                                            // Built-in search can't be interrupted (provider-hosted tool), so the
                                            // menu says so instead of promising to stop it.
                                            builtInSearchOn = uiState.webSearchRoute == WebSearchRoute.BUILT_IN,
                                            duringRunAction = uiState.effectiveDuringRunAction,
                                            duringRunSendTarget = uiState.duringRunSendTarget,
                                            chrome = composerChrome,
                                            onSetDuringRunAction = actions::setDuringRunAction,
                                            onPasteFiles = actions::onFilesSelected,
                                            pasteScope = coroutineScope,
                                            promptSuggestions = uiState.availablePrompts,
                                            skillSuggestions = pickerSkills,
                                            isLoadingSkills = uiState.isLoadingSkills && uiState.canPickSkills,
                                            skillsLoadFailed = uiState.skillsLoadFailed && uiState.canPickSkills,
                                            canPickSkills = uiState.canPickSkills,
                                            onRetrySkills = actions::retrySkillCatalog,
                                            onPickerSelect = { item ->
                                                actions.selectPickerItem(item)
                                                // As the + sheet's MCP row: a sign-in turns the server on once connected.
                                                if (item is ComposerPickerItem.Mcp) {
                                                    when (item.mcp.pick) {
                                                        McpPick.SIGN_IN -> startSheetMcpSignIn(item.mcp.server.name)
                                                        McpPick.SET_UP -> mcpVariablesServer = item.mcp.server.name
                                                        McpPick.TOGGLE, McpPick.NONE -> Unit
                                                    }
                                                }
                                                if (item is ComposerPickerItem.Model) {
                                                    (item.model.pick as? ModelPick.AddKey)?.let { onNavigateToProviderKeys(it.endpoint) }
                                                }
                                            },
                                            isRecording = uiState.isRecording,
                                            isTranscribing = uiState.isTranscribing,
                                            onStartRecording = onStartRecordingWithPermission,
                                            onStopRecording = actions::stopRecording,
                                            selectedModelDisplay = effectiveSelectedModelDisplay,
                                            isCodeInterpreterAvailable = uiState.isCodeInterpreterAvailable,
                                            gates = uiState.chatInputGates,
                                            // After a Stop/error pause, the queue waits for an explicit nudge.
                                            queuedPausedCount = uiState.pausedQueueCount,
                                            queueHeldForRefusal = uiState.isQueueHeldForRefusal,
                                            // The band, not the flag, tints the composer: it turns violet as the band reaches it.
                                            isTemporaryChat = composerVeil.crossed,
                                            onSendQueuedMessages = actions::sendQueuedNow,
                                            isEditingQueued = uiState.isEditingQueued,
                                            onCommitEdit = actions::commitQueuedEdit,
                                            onCancelEdit = actions::cancelQueuedEdit,
                                            isAwaitingUploadSend = uiState.isAwaitingUploadSend,
                                            arePicksUnsettled = uiState.arePicksUnsettled,
                                            comparison = composerComparison,
                                            comparisonHint = composerComparisonHint,
                                            onCancelPendingSend = actions::cancelPendingUploadSend,
                                            fontSizeMultiplier = fontSizeMultiplier,
                                        ),
)
                                    }
                                }
                            }
                        }
                    }

                    SendFlightEffect(sendFlight)

                    // Floating top bar overlays the message list (drawn last so it sits above content),
                    // measured so ChatContent can reserve a matching scrollable top inset.
                    RailTopBar {
                        slots.topBar(
ChatTopBarParams(
                            uiState = uiState,
                            onLoadPreset = {
                                actions.reloadPresetsIfFailed()
                                showPresetPicker = true
                            },
                            onSavePreset = { showSavePresetDialog = true },
                            onRename = actions::showRenameDialog,
                            onOpenDrawer = onOpenDrawer,
                            onShowAllMedia = onShowAllMedia,
                            onOpenPromptsLibrary = onNavigateToPromptsLibrary,
                            // The same 600dp of chat pane that splits Compare and the pause panels.
                            widePane = comparisonWide,
                            railMode = inRail,
                            onNewChat = onNewChat,
                            contextRingDialogs = contextRingDialogs,
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
                        ),
)
                    }
                }
            }

            ChatPullUpSheetOverlay(
                state = pullUp,
                topContentPadding = topContentPadding,
                uiState = uiState,
                actions = actions,
                optionsController = optionsController,
                selectedModelDisplay = effectiveSelectedModelDisplay,
                onAttachFiles = attachmentActions.onAttachFiles,
                onTakePhoto = attachmentActions.onTakePhoto,
                onPickPhotos = attachmentActions.onPickPhotos,
                onAttachFromServer = onAttachFromServer,
                onAttachToCode = onAttachToCode,
                onSignInMcpServer = startSheetMcpSignIn,
                onCancelMcpSignIn = cancelMcpSignIn,
                onSetUpMcpServer = { mcpVariablesServer = it },
                onOpenWebSearchKeys = { showWebSearchKeys = true },
            )

            val pendingRouting = uiState.composer.pendingUploadRouting
            if (pendingRouting != null) {
                UploadRoutingSheet(
                    files = pendingRouting.files,
                    onRouteChange = actions::setPendingUploadRoute,
                    onApplyToAll = actions::setAllPendingUploadRoutes,
                    onConfirm = actions::confirmPendingUploadRouting,
                    onDismiss = actions::cancelPendingUploadRouting,
                )
            }
        }
    }

    ChatOptionsSheetHost(
        controller = optionsController,
        uiState = uiState,
        actions = actions,
        isSecondaryTab = isSecondaryTab,
        selectedModelDisplay = effectiveSelectedModelDisplay,
        onAttachFiles = attachmentActions.onAttachFiles,
        onTakePhoto = attachmentActions.onTakePhoto,
        onPickPhotos = attachmentActions.onPickPhotos,
        onAttachFromServer = onAttachFromServer,
        onAttachToCode = onAttachToCode,
        onSignInMcpServer = startSheetMcpSignIn,
        onCancelMcpSignIn = cancelMcpSignIn,
        onSetUpMcpServer = { mcpVariablesServer = it },
        onOpenWebSearchKeys = { showWebSearchKeys = true },
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onShowSavePresetDialog = { showSavePresetDialog = true },
        compareSetupPick = compareSetupPick,
        snackbarHostState = snackbarHostState,
    )

    val webSearchKeys = uiState.gates.webSearchKeys
    if (showWebSearchKeys && webSearchKeys != null) {
        WebSearchKeysSheet(
            keys = webSearchKeys,
            onSave = actions::saveWebSearchKeys,
            onRemove = actions::removeWebSearchKeys,
            onDismiss = { showWebSearchKeys = false },
        )
    }

    // Closes itself when the server leaves the list (an account or server switch).
    val mcpVariablesFor = mcpVariablesServer?.let { name -> uiState.mcpServers.firstOrNull { it.name == name } }
    if (mcpVariablesFor != null) {
        McpVariablesSheet(
            serverLabel = mcpVariablesFor.label,
            vars = mcpVariablesFor.userVars,
            loadFlags = { actions.mcpVariableFlags(mcpVariablesFor.name) },
            onSave = { values ->
                // Saving drops the connection; the sheet's sign-in reconnects and turns the server on.
                actions.saveMcpVariables(mcpVariablesFor.name, values).also { saved ->
                    if (saved) startSheetMcpSignIn(mcpVariablesFor.name)
                }
            },
            onRemove = { actions.removeMcpVariables(mcpVariablesFor.name) },
            onDismiss = { mcpVariablesServer = null },
        )
    }

    // Closes itself when the chip goes away (a model switch, a spec, a comparison).
    if (showThinkingSheet && thinkingChip != null) {
        ThinkingSheet(
            chip = thinkingChip,
            onSelect = actions::selectThinkingLevel,
            onOpenChatSetting = {
                showThinkingSheet = false
                optionsController.open(ChatOptionsPage.ModelParameters)
            },
            onDismiss = { showThinkingSheet = false },
        )
    }

    ChatScreenDialogs(
        uiState = uiState,
        actions = actions,
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

    LaunchedEffect(Unit) { actions.consumePendingPromptInsertion() }

    val pdfPasswordPrompts by pdfPasswordPromptsFlow.collectAsStateWithLifecycle()
    pdfPasswordPrompts.firstOrNull()?.let { prompt ->
        // Keyed so a re-prompt after a wrong password starts from an empty field.
        key(prompt) {
            PdfPasswordDialog(
                filename = prompt.file.name,
                incorrectPassword = prompt.incorrectPassword,
                onSubmit = actions::submitPdfPassword,
                onDismiss = actions::dismissPdfPassword,
            )
        }
    }

    uiState.pendingVariablePrompt?.let { pending ->
        VariableInputDialog(
            promptTemplate = pending.template,
            variables = pending.variables,
            onInsert = actions::confirmVariablePrompt,
            onDismiss = actions::dismissVariablePrompt,
        )
    }
    }
    }
}

/** The native iOS bar's toggle centre, from the bar's bottom-end corner: before the ⋯ button. */
private val NativeToggleFromEnd = 82.dp
private val NativeToggleFromBottom = 28.dp
