package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore
import com.garfiec.librechat.core.data.datastore.ConversationWebSearchStore
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.CodeRunRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.data.repository.ReasoningCapabilitiesRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.repository.WebSearchReadinessRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.isTraceViewerEnabled
import com.garfiec.librechat.core.model.content.speechTextOf
import com.garfiec.librechat.core.model.content.stripCitations
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.pullrequest.pullRequestsAvailable
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.notifications.ReplyWatch
import com.garfiec.librechat.core.notifications.ReplyWatchScheduler
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.CodeRunHost
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance
import com.garfiec.librechat.feature.chat.components.PickerScope
import com.garfiec.librechat.feature.chat.components.parsePickerQuery
import com.garfiec.librechat.feature.chat.components.pickerListsSkills
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.McpPick
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.ModelPick
import com.garfiec.librechat.feature.chat.util.ReplyAvatarMemo
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipsMemo
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import com.garfiec.librechat.feature.chat.util.ToolPartIdentity
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.extractBranchMedia
import com.garfiec.librechat.feature.chat.util.isAddedAgentId
import com.garfiec.librechat.feature.chat.util.newChatDraftKey
import com.garfiec.librechat.feature.chat.util.overrideFor
import com.garfiec.librechat.feature.chat.util.parametersFor
import com.garfiec.librechat.feature.chat.util.parametersLaneRanOn
import com.garfiec.librechat.feature.chat.util.reopenedParameters
import com.garfiec.librechat.feature.chat.util.restore
import com.garfiec.librechat.feature.chat.util.serializeMessageForClipboard
import com.garfiec.librechat.feature.chat.util.stored
import com.garfiec.librechat.feature.chat.util.withAttachmentSources
import com.garfiec.librechat.feature.chat.viewmodel.delegate.AttachmentPreviewDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.BranchSwitch
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ChatBookmarksDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ChatConfigDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.CodeRunDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.CodeWorkspaceDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ComparisonModeDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ContextProjectionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ConversationActionsDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ConversationLoadDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.DraftItemsDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.EndpointKeyStatusDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.FavoritesDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.InConversationSearchDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.JumpToMessageDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.LiveReplyDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.LocalQueueContinuityDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.McpServersDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageEditingDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageQueueDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageTreeDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ModelSelectionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.OwnerTextDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PdfPasswordPrompt
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PendingActionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformDelegateFactory
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PresetPromptDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PullRequestDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.QueueOrchestrationDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.QueuedTurnDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ReasoningCatalogDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SendCompletionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SendDispatchDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SendReadinessDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ShareData
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SkillPickerDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SkillsToggleDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SteeringDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.StreamingHost
import com.garfiec.librechat.feature.chat.viewmodel.delegate.StreamingManagerDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SubagentHintsDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SubagentTraceDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ToolPartHydrationDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.UploadIntakeDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock

// debt — LargeClass: 1484-line file
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class ChatViewModel(
    initialConversationId: String? = null,
    initialAgentId: String? = null,
    /** Explicit (endpoint, model) to pre-select on a new chat — set when launched from a home-screen
     *  model shortcut / quick action. Null for normal new chats. Mutually exclusive with an agent. */
    initialEndpoint: String? = null,
    initialModel: String? = null,
    /** The spec that model shortcut was recorded from, if any. */
    initialSpec: String? = null,
    /** True when this Chat(id) entry is a temporary chat. Rides on the Chat route so it
     *  survives process death: a restored entry re-initializes temp-aware and never persists the
     *  server-hidden conversation to Room. SECURITY: temp-chat data-at-rest guard — see init. */
    initialIsTemporary: Boolean = false,
    /** A message to open the chat at (a search hit), and the query to highlight in it. */
    initialTargetMessageId: String? = null,
    initialHighlightQuery: String? = null,
    /** The hit matched in the target's reasoning, so the jump lands on a match there. */
    initialTargetInReasoning: Boolean = false,
    /** Project a new chat started from a project is filed into (null for an ordinary new chat). */
    initialChatProjectId: String? = null,
    private val agentRepository: AgentRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val resumePinStore: ResumePinStore,
    private val configRepository: ConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val endpointTokenRepository: EndpointTokenRepository,
    reasoningCapabilitiesRepository: ReasoningCapabilitiesRepository,
    private val draftRepository: DraftRepository,
    localQueueStash: LocalQueueStash,
    favoritesRepository: FavoritesRepository,
    private val keyRepository: KeyRepository,
    presetRepository: PresetRepository,
    private val promptRepository: PromptRepository,
    queuedTurnRepository: QueuedTurnRepository,
    shareRepository: ShareRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    tagRepository: TagRepository,
    private val traceRepository: TraceRepository,
    mcpRepository: McpRepository,
    skillsRepository: SkillsRepository,
    userRepository: UserRepository,
    roleRepository: RoleRepository,
    memoryRepository: MemoryRepository,
    webSearchReadiness: WebSearchReadinessRepository,
    private val conversationWebSearchStore: ConversationWebSearchStore,
    codeEnvironmentRepository: CodeEnvironmentRepository,
    codeWorkspacePreferenceStore: CodeWorkspacePreferenceStore,
    pullRequestRepository: PullRequestRepository,
    codeRunRepository: CodeRunRepository,
    private val permissionGate: PermissionGate,
    private val connectivityObserver: ConnectivityObserver,
    private val activeAccountProvider: ActiveAccountProvider,
    serverDataStore: ServerDataStore,
    private val settingsDataStore: SettingsDataStore,
    platformDelegateFactory: PlatformDelegateFactory,
    private val json: Json,
    defaultDispatcher: CoroutineDispatcher,
    private val selectionHandoff: NewChatSelectionHandoff,
    private val serverFileSelectionHandoff: ServerFileSelectionHandoff,
    private val promptInsertionHandoff: PromptInsertionHandoff,
    private val siteIconPromptSession: SiteIconPromptSession,
    private val preferenceStore: PreferenceStore,
    private val replyWatchScheduler: ReplyWatchScheduler? = null,
    /** The wall clock key expiries are held against; a test moves it to stand in for a sleep. */
    clock: Clock = Clock.System,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())

    /** Set once the role confirms PROMPTS.USE, so a denied user's screen issues no prompt fetch. */
    private var promptsUseAllowed = false

    private val stateHandle = ChatStateHandle(_uiState, viewModelScope)

    // Mermaid SVG cache, scoped to this ViewModel's lifecycle. Filled by inline-
    // artifact WebViews via a JS bridge; read by SharedContentParts when a
    // recompose reaches an already-rendered flowchart mermaid.
    val mermaidRenderCache: com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache =
        com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache()

    // Parsed-AST cache for chat-text markdown. m3 parses async; when a LazyColumn
    // item is recycled its remembered state dies and parsing restarts, producing
    // a 0-px → final-height cascade that pushes adjacent inline-artifact slots
    // around (the scroll-jump root cause). Caching the parsed State.Success lets
    // re-entry render directly from the cached AST. See CachedMarkdown.
    val parsedMarkdownCache: ParsedMarkdownCache = ParsedMarkdownCache()

    // --- Platform delegates (created via factory so they get a narrowed handle over stateHandle) ---
    private val fileDelegate = platformDelegateFactory.createFileHandler(ErrorOnlyHandle(stateHandle))

    // Spoken without citation markers: "turn zero search zero" is not part of the reply.
    private val ttsDelegate = platformDelegateFactory.createTts(TtsHandle(stateHandle)) { stripCitations(getSpeechText(it)) }
    private val voiceDelegate =
        platformDelegateFactory.createVoiceInput(VoiceHandle(stateHandle, ::persistComposerDraft), ::sendMessage)
    private val shareConsumer = platformDelegateFactory.createShareConsumer()

    // --- Delegates (each gets a narrowed handle that can write only its own slices) ---
    private val requestBuilder = ChatRequestBuilder { _uiState.value }
    private val treeDelegate = MessageTreeDelegate(MessageTreeHandle(stateHandle))

    // Explicitly typed: inference cannot close the loop through conversationLoadDelegate.
    private val comparisonDelegate: ComparisonModeDelegate = ComparisonModeDelegate(
        handle = ComparisonHandle(stateHandle),
        messageRepository = messageRepository,
        reloadConversation = { id, onSettled -> conversationLoadDelegate.loadConversation(id, onSettled = onSettled) },
        currentModelSpecs = { configRepository.startupConfig.value?.modelSpecs },
    )
    private val contextProjectionDelegate = ContextProjectionDelegate(
        ContextProjectionHandle(stateHandle),
        agentRepository,
        endpointTokenRepository,
    )
    private val ownerTextDelegate = OwnerTextDelegate(OwnerTextHandle(stateHandle), messageRepository)
    private val toolPartDelegate = ToolPartHydrationDelegate(
        scope = viewModelScope,
        messageRepository = messageRepository,
        currentConversationId = { _uiState.value.conversationId },
        currentMessages = { _uiState.value.messages },
        currentAccountId = { activeAccountProvider.currentAccountId()?.value },
        defaultDispatcher = defaultDispatcher,
        isStreaming = { _uiState.value.isStreaming },
    )
    private val subagentHintsDelegate = SubagentHintsDelegate(
        scope = viewModelScope,
        messageRepository = messageRepository,
        currentAccountId = { activeAccountProvider.currentAccountId()?.value },
        defaultDispatcher = defaultDispatcher,
    )
    private val toolPartOverlay = ToolPartOverlay()
    private val searchDelegate = InConversationSearchDelegate(SearchHandle(stateHandle))
    private val jumpDelegate = JumpToMessageDelegate(JumpHandle(stateHandle), defaultDispatcher)

    /** Fires when a requested message isn't in the chat, for a "Message not found" snackbar. */
    val messageNotFound: Flow<Unit> = jumpDelegate.notFound

    // Channel-backed one-shot signal: a send arrived during Compare setup and was held. Surfaced as
    // a snackbar.
    private val _sendHeldForCompareSetup = Channel<Unit>(Channel.CONFLATED)
    val sendHeldForCompareSetup: Flow<Unit> = _sendHeldForCompareSetup.receiveAsFlow()
    val jumpBranchSwitched: Flow<BranchSwitch> = jumpDelegate.branchSwitched
    private val conversationActionsDelegate =
        ConversationActionsDelegate(
            handle = ConversationActionsHandle(stateHandle),
            conversationRepository = conversationRepository,
            shareRepository = shareRepository,
            shareLink = shareLinkControllerFactory.create(viewModelScope),
            draftRepository = draftRepository,
        )
    private val bookmarksDelegate = ChatBookmarksDelegate(BookmarksHandle(stateHandle), tagRepository)
    private val presetPromptDelegate =
        PresetPromptDelegate(
            PresetPromptHandle(stateHandle, ::persistComposerDraft),
            presetRepository,
            promptRepository,
        )
    private val skillPickerDelegate = SkillPickerDelegate(
        handle = SkillPickerHandle(stateHandle),
        skillsRepository = skillsRepository,
        userRepository = userRepository,
        configRepository = configRepository,
    )
    private val favoritesDelegate = FavoritesDelegate(FavoritesHandle(stateHandle), favoritesRepository)
    private val modelDelegate = ModelSelectionDelegate(
        handle = ModelSelectionHandle(stateHandle),
        configRepository = configRepository,
        agentRepository = agentRepository,
        settingsDataStore = settingsDataStore,
        permissionGate = permissionGate,
        connectivityObserver = connectivityObserver,
        preferLastUsedModel = preferenceStore.observe(AppPreferences.PreferLastUsedModel),
        initialAgentId = initialAgentId,
        initialEndpoint = initialEndpoint,
        initialModel = initialModel,
        initialSpec = initialSpec,
    )
    private val mcpServersDelegate =
        McpServersDelegate(ModelSelectionHandle(stateHandle), mcpRepository, toggleMcpServer = modelDelegate::toggleMcpServer)
    private val skillsToggleDelegate = SkillsToggleDelegate(SkillsToggleHandle(stateHandle), settingsDataStore)

    // The attached-machine workspace a chat's coding agents run on, and what each send carries.
    private val codeWorkspaceDelegate = CodeWorkspaceDelegate(
        handle = CodeWorkspaceHandle(stateHandle),
        configRepository = configRepository,
        roleRepository = roleRepository,
        environments = codeEnvironmentRepository,
        preferences = codeWorkspacePreferenceStore,
    )

    // The chat's pull request (a code chat started elsewhere): looked up, polled while in front.
    private val pullRequestDelegate = PullRequestDelegate(
        handle = PullRequestHandle(stateHandle),
        configRepository = configRepository,
        conversationRepository = conversationRepository,
        repository = pullRequestRepository,
    )

    // Run Code on a reply's fenced blocks; its own flow, read by the code blocks only.
    private val codeRunDelegate = CodeRunDelegate(
        scope = viewModelScope,
        repository = codeRunRepository,
        conversationId = { _uiState.value.conversationId },
        isTemporary = { _uiState.value.isTemporaryChat },
        onRunFailed = { _uiState.update { it.copy(error = ChatNotice.RUN_CODE_FAILED.marker()) } },
    )

    /** What the chat screen lends its replies' code blocks (`LocalCodeRunHost`), behind RUN_CODE. */
    val codeRunHost = CodeRunHost(
        runs = codeRunDelegate.state,
        onRun = codeRunDelegate::run,
        onShown = codeRunDelegate::ensureLoaded,
    )

    private val reasoningCatalogDelegate = ReasoningCatalogDelegate(
        handle = ReasoningHandle(stateHandle),
        repository = reasoningCapabilitiesRepository,
    )
    private val keyStatusDelegate = EndpointKeyStatusDelegate(
        handle = EndpointKeyHandle(stateHandle),
        keyRepository = keyRepository,
        clock = clock,
    )
    private val subagentTraceDelegate = SubagentTraceDelegate(SubagentHandle(stateHandle), json)
    private val attachmentPreviewDelegate =
        AttachmentPreviewDelegate(AttachmentPreviewHandle(stateHandle), fileRepository)
    private val newChatDraftKey = newChatDraftKey(initialChatProjectId)
    private val completionDelegate: SendCompletionDelegate = SendCompletionDelegate(
        handle = SendCompletionHandle(stateHandle),
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        draftRepository = draftRepository,
        modelDelegate = modelDelegate,
        treeDelegate = treeDelegate,
        tts = ttsDelegate,
        selectionHandoff = selectionHandoff,
        reloadConversation = { id -> conversationLoadDelegate.loadConversation(id) },
        newChatDraftKey = newChatDraftKey,
        carrySkillsToggle = { conversationId, isTemporary ->
            skillsToggleDelegate.carryToCreatedConversation(conversationId, isTemporary)
            if (!isTemporary) rememberWebSearch(conversationId)
        },
    )

    // Channel-backed one-shot signal: N queued follow-ups were dropped on drain because they were
    // composed under an account the user has since switched away from. Surfaced as a snackbar.
    private val _queuedMessagesDropped = Channel<Int>(Channel.BUFFERED)
    val queuedMessagesDropped: Flow<Int> = _queuedMessagesDropped.receiveAsFlow()

    // Channel-backed one-shot signal: N steers went to the follow-up queue instead of into the
    // reply (it finished first, or the run could not take them). Surfaced as a snackbar.
    private val _steersQueuedAsFollowUp = Channel<Int>(Channel.BUFFERED)
    val steersQueuedAsFollowUp: Flow<Int> = _steersQueuedAsFollowUp.receiveAsFlow()

    /** An interrupt that didn't go as asked (one-shot, like [steersQueuedAsFollowUp]; never `error`). */
    private val _steerNotices = Channel<SteerNotice>(Channel.BUFFERED)
    val steerNotices: Flow<SteerNotice> = _steerNotices.receiveAsFlow()

    // Explicitly typed: inference cannot close the loop through sendDispatchDelegate.
    private val queueDelegate: MessageQueueDelegate = MessageQueueDelegate(
        handle = QueueHandle(stateHandle),
        // Drained items send with their snapshotted config but LIVE lineage. We first wait for
        // the previous reply to settle into the tree (the Final-triggered Room reload is async),
        // so the optimistic insert chains onto the freshly-finalized assistant message rather
        // than the still-optimistic user turn. No upload-wait is needed — attachments were
        // already resolved to FileReferences at queue time.
        sendWithSpec = { spec, awaitSettle ->
            viewModelScope.launch {
                if (awaitSettle) sendDispatchDelegate.awaitReplySettled()
                // drainNext POPS before it sends, and this gate is allowed to refuse (no model
                // selected, readiness timeout). Without putting the item back, a refusal silently
                // destroys a queued message — including a steer that was re-homed here precisely
                // so it could not be lost.
                sendReadinessDelegate.runWhenSendReady(
                    onRefused = { queueOrchestrationDelegate.requeueRefusedDrain(spec) },
                ) {
                    sendDispatchDelegate.doSendWithSpec(spec)
                }
            }
        },
        activeAccountProvider = activeAccountProvider,
        onQueuedDropped = { count -> _queuedMessagesDropped.trySend(count) },
        markFilesUsed = { fileIds -> fileRepository.markFilesUsed(fileIds) },
        holdRenewalSupported = { fileRepository.supportsUsageHold() },
    )

    // Explicitly typed: inference cannot close the loop through queueOrchestrationDelegate.
    private val queuedTurnDelegate: QueuedTurnDelegate = QueuedTurnDelegate(
        handle = QueueHandle(stateHandle),
        repository = queuedTurnRepository,
        // The server started a run this client never asked for. Nothing else would notice it:
        // queued turns have no push channel, and the ordinary resume path only fires for a client
        // that was already streaming.
        onSuccessorOwed = { streamingManager.attachToServerStartedRun() },
        projectOrphan = { receipt -> queueOrchestrationDelegate.projectOrphanQueuedTurn(receipt) },
        onServerRowsCleared = { queueOrchestrationDelegate.tryResumeDrain() },
        onReceiptsApplied = { queueOrchestrationDelegate.onQueueReconciled() },
    )

    // --- Delegate-owned flows exposed to the UI ---
    val attachedFiles: StateFlow<List<AttachedFile>> get() = fileDelegate.attachedFiles
    val pdfPasswordPrompts: StateFlow<List<PdfPasswordPrompt>> get() = fileDelegate.pdfPasswordPrompts

    val shareLink: ShareLinkController get() = conversationActionsDelegate.shareLink

    /** The ⋮ menu's bookmark checklist on the open chat. */
    val bookmarkPicker: BookmarkPickerController get() = bookmarksDelegate.picker

    private data class TraceGateInputs(
        val conversationId: String?,
        val isStreaming: Boolean,
        val enabled: Boolean,
    )

    private data class BaseChatPrefs(
        val showImageDescriptions: Boolean,
        val dismissKeyboardOnSend: Boolean,
        val chatLayoutStyle: String,
        val showAvatars: Boolean,
        val showBubbles: Boolean,
    )

    private data class SttAndRendererPrefs(
        val latexRenderer: LatexRenderer,
        val autoSendAfterStt: Boolean,
        val sttEngine: String,
        val sttLanguage: String,
        val inlineArtifactPrefs: com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs,
    )

    // Combined in stages because Kotlin's `combine` maxes out at 5 args. Each stage
    // produces a typed sub-record, and they're folded into `ChatPreferences` at the end.
    // Adding a new pref: extend a sub-record (or add a third combine) — no positional casts.
    private val baseChatPrefs = combine(
        settingsDataStore.showImageDescriptions,
        settingsDataStore.dismissKeyboardOnSend,
        settingsDataStore.chatLayoutStyle,
        settingsDataStore.showAvatars,
        settingsDataStore.showBubbles,
    ) { imgDesc, dismissKb, layout, avatars, bubbles ->
        BaseChatPrefs(imgDesc, dismissKb, layout, avatars, bubbles)
    }

    private val sttAndRendererPrefs = combine(
        settingsDataStore.latexRenderer,
        settingsDataStore.autoSendAfterStt,
        settingsDataStore.sttEngine,
        settingsDataStore.sttLanguage,
        settingsDataStore.inlineArtifactPrefs,
    ) { latex, autoSendStt, sttEngine, sttLang, inlineArtifacts ->
        SttAndRendererPrefs(latex, autoSendStt, sttEngine, sttLang, inlineArtifacts)
    }

    // Third stage: a stored choice wins; with none, ask unless the prompt was already closed
    // this process (see SiteIconPromptSession).
    private val siteIconsState = combine(
        preferenceStore.observe(AppPreferences.SiteIcons),
        siteIconPromptSession.dismissed,
    ) { choice, dismissed ->
        when (choice) {
            true -> SiteIconsState.ON
            false -> SiteIconsState.OFF
            null -> if (dismissed) SiteIconsState.OFF else SiteIconsState.ASK
        }
    }

    val chatPreferences: StateFlow<ChatPreferences> = combine(
        baseChatPrefs,
        sttAndRendererPrefs,
        siteIconsState,
        settingsDataStore.thinkingOpenByDefault,
        preferenceStore.observe(AppPreferences.CollapseLongUserMessages),
    ) { base, sttRenderer, siteIcons, thinkingOpen, collapseLong ->
        ChatPreferences(
            showImageDescriptions = base.showImageDescriptions,
            dismissKeyboardOnSend = base.dismissKeyboardOnSend,
            chatLayoutStyle = base.chatLayoutStyle,
            showAvatars = base.showAvatars,
            showBubbles = base.showBubbles,
            latexRenderer = sttRenderer.latexRenderer,
            autoSendAfterStt = sttRenderer.autoSendAfterStt,
            sttEngine = sttRenderer.sttEngine,
            sttLanguage = sttRenderer.sttLanguage,
            inlineArtifactPrefs = sttRenderer.inlineArtifactPrefs,
            thinkingOpenByDefault = thinkingOpen,
            collapseLongUserMessages = collapseLong,
            siteIcons = siteIcons,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        // A store build's icons are on by default; seed that so they don't pop in on the first read.
        ChatPreferences(
            siteIcons = if (preferenceStore.initial(AppPreferences.SiteIcons) == true) SiteIconsState.ON else SiteIconsState.OFF,
        ),
    )

    // In-memory echo of the gauge-expanded toggle: a tap flips the UI synchronously instead of
    // waiting on the DataStore write round-trip (which would also make a quick second tap read
    // stale state). The persisted value only seeds the session until the first tap.
    private val contextGaugeExpandedOverride = MutableStateFlow<Boolean?>(null)

    // Folded first so the display combine below stays within Kotlin's 5-argument typed ceiling.
    private val contextUsagePrefs: Flow<ContextUsagePrefs> = combine(
        settingsDataStore.contextGaugeExpanded,
        contextGaugeExpandedOverride,
        settingsDataStore.effectiveContextSections,
        settingsDataStore.compactNudgeThreshold,
        settingsDataStore.compactNudgeSnoozes,
    ) { persisted, override, sections, threshold, snoozes ->
        ContextUsagePrefs(override ?: persisted, sections, threshold, snoozes)
    }

    private val chatDisplayPrefs: Flow<ChatDisplayPrefs> = combine(
        settingsDataStore.chatHeaderContent,
        settingsDataStore.chatHeaderAlignment,
        settingsDataStore.contextBarPlacement,
        contextUsagePrefs,
        settingsDataStore.duringRunAction,
    ) { content, alignment, contextBarPlacement, contextPrefs, duringRunAction ->
        ChatDisplayPrefs(
            content,
            alignment,
            contextBarPlacement,
            contextPrefs,
            duringRunAction,
        )
    }

    private val replyAvatarMemo = ReplyAvatarMemo()
    private val settingChipsMemo = SettingChipsMemo()

    val uiState: StateFlow<ChatUiState> = combine(
        _uiState,
        // Paired off the backing-state path, so a fetched tool part adds no hop to every state emission.
        combine(serverDataStore.currentUrlFlow, toolPartDelegate.loaded, subagentHintsDelegate.hints, ::Triple),
        settingsDataStore.chatFontSize,
        settingsDataStore.starredModelsDisplay,
        chatDisplayPrefs,
    ) { state, (url, loadedToolParts, subagentHints), fontSize, starredDisplay, displayPrefs ->
        // Fetched full tool calls go over the display path here, after it is built and before the
        // UI collects it: a fetch landing never rebuilds the tree or touches the streaming anchor,
        // and delegates keep reading the backing state's previews. Everything below builds on
        // `hydrated`: a field copied from `state` would put the previews back.
        val hydrated = toolPartOverlay.apply(state, loadedToolParts, subagentHints)
        hydrated.copy(
            prefs = ChatPrefsState(
                serverUrl = url,
                chatFontSize = fontSize,
                starredModelsDisplay = starredDisplay,
                chatHeaderContent = displayPrefs.content,
                chatHeaderAlignment = displayPrefs.alignment,
                contextBarPlacement = displayPrefs.contextBarPlacement,
                contextGaugeExpanded = displayPrefs.context.gaugeExpanded,
                contextSections = displayPrefs.context.sections,
                compactNudgeThreshold = displayPrefs.context.compactNudgeThreshold,
                compactNudgeSnoozes = displayPrefs.context.compactNudgeSnoozes,
                duringRunAction = displayPrefs.duringRunAction,
                // Mirrored into the backing state by its own collector, which the send reads.
                webSearchFirst = state.prefs.webSearchFirst,
            ),
            // The user record carries a RELATIVE `/images/…` avatar, which Coil has no fetcher for.
            // Resolved here rather than in `loadUserProfile` because the base URL arrives on its own
            // flow: resolving at load time races it and would pin an unloadable path for the session.
            // Idempotent — an already-absolute URL (a social-login avatar) passes through untouched.
            account = state.account.copy(
                userAvatarUrl = resolveAvatarUrl(state.account.userAvatarUrl, url),
            ),
            // Reply avatars, resolved here for the same reason: the URL, the endpoint configs (a custom
            // endpoint's served providerId) and the agents (an agent's avatar) each arrive on their own flow.
            content = hydrated.content.copy(
                displayMessages = replyAvatarMemo.resolve(
                    hydrated.content.displayMessages,
                    state.selectedEndpoint,
                    url,
                    state.endpointConfigs,
                    state.agents,
                    fallbackAgentId = state.selectedModel.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS },
                ),
            ),
            avatars = state.replyAvatars(url),
            // The composer's settings line, mapped here once rather than on every chrome recomposition.
            settingChips = settingChipsMemo.resolve(state.settingChipSlices) { state.settingChipInputs },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    // Channel-backed for exactly-once snackbar delivery across rotation.
    private val _userKeyErrors = Channel<UserKeyError>(Channel.BUFFERED)
    val userKeyErrors: Flow<UserKeyError> = _userKeyErrors.receiveAsFlow()

    private val conversationLoadDelegate = ConversationLoadDelegate(
        handle = ConversationLoadHandle(stateHandle),
        newChatDraftKey = newChatDraftKey,
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        draftRepository = draftRepository,
        defaultDispatcher = defaultDispatcher,
        applyConversationModel = modelDelegate::applyConversationModel,
        // A handed-off new chat already holds what its first turn was sent with.
        wantsStoredParameters = { !isHandedOffNewChat },
        rememberedWebSearch = conversationWebSearchStore::get,
        restoreParameters = { conversation, remembered ->
            // This device's last switch for this chat decides, unless the server's `web_search` is true
            // (built-in search, turned on in web since). Not under a spec (see rememberWebSearch).
            // Written with the parameters, so the load's "did the user change it meanwhile" check sees it.
            val restored = _uiState.value.selection.reopenedParameters(conversation)
            val switch = remembered.takeIf { _uiState.value.selectedSpec == null && conversation.webSearch != true }
            modelDelegate.updateModelParameters(
                switch?.let { restored.copy(webSearch = it) } ?: restored,
                restoring = true,
            )
            val keepsBuiltIn = _uiState.value.selectedSpec == null && conversation.webSearch == true
            _uiState.update { it.copy(selection = it.selection.copy(keepsBuiltInSearch = keepsBuiltIn)) }
        },
        onModelLoadSettled = {
            modelDelegate.conversationModelLoaded = true
            modelDelegate.refilterModels(isNewConversation)
        },
        rehydrateComparison = comparisonDelegate::rehydrateFromMessage,
        onMessagesSettled = attachmentPreviewDelegate::onMessagesSettled,
        currentModelSpecs = { configRepository.startupConfig.value?.modelSpecs },
        currentAccountId = { activeAccountProvider.currentAccountId()?.value },
        pullRequestsAvailable = {
            pullRequestsAvailable(configRepository.startupConfig.value, configRepository.detectedBackendVersion.value)
        },
        watchSelection = modelDelegate.selectionWatches::watch,
        lastCachedTurn = { completionDelegate.lastCachedTurn },
    )

    private val uploadIntakeDelegate = UploadIntakeDelegate(
        handle = UploadIntakeHandle(stateHandle),
        fileHandler = fileDelegate,
        settingsDataStore = settingsDataStore,
        awaitAgentProvider = { modelDelegate.awaitSelectedAgentProvider() },
        // Only the EXPOSED combine folds serverDataStore.currentUrlFlow into prefs.serverUrl.
        serverUrl = { uiState.value.serverUrl },
        turnOnToolForUpload = ::turnOnToolForUpload,
    )

    private val chatConfigDelegate = ChatConfigDelegate(
        handle = ChatConfigHandle(stateHandle),
        configRepository = configRepository,
        roleRepository = roleRepository,
        fileRepository = fileRepository,
        userRepository = userRepository,
        memoryRepository = memoryRepository,
        webSearchReadiness = webSearchReadiness,
    )

    private val pendingActionDelegate = PendingActionDelegate(
        handle = PendingActionHandle(stateHandle),
        chatRepository = chatRepository,
        requestBuilder = requestBuilder,
        resumeFailureMessage = { message -> message ?: "Could not resume the paused response." },
        fingerprintRejectedMessage = {
            "This paused response was started with a different setup, so it can't be answered here."
        },
        restoreAnswer = { text -> conversationLoadDelegate.restoreUnsentInput(text) },
        resumePinStore = resumePinStore,
    )

    // Explicitly typed: inference cannot close the loop through sendDispatchDelegate.
    private val steeringDelegate: SteeringDelegate = SteeringDelegate(
        handle = SteeringHandle(stateHandle),
        chatRepository = chatRepository,
        // Snapshots the CURRENT send config. Only used for steers the server reported (a
        // reconnect, another device) — steers this client sent carry the spec they were
        // composed with, so a model switch mid-run never retro-edits them.
        buildFollowUp = { text -> sendDispatchDelegate.buildSendSpec(text) },
        // Always the queue, never the live-send path: `runWhenSendReady` is allowed to REFUSE
        // (no model selected, or a readiness timeout), and a degraded steer has nowhere to put
        // the text back — its composer was cleared at send time. `enqueueSpec` self-drains the
        // moment the run is over, so an ended run still sends immediately; a paused queue holds
        // the item for the user's own "Send queued" instead of dropping it.
        enqueueFollowUp = { spec -> queueOrchestrationDelegate.enqueueSpec(spec) },
        // Deliberately NOT `enqueueSpec`: its self-drain would auto-send a parked steer on
        // conversation open, where the run is already over. See SteeringDelegate.reclaimParked.
        enqueueParked = queueDelegate::enqueue,
        pauseQueue = { queueDelegate.pause() },
        holdQueueForRefusal = { localId -> queueDelegate.holdForRefusal(localId) },
        // A held queue (behind a Stop, a stream error, a refused steer) does not self-drain, so "queued to
        // send after it" would be untrue; the row waits behind "Send queued", which is visible.
        onQueuedAsFollowUp = { count ->
            if (!_uiState.value.isQueuePaused) _steersQueuedAsFollowUp.trySend(count)
        },
        isStreaming = { _uiState.value.isStreaming },
        restageQuotes = { quotes -> sendDispatchDelegate.restagePendingQuotes(quotes) },
        onSteerNotice = { notice -> _steerNotices.trySend(notice) },
        armTimeoutMs = { _uiState.value.gates.steerArmConfirmationMs },
    )

    private val streamingManager = StreamingManagerDelegate(
        handle = StreamingHandle(stateHandle),
        chatRepository = chatRepository,
        activeAccountProvider = activeAccountProvider,
        connectivityObserver = connectivityObserver,
        comparisonDelegate = comparisonDelegate,
        liveReply = LiveReplyDelegate(
            StreamingHandle(stateHandle),
            subagentTraceDelegate,
            attachmentPreviewDelegate,
            endpointTokenRepository,
        ),
        completionDelegate = completionDelegate,
        queueDelegate = queueDelegate,
        pendingActionDelegate = pendingActionDelegate,
        steeringDelegate = steeringDelegate,
        host = object : StreamingHost {
            override val isNewConversation get() = this@ChatViewModel.isNewConversation
            override val isHandedOffNewChat get() = this@ChatViewModel.isHandedOffNewChat
            override fun emitUserKeyError(error: UserKeyError) {
                _userKeyErrors.trySend(error)
            }
            override fun reloadConversation(conversationId: String) =
                conversationLoadDelegate.loadConversation(conversationId)
            override fun reloadRestoringUnsaved(conversationId: String, unsent: Message) =
                conversationLoadDelegate.loadConversation(conversationId, unsavedTurn = unsent)
            override fun unsendTurn(optimisticId: String?) {
                val unsent = optimisticId?.let { id -> _uiState.value.messages.firstOrNull { it.messageId == id } }
                treeDelegate.unsendOptimisticTurn(optimisticId)
                // A file-only turn has no text but may carry quotes and picked skills to put back.
                unsent?.let {
                    conversationLoadDelegate.restoreUnsentInput(
                        it.text,
                        it.quotes.orEmpty(),
                        it.manualSkills.orEmpty(),
                    )
                }
            }
            override fun surfaceSpecRefusal() = surfaceModelSheet(SendBlockReason.SelectModel)
        },
    )

    private val sendReadinessDelegate = SendReadinessDelegate(
        handle = SendReadinessHandle(stateHandle),
        surfaceModelSheet = { reason -> surfaceModelSheet(reason) },
    )

    private val sendDispatchDelegate = SendDispatchDelegate(
        handle = SendDispatchHandle(stateHandle),
        chatRepository = chatRepository,
        draftRepository = draftRepository,
        settingsDataStore = settingsDataStore,
        fileHandler = fileDelegate,
        requestBuilder = requestBuilder,
        activeAccountProvider = activeAccountProvider,
        beginStreaming = { optimisticUserMessageId, turnSpec ->
            streamingManager.beginStreaming(
                isEdit = false,
                optimisticUserMessageId = optimisticUserMessageId,
                turnSpec = turnSpec,
            )
        },
        launchStream = streamingManager::launchStream,
        onComparisonSendStart = comparisonDelegate::onSendStart,
        buildAddedConvo = comparisonDelegate::buildAddedConvo,
        chatProjectId = initialChatProjectId,
        newChatDraftKey = newChatDraftKey,
        turnSkillsOn = skillsToggleDelegate::turnOn,
        loadSkillCatalog = { skillPickerDelegate.ensureLoaded(newTrigger = true) },
        codeWorkspace = codeWorkspaceDelegate::submissionForSend,
        sendsWithoutWorkspace = codeWorkspaceDelegate::sendsWithoutWorkspace,
    )

    private val draftItemsDelegate = DraftItemsDelegate(
        handle = DraftItemsHandle(stateHandle),
        fileHandler = fileDelegate,
        sendDispatchDelegate = sendDispatchDelegate,
    )

    private val queueOrchestrationDelegate = QueueOrchestrationDelegate(
        handle = QueueOrchestrationHandle(stateHandle),
        queueDelegate = queueDelegate,
        queuedTurnDelegate = queuedTurnDelegate,
        uploadIntakeDelegate = uploadIntakeDelegate,
        sendDispatchDelegate = sendDispatchDelegate,
        requestBuilder = requestBuilder,
        activeAccountProvider = activeAccountProvider,
        fileHandler = fileDelegate,
        generationEpoch = { pendingActionDelegate.generationEpoch },
        queueAnchor = streamingManager::queueAnchorFor,
    )

    private val localQueueContinuity = LocalQueueContinuityDelegate(
        handle = QueueHandle(stateHandle),
        queueStates = _uiState.map { it.queue },
        stash = localQueueStash,
        draftRepository = draftRepository,
        activeAccountProvider = activeAccountProvider,
        isTemporary = { _uiState.value.isTemporaryChat },
        putBackInComposer = ::putBackInComposer,
    )

    /** Recovered text goes after the chat's saved draft (read first, so it is not shadowed). */
    private suspend fun putBackInComposer(text: String) {
        val draft = _uiState.value.conversationId?.let { draftRepository.awaitDraft(it) }.orEmpty()
        _uiState.update {
            val base = it.composer.inputText.ifBlank { draft }
            val merged = listOf(base, text).filter { part -> part.isNotBlank() }.joinToString("\n\n")
            it.copy(composer = it.composer.copy(inputText = merged))
        }
        persistComposerDraft(_uiState.value.inputText)
    }

    private val editingDelegate = MessageEditingDelegate(
        handle = MessageEditingHandle(stateHandle),
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        treeDelegate = treeDelegate,
        streamingManager = streamingManager,
        requestBuilder = requestBuilder,
        runWhenSendReady = sendReadinessDelegate::runWhenSendReady,
        onComparisonSendStart = comparisonDelegate::onSendStart,
        buildAddedConvo = comparisonDelegate::buildAddedConvo,
        codeWorkspace = codeWorkspaceDelegate::submissionForSend,
    )

    /** True when this ViewModel was opened for a brand-new chat (no conversationId from navigation). */
    private val isNewConversation: Boolean

    /**
     * True when this ViewModel took the [NewChatSelectionHandoff] for a conversation just
     * created from the NewChat landing. Navigation to Chat(id) fires at the `created` SSE
     * event and resets the landing VM, so THIS VM is the one whose resumed stream sees the
     * first Final — with [isNewConversation] false. This flag keeps new-chat-only work
     * (title generation) running for the handed-off chat.
     */
    private val isHandedOffNewChat: Boolean

    init {
        val conversationId = initialConversationId
        isNewConversation = conversationId == null
        if (conversationId != null) {
            _uiState.update {
                it.copy(
                    // SECURITY: do not remove — temp-chat data-at-rest guard. Seeded from the Chat
                    // route (durable across process death), so a restored temp Chat(id) stays
                    // temp-aware from the first frame: this short-circuits loadConversation and
                    // loadConversationModel below, both of which would otherwise upsert the
                    // server-hidden conversation to Room, and makes follow-up sends temporary.
                    conversation = it.conversation.copy(
                        conversationId = conversationId,
                        isTemporaryChat = initialIsTemporary,
                    ),
                    // A temp chat restored across process death has no in-memory handoff to seed its
                    // messages (they're gone with the session), and its Room read is guarded off — so
                    // land on an empty ACTIVE chat rather than a LOADING spinner that never resolves.
                    content = it.content.copy(
                        screenState = if (initialIsTemporary) ChatScreenState.ACTIVE else ChatScreenState.LOADING,
                        // Settled by resumeActiveStreamIfNeeded below, which learns of a paused run.
                        runStatusSettled = false,
                    ),
                )
            }
            // If we arrived here straight from the NewChat landing (the common case for a
            // just-created chat), the landing VM staged the exact (endpoint, model) it sent.
            // Apply it up front so the header/selection is correct immediately and the
            // racy loadConversationModel GET below can't clobber it with a fallback guess
            // when the server hasn't persisted the conversation yet (the created-before-save
            // race). See NewChatSelectionHandoff.
            val handoff = selectionHandoff.take(conversationId)
            isHandedOffNewChat = handoff != null
            if (handoff != null) {
                Diag.d(
                    tag = "ModelSel",
                    attrs = mapOf(
                        "endpoint" to handoff.choice.endpoint,
                        "model" to (handoff.choice.model ?: "null"),
                        "sentWithoutWorkspaceNotice" to handoff.sentWithoutWorkspaceNotice.toString(),
                    ),
                ) { "handoff applied for $conversationId" }
                modelDelegate.applyResolvedConversationModel(handoff.choice.endpoint, handoff.choice.model, handoff.choice.spec)
                handoff.modelParameters?.let { modelDelegate.updateModelParameters(it, restoring = true) }
                handoff.specTools?.let { tools ->
                    _uiState.update {
                        if (it.selectedSpec != tools.spec) it else it.copy(selection = it.selection.copy(specTools = tools))
                    }
                }
                handoff.skillsToggle?.let { skillsToggleDelegate.seed(conversationId, it) }
                if (handoff.sentWithoutWorkspaceNotice) {
                    _uiState.update { it.copy(error = ChatNotice.CODE_WORKSPACE_SENT_WITHOUT.marker()) }
                }
                // Chips the first send held back stay staged here, still saying why.
                if (handoff.stagedManualSkills.isNotEmpty()) {
                    sendDispatchDelegate.stageHeldBackManualSkills(
                        handoff.stagedManualSkills,
                        notice = handoff.skillsNotSentNotice,
                    )
                    // The send rechecks the chips against the catalog (active, invocable, the
                    // agent's scope), and holds them back until there is one.
                    skillPickerDelegate.ensureLoaded(newTrigger = true)
                }
            }
            // Seed the optimistic user message the landing VM sent, so it stays visible while the
            // resumed stream runs. The server doesn't persist the request message until the reply
            // completes, so the loadConversation read-through below would otherwise show only the
            // streaming bubble with no user message above it. loadConversation reconciles the seed
            // away by id once the server's copy lands. See NewChatSelectionHandoff.
            handoff?.optimisticUserMessage?.let { optimistic ->
                _uiState.update {
                    val seeded = listOf(optimistic)
                    it.copy(
                        content = it.content.copy(
                            messages = seeded,
                            displayMessages = buildActiveMessagePath(seeded, it.activeBranches),
                            pendingResumeUserMessage = optimistic,
                            // Show the message immediately instead of the LOADING spinner set above —
                            // we already have content to render while the stream resumes.
                            screenState = ChatScreenState.ACTIVE,
                        ),
                    )
                }
            }
            conversationLoadDelegate.loadConversation(conversationId, cacheFirst = true)
            initialTargetMessageId?.let { jumpDelegate.start(it, initialHighlightQuery, initialTargetInReasoning) }
            conversationLoadDelegate.loadConversationModel(conversationId)
            conversationLoadDelegate.restoreDraft(conversationId)
            // Before the attach below, so follow-ups queued before the chat was left are back in
            // the queue when the run they wait on is found still going.
            localQueueContinuity.start(conversationId)
            // Check if there's an active stream for this conversation (e.g. when
            // navigating here from NewChat immediately after sending). If so,
            // resume it so the user sees streaming content on this screen.
            streamingManager.resumeOnOpen(conversationId, onAttached = localQueueContinuity::onRunAttached)
        } else {
            isHandedOffNewChat = false
            // For new chats, mark conversationModelLoaded so refilterModels
            // doesn't wait for a conversation model that will never arrive.
            modelDelegate.conversationModelLoaded = true
            conversationLoadDelegate.restoreDraft(newChatDraftKey)
        }

        // Content shared in from another app, addressed to this chat by the navigation layer.
        // Covers both a share that launched the app (staged before this screen composed, drained
        // on subscribe) and one arriving while this ViewModel is already on screen.
        val handoffKey = chatHandoffKey(initialConversationId, initialChatProjectId)
        viewModelScope.launch {
            shareConsumer.sharesFor(handoffKey).collect(::applyShare)
        }

        // Collect server-file picker results routed to this conversation's own channel
        // (keyed by id; null for the NewChat landing). Consumed here in common code rather
        // than per-platform in the screen — mirroring how NewChatSelectionHandoff is wired.
        viewModelScope.launch {
            serverFileSelectionHandoff.selectionsFor(handoffKey).collect { files ->
                uploadIntakeDelegate.attachServerFiles(files)
            }
        }

        // Seed/refresh the context-usage gauge for a loaded or snapshot-less branch (v0.8.7).
        contextProjectionDelegate.start()
        ownerTextDelegate.start()
        toolPartDelegate.start(
            messages = _uiState.map { it.conversationId to it.messages },
            streaming = _uiState.map { it.isStreaming },
            connectivity = connectivityObserver.isConnected,
        )
        // Asked for what is on screen: the display path, not every stored branch.
        subagentHintsDelegate.start(
            messages = _uiState
                .map { it.conversationId to it.content.displayMessages }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second === b.second }
                .map { (id, nodes) -> id to nodes.map { it.message } },
            connectivity = connectivityObserver.isConnected,
        )
        // The Skills toggle follows the slot this chat maps to (conversation, new chat, temp, spec).
        skillsToggleDelegate.start()
        codeWorkspaceDelegate.start()
        pullRequestDelegate.start()
        // The Thinking chip's catalog, and dropping a staged choice the selection stops accepting.
        reasoningCatalogDelegate.start()
        loadSkillCatalogWhenPickerOpens()
        clearCompactNudgeSnoozeWhenBelowThreshold()

        // Queued turns are reconciled by polling, so the poll has to be (re)aimed whenever the
        // conversation it is about changes — including the moment a new chat's id resolves, which
        // is the first point a follow-up can become server-owned.
        viewModelScope.launch {
            _uiState
                .map { Triple(it.conversationId, it.selectedEndpoint, it.gates.serverQueueSupported) }
                .distinctUntilChanged()
                .collect { queueOrchestrationDelegate.refreshQueuedTurns() }
        }

        // Single authority for a new chat's initial model selection. Continuous so
        // the retained NewChat landing VM re-syncs to last-used when it changes
        // (a model picked later inside a conversation), and deterministic so the
        // selection no longer races between the last-used read, agent auto-select,
        // and model fallbacks. No-ops for existing conversations (loadConversationModel
        // owns those). See ModelSelectionDelegate.seedInitialSelection.
        modelDelegate.seedInitialSelection(isNewConversation)

        // Resolves the selected agent's provider, which upload routing needs and which the agent
        // list can't supply (its projection omits the field). Continuous — the selection moves
        // well after startup.
        modelDelegate.observeSelectedAgentProvider()

        viewModelScope.launch {
            configRepository.endpointConfigs.collect { configs ->
                _uiState.update { it.copy(selection = it.selection.copy(endpointConfigs = configs)) }
                modelDelegate.refilterModels(isNewConversation)
                keyStatusDelegate.recomputeFor(configs)
                // If code interpreter is no longer available, remove it from enabled tools and
                // from what was added on top of a spec
                val agentsCapabilities = configs[EndpointConstants.AGENTS]?.capabilities
                if (agentsCapabilities != null && ToolConstants.EXECUTE_CODE !in agentsCapabilities) {
                    _uiState.update {
                        val specTools = it.selection.specTools
                        val code = ToolConstants.CODE_INTERPRETER
                        if (code in it.enabledTools || (specTools != null && code in specTools.added)) {
                            it.copy(
                                selection = it.selection.copy(
                                    enabledTools = it.enabledTools - code,
                                    specTools = specTools?.copy(added = specTools.added - code),
                                ),
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }

        // Mid-run steering (v0.8.8-rc1). Version-gated rather than self-proving: unlike a HITL
        // pause, which the server pushes, steering has to be OFFERED before any server has said
        // anything about it. Plain version compare since the rc1 tag shipped. Failing closed here
        // just leaves the composer queueing mid-run, which every supported server handles.
        viewModelScope.launch {
            configRepository.detectedBackend.collect { detected ->
                val supported = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8-rc1",
                )
                // Fail-SAFE rather than fail-closed, unlike steering above: suppressed only on a
                // build that resolved to a tag below the routes. A 0.8.6/0.8.7 server renders
                // subagent trace cards, so this is what keeps the "View thread" row off exactly
                // the servers that cannot serve it, without hiding it from an unplaceable one.
                val subagentThreads = !BackendVersion.featureSupport(
                    detected = detected,
                    minVersion = "0.8.8-rc2",
                ).isRuledOut
                val serverQueue = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8-rc2",
                    landedDate = "2026-08-31",
                )
                // queued-turns/v2 (163c1ad93, landed 2026-09-25): dated the day AFTER landing.
                val serverQueueApprovalMode = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8",
                    landedDate = "2026-09-26",
                )
                // restoreAgentTriggerModelSpec (20701bed0, landed 2026-10-22): dated the day AFTER landing.
                val serverQueueEnforcedAgentSpec = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.9-rc1",
                    landedDate = "2026-10-23",
                )
                // thinkingDisplay `updates` (40bb16ed, landed 2026-09-28): PRESENT only, dated the day
                // AFTER landing. Keep in step with AgentCapabilitiesDelegate; see VERSION_GATES.md.
                val thinkingDisplayUpdates = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8",
                    landedDate = "2026-09-29",
                )
                // resolveUploadEndpoint (ec15075dc, landed 2026-09-09 03:28 UTC): PRESENT only, dated
                // the day AFTER landing. See VERSION_GATES.md.
                val providerScopedUploads = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8-rc3",
                    landedDate = "2026-09-10",
                )
                _uiState.update {
                    it.copy(
                        selection = it.selection.copy(thinkingDisplayUpdatesSupported = thinkingDisplayUpdates),
                        gates = it.gates.copy(
                            steeringSupported = supported,
                            subagentThreadsSupported = subagentThreads,
                            serverQueueSupported = serverQueue,
                            serverQueueApprovalModeSupported = serverQueueApprovalMode,
                            serverQueueEnforcedAgentSpecSupported = serverQueueEnforcedAgentSpec,
                            providerScopedUploadsSupported = providerScopedUploads,
                            backendVersion = detected?.version,
                        ),
                    )
                }
            }
        }

        // The during-run preference is folded into `prefs` by the `uiState` combine below, but that
        // copy exists only on the EXPOSED state. `sendDuringRun` decides from `_uiState`, which
        // carries `ChatPrefsState()`'s default — so without this collector the send reads that default
        // whatever the user chose, while the very same button renders the user's choice (it takes
        // its icon from the exposed state). Behaviour must never be decided from a slice
        // only the edge populates.
        viewModelScope.launch {
            settingsDataStore.duringRunAction.collect { action ->
                _uiState.update { it.copy(prefs = it.prefs.copy(duringRunAction = action)) }
            }
        }
        // Decides which web search a send runs, so it too lives on the backing state.
        viewModelScope.launch {
            preferenceStore.observe(AppPreferences.WebSearchFirst).collect { first ->
                _uiState.update { it.copy(prefs = it.prefs.copy(webSearchFirst = first)) }
            }
        }

        viewModelScope.launch {
            // refilterModels publishes the filtered availableModels into state; no
            // need to write the raw map first (it would only be overwritten).
            configRepository.availableModels.collect {
                modelDelegate.refilterModels(isNewConversation)
            }
        }

        // Gate the `xhigh` and `max` reasoning-effort dropdown values to v0.8.5-rc1+ servers.
        // Older servers reject the unknown enums at request time. See VERSION_GATES.md.
        viewModelScope.launch {
            configRepository.detectedBackendVersion.collect { version ->
                val supported = version != null &&
                    BackendVersion.isCompatibleOrNewer(version, "0.8.5-rc1")
                _uiState.update { it.copy(selection = it.selection.copy(extendedEffortSupported = supported)) }
            }
        }

        viewModelScope.launch {
            val endpointsResult = configRepository.fetchEndpoints()
            if (endpointsResult is Result.Error) {
                _uiState.update {
                    it.copy(error = endpointsResult.message ?: "Could not load endpoint configuration")
                }
                return@launch
            }
            val modelsResult = configRepository.fetchModels()
            if (modelsResult is Result.Error) {
                _uiState.update {
                    it.copy(error = modelsResult.message ?: "Could not load available models")
                }
            }
        }

        // Restore MCP server and tool selections from DataStore so they
        // survive the NewChat -> Chat(id) navigation re-creation.
        viewModelScope.launch {
            val mcpServers = settingsDataStore.selectedMcpServers.first()
            val tools = settingsDataStore.enabledTools.first()
            if (mcpServers.isNotEmpty() || tools.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        selection = it.selection.copy(
                            selectedMcpServerNames = mcpServers,
                            enabledTools = tools,
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            settingsDataStore.artifactsMode.distinctUntilChanged().collect { mode ->
                _uiState.update { it.copy(selection = it.selection.copy(artifactsMode = mode)) }
            }
        }

        presetPromptDelegate.loadPresets()
        // Favorites is user-personal (not server-permission-gated upstream); load eagerly
        // so the chat-side pin stars and Settings → Favorites stay in sync from cold start.
        favoritesDelegate.load()
        chatConfigDelegate.loadUserProfile()
        chatConfigDelegate.observeMemoriesPreference()
        chatConfigDelegate.loadFlags()
        chatConfigDelegate.loadWebSearchReadiness()
        rememberWebSearchChanges()
        observeTraceAvailability()
        chatConfigDelegate.loadFileConfig()
        voiceDelegate.loadSpeechConfig()

        // Gated loads share a single 5-second role-await budget so offline/timeout
        // launches don't serialize into N×5s. `role?.hasAccess(...) != false`
        // preserves permissive default: null role (timeout/never-loaded) → true,
        // missing type/action → true, explicit false → false.
        viewModelScope.launch {
            val role = permissionGate.awaitRole()
            if (role?.hasAccess(PermissionType.PROMPTS, Permission.USE) != false) {
                presetPromptDelegate.loadAvailablePrompts()
                promptsUseAllowed = true
            }
            if (role?.hasAccess(PermissionType.MCP_SERVERS, Permission.USE) != false) {
                mcpServersDelegate.load()
            }
            // Always call loadAgents — it self-gates on the AGENTS.USE permission and
            // flips its agentsLoaded flag on every path (including denial). Skipping it
            // here would leave the flag false and park the seeder on the agents tier
            // forever (no model on the landing) for agents-denied users.
            modelDelegate.loadAgents(isNewConversation)
        }
    }

    // ── Core chat flow ──────────────────────────────────────────────

    fun switchBranch(parentMessageId: String, siblingIndex: Int) =
        treeDelegate.switchBranch(parentMessageId, siblingIndex)

    fun onInputChanged(text: String) {
        val canPickSkills = _uiState.value.canPickSkills
        val wasListingSkills = pickerListsSkills(_uiState.value.inputText, canPickSkills)
        val wasListingModels = parsePickerQuery(_uiState.value.inputText, canPickSkills)?.scope == PickerScope.MODEL
        _uiState.update { it.copy(composer = it.composer.copy(inputText = text)) }
        // `/model ` is a model selector too, so it takes the selector's self-heals as it opens.
        if (!wasListingModels && parsePickerQuery(text, canPickSkills)?.scope == PickerScope.MODEL) prepareModelSelector()
        // Opening the picker starts the load (loadSkillCatalogWhenPickerOpens); typing on in an
        // open one only picks up a catalog gone stale, never retries a failed load.
        if (wasListingSkills && pickerListsSkills(text, canPickSkills)) {
            skillPickerDelegate.ensureLoaded(newTrigger = false)
        }
        persistComposerDraft(text)
    }

    /**
     * Saves composer text as this chat's draft. Every write of the user's own text goes through
     * here (typing, an inserted prompt, dictation, a share), so reopening the chat brings back what
     * was on screen. Writes that only stage text for an immediate send, or swap in a queued item for
     * editing, deliberately skip it.
     */
    private fun persistComposerDraft(text: String) {
        // While editing a queued item the composer holds that item, not the persisted draft —
        // don't overwrite the on-disk new-message draft (it's restored on commit/cancel).
        if (_uiState.value.isEditingQueued) return
        val draftKey = _uiState.value.conversationId ?: newChatDraftKey
        viewModelScope.launch {
            draftRepository.saveDraft(draftKey, text)
        }
    }

    /**
     * Starts the skill catalog load whenever a picker listing skills is on screen where a skill
     * could be picked: a leading `/`, `/skill ` or `$` and [ChatUiState.canPickSkills]. Driven by
     * the state rather than by a keystroke, so the picker also loads when its text arrives another
     * way (a restored draft, a failed send's text put back) or when the gate opens under a trigger
     * already typed (the role or config arriving late, a switch away from an assistants endpoint).
     */
    private fun loadSkillCatalogWhenPickerOpens() {
        viewModelScope.launch {
            _uiState
                // Only the slices the gate reads, so a streaming flush costs a few reference compares.
                .distinctUntilChangedBy { Triple(it.composer.inputText, it.gates, it.selection) }
                .map { pickerListsSkills(it.inputText, it.canPickSkills) }
                .distinctUntilChanged()
                .collect { open -> if (open) skillPickerDelegate.ensureLoaded(newTrigger = true) }
        }
    }

    private fun applyShare(shareData: ShareData) {
        Logger.d { "applyShare: text=${shareData.text != null}, files=${shareData.fileRefs.size}" }

        if (!shareData.text.isNullOrBlank()) {
            // Appended, never assigned: the composer may already hold a restored draft or something
            // half-typed, and a share is one more thing the user wants to send — not a reason to
            // drop what is already there.
            _uiState.update {
                val existing = it.composer.inputText
                val merged = if (existing.isBlank()) shareData.text else "$existing\n${shareData.text}"
                it.copy(composer = it.composer.copy(inputText = merged))
            }
            persistComposerDraft(_uiState.value.inputText)
        }

        if (shareData.fileRefs.isNotEmpty()) {
            // Always auto-routed, never prompted: this fires on cold start, before the endpoint
            // configs and the agent's provider have resolved, so a prompt here would both
            // interrupt and decide against context that isn't there yet.
            //
            // It must still go through the same intake, though. This flow also delivers shares
            // that arrive while the screen is already up, and routing without waiting on the
            // agent's provider sends every shared document down the provider path — the silent
            // drop this feature exists to fix, and a disagreement with the same file picked from
            // the "+" menu a second later.
            uploadIntakeDelegate.intakePickedFiles(shareData.fileRefs, prompt = false)
        }
    }

    // --- Message sending ---

    fun sendMessage() {
        // In queued-edit mode the composer holds a queued item, not a new message — a send
        // (e.g. voice auto-send, which bypasses the UPDATE button) commits the edit instead of
        // live-sending, so the edit session is never orphaned.
        if (_uiState.value.isEditingQueued) {
            queueOrchestrationDelegate.commitQueuedEdit()
            return
        }
        if (_uiState.value.isStreaming) return
        // The composer's Send is disabled through Compare setup, but voice auto-send arrives here
        // without it: keep the text and say why it didn't go.
        if (!_uiState.value.canStartRun) {
            _sendHeldForCompareSetup.trySend(Unit)
            return
        }
        val text = _uiState.value.inputText.trim()
        uploadIntakeDelegate.withUploadGate(text) {
            sendReadinessDelegate.runWhenSendReady { sendDispatchDelegate.sendNow(it) }
        }
    }

    /**
     * An MCP App's approved `ui/message`: sends only [text] as a new user turn and leaves the
     * composer as it is. Refused (false) while a reply runs, or before a run can start, as web's
     * `ask` refuses; once it returns true the turn is the chat's and a later App abort can't undo it.
     */
    fun sendAppMessage(text: String): Boolean {
        val state = _uiState.value
        if (state.isStreaming || !state.canStartRun || state.isEditingQueued) return false
        return sendDispatchDelegate.sendAppText(text.trim())
    }

    /**
     * Queues a follow-up message while a reply streams, to auto-send (FIFO) when the current
     * reply completes. Only valid mid-stream and on an existing conversation (the queue
     * affordance is hidden on the landing/new-chat screen). Shares [sendMessage]'s upload-wait
     * gate so a queued message with a still-uploading attachment captures it.
     */
    fun queueMessage() {
        if (!_uiState.value.isStreaming) return
        if (_uiState.value.conversationId == null) return
        val text = _uiState.value.inputText.trim()
        uploadIntakeDelegate.withUploadGate(text) { queueOrchestrationDelegate.enqueueNow(it) }
    }

    /**
     * The composer's send while a reply is generating: routes to steering or queueing per
     * [ChatUiState.effectiveDuringRunAction], which has already degraded the user's preference
     * against what this server and this run actually support.
     */
    fun sendDuringRun() {
        val state = _uiState.value
        // Both branches below can reach `clearComposer()` without passing `withUploadGate`, and
        // that would drop an unsettled pick on the floor — nothing uploaded, no error, sheet gone.
        if (uploadIntakeDelegate.hasUnsettledPicks()) {
            Logger.d { "sendDuringRun: refusing — picked files are not settled yet" }
            return
        }
        // A run paused on `ask_user_question` is waiting for exactly this text, so a send that
        // reaches here (the screens hide the composer behind the question panel) must ANSWER the
        // pause, not queue a next turn. Queueing it fails silently: the pause stays unresolved and
        // the message arrives as a non-sequitur once the run expires.
        when (state.duringRunSendTarget) {
            DuringRunSendTarget.ANSWER_PAUSE -> {
                val answer = state.inputText.trim()
                if (answer.isEmpty()) return
                // A batched pause (one question or many) resolves through the batched channel:
                // the route reads the PAYLOAD to pick which body it accepts, and a pause carrying
                // `questions` rejects a bare `answer`. The delegate fills the question on the
                // panel's active tab (else the first one still unanswered) — the drafts are shared
                // state, so the answer shows up in that question's field — and submits the full map
                // once the last one is in; a partial map is 400 "Answers are required for every
                // question", so there is no per-question submit to route to. The composer is
                // cleared only if the delegate took the text, so a send it cannot use leaves the
                // words where the user put them.
                if (state.renderablePendingAction?.payload?.questions != null) {
                    if (pendingActionDelegate.answerNextBatchQuestion(answer)) sendDispatchDelegate.clearComposer()
                } else {
                    sendDispatchDelegate.clearComposer()
                    pendingActionDelegate.submitAnswerFromComposer(answer)
                }
            }

            DuringRunSendTarget.STEER -> steerMessage()
            DuringRunSendTarget.INTERRUPT -> steerMessage(interrupt = true)
            DuringRunSendTarget.QUEUE -> queueMessage()
        }
    }

    /**
     * Pushes the composer's text into the *running* turn (v0.8.8 steering) instead of waiting
     * for it to finish.
     *
     * Attachments send it to the queue instead: mobile steering is text-only, and silently
     * dropping the files the user attached would be worse than delivering the message a turn
     * later with them intact.
     *
     * [interrupt] is "Interrupt and steer": the same steer, sent with `preempt` so the run stops
     * its current tool or thinking step. It falls back exactly like a steer (to the queue when the
     * run can't take a steer, to a plain steer when it can't be interrupted), never to Stop + send.
     */
    fun steerMessage(interrupt: Boolean = false) {
        val state = _uiState.value
        if (!state.isStreaming || !state.canSteerNow) return
        val conversationId = state.conversationId ?: return
        // Reachable directly from `DuringRunSendMenu`, not only via `sendDuringRun`, so the guard
        // has to sit here too. An unsettled pick is not yet in `attachedFiles`, so the check below
        // would wave it through and `clearComposer()` would destroy it.
        if (uploadIntakeDelegate.hasUnsettledPicks()) {
            Logger.d { "steerMessage: refusing — picked files are not settled yet" }
            return
        }
        if (attachedFiles.value.isNotEmpty()) {
            queueMessage()
            return
        }
        // The steer's own fallback spec, minted now: every degradation path re-homes it as a
        // queued follow-up, and rebuilding it then would capture whatever model, tools, and
        // attachments the composer holds by that point rather than what was sent.
        // The staged excerpts ride the spec, taken here rather than left behind: a steer carries
        // quotes from v0.8.8-rc2, and every path out of the delegate — injection, a rejection that
        // re-homes to the queue, a terminal leftover — delivers or restores what the spec holds.
        val spec = sendDispatchDelegate.buildSendSpec(state.inputText.trim())
            ?.let { it.copy(quotes = sendDispatchDelegate.takePendingQuotes(it.endpoint)) }
            ?: return
        sendDispatchDelegate.clearComposer()
        steeringDelegate.steer(conversationId, spec, interrupt = interrupt)
    }

    /** The during-run menu's explicit "Interrupt and steer" for this one message. */
    fun interruptMessage() = steerMessage(interrupt = true)

    /** Withdraws a steer that has not been injected into the running reply yet. */
    fun cancelSteer(steerId: String) = steeringDelegate.cancel(steerId)

    /** "Interrupt now" on a waiting steer chip: upgrades it to an interrupt in place. */
    fun armSteer(steerId: String) = steeringDelegate.arm(steerId)

    /** The website-icons prompt's answer; also what stops it asking again. */
    fun setShowSiteIcons(show: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.SiteIcons, show) }
    }

    /** Whether Compare's side-by-side panes scroll together (see [AppPreferences.CompareSyncScroll]). */
    val compareSyncScroll: StateFlow<Boolean> = preferenceStore.observe(AppPreferences.CompareSyncScroll)
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferenceStore.initial(AppPreferences.CompareSyncScroll))

    fun setCompareSyncScroll(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.CompareSyncScroll, enabled) }
    }

    /** The website-icons prompt was closed without a choice: stay quiet until the next launch. */
    fun dismissSiteIconPrompt() = siteIconPromptSession.dismiss()

    /** Settings/composer-menu write for the default during-run action (steer, interrupt or queue). */
    fun setDuringRunAction(action: DuringRunAction) {
        viewModelScope.launch { settingsDataStore.setDuringRunAction(action) }
    }

    fun regenerateMessage(messageId: String) {
        if (_uiState.value.isEditingQueued) return
        editingDelegate.regenerateMessage(messageId)
    }

    /** "Retry without files" under a failed reply: its user turn again, as a new version, with no files. */
    fun retryWithoutFiles(failedReplyId: String) {
        if (_uiState.value.isEditingQueued) return
        editingDelegate.retryWithoutFiles(failedReplyId)
    }

    /** "Retry loading original" under a redacted user turn. */
    fun retryOwnerTexts() = ownerTextDelegate.retry()

    /** "Branch without files" under a failed reply; see [ConversationActionsDelegate.branchWithoutFiles]. */
    fun branchWithoutFiles(failedReplyId: String) = conversationActionsDelegate.branchWithoutFiles(failedReplyId)

    /**
     * What Read Aloud speaks for a message: its answer, never its reasoning (upstream
     * `getSpeechText`, see [speechTextOf]). NOT the copy path — whole-message copy goes through
     * [getMessageClipboardText], which serializes every part.
     */
    fun getSpeechText(messageId: String): String {
        val message = _uiState.value.messages.find { it.messageId == messageId } ?: return ""
        return speechTextOf(message)
    }

    /**
     * The clipboard serialization of a whole message — tool calls, reasoning and media parts as
     * labeled blocks, not just its text. Mirrors web `serializeMessageForClipboard`.
     */
    fun copyMessage(messageId: String, write: (String) -> Unit) {
        viewModelScope.launch {
            val text = getMessageClipboardText(messageId)
            if (text.isNotBlank()) write(text)
        }
    }

    internal suspend fun getMessageClipboardText(messageId: String): String {
        val message = _uiState.value.messages.find { it.messageId == messageId } ?: return ""
        if (message.content.orEmpty().none { it.toolCall?.isPreview == true }) {
            return serializeMessageForClipboard(message)
        }
        // A previewed call would copy shortened output: fetch the full parts first, or copy nothing.
        val full = toolPartDelegate.fullMessage(messageId)
        if (full == null) {
            stateHandle.update { copy(error = ChatNotice.COPY_TOOL_CONTENT_FAILED.marker()) }
            return ""
        }
        return serializeMessageForClipboard(full)
    }

    /** Tool-call previews: what each card whose full content is not in place shows. */
    val toolPartStatuses: StateFlow<Map<ToolPartKey, ToolPartStatus>> get() = toolPartDelegate.statuses

    /** A card that shows a preview's content opened: fetch its full part (a retry from failed). */
    fun requestToolPart(key: ToolPartKey) = toolPartDelegate.request(key)

    /** An open card holds its fetched part against eviction until [releaseToolPart]. */
    fun retainToolPart(identity: ToolPartIdentity) = toolPartDelegate.retain(identity)

    fun releaseToolPart(identity: ToolPartIdentity) = toolPartDelegate.release(identity)

    fun stopGeneration() = streamingManager.stopGeneration()

    /**
     * Resolves the paused run's tool batch. One [ToolApprovalResolution] per paused
     * `tool_call_id` — the server rejects a partial batch — and each decision must be one the
     * call's policy allows.
     */
    fun resolveToolApproval(decisions: List<ToolApprovalResolution>) =
        pendingActionDelegate.submitToolDecisions(decisions)

    /** Answers a single-question `ask_user_question` pause and lets the run continue. */
    fun answerPendingQuestion(answer: String) = pendingActionDelegate.submitAnswer(answer)

    /**
     * Answers a batched `ask_user_question` pause — one answer per question id.
     *
     * Separate from [answerPendingQuestion] because the resume route is: it selects the channel
     * from the pause's payload, so a batch cannot be resolved with a joined string and a single
     * question cannot be resolved with a map.
     */
    fun answerPendingQuestions(answers: Map<String, String>) =
        pendingActionDelegate.submitAnswers(answers)

    /** One question's editor state, hoisted out of `AskUserQuestionPanel`. */
    fun updateAskAnswerDraft(questionId: String, draft: AskAnswerDraft) =
        pendingActionDelegate.updateAskAnswerDraft(questionId, draft)

    fun selectAskQuestion(questionId: String) = pendingActionDelegate.selectAskQuestion(questionId)

    fun setAskPanelCollapsed(collapsed: Boolean) = pendingActionDelegate.setAskPanelCollapsed(collapsed)

    fun updateToolDecisionDraft(toolCallId: String, draft: ToolDecisionDraft) =
        pendingActionDelegate.updateToolDecisionDraft(toolCallId, draft)

    fun selectToolCall(toolCallId: String) = pendingActionDelegate.selectToolCall(toolCallId)

    fun setToolPanelCollapsed(collapsed: Boolean) = pendingActionDelegate.setToolPanelCollapsed(collapsed)

    fun pickAskOption(questionId: String, draft: AskAnswerDraft, advance: PausePanelAutoAdvance) =
        pendingActionDelegate.pickAskOption(questionId, draft, advance)

    fun pickToolDecision(toolCallId: String, draft: ToolDecisionDraft, advance: PausePanelAutoAdvance) =
        pendingActionDelegate.pickToolDecision(toolCallId, draft, advance)

    fun cancelPausePanelAutoAdvance() = pendingActionDelegate.cancelAutoAdvance()

    /** Continues the latest reply. Not in a comparison, whose panes use [keepAndContinue]. */
    fun continueGeneration() {
        if (_uiState.value.isEditingQueued || _uiState.value.comparisonState.isEnabled) return
        editingDelegate.continueGeneration()
    }

    /** Manual context compaction (v0.8.8-rc3). See [ChatUiState.canCompactNow]. */
    fun compactConversation() = editingDelegate.compactConversation()

    /** "Not now" on the compact suggestion: hide it in this conversation until usage reaches the next band. */
    fun snoozeCompactNudge() {
        val state = uiState.value
        val conversationId = state.conversationId ?: return
        val nudge = state.compactNudge ?: return
        viewModelScope.launch { settingsDataStore.snoozeCompactNudge(conversationId, nudge.band) }
    }

    /**
     * Forgets a "Not now" once a real reading is back below the threshold (the user compacted, or
     * moved to a shorter branch), so the next fill suggests compacting again from the start.
     * Only a real reading counts: a chat still loading has none, and an estimate is a heuristic.
     */
    private fun clearCompactNudgeSnoozeWhenBelowThreshold() {
        viewModelScope.launch {
            uiState
                .map { state -> state.conversationId.takeIf { state.isBelowCompactThreshold } }
                .distinctUntilChanged()
                .collect { conversationId ->
                    if (conversationId != null && conversationId in uiState.value.prefs.compactNudgeSnoozes) {
                        settingsDataStore.clearCompactNudgeSnooze(conversationId)
                    }
                }
        }
    }

    /**
     * Bumped when any prompt is created, edited or deleted — the signal the composer's `/` picker
     * is stale. Read from the chat screen's composition (`ChatRoot`), not collected here, so the
     * refetch lands on a screen the user is looking at.
     */
    val promptLibraryRevision: StateFlow<Long> = promptRepository.revision

    /** Paired with [promptLibraryRevision]; a no-op unless a prompt changed since the last load. */
    fun refreshPromptsIfStale() {
        if (!promptsUseAllowed) return
        presetPromptDelegate.refreshAvailablePromptsIfStale()
    }

    fun onPause() {
        if (streamingManager.onPause()) watchDetachedReply()
        codeWorkspaceDelegate.onForeground(false)
        pullRequestDelegate.onForeground(false)
        queuedTurnDelegate.stopPolling()
        mcpServersDelegate.onBackground()
    }

    /** [mcpSignInEnding]: an MCP sign-in ends on this return and re-reads the MCP statuses itself. */
    fun onResume(mcpSignInEnding: Boolean = false) {
        uiState.value.conversationId?.let { replyWatchScheduler?.stop(it) }
        streamingManager.onResume()
        codeWorkspaceDelegate.onForeground(true)
        pullRequestDelegate.onForeground(true)
        // A foreground is a reconciliation point, not just a stream resume: the server may have
        // admitted, dropped or added a queued turn while the app was away, and there is nothing
        // to hear it from.
        queueOrchestrationDelegate.refreshQueuedTurns()
        // A key may have run out while the app was away, where the expiry timer doesn't count.
        keyStatusDelegate.resettle()
        // Skills may have changed on the Skills screen while this chat was behind it.
        val state = _uiState.value
        skillPickerDelegate.refreshOnReturn(pickerOpen = pickerListsSkills(state.inputText, state.canPickSkills))
        // A chip's tap acts on its server's status, which may have changed elsewhere meanwhile (the
        // MCP servers page, another chat's tool call, a browser sign-in finished there).
        mcpServersDelegate.onForeground(signInEnding = mcpSignInEnding)
    }

    /** The picker's retry after the skill catalog failed to load. */
    fun retrySkillCatalog() = skillPickerDelegate.retry()

    /**
     * Submits [feedback] for a message, or clears it when null.
     *
     * Gated on `!isStreaming` like `switchBranch` / `rerunEdit` / `regenerateMessage`, and for
     * the same reason: the repository caches the result in Room, and the `loadConversation`
     * observer would re-emit and rebuild `displayMessages` with no `streamingLeafId` — un-truncating
     * the path so the in-flight reply renders after a stale branch instead of in its place. The
     * write was unreachable while the body was a bare rating string (the route rejected it), so
     * correcting the payload is what armed this.
     *
     * Defence in depth, not the only line: the thumbs are disabled while streaming
     * (`LocalFeedbackEnabled`), so the user never reaches the tag sheet, picks a reason and types a
     * comment only to have all of it dropped here. Keep both — this guard is what makes the Room
     * write safe regardless of which affordance grows a path to it.
     */
    fun submitFeedback(messageId: String, feedback: MinimalFeedback?) {
        val conversationId = _uiState.value.conversationId ?: return
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            val result = messageRepository.updateFeedback(conversationId, messageId, feedback)
            // The user picked a reason and may have typed up to 1024 characters. There is no
            // optimistic state, so a dropped submission leaves an empty thumb and no explanation.
            if (result is Result.Error) {
                _uiState.update { it.copy(error = result.message ?: "Could not save your feedback") }
            }
        }
    }

    fun startEditing(messageId: String) {
        // Don't start a tree-message edit while a queued item occupies the composer (it would
        // build its resubmit from the queued item's loaded model/tools).
        if (_uiState.value.isEditingQueued) return
        editingDelegate.startEditing(messageId)
    }

    fun onEditTextChanged(text: String) = editingDelegate.onEditTextChanged(text)

    fun onEditPartTextChanged(index: Int, text: String) =
        editingDelegate.onEditPartTextChanged(index, text)

    fun cancelEditing() = editingDelegate.cancelEditing()

    fun rerunEdit() = editingDelegate.rerunEdit()

    fun saveEdit() = editingDelegate.saveEdit()

    fun onPendingNavigationHandled() {
        streamingManager.reset()
        conversationLoadDelegate.stopObserving()
        // The conversation changes here: nothing learned about the previous one's previews applies.
        attachmentPreviewDelegate.reset()
        // A share sheet belongs to the conversation that just left this landing.
        conversationActionsDelegate.shareLink.close()
        bookmarksDelegate.close()
        // The landing now stands for the next new chat, which opens on the server's spec again.
        modelDelegate.rearmModelSpecDefault()
        // …and with Skills as the new-chat slot has them, not as a pick on the last one left them.
        skillsToggleDelegate.resetNewChat()
        // The next new chat decides its workspace afresh.
        codeWorkspaceDelegate.reset()
        // The reset below ends any Compare setup without Cancel or Start, so nothing would release
        // its hold; the queue it held is cleared with it.
        queueOrchestrationDelegate.clearCompareSetupHold()
        _uiState.update { current ->
            ChatUiState(
                selection = ModelSelectionState(
                    choice = current.selection.choice,
                    // The next new chat starts from the carried spec's preset, not the last chat's tweaks.
                    modelParameters = current.selection.modelSpecs.parametersFor(current.selection.choice.spec)
                        ?: ModelParameters.DEFAULT,
                    // They describe the agent in `choice`, and the observer that resolves them only
                    // fires when the agent id changes, so dropping them here would leave the next
                    // new chat with no provider and its starters unresolved until another agent is
                    // picked.
                    selectedAgentProvider = current.selection.selectedAgentProvider,
                    selectedAgentStarters = current.selection.selectedAgentStarters,
                    selectedAgentModel = current.selection.selectedAgentModel,
                    selectedAgentReasoning = current.selection.selectedAgentReasoning,
                    // Per endpoint, not per chat; the fetcher only refetches when the endpoint changes.
                    reasoningCatalog = current.selection.reasoningCatalog,
                    modelSpecs = current.selection.modelSpecs,
                    availableModels = current.availableModels,
                    endpointConfigs = current.endpointConfigs,
                    // Fetched when the configs change, which a reset doesn't make them do.
                    endpointKeyStates = current.endpointKeyStates,
                    agents = current.agents,
                    agentsLoaded = current.agentsLoaded,
                    mcpServers = current.mcpServers,
                    selectedMcpServerNames = current.selectedMcpServerNames,
                    enabledTools = current.enabledTools,
                    // App-wide, behind distinctUntilChanged: dropped here it would never come back.
                    artifactsMode = current.selection.artifactsMode,
                ),
                presetPrompts = current.presetPrompts,
                voice = VoiceState(serverSttEnabled = current.serverSttEnabled),
                // Server- and account-derived slices carry over: their sources are StateFlows behind
                // distinctUntilChanged, so a reset here would never be refilled while this landing
                // lives, and every later new chat would lose (say) the memory toggle.
                account = current.account,
                gates = current.gates.copy(traceViewerConversationId = null),
                conversation = ConversationMetaState(sharedLinksEnabled = current.sharedLinksEnabled),
            )
        }
    }

    fun toggleTemporaryChat() = treeDelegate.toggleTemporaryChat()

    /**
     * A run started on another device reaches this screen only through a status check, so a refresh
     * asks for one: the server saves the reply when the run ends, and the reload alone shows nothing.
     */
    fun refreshMessages() = conversationLoadDelegate.refreshMessages { conversationId ->
        val state = _uiState.value
        if (!state.isStreaming && state.conversationId == conversationId) {
            streamingManager.resumeOnOpen(conversationId, onAttached = localQueueContinuity::onRunAttached)
        }
    }

    /**
     * Resolves whether the trace entry point may render for the conversation on screen.
     *
     * Three eligibility inputs and then one network round trip, mirroring upstream's
     * `useTraceControl`: a persisted conversation, `interface.traceViewer` on, and a server not
     * known to predate the routes. There is no permission to check — unlike schedules, this is
     * interface config and conversation state only.
     *
     * `collectLatest` is doing real work here: the resolve suspends across the backend's
     * "ask again" waits, and leaving a conversation or starting a run has to cancel it rather
     * than let a late answer land against a conversation that is no longer on screen.
     *
     * Nothing is asked while a run is in flight, and the previous answer is KEPT rather than
     * cleared — the entry point must not blink out for the duration of every reply. The
     * re-emission when streaming ends is the re-read a settled run needs: the turn it just
     * added is what can make a trace readable for the first time.
     */
    private fun observeTraceAvailability() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.conversationId }.distinctUntilChanged(),
                _uiState.map { it.isStreaming }.distinctUntilChanged(),
                configRepository.startupConfig
                    .map { isTraceViewerEnabled(it?.interfaceConfig?.traceViewer) }
                    .distinctUntilChanged(),
            ) { conversationId, isStreaming, enabled ->
                TraceGateInputs(conversationId, isStreaming, enabled)
            }.collectLatest { (conversationId, isStreaming, enabled) ->
                val isRuledOut = traceRepository.isRuledOutForServer()
                if (conversationId == null || !enabled || isRuledOut) {
                    setTraceViewerConversation(null)
                    return@collectLatest
                }
                val shouldResolve = shouldResolveTraceAvailability(
                    conversationId = conversationId,
                    enabled = true,
                    isRuledOut = false,
                    isStreaming = isStreaming,
                    alreadyShownFor = _uiState.value.gates.traceViewerConversationId,
                )
                if (!shouldResolve) return@collectLatest
                val result = traceRepository.resolveAvailability(conversationId)
                val available = result is Result.Success && result.data.available
                setTraceViewerConversation(conversationId.takeIf { available })
            }
        }
    }

    private fun setTraceViewerConversation(conversationId: String?) {
        _uiState.update {
            if (it.gates.traceViewerConversationId == conversationId) {
                it
            } else {
                it.copy(gates = it.gates.copy(traceViewerConversationId = conversationId))
            }
        }
    }

    /** Opens the model-selector sheet. Called when the user taps the model chip. */
    fun openModelSheet() = surfaceModelSheet()

    /**
     * Side effects of surfacing the selector (retry a failed agent load, refetch favorites), split
     * out for the paged sheet, which shows the selector without setting `showModelSheet`. Every
     * selector path must route through here or these self-heals are lost.
     */
    fun prepareModelSelector() {
        modelDelegate.retryAgentsIfFailed(isNewConversation)
        favoritesDelegate.refresh()
        // So the selector never offers an endpoint whose key ran out on a timer that slept.
        keyStatusDelegate.resettle()
    }

    /**
     * Single choke point for the *standalone* selector sheet, layering the sheet flag over
     * [prepareModelSelector]. A null [reason] leaves the current sendBlockReason untouched.
     */
    private fun surfaceModelSheet(reason: SendBlockReason? = null) {
        prepareModelSelector()
        _uiState.update {
            it.copy(
                composer = it.composer.copy(sendBlockReason = reason ?: it.sendBlockReason),
                // No picker on a server that hides it and offers no specs; the reason still shows.
                selection = it.selection.copy(showModelSheet = it.modelPickerShown),
            )
        }
    }

    /** Dismisses the model-selector sheet. Called on sheet dismiss and model selection. */
    fun dismissModelSheet() {
        _uiState.update { it.copy(selection = it.selection.copy(showModelSheet = false)) }
    }

    /** Backs [com.garfiec.librechat.feature.chat.components.LocalAttachmentDownloader]; see [ChatConfigDelegate.downloadFileBytes]. */
    suspend fun downloadFileBytes(attachment: Attachment): ByteArray? = chatConfigDelegate.downloadFileBytes(attachment)

    fun dismissError() {
        Diag.d(tag = NOTICE_TAG, attrs = mapOf("notice" to noticeLabel(_uiState.value.error))) {
            "dismissError on ${_uiState.value.conversationId ?: "landing"}"
        }
        _uiState.update { it.copy(error = null) }
    }

    // ── Code workspace row (attached machines) ──────────────────────────────

    /** Opens the workspace sheet; [machineId] is a machine picked from the machine chip. */
    fun openCodeWorkspaceSheet(machineId: String? = null) = codeWorkspaceDelegate.openSheet(machineId)

    fun dismissCodeWorkspaceSheet() = codeWorkspaceDelegate.dismissSheet()

    fun selectCodeWorkspace(environmentId: String, workspaceId: String) =
        codeWorkspaceDelegate.selectWorkspace(environmentId, workspaceId)

    fun setCodeCheckout(environmentId: String, mode: CodeCheckoutMode) =
        codeWorkspaceDelegate.setCheckout(environmentId, mode)

    fun selectNoCodeWorkspace() = codeWorkspaceDelegate.selectNoWorkspace()

    fun refreshCodeWorkspaces() = codeWorkspaceDelegate.refresh()

    fun selectTransitionCodeWorkspace(environmentId: String, workspaceId: String) =
        codeWorkspaceDelegate.selectTransitionWorkspace(environmentId, workspaceId)

    fun setTransitionCodeCheckout(environmentId: String, mode: CodeCheckoutMode) =
        codeWorkspaceDelegate.setTransitionCheckout(environmentId, mode)

    fun confirmCodeWorkspaceTransition() = codeWorkspaceDelegate.confirmTransition()

    fun detachCodeWorkspace() = codeWorkspaceDelegate.detach()

    fun retryCodeWorkspaceCheck() = codeWorkspaceDelegate.refresh()

    fun selectCodeApprovalMode(option: CodeApprovalOption) = codeWorkspaceDelegate.selectApprovalMode(option)

    /** The pull request sheet's Retry, after a refresh failed. */
    fun retryPullRequest() = pullRequestDelegate.retry()

    fun openPullRequestSheet() = pullRequestDelegate.openSheet()

    fun dismissPullRequestSheet() = pullRequestDelegate.dismissSheet()

    /** The composer chip has been on screen long enough to count as read. */
    fun markPullRequestSeen() = pullRequestDelegate.markSeen()

    fun dismissSendBlockReason() {
        _uiState.update { it.copy(composer = it.composer.copy(sendBlockReason = null)) }
    }

    /**
     * Opens the full-screen media viewer at [url]. The swipeable list is the image set of the
     * current branch, computed once here from a state snapshot (never on the streaming hot path).
     * If [url] isn't in the derived list (an edge case), it opens as a single item rather than
     * silently jumping to index 0. Reads only state — no Room write, no `activeBranches` mutation —
     * so it never trips the streaming invariant.
     */
    fun openMedia(url: String) {
        if (url.isBlank()) return
        val state = uiState.value
        val items = extractBranchMedia(
            displayMessages = state.displayMessages,
            activeToolCalls = state.activeToolCalls,
            streamingAttachments = state.streamingAttachments,
            baseUrl = state.serverUrl,
        )
        val index = items.indexOfFirst { it.url == url }
        val shown = if (index >= 0) items else listOf(MediaItem(url = url, contentDescription = ""))
        val attachments = state.displayMessages.flatMap { it.message.attachments.orEmpty() } +
            state.streamingAttachments
        val preview = MediaPreviewState(
            items = withAttachmentSources(shown, attachments, state.serverUrl),
            initialIndex = index.coerceAtLeast(0),
        )
        _uiState.update { it.copy(mediaPreview = preview) }
    }

    /**
     * Opens the media viewer on the composer's own images ([preview], built by the attachment strip
     * from what the draft holds), which are on the device and not in the thread.
     */
    fun openDraftMedia(preview: MediaPreviewState) {
        _uiState.update { it.copy(mediaPreview = preview) }
    }

    fun closeMedia() {
        _uiState.update { it.copy(mediaPreview = null) }
    }

    /** The reply runs on without this screen; the watcher announces it when it ends. */
    private fun watchDetachedReply() {
        val state = uiState.value
        if (state.isTemporaryChat) return
        val conversationId = state.conversationId ?: return
        val account = activeAccountProvider.currentAccountId() ?: return
        replyWatchScheduler?.watch(ReplyWatch(account, conversationId))
    }

    override fun onCleared() {
        super.onCleared()
        voiceDelegate.release()
        ttsDelegate.release()
        attachmentPreviewDelegate.cancelPolls()
    }

    // ── Delegated public API ────────────────────────────────────────

    // Search
    fun openSearch() = searchDelegate.openSearch()
    fun closeSearch() = searchDelegate.closeSearch()
    fun onSearchQueryChanged(query: String) = searchDelegate.onSearchQueryChanged(query)
    fun nextSearchMatch() = searchDelegate.nextSearchMatch()
    fun previousSearchMatch() = searchDelegate.previousSearchMatch()
    fun onSearchScrollHandled() = searchDelegate.onSearchScrollHandled()

    // Conversation actions
    fun showRenameDialog() = conversationActionsDelegate.showRenameDialog()
    fun dismissRenameDialog() = conversationActionsDelegate.dismissRenameDialog()
    fun renameConversation(newTitle: String) = conversationActionsDelegate.renameConversation(newTitle)
    fun showDeleteConfirmation() = conversationActionsDelegate.showDeleteConfirmation()
    fun dismissDeleteConfirmation() = conversationActionsDelegate.dismissDeleteConfirmation()
    fun deleteConversation() = conversationActionsDelegate.deleteConversation()
    fun archiveConversation() = conversationActionsDelegate.archiveConversation()
    fun duplicateConversation() = conversationActionsDelegate.duplicateConversation()
    fun onDuplicatedConversationHandled() = conversationActionsDelegate.onDuplicatedConversationHandled()
    fun shareConversation() = conversationActionsDelegate.shareConversation()

    fun openBookmarks() = bookmarksDelegate.open()
    fun closeShareSheet() = conversationActionsDelegate.closeShareSheet()
    fun showForkOptions(messageId: String) = conversationActionsDelegate.showForkOptions(messageId)
    fun dismissForkOptions() = conversationActionsDelegate.dismissForkOptions()
    fun forkFromMessage(messageId: String, option: String, splitAtTarget: Boolean = false, excludeFiles: Boolean = false) =
        conversationActionsDelegate.forkFromMessage(messageId, option, splitAtTarget, excludeFiles)
    fun onForkedConversationHandled() = conversationActionsDelegate.onForkedConversationHandled()

    // TTS
    fun readAloud(messageId: String) = ttsDelegate.readAloud(messageId)
    fun stopReading() = ttsDelegate.stopReading()

    // Voice input
    /** The mic would transcribe a read in progress, so dictation stops it first. */
    fun startRecording() {
        ttsDelegate.stopReading()
        voiceDelegate.startRecording()
    }
    fun stopRecording() = voiceDelegate.stopRecording()
    fun cancelRecording() = voiceDelegate.cancelRecording()
    fun onDeviceSpeechResult(transcribedText: String) = voiceDelegate.onDeviceSpeechResult(transcribedText)

    // File attachments
    fun onFilesSelected(platformRefs: List<Any>) = uploadIntakeDelegate.intakePickedFiles(platformRefs, prompt = true)

    /** "Upload to Code Environment" picks ([ChatUiState.codeUploadOffered]); see [turnOnToolForUpload]. */
    fun onCodeFilesSelected(platformRefs: List<Any>) {
        if (!_uiState.value.codeUploadOffered) return
        turnOnToolForUpload(ToolConstants.CODE_INTERPRETER)
        uploadIntakeDelegate.intakeCodeFiles(platformRefs)
    }

    /**
     * Turns [tool] on for a file routed to it, as web does, before the upload starts so a send
     * parked on it carries the tool: on for ephemeral tools (a session addition under a spec); a
     * saved agent already has it, which is what offered the route.
     */
    private fun turnOnToolForUpload(tool: String) {
        val state = _uiState.value
        if (state.toolbarShown && tool !in state.selection.effectiveTools) modelDelegate.toggleTool(tool)
    }

    fun setPendingUploadRoute(index: Int, route: UploadRoute) = uploadIntakeDelegate.setPendingUploadRoute(index, route)
    fun setAllPendingUploadRoutes(route: UploadRoute) = uploadIntakeDelegate.setAllPendingUploadRoutes(route)
    fun confirmPendingUploadRouting() = uploadIntakeDelegate.confirmPendingUploadRouting()
    fun cancelPendingUploadRouting() = uploadIntakeDelegate.cancelPendingUploadRouting()
    fun cancelPendingUploadSend() = uploadIntakeDelegate.cancelPendingUploadSend()
    fun retryUpload(file: AttachedFile) = uploadIntakeDelegate.retryUpload(file)
    fun submitPdfPassword(password: String) = uploadIntakeDelegate.submitPdfPassword(password)
    fun dismissPdfPassword() = uploadIntakeDelegate.dismissPdfPassword()

    // Quote chips (selection toolbar "Add to chat")
    fun addPendingQuote(text: String) = sendDispatchDelegate.addPendingQuote(text)

    // Pending skill chips (picked from the `/` and `$` list). A pick also turns Skills on here.
    fun addPendingManualSkill(name: String) {
        if (sendDispatchDelegate.addPendingManualSkill(name)) skillsToggleDelegate.turnOn()
    }

    fun removeDraftItem(item: DraftItem) = draftItemsDelegate.remove(item)
    fun undoDraftRemoval(id: Long) = draftItemsDelegate.undo(id)
    fun dropDraftUndo(id: Long) = draftItemsDelegate.dropUndo(id)

    /**
     * Stages [value] from the Thinking sheet for the next message only (v0.8.9-rc1). Picking the
     * chat's own setting clears the choice instead.
     */
    fun selectThinkingLevel(value: String) {
        val chip = _uiState.value.thinkingChip ?: return
        _uiState.update { it.copy(composer = it.composer.copy(pendingReasoningOverride = chip.overrideFor(value))) }
    }

    // Queue
    fun editQueued(localId: String) = queueOrchestrationDelegate.editQueued(localId)
    fun commitQueuedEdit() = queueOrchestrationDelegate.commitQueuedEdit()
    fun cancelQueuedEdit() = queueOrchestrationDelegate.cancelQueuedEdit()
    fun cancelQueued(localId: String) = queueOrchestrationDelegate.cancelQueued(localId)
    fun reorderQueue(fromIndex: Int, toIndex: Int) = queueOrchestrationDelegate.reorderQueue(fromIndex, toIndex)
    fun sendQueuedNow() = queueOrchestrationDelegate.sendQueuedNow()

    // Presets and prompts
    fun savePreset(name: String) = presetPromptDelegate.savePreset(name)
    fun loadPreset(displayData: PresetDisplayData) = presetPromptDelegate.loadPreset(displayData)
    fun reloadPresetsIfFailed() = presetPromptDelegate.reloadPresetsIfFailed()
    fun deletePreset(presetId: String) = presetPromptDelegate.deletePreset(presetId)
    fun editPreset(preset: Preset) = presetPromptDelegate.editPreset(preset)
    fun handleSlashCommand(displayData: PromptMentionDisplayData) = presetPromptDelegate.handleSlashCommand(displayData)

    /**
     * A row picked from the composer's picker. A prompt runs as a slash command. A skill becomes a
     * pending chip (which also turns the conversation's Skills on), an MCP server switches when
     * its pick is [McpPick.TOGGLE] (the screen starts a sign-in or set-up), and a model is picked as
     * in the model sheet (the screen opens a missing key). All but a prompt clear the command and
     * query the composer holds: the list only opens when its trigger leads the input, so the whole
     * text is the command.
     */
    fun selectPickerItem(item: ComposerPickerItem) {
        when (item) {
            is ComposerPickerItem.Prompt -> handleSlashCommand(item.prompt)
            is ComposerPickerItem.Skill -> {
                if (parsePickerQuery(_uiState.value.inputText, _uiState.value.canPickSkills) != null) onInputChanged("")
                addPendingManualSkill(item.skill.name)
            }
            is ComposerPickerItem.Mcp -> {
                if (parsePickerQuery(_uiState.value.inputText, _uiState.value.canPickSkills) != null) onInputChanged("")
                if (item.mcp.pick == McpPick.TOGGLE) toggleMcpServer(item.mcp.server.name)
            }
            is ComposerPickerItem.Model -> {
                if (parsePickerQuery(_uiState.value.inputText, _uiState.value.canPickSkills) != null) onInputChanged("")
                when (val pick = item.model.pick) {
                    is ModelPick.Spec -> onSpecSelected(pick.spec)
                    is ModelPick.Raw -> onModelSelected(pick.endpoint, pick.model)
                    // The screen opens Provider API Keys.
                    is ModelPick.AddKey -> Unit
                }
            }
        }
    }

    /**
     * Picks up text staged in [PromptInsertionHandoff] (the prompts library, an agent's starter).
     * Called whenever the chat screen enters composition, first or again, which is when either
     * producer's text can be waiting. It replaces the composer text outright, so a new-chat draft
     * restored first yields to it, and one restored later yields because the composer isn't blank.
     */
    fun consumePendingPromptInsertion() {
        promptInsertionHandoff.take()?.let(presetPromptDelegate::insertPromptText)
    }

    /**
     * Sends a new-chat starter through the composer, so the upload gate, the send preflight and a
     * failed send's restore all treat it as typed text. Refused over a non-blank composer, which
     * the starters are never shown over, so a draft is never replaced. The text skips
     * [persistComposerDraft]: that would race the send's draft delete and could bring the
     * starter back as the next new chat's draft.
     */
    fun sendStarter(text: String) {
        if (_uiState.value.inputText.isNotBlank()) return
        sendDispatchDelegate.stageForSend(text)
        sendMessage()
    }

    /** Puts a new-chat starter in the composer unsent, as an agent page's starter arrives. */
    fun editStarter(text: String) = presetPromptDelegate.insertPromptText(text)

    fun confirmVariablePrompt(interpolated: String) = presetPromptDelegate.confirmVariablePrompt(interpolated)
    fun dismissVariablePrompt() = presetPromptDelegate.dismissVariablePrompt()

    // Favorites (v0.8.5)
    fun toggleAgentFavorite(agentId: String) = favoritesDelegate.toggleAgent(agentId)
    fun toggleModelFavorite(endpoint: String, model: String) = favoritesDelegate.toggleModel(endpoint, model)
    fun toggleSpecFavorite(name: String) = favoritesDelegate.toggleSpec(name)

    // Model selection and comparison
    fun onSpecSelected(spec: ModelSpec) = modelDelegate.onSpecSelected(spec)

    fun onModelSelected(endpoint: String, model: String) = modelDelegate.onChoiceSelected(ModelChoice.Raw(endpoint, model))
    fun beginComparisonSetup() {
        comparisonDelegate.beginComparisonSetup()
        // Setup opens a model list (sheet or pane), so it takes the selector's self-heals.
        prepareModelSelector()
    }

    fun cancelComparisonSetup() {
        comparisonDelegate.cancelComparisonSetup()
        // Cancel means a held follow-up goes to the chat's model alone. Its send builds the request
        // only once the gate is ready, which can be later, so restoring a held comparison now could
        // still turn it into a two-model send. When one is on its way, the old comparison stays off
        // (reopening the chat restores it).
        val followUpOwed = queueOrchestrationDelegate.releaseCompareSetupHold()
        conversationLoadDelegate.releaseRehydrateHeldForSetup(started = followUpOwed)
    }

    /**
     * Starts comparing against [endpoint]/[model] ([spec] when B is a spec), first applying
     * [primary] (a changed A) as the chat's own model. Refused as a whole mid-reply (see
     * ComparisonModeDelegate.startComparison), so a refused Start never applies A alone.
     */
    fun startComparison(endpoint: String, model: String, spec: String? = null, primary: ComparisonPick? = null) {
        if (_uiState.value.isStreaming) return
        primary?.let { modelDelegate.onChoiceSelected(ModelChoice.restore(it.endpoint, it.model, it.spec)) }
        comparisonDelegate.startComparison(endpoint, model, spec)
        if (!_uiState.value.comparisonState.isEnabled) return
        conversationLoadDelegate.releaseRehydrateHeldForSetup(started = true)
        queueOrchestrationDelegate.releaseCompareSetupHold()
    }

    fun stopComparison() = comparisonDelegate.stopComparison()
    fun setSecondarySpec(spec: ModelSpec) = comparisonDelegate.setSecondarySpec(spec)

    fun setSecondaryModel(endpoint: String, model: String) = comparisonDelegate.setSecondaryModel(endpoint, model)
    fun toggleMcpServer(serverName: String) = modelDelegate.toggleMcpServer(serverName)

    /** A settings chip's "sign in" or "Start over", or the "+" sheet's "Sign in": see [McpServersDelegate.signIn]. */
    fun signInMcpServer(
        serverName: String,
        openSignIn: (serverName: String, authorizationUrl: String) -> Boolean,
        onFailed: () -> Unit,
        selectWhenConnected: Boolean = false,
    ) = mcpServersDelegate.signIn(serverName, openSignIn, onFailed, selectWhenConnected)

    /** A connecting chip's or "+" sheet row's "Cancel sign-in": see [McpServersDelegate.cancelSignIn]. */
    fun cancelMcpSignIn(serverName: String, onFailed: () -> Unit) =
        mcpServersDelegate.cancelSignIn(serverName, onFailed)

    /** An MCP sign-in ended: see [McpServersDelegate.onSignInEnded]. */
    fun onMcpSignInEnded() = mcpServersDelegate.onSignInEnded()

    /** The variables sheet's Set / Not set: see [McpServersDelegate.userVarFlags]. */
    suspend fun mcpVariableFlags(serverName: String): Map<String, Boolean>? =
        mcpServersDelegate.userVarFlags(serverName)

    /** Runs in the ViewModel's scope, so closing the sheet doesn't cancel it: see [McpServersDelegate.saveUserVars]. */
    suspend fun saveMcpVariables(serverName: String, values: Map<String, String>): Boolean =
        viewModelScope.async { mcpServersDelegate.saveUserVars(serverName, values) }.await()

    /** See [McpServersDelegate.removeUserVars]; in the ViewModel's scope like [saveMcpVariables]. */
    suspend fun removeMcpVariables(serverName: String): Boolean =
        viewModelScope.async { mcpServersDelegate.removeUserVars(serverName) }.await()

    /** Saves the Artifacts mode app-wide and turns Artifacts on, since picking a mode means using it. */
    fun setArtifactsMode(mode: ArtifactsMode) {
        _uiState.update { it.copy(selection = it.selection.copy(artifactsMode = mode)) }
        viewModelScope.launch { settingsDataStore.setArtifactsMode(mode) }
        if (ToolConstants.ARTIFACTS !in _uiState.value.selection.effectiveTools) toggleTool(ToolConstants.ARTIFACTS)
    }

    fun toggleTool(toolName: String) {
        // Per conversation, so it never goes through the global enabled-tools store.
        if (toolName == ToolConstants.SKILLS) skillsToggleDelegate.toggle() else modelDelegate.toggleTool(toolName)
    }

    /**
     * Saves the user's LibreChat search keys ([auth] in web's form) and, once saved, turns Web Search
     * on through [toggleTool] if it is off. Runs in the ViewModel's scope, so closing the key sheet
     * doesn't cancel it; false when the server refused.
     */
    suspend fun saveWebSearchKeys(auth: Map<String, String>): Boolean = viewModelScope.async {
        chatConfigDelegate.saveWebSearchKeys(auth).also { saved ->
            if (saved && ToolConstants.WEB_SEARCH !in _uiState.value.selection.effectiveTools) {
                toggleTool(ToolConstants.WEB_SEARCH)
            }
        }
    }.await()

    /** Removes the user's LibreChat search keys; false when the server refused. */
    suspend fun removeWebSearchKeys(): Boolean =
        viewModelScope.async { chatConfigDelegate.removeWebSearchKeys() }.await()

    /** A settings-line chip's switch: the same toggle the "+" sheet's row flips. */
    fun toggleSettingChip(chip: SettingChip) {
        when (chip) {
            is SettingChip.Tool -> toggleTool(chip.key)
            is SettingChip.Mcp -> toggleMcpServer(chip.name)
            is SettingChip.Thinking -> Unit
        }
    }

    /**
     * Stores the switch whenever it changes within one conversation, whoever wrote it (a toggle, a
     * loaded preset, a queued edit's composer swap, the reopen's restore). A change of conversation
     * is not a change of switch: the reopen's restore is what sets the new one, and a new chat is
     * stored when it is created (`carrySkillsToggle`).
     */
    private fun rememberWebSearchChanges() {
        viewModelScope.launch {
            var last: Pair<String?, Boolean>? = null
            _uiState.map { it.conversationId to it.modelParameters.webSearch }.distinctUntilChanged().collect { current ->
                val previous = last
                last = current
                val id = current.first
                if (id != null && previous?.first == id) rememberWebSearch(id)
            }
        }
    }

    /**
     * Stores the user's web-search switch for [conversationId] ([ConversationWebSearchStore]), so a
     * reopened chat has it back: when it runs LibreChat's search the server saves nothing of it.
     * Not under a spec, whose chat starts from the spec's tools on reopen, nor in a temporary chat.
     */
    private fun rememberWebSearch(conversationId: String) {
        val state = _uiState.value
        if (state.isTemporaryChat || state.selectedSpec != null) return
        val on = state.modelParameters.webSearch
        viewModelScope.launch { conversationWebSearchStore.set(conversationId, on) }
    }
    fun updateModelParameters(parameters: ModelParameters) = modelDelegate.updateModelParameters(parameters)

    fun branchFromComparison(agentId: String) = comparisonDelegate.branchFromComparison(agentId)

    /**
     * Keeps [agentId]'s answer and continues it with the model that wrote it. A Continue sends with
     * the chat's model, which during a comparison is lane A's, so keeping lane B switches the chat
     * to lane B's model, spec and parameters first ([parametersLaneRanOn]); read now, since keeping
     * the lane clears the comparison. Its spec is held against the config as every writer holds it
     * ([stored]). A pick like any other, so it writes last-used, except in a temporary chat.
     */
    fun keepAndContinue(agentId: String) {
        val state = _uiState.value
        val comparison = state.comparisonState
        val laneB = comparison.secondaryEndpoint?.takeIf { isAddedAgentId(agentId) }?.let { endpoint ->
            comparison.secondaryModel?.let { model -> ModelChoice.stored(endpoint, model, state.laneBSpec, state.modelSpecs) }
        }
        comparisonDelegate.keepAndContinue(agentId) {
            laneB?.let { choice ->
                // Read before the pick: moving onto or off a spec resets lane A's custom instructions.
                val ran = _uiState.value.selection.parametersLaneRanOn(choice)
                modelDelegate.onChoiceSelected(choice, saveLastUsed = !state.isTemporaryChat)
                modelDelegate.updateModelParameters(ran, restoring = true)
            }
            continueGeneration()
        }
    }

    fun setContextGaugeExpanded(expanded: Boolean) {
        contextGaugeExpandedOverride.value = expanded
        viewModelScope.launch {
            // Survive ViewModel teardown (tap, then navigate away) and never crash on a storage
            // failure — the in-memory override above already reflects the user's choice.
            runCatching {
                withContext(NonCancellable) { settingsDataStore.setContextGaugeExpanded(expanded) }
            }
        }
    }
}
