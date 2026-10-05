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
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.isTraceViewerEnabled
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFileRef
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.steer.mergeRestagedQuotes
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.NEW_CHAT_DRAFT_KEY
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.extractBranchMedia
import com.garfiec.librechat.feature.chat.util.isImageType
import com.garfiec.librechat.feature.chat.util.serializeMessageForClipboard
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ChatConfigDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ComparisonModeDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ContextProjectionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ConversationActionsDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ConversationLoadDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.EndpointKeyStatusDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.FavoritesDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.InConversationSearchDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.LiveReplyDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageEditingDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageQueueDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageTreeDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ModelSelectionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.OfficePreviewDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PdfPasswordPrompt
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PendingActionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformDelegateFactory
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PresetPromptDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.QueuedTurnDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SendCompletionDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ShareData
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SteeringDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.StreamingHost
import com.garfiec.librechat.feature.chat.viewmodel.delegate.StreamingManagerDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.SubagentTraceDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.UploadIntakeDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.toFileReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// debt — LargeClass: 2189-line file
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class ChatViewModel(
    initialConversationId: String? = null,
    initialAgentId: String? = null,
    /** Explicit (endpoint, model) to pre-select on a new chat — set when launched from a home-screen
     *  model shortcut / quick action. Null for normal new chats. Mutually exclusive with an agent. */
    initialEndpoint: String? = null,
    initialModel: String? = null,
    /** True when this Chat(id) entry is a temporary chat. Rides on the Chat route so it
     *  survives process death: a restored entry re-initializes temp-aware and never persists the
     *  server-hidden conversation to Room. SECURITY: temp-chat data-at-rest guard — see init. */
    initialIsTemporary: Boolean = false,
    private val agentRepository: AgentRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
    private val resumePinStore: ResumePinStore,
    private val configRepository: ConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val endpointTokenRepository: EndpointTokenRepository,
    private val draftRepository: DraftRepository,
    favoritesRepository: FavoritesRepository,
    private val keyRepository: KeyRepository,
    presetRepository: PresetRepository,
    private val promptRepository: PromptRepository,
    queuedTurnRepository: QueuedTurnRepository,
    shareRepository: ShareRepository,
    private val traceRepository: TraceRepository,
    mcpRepository: McpRepository,
    userRepository: UserRepository,
    roleRepository: RoleRepository,
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
    private val ttsDelegate = platformDelegateFactory.createTts(TtsHandle(stateHandle), ::getMessageText)
    private val voiceDelegate = platformDelegateFactory.createVoiceInput(VoiceHandle(stateHandle), ::sendMessage)
    private val shareConsumer = platformDelegateFactory.createShareConsumer()

    // --- Delegates (each gets a narrowed handle that can write only its own slices) ---
    private val requestBuilder = ChatRequestBuilder { _uiState.value }
    private val treeDelegate = MessageTreeDelegate(MessageTreeHandle(stateHandle))

    // Explicitly typed: inference cannot close the loop through conversationLoadDelegate.
    private val comparisonDelegate: ComparisonModeDelegate = ComparisonModeDelegate(
        handle = ComparisonHandle(stateHandle),
        messageRepository = messageRepository,
        reloadConversation = { id -> conversationLoadDelegate.loadConversation(id) },
    )
    private val contextProjectionDelegate = ContextProjectionDelegate(
        ContextProjectionHandle(stateHandle),
        agentRepository,
        endpointTokenRepository,
    )
    private val searchDelegate = InConversationSearchDelegate(SearchHandle(stateHandle))
    private val conversationActionsDelegate =
        ConversationActionsDelegate(ConversationActionsHandle(stateHandle), conversationRepository, shareRepository)
    private val presetPromptDelegate =
        PresetPromptDelegate(PresetPromptHandle(stateHandle), presetRepository, promptRepository)
    private val favoritesDelegate = FavoritesDelegate(FavoritesHandle(stateHandle), favoritesRepository)
    private val modelDelegate = ModelSelectionDelegate(
        handle = ModelSelectionHandle(stateHandle),
        configRepository = configRepository,
        agentRepository = agentRepository,
        mcpRepository = mcpRepository,
        settingsDataStore = settingsDataStore,
        permissionGate = permissionGate,
        connectivityObserver = connectivityObserver,
        initialAgentId = initialAgentId,
        initialEndpoint = initialEndpoint,
        initialModel = initialModel,
    )
    private val keyStatusDelegate = EndpointKeyStatusDelegate(
        handle = EndpointKeyHandle(stateHandle),
        keyRepository = keyRepository,
    )
    private val subagentTraceDelegate = SubagentTraceDelegate(SubagentHandle(stateHandle), json)
    private val officePreviewDelegate = OfficePreviewDelegate(OfficePreviewHandle(stateHandle), fileRepository)
    private val completionDelegate = SendCompletionDelegate(
        handle = SendCompletionHandle(stateHandle),
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        draftRepository = draftRepository,
        modelDelegate = modelDelegate,
        treeDelegate = treeDelegate,
        tts = ttsDelegate,
        selectionHandoff = selectionHandoff,
        reloadConversation = { id -> conversationLoadDelegate.loadConversation(id) },
    )

    // Channel-backed one-shot signal: N queued follow-ups were dropped on drain because they were
    // composed under an account the user has since switched away from. Surfaced as a snackbar.
    private val _queuedMessagesDropped = Channel<Int>(Channel.BUFFERED)
    val queuedMessagesDropped: Flow<Int> = _queuedMessagesDropped.receiveAsFlow()

    private val queueDelegate = MessageQueueDelegate(
        handle = QueueHandle(stateHandle),
        // Drained items send with their snapshotted config but LIVE lineage. We first wait for
        // the previous reply to settle into the tree (the Final-triggered Room reload is async),
        // so the optimistic insert chains onto the freshly-finalized assistant message rather
        // than the still-optimistic user turn. No upload-wait is needed — attachments were
        // already resolved to FileReferences at queue time.
        sendWithSpec = { spec, awaitSettle ->
            viewModelScope.launch {
                if (awaitSettle) awaitReplySettled()
                // drainNext POPS before it sends, and this gate is allowed to refuse (no model
                // selected, readiness timeout). Without putting the item back, a refusal silently
                // destroys a queued message — including a steer that was re-homed here precisely
                // so it could not be lost.
                runWhenSendReady(onRefused = { requeueRefusedDrain(spec) }) {
                    doSendWithSpec(spec)
                }
            }
        },
        activeAccountProvider = activeAccountProvider,
        onQueuedDropped = { count -> _queuedMessagesDropped.trySend(count) },
        markFilesUsed = { fileIds -> fileRepository.markFilesUsed(fileIds) },
        holdRenewalSupported = { fileRepository.supportsUsageHold() },
    )

    private val queuedTurnDelegate = QueuedTurnDelegate(
        handle = QueueHandle(stateHandle),
        repository = queuedTurnRepository,
        // The server started a run this client never asked for. Nothing else would notice it:
        // queued turns have no push channel, and the ordinary resume path only fires for a client
        // that was already streaming.
        onSuccessorOwed = { streamingManager.attachToServerStartedRun() },
        projectOrphan = ::projectOrphanQueuedTurn,
        onServerRowsCleared = ::tryResumeDrain,
    )

    // --- Delegate-owned flows exposed to the UI ---
    val attachedFiles: StateFlow<List<AttachedFile>> get() = fileDelegate.attachedFiles
    val pdfPasswordPrompts: StateFlow<List<PdfPasswordPrompt>> get() = fileDelegate.pdfPasswordPrompts
    val shareLinkUrl: StateFlow<String?> get() = conversationActionsDelegate.shareLinkUrl

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
        settingsDataStore.siteIconsChoice,
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
    ) { base, sttRenderer, siteIcons ->
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
            siteIcons = siteIcons,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatPreferences())

    // In-memory echo of the gauge-expanded toggle: a tap flips the UI synchronously instead of
    // waiting on the DataStore write round-trip (which would also make a quick second tap read
    // stale state). The persisted value only seeds the session until the first tap.
    private val contextGaugeExpandedOverride = MutableStateFlow<Boolean?>(null)

    // Bundled into one source so the uiState combine below stays within Kotlin's
    // 5-argument typed `combine` ceiling.
    // Folded first so the display combine below stays within Kotlin's 5-argument typed ceiling.
    private val gaugeExpanded: Flow<Boolean> = combine(
        settingsDataStore.contextGaugeExpanded,
        contextGaugeExpandedOverride,
    ) { persisted, override -> override ?: persisted }

    private val chatDisplayPrefs: Flow<ChatDisplayPrefs> = combine(
        settingsDataStore.chatHeaderContent,
        settingsDataStore.chatHeaderAlignment,
        settingsDataStore.contextBarPlacement,
        gaugeExpanded,
        settingsDataStore.duringRunAction,
    ) { content, alignment, contextBarPlacement, gaugeExpanded, duringRunAction ->
        ChatDisplayPrefs(
            content,
            alignment,
            contextBarPlacement,
            gaugeExpanded,
            duringRunAction,
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _uiState,
        serverDataStore.currentUrlFlow,
        settingsDataStore.chatFontSize,
        settingsDataStore.starredModelsDisplay,
        chatDisplayPrefs,
    ) { state, url, fontSize, starredDisplay, displayPrefs ->
        state.copy(
            prefs = ChatPrefsState(
                serverUrl = url,
                chatFontSize = fontSize,
                starredModelsDisplay = starredDisplay,
                chatHeaderContent = displayPrefs.content,
                chatHeaderAlignment = displayPrefs.alignment,
                contextBarPlacement = displayPrefs.contextBarPlacement,
                contextGaugeExpanded = displayPrefs.contextGaugeExpanded,
                duringRunAction = displayPrefs.duringRunAction,
            ),
            // The user record carries a RELATIVE `/images/…` avatar, which Coil has no fetcher for.
            // Resolved here rather than in `loadUserProfile` because the base URL arrives on its own
            // flow: resolving at load time races it and would pin an unloadable path for the session.
            // Idempotent — an already-absolute URL (a social-login avatar) passes through untouched.
            account = state.account.copy(
                userAvatarUrl = resolveAvatarUrl(state.account.userAvatarUrl, url),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    // Channel-backed for exactly-once snackbar delivery across rotation.
    private val _userKeyErrors = Channel<UserKeyError>(Channel.BUFFERED)
    val userKeyErrors: Flow<UserKeyError> = _userKeyErrors.receiveAsFlow()

    private val conversationLoadDelegate = ConversationLoadDelegate(
        handle = ConversationLoadHandle(stateHandle),
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        draftRepository = draftRepository,
        defaultDispatcher = defaultDispatcher,
        applyConversationModel = modelDelegate::applyConversationModel,
        onModelLoadSettled = {
            modelDelegate.conversationModelLoaded = true
            modelDelegate.refilterModels(isNewConversation)
        },
        rehydrateComparison = comparisonDelegate::rehydrateFromMessage,
    )

    private val uploadIntakeDelegate = UploadIntakeDelegate(
        handle = UploadIntakeHandle(stateHandle),
        fileHandler = fileDelegate,
        settingsDataStore = settingsDataStore,
        awaitAgentProvider = { modelDelegate.awaitSelectedAgentProvider() },
        // Only the EXPOSED combine folds serverDataStore.currentUrlFlow into prefs.serverUrl.
        serverUrl = { uiState.value.serverUrl },
    )

    private val chatConfigDelegate = ChatConfigDelegate(
        handle = ChatConfigHandle(stateHandle),
        configRepository = configRepository,
        roleRepository = roleRepository,
        fileRepository = fileRepository,
        userRepository = userRepository,
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

    private val steeringDelegate = SteeringDelegate(
        handle = SteeringHandle(stateHandle),
        chatRepository = chatRepository,
        // Snapshots the CURRENT send config. Only used for steers the server reported (a
        // reconnect, another device) — steers this client sent carry the spec they were
        // composed with, so a model switch mid-run never retro-edits them.
        buildFollowUp = ::buildSendSpec,
        // Always the queue, never the live-send path: `runWhenSendReady` is allowed to REFUSE
        // (no model selected, or a readiness timeout), and a degraded steer has nowhere to put
        // the text back — its composer was cleared at send time. `enqueueSpec` self-drains the
        // moment the run is over, so an ended run still sends immediately; a paused queue holds
        // the item for the user's own "Send queued" instead of dropping it.
        enqueueFollowUp = ::enqueueSpec,
        // Deliberately NOT `enqueueSpec`: its self-drain would auto-send a parked steer on
        // conversation open, where the run is already over. See SteeringDelegate.reclaimParked.
        enqueueParked = queueDelegate::enqueue,
        pauseQueue = { queueDelegate.pause() },
        isStreaming = { _uiState.value.isStreaming },
        restageQuotes = ::restagePendingQuotes,
    )

    private val streamingManager = StreamingManagerDelegate(
        handle = StreamingHandle(stateHandle),
        chatRepository = chatRepository,
        activeAccountProvider = activeAccountProvider,
        connectivityObserver = connectivityObserver,
        comparisonDelegate = comparisonDelegate,
        liveReply = LiveReplyDelegate(StreamingHandle(stateHandle), subagentTraceDelegate, officePreviewDelegate),
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
                unsent?.text?.takeIf { it.isNotBlank() }?.let {
                    conversationLoadDelegate.restoreUnsentInput(it, unsent.quotes.orEmpty())
                }
            }
        },
    )

    private val editingDelegate = MessageEditingDelegate(
        handle = MessageEditingHandle(stateHandle),
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        treeDelegate = treeDelegate,
        streamingManager = streamingManager,
        requestBuilder = requestBuilder,
        getMessageText = ::getMessageText,
        runWhenSendReady = ::runWhenSendReady,
    )

    companion object {
        /** Timeout for the pre-send "is the endpoint/config ready" await. Snappier than the
         *  5 s role-load timeout because this only needs one of role OR availableModels to
         *  satisfy the check. */
        private const val SEND_READY_TIMEOUT_MS = 3_000L

        /** Upper bound on waiting for a finished reply to land in the tree before draining the
         *  next queued message. Generous so a slow post-Final reload still chains correctly. */
        private const val REPLY_SETTLE_TIMEOUT_MS = 8_000L

        // Plain strings, like every other message on this `error` channel. `getString(Res.string…)`
        // is not usable from a ViewModel here: compose-resources resolves through
        // `Resources.getSystem()`, which is null under this module's plain-JVM unit tests.
        private const val WITHDRAW_REFUSED_MESSAGE =
            "Could not withdraw this message from the server."
        private const val WITHDRAW_BUSY_MESSAGE =
            "Still finishing the previous withdrawal. Try again in a moment."
    }

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
                    attrs = mapOf("endpoint" to handoff.endpoint, "model" to (handoff.model ?: "null")),
                ) { "handoff applied for $conversationId" }
                modelDelegate.applyResolvedConversationModel(handoff.endpoint, handoff.model)
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
            conversationLoadDelegate.loadConversationModel(conversationId)
            conversationLoadDelegate.restoreDraft(conversationId)
            // Check if there's an active stream for this conversation (e.g. when
            // navigating here from NewChat immediately after sending). If so,
            // resume it so the user sees streaming content on this screen.
            streamingManager.resumeActiveStreamIfNeeded(conversationId)
        } else {
            isHandedOffNewChat = false
            // For new chats, mark conversationModelLoaded so refilterModels
            // doesn't wait for a conversation model that will never arrive.
            modelDelegate.conversationModelLoaded = true
            conversationLoadDelegate.restoreDraft(NEW_CHAT_DRAFT_KEY)
        }

        // Content shared in from another app, addressed to this chat by the navigation layer.
        // Covers both a share that launched the app (staged before this screen composed, drained
        // on subscribe) and one arriving while this ViewModel is already on screen.
        viewModelScope.launch {
            shareConsumer.sharesFor(initialConversationId).collect(::applyShare)
        }

        // Collect server-file picker results routed to this conversation's own channel
        // (keyed by id; null for the NewChat landing). Consumed here in common code rather
        // than per-platform in the screen — mirroring how NewChatSelectionHandoff is wired.
        viewModelScope.launch {
            serverFileSelectionHandoff.selectionsFor(initialConversationId).collect { files ->
                uploadIntakeDelegate.attachServerFiles(files)
            }
        }

        // Seed/refresh the context-usage gauge for a loaded or snapshot-less branch (v0.8.7).
        contextProjectionDelegate.start()

        // Queued turns are reconciled by polling, so the poll has to be (re)aimed whenever the
        // conversation it is about changes — including the moment a new chat's id resolves, which
        // is the first point a follow-up can become server-owned.
        viewModelScope.launch {
            _uiState
                .map { Triple(it.conversationId, it.selectedEndpoint, it.gates.serverQueueSupported) }
                .distinctUntilChanged()
                .collect { refreshQueuedTurns() }
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
                // If code interpreter is no longer available, remove it from enabled tools
                val agentsCapabilities = configs[EndpointConstants.AGENTS]?.capabilities ?: emptyList()
                if (agentsCapabilities.isNotEmpty() && ToolConstants.EXECUTE_CODE !in agentsCapabilities) {
                    _uiState.update {
                        if (ToolConstants.CODE_INTERPRETER in it.enabledTools) {
                            it.copy(selection = it.selection.copy(enabledTools = it.enabledTools - ToolConstants.CODE_INTERPRETER))
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
                // thinkingDisplay `updates` (40bb16ed, landed 2026-09-28): PRESENT only, dated the day
                // AFTER landing. Keep in step with AgentCapabilitiesDelegate; see VERSION_GATES.md.
                val thinkingDisplayUpdates = BackendVersion.supportsFeature(
                    detected = detected,
                    minVersion = "0.8.8",
                    landedDate = "2026-09-29",
                )
                _uiState.update {
                    it.copy(
                        selection = it.selection.copy(thinkingDisplayUpdatesSupported = thinkingDisplayUpdates),
                        gates = it.gates.copy(
                            steeringSupported = supported,
                            subagentThreadsSupported = subagentThreads,
                            serverQueueSupported = serverQueue,
                            backendVersion = detected?.version,
                        ),
                    )
                }
            }
        }

        // The during-run preference is folded into `prefs` by the `uiState` combine below, but that
        // copy exists only on the EXPOSED state. `sendDuringRun` decides from `_uiState`, which
        // carries `ChatPrefsState()`'s default — so without this collector the send reads QUEUE no
        // matter what the user chose, while the very same button renders itself "Steer this reply"
        // (it takes its icon from the exposed state). Behaviour must never be decided from a slice
        // only the edge populates.
        viewModelScope.launch {
            settingsDataStore.duringRunAction.collect { action ->
                _uiState.update { it.copy(prefs = it.prefs.copy(duringRunAction = action)) }
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

        presetPromptDelegate.loadPresets()
        // Favorites is user-personal (not server-permission-gated upstream); load eagerly
        // so the chat-side pin stars and Settings → Favorites stay in sync from cold start.
        favoritesDelegate.load()
        chatConfigDelegate.loadUserProfile()
        chatConfigDelegate.loadFlags()
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
                modelDelegate.loadMcpServers()
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
        _uiState.update { it.copy(composer = it.composer.copy(inputText = text)) }
        // While editing a queued item the composer holds that item, not the persisted draft —
        // don't overwrite the on-disk new-message draft (it's restored on commit/cancel).
        if (_uiState.value.isEditingQueued) return
        val draftKey = _uiState.value.conversationId ?: NEW_CHAT_DRAFT_KEY
        viewModelScope.launch {
            draftRepository.saveDraft(draftKey, text)
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
            commitQueuedEdit()
            return
        }
        if (_uiState.value.isStreaming) return
        val text = _uiState.value.inputText.trim()
        uploadIntakeDelegate.withUploadGate(text) { runWhenSendReady { sendNow(it) } }
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
        uploadIntakeDelegate.withUploadGate(text) { enqueueNow(it) }
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
                    if (pendingActionDelegate.answerNextBatchQuestion(answer)) clearComposer()
                } else {
                    clearComposer()
                    pendingActionDelegate.submitAnswerFromComposer(answer)
                }
            }

            DuringRunSendTarget.STEER -> steerMessage()
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
     */
    fun steerMessage() {
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
        val spec = buildSendSpec(state.inputText.trim())?.let { it.copy(quotes = takePendingQuotes(it.endpoint)) }
            ?: return
        clearComposer()
        steeringDelegate.steer(conversationId, spec)
    }

    /** Withdraws a steer that has not been injected into the running reply yet. */
    fun cancelSteer(steerId: String) = steeringDelegate.cancel(steerId)

    /** The website-icons prompt's answer; also what stops it asking again. */
    fun setShowSiteIcons(show: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowSiteIcons(show) }
    }

    /** The website-icons prompt was closed without a choice: stay quiet until the next launch. */
    fun dismissSiteIconPrompt() = siteIconPromptSession.dismiss()

    /** Settings/composer-menu write for the default during-run action (steer vs queue). */
    fun setDuringRunAction(action: DuringRunAction) {
        viewModelScope.launch { settingsDataStore.setDuringRunAction(action) }
    }

    private fun enqueueNow(text: String) {
        val spec = buildSendSpec(text) ?: return
        // Composer-origin queue takes the staged quotes with it (web takeComposerContext): they
        // pair with THIS queued message instead of gluing onto whatever the user sends next.
        val withQuotes = spec.copy(quotes = takePendingQuotes(spec.endpoint))
        clearComposer()
        enqueueSpec(withQuotes)
    }

    /**
     * Queues an already-built send spec. Split from [enqueueNow] because a steer that degrades
     * arrives with its spec minted at send time and its composer long since cleared — clearing
     * again there would wipe whatever the user has typed in the meantime.
     */
    private fun enqueueSpec(spec: QueuedMessage) {
        placeInQueue(spec, queueDelegate::enqueue)
        // If the in-flight reply already finished, no Final will arrive to drain this — kick it now.
        tryResumeDrain()
    }

    /** Puts [spec] in the queue through [insert], handing it to the server when it can own it. */
    private fun placeInQueue(spec: QueuedMessage, insert: (QueuedMessage) -> Unit) {
        val conversationId = _uiState.value.conversationId
        val owned = if (conversationId == null) spec else serverOwnedSpec(spec)
        insert(owned)
        // Strictly after the row is in the queue: everything the enqueue answer does — marking it
        // rejected, handing it back to the legacy drain — addresses a row that has to exist.
        if (owned.server != null && conversationId != null) {
            queuedTurnDelegate.enqueue(owned, conversationId)
        }
    }

    /**
     * Marks [spec] as one the SERVER will admit and run, or returns it unchanged for the legacy
     * local drain.
     *
     * Three things have to be true, and all three are properties of a live agent run:
     * the endpoint takes queued turns at all, there is a visible branch leaf to anchor to, and
     * the run's generation epoch is known. Without an authoritative pair the follow-up stays
     * local — a guess here would have the server admit behind the wrong boundary.
     *
     * The anchor is the *user* message of the running turn, not the reply: the reply has no
     * server id until it is persisted. That is what upstream sends too, and the server treats it
     * as a branch anchor rather than a literal parent — it walks forward to the newest assistant
     * message descending from it, which is how a queue of several chains correctly.
     * `displayMessages` is truncated at that leaf for the duration of a stream, so its tail IS
     * the anchor (the same identity the completion-render keying relies on).
     *
     * `clientRequestId` is minted here and not taken from [QueuedMessage.localId]: localId
     * survives an edit, and reusing an id for different text is a 409.
     */
    private fun serverOwnedSpec(spec: QueuedMessage): QueuedMessage {
        val state = _uiState.value
        if (!state.gates.serverQueueSupported) return spec
        if (state.selectedEndpoint != EndpointConstants.AGENTS) return spec
        if (!state.isStreaming) return spec
        val parentMessageId = state.displayMessages.lastOrNull()?.message?.messageId ?: return spec
        val predecessorCreatedAt = pendingActionDelegate.generationEpoch ?: return spec
        return spec.copy(
            server = QueuedTurnServerState(status = QueuedTurnServerState.Status.Sending),
            clientRequestId = Uuid.random().toString(),
            parentMessageId = parentMessageId,
            expectedPredecessorCreatedAt = predecessorCreatedAt,
        )
    }

    /**
     * A display row for a queued turn this client has no record of — one queued on another
     * device, or by a process that has since been killed (the queue is memory-only, the server's
     * is not).
     *
     * The server runs the turn with the conversation's own config, so the model, tools and
     * parameters here are the composer's current ones. They are read only if the user edits the
     * row, which loads them into the composer exactly as upstream's edit does. The files are the
     * receipt's own: an edit that dropped them would send the turn without them.
     */
    private fun projectOrphanQueuedTurn(receipt: AgentQueuedTurnReceipt): QueuedMessage {
        val state = _uiState.value
        return QueuedMessage(
            localId = receipt.clientRequestId,
            text = receipt.text,
            attachments = receipt.files.orEmpty().map { it.toAttachedFile(state.serverUrl) },
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            agentId = state.selectedModel.takeIf {
                state.selectedEndpoint == EndpointConstants.AGENTS
            },
            dispatch = requestBuilder.currentDispatch(),
            accountId = activeAccountProvider.currentAccountId()?.value,
        )
    }

    private fun QueuedTurnFileRef.toAttachedFile(baseUrl: String): AttachedFile {
        val isImage = type?.let(::isImageType) == true
        val previewUrl = if (isImage) {
            resolveFileReferenceUrl(FileReference(fileId = fileId, filepath = filepath, type = type), baseUrl)
        } else {
            null
        }
        return AttachedFile(
            uri = previewUrl ?: fileId,
            name = filename ?: fileId,
            isImage = isImage,
            uploadProgress = 1f,
            fileId = fileId,
            filepath = filepath,
            type = type,
            width = width,
            height = height,
        )
    }

    /** Resumes FIFO draining when the queue is idle (not mid-stream, not paused). No-op otherwise;
     *  [MessageQueueDelegate.drainNext] additionally guards the paused / editing / empty cases. */
    private fun tryResumeDrain() {
        if (!_uiState.value.isStreaming && !_uiState.value.isQueuePaused) {
            queueDelegate.drainNext(awaitSettle = false)
        }
    }

    /**
     * The row whose withdrawal DELETE is in flight, or null.
     *
     * A **global** fence, not a per-row one: one withdrawal at a time across the whole queue. The
     * row it names is for diagnostics; every consumer tests it for null. That is deliberately
     * stricter than the race each consumer can describe on its own — [reorderQueue] in particular
     * needs it, because any in-flight withdrawal is about to shift the indices it operates on —
     * and it is why the two consumers that refuse a user's tap say so rather than returning
     * silently. A per-row `Set` is recorded as a follow-up; it changes the concurrency model.
     */
    private var withdrawingForEdit: String? = null

    /**
     * Tap a queued ghost bubble: enter queued-edit mode. Stashes the current new-message draft,
     * pulls the item OUT of the queue, and loads its text + attachments + model/tools/params into
     * the composer for editing. Commit ([commitQueuedEdit]) or cancel ([cancelQueuedEdit]) puts the
     * item back in its slot and restores the stashed draft. Ignored if already editing one.
     */
    fun editQueued(localId: String) {
        if (_uiState.value.isEditingQueued) return
        // A server-owned edit opens its session only after the withdrawal DELETE returns, so
        // `isEditingQueued` is still false for the whole round trip. A second tap in that window
        // — including a legacy one, which opens its session synchronously and would then be
        // overwritten by the withdrawal landing on top of it — leaves a row out of the queue with
        // nothing that will put it back.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // A pick that has not settled yet belongs to the new-message draft. Swapping the composer
        // out from under it re-homes it onto the queued item instead — attaching it to a message
        // the user did not pick it for, and losing it from the one they did, since `captureComposer`
        // cannot stash a file that is not in the tray yet.
        if (uploadIntakeDelegate.hasUnsettledPicks()) {
            Logger.d { "editQueued: refusing — picked files are not settled yet" }
            return
        }
        val index = _uiState.value.messageQueue.indexOfFirst { it.localId == localId }
        val serverOwned = _uiState.value.messageQueue.getOrNull(index)?.takeIf { it.server != null }
        if (serverOwned != null) {
            withdrawingForEdit = localId
            // The server holds these words and will run them, so editing in place would leave the
            // original queued behind the edit. Withdraw it first, and only edit if that succeeded.
            viewModelScope.launch {
                try {
                    if (!queuedTurnDelegate.cancel(serverOwned)) {
                        // Reported for the same reason the × reports it: the tap looks like it
                        // did nothing, and the row it was aimed at is one the server will still
                        // run. Silence here reads as a dead bubble.
                        reportWithdrawRefused()
                        return@launch
                    }
                    // A real DELETE's receipt already retired the row; a refused one never had an
                    // id to delete and is still sitting there. Take it out either way — leaving it
                    // would put the edit BESIDE the original and block the drain on a row nothing
                    // retires. Its server identity is dropped here and a fresh one minted when the
                    // edit is offered back (see [placeInQueue]). Its slot is re-read here because a
                    // drain may have shifted it meanwhile.
                    val taken = queueDelegate.takeForEdit(localId)
                    beginQueuedEdit(serverOwned.asLegacyRow(), taken?.index ?: index, withdrawnFromServer = true)
                } finally {
                    withdrawingForEdit = null
                }
            }
            return
        }
        val taken = queueDelegate.takeForEdit(localId) ?: return
        beginQueuedEdit(taken.value, taken.index)
    }

    /** Drops every trace of server ownership, leaving a row the local drain may send. */
    private fun QueuedMessage.asLegacyRow(): QueuedMessage = copy(
        server = null,
        clientRequestId = null,
        parentMessageId = null,
        expectedPredecessorCreatedAt = null,
    )

    private fun beginQueuedEdit(item: QueuedMessage, index: Int, withdrawnFromServer: Boolean = false) {
        val stashed = captureComposer()
        applyComposer(item.toComposerSnapshot())
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    editingQueuedItem = QueuedEditSession(
                        original = item,
                        originalIndex = index,
                        stashed = stashed,
                        withdrawnFromServer = withdrawnFromServer,
                    ),
                ),
            )
        }
    }

    /** "Update" in queued-edit mode: re-queue the edited item at its original slot (or drop it if
     *  emptied), then restore the stashed new-message draft. Waits for any attachment added during
     *  the edit to finish uploading (same gate as send/queue) so it isn't silently dropped. */
    fun commitQueuedEdit() {
        val session = _uiState.value.editingQueuedItem ?: return
        uploadIntakeDelegate.withUploadGate(_uiState.value.inputText.trim()) { text ->
            // The upload wait is async — bail if the edit was cancelled (or replaced) meanwhile,
            // so we don't reinsert a duplicate after cancelQueuedEdit already restored the item.
            if (_uiState.value.editingQueuedItem != session) return@withUploadGate
            val edited = buildSendSpec(text)
                ?.copy(localId = session.original.localId, quotes = session.original.quotes)
            if (edited != null) {
                restoreQueued(session, edited)
            } else {
                // Composer emptied → treat as delete; the item is simply not put back.
                queueDelegate.clearPauseIfEmpty()
            }
            finishQueuedEdit(session)
        }
    }

    /** "Cancel edit": discard composer changes, restore the original item to its slot unchanged,
     *  and bring back the stashed new-message draft. */
    fun cancelQueuedEdit() {
        val session = _uiState.value.editingQueuedItem ?: return
        restoreQueued(session, session.original)
        finishQueuedEdit(session)
    }

    /**
     * Puts an edited (or un-edited) item back into its slot.
     *
     * One withdrawn from the server goes back to it while the run is still live. Left local, it
     * would be overtaken: every follow-up queued after it is server-owned, the server admits those
     * at the run end, and the drain refuses to send this one until they have all run.
     */
    private fun restoreQueued(session: QueuedEditSession, item: QueuedMessage) {
        if (session.withdrawnFromServer) {
            placeInQueue(item) { queueDelegate.reinsert(session.originalIndex, it) }
        } else {
            queueDelegate.reinsert(session.originalIndex, item)
        }
    }

    private fun finishQueuedEdit(session: QueuedEditSession) {
        applyComposer(session.stashed)
        _uiState.update { it.copy(composer = it.composer.copy(editingQueuedItem = null)) }
        // Draining was frozen during the edit; resume it now if the queue is idle (a reply may
        // have finished while editing).
        tryResumeDrain()
    }

    /** The queue refused a tap because [withdrawingForEdit] holds it; say so. */
    private fun reportQueueBusy() {
        _uiState.update { it.copy(error = WITHDRAW_BUSY_MESSAGE) }
    }

    /** A withdrawal the server declined. Shared, so the × and the tap-to-edit read the same. */
    private fun reportWithdrawRefused() {
        _uiState.update { it.copy(error = WITHDRAW_REFUSED_MESSAGE) }
    }

    fun cancelQueued(localId: String) {
        // Ignore ghost ×/reorder while an edit is in flight, so the queue can't shift under the
        // session's captured originalIndex.
        if (_uiState.value.isEditingQueued) return
        val item = _uiState.value.messageQueue.firstOrNull { it.localId == localId }
        if (item?.server == null) {
            // A purely local row is removed by id and talks to nothing, so the withdrawal fence
            // does not apply to it — and must not be consulted BEFORE this branch, or every × on
            // an ordinary queued message during any withdrawal is refused with a message about an
            // operation that row has no part in.
            queueDelegate.cancel(localId)
            return
        }
        // An unconfirmed delivery: its window expired with no id, so nothing can withdraw it and
        // it refuses every drain. Upstream's × dismisses it locally, leaving the server to run it
        // if it did land. Only the × — an edit would resend words the server may already hold.
        if (item.server.status == QueuedTurnServerState.Status.Uncertain &&
            item.server.reconciliationExpired
        ) {
            queueDelegate.cancel(localId)
            tryResumeDrain()
            return
        }
        // A withdrawal is already in flight, which is the same window with `isEditingQueued` not
        // yet set: the rows are still on screen, so a tap here would start a second concurrent
        // DELETE, and whichever lands first leaves the other holding a row the server no longer
        // has. Reported rather than swallowed — the fence is global, so this also refuses a tap on
        // a DIFFERENT server-owned row, and a × that dies silently reads as a broken button.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // The server holds this one. Withdraw it there FIRST and drop the local row only on a
        // confirmed cancel — removing it locally on a refused one would hide a turn the server
        // still intends to run.
        //
        // Claimed BEFORE the launch, not only read: this path issues a withdrawal of its own, and
        // the fence it consults is worthless to it unless it also raises it. Without this a second
        // × tap, or an × followed by a tap-to-edit, starts a second DELETE of the same row — and
        // whichever lands second reports a refusal over a withdrawal that actually succeeded.
        withdrawingForEdit = localId
        viewModelScope.launch {
            try {
                if (!queuedTurnDelegate.cancel(item)) {
                    // Reported, not swallowed. A refusal is either "the server is past withdrawing
                    // this" or "this row has no id to withdraw" — and the latter is reachable and
                    // sticky: an `Uncertain` row whose reconciliation window has expired will never
                    // be handed one, so its × is a permanent no-op while the row itself refuses
                    // every drain. Silence made that read as a dead button on a row the UI has
                    // already labelled as needing attention.
                    reportWithdrawRefused()
                    return@launch
                }
                // Re-checked after the round trip, not only before it: an edit session opened while
                // the DELETE was out, and dropping a row into it now shifts the slots its captured
                // originalIndex points at — the exact thing the guard above exists to prevent.
                if (_uiState.value.isEditingQueued) return@launch
                queueDelegate.cancel(localId)
                // The run end that found this row refused to drain; if it was the last server
                // row, the local ones behind it have no other trigger.
                tryResumeDrain()
            } finally {
                withdrawingForEdit = null
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (_uiState.value.isEditingQueued) return
        // Global on purpose, unlike the ×: a withdrawal that lands removes a row and renumbers
        // every index behind it, so a drag started now commits against slots that have moved.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // Server-owned rows run in the server's sequence; dragging one would show an order the
        // backend will not honour.
        val queue = _uiState.value.messageQueue
        if (queue.getOrNull(fromIndex)?.server != null || queue.getOrNull(toIndex)?.server != null) {
            return
        }
        queueDelegate.reorder(fromIndex, toIndex)
    }

    /**
     * "Send queued" control after a Stop/error pause: lift the pause and resume draining.
     *
     * A refused row the server still lists is withdrawn there first, under the same fence as the
     * × — see [QueuedTurnDelegate.withdrawRefused].
     */
    fun sendQueuedNow() {
        val refused = _uiState.value.messageQueue.firstOrNull {
            it.server?.status == QueuedTurnServerState.Status.Rejected && it.server.id != null
        }
        if (refused == null) {
            queueDelegate.resume()
            return
        }
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        withdrawingForEdit = refused.localId
        viewModelScope.launch {
            try {
                // A row that could not be withdrawn stays refused, and resume() says the server
                // still holds one.
                queuedTurnDelegate.withdrawRefused()
                queueDelegate.resume()
            } finally {
                withdrawingForEdit = null
            }
        }
    }

    /** Snapshots the editable composer surface (the new-message draft) for stashing during an edit. */
    private fun captureComposer(): ComposerSnapshot {
        val state = _uiState.value
        return ComposerSnapshot(
            text = state.inputText,
            attachments = fileDelegate.attachedFiles.value,
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            enabledTools = state.enabledTools,
            mcpServerNames = state.selectedMcpServerNames,
            modelParameters = state.modelParameters,
        )
    }

    /** Writes a [ComposerSnapshot] back onto the composer (text, attachments, model, tools, params).
     *  Sets [ChatUiState.inputText] directly rather than via [onInputChanged] so swapping composer
     *  contents for an edit never overwrites the persisted on-disk new-message draft. */
    private fun applyComposer(snapshot: ComposerSnapshot) {
        fileDelegate.restoreAttachedFiles(snapshot.attachments)
        _uiState.update {
            it.copy(
                // The snapshot replaces the whole tray, so an undecided batch from before the edit
                // no longer belongs to anything on screen.
                composer = it.composer.copy(inputText = snapshot.text, pendingUploadRouting = null),
                selection = it.selection.copy(
                    selectedEndpoint = snapshot.endpoint,
                    selectedModel = snapshot.model,
                    enabledTools = snapshot.enabledTools,
                    selectedMcpServerNames = snapshot.mcpServerNames,
                    modelParameters = snapshot.modelParameters,
                ),
            )
        }
    }

    /**
     * Snapshots the current send config into a [QueuedMessage]. Used both for a normal send
     * (fired immediately) and for queueing (fired later, unchanged by intervening config edits).
     * Returns null when there is nothing to send (blank text and no uploaded files).
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun buildSendSpec(text: String): QueuedMessage? {
        // Snapshot the uploaded AttachedFiles (not just FileReferences) so a queued item can
        // round-trip losslessly back into the composer on edit — keeping its local-uri thumbnail.
        val allFiles = fileDelegate.attachedFiles.value
        val files = allFiles.filter { it.fileId != null }
        // Surface attachments excluded from the send (still uploading or failed) so a dropped
        // file leaves a diagnostic trail rather than vanishing silently.
        val dropped = allFiles.filter { it.fileId == null }
        if (dropped.isNotEmpty()) {
            Logger.w {
                "buildSendSpec: ${dropped.size} attachment(s) not yet uploaded, excluded from send: " +
                    dropped.joinToString { it.name }
            }
        }
        if (text.isBlank() && files.isEmpty()) return null
        val state = _uiState.value
        val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
        return QueuedMessage(
            localId = Uuid.random().toString(),
            text = text,
            attachments = files,
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            agentId = if (isAgent) state.selectedModel else null,
            enabledTools = state.enabledTools,
            mcpServerNames = state.selectedMcpServerNames,
            modelParameters = state.modelParameters,
            modelParamsPayload = requestBuilder.buildModelParams(),
            ephemeralAgent = requestBuilder.buildEphemeralAgent(),
            dispatch = requestBuilder.currentDispatch(),
            isTemporary = state.isTemporaryChat,
            // Capture the composing account so a drain after an account switch drops this item rather
            // than POSTing it to the newly-active account's server.
            accountId = activeAccountProvider.currentAccountId()?.value,
        )
    }

    private fun sendNow(text: String) {
        val spec = buildSendSpec(text) ?: return
        doSendWithSpec(
            spec.copy(quotes = takePendingQuotes(spec.endpoint)),
            clearComposerOnSend = true,
        )
    }

    /**
     * Atomically takes (and clears) the staged quote chips for a send on [endpoint] — the
     * fresh-submit / composer-queue drain of web's `pendingQuotesByConvoId` atom. Assistants
     * endpoints take nothing and leave the chips staged: they bypass the server-side merge, and
     * a selection staged elsewhere must not silently ride along (web's `quotesSupported` guard).
     * Regenerate/continue/edit never call this — those flows replay a prior turn.
     */
    private fun takePendingQuotes(endpoint: String): List<String> {
        if (!quotesSupportedOn(endpoint)) return emptyList()
        var taken: List<String> = emptyList()
        _uiState.update {
            taken = it.composer.pendingQuotes
            if (taken.isEmpty()) it else it.copy(composer = it.composer.copy(pendingQuotes = emptyList()))
        }
        return taken
    }

    /** Stages a selected excerpt as a pending quote chip (selection toolbar "Add to chat"). */
    fun addPendingQuote(text: String) {
        val excerpt = text.trim()
        if (excerpt.isEmpty()) return
        _uiState.update {
            it.copy(composer = it.composer.copy(pendingQuotes = it.composer.pendingQuotes + excerpt))
        }
    }

    /**
     * Puts excerpts a steer lost back on the composer's chips, deduped and capped.
     *
     * Unlike [addPendingQuote] this is a RESTORE, not a new selection, so it goes through
     * [mergeRestagedQuotes]: the same excerpts can arrive from more than one recovery trigger for
     * one steer, and appending blindly would multiply the user's chips.
     */
    private fun restagePendingQuotes(quotes: List<String>) {
        if (quotes.isEmpty()) return
        _uiState.update {
            val merged = mergeRestagedQuotes(it.composer.pendingQuotes, quotes)
            if (merged === it.composer.pendingQuotes) it else it.copy(composer = it.composer.copy(pendingQuotes = merged))
        }
    }

    /** Removes one staged quote chip (its ×). */
    fun removePendingQuote(index: Int) {
        _uiState.update {
            val quotes = it.composer.pendingQuotes
            if (index !in quotes.indices) return@update it
            it.copy(
                composer = it.composer.copy(
                    pendingQuotes = quotes.filterIndexed { i, _ -> i != index },
                ),
            )
        }
    }

    /**
     * Suspends until the previous reply has settled into the message tree: streaming is over and
     * the active path ends in an assistant message (the post-Final Room reload has landed). Used
     * before draining a queued follow-up so its optimistic insert chains onto that reply. Bounded
     * by [REPLY_SETTLE_TIMEOUT_MS]; on timeout (e.g. a failed reload) we proceed best-effort.
     */
    private suspend fun awaitReplySettled() {
        withTimeoutOrNull(REPLY_SETTLE_TIMEOUT_MS) {
            _uiState.first { state ->
                !state.isStreaming &&
                    state.displayMessages.lastOrNull()?.message?.isCreatedByUser == false
            }
        }
    }

    /** Clears the input, its persisted draft, and any attached files. */
    private fun clearComposer() {
        val draftKey = _uiState.value.conversationId ?: NEW_CHAT_DRAFT_KEY
        // Drop any staged batch too: it belongs to the message just sent, and surviving here would
        // attach it to the next one.
        _uiState.update {
            it.copy(composer = it.composer.copy(inputText = "", pendingUploadRouting = null))
        }
        viewModelScope.launch { draftRepository.deleteDraft(draftKey) }
        fileDelegate.clearAttachedFiles()
    }

    /**
     * Sends one message from a [QueuedMessage] config snapshot. The config (endpoint/model/
     * tools/webSearch/attachments/dispatch/ephemeralAgent) comes from the spec, but the
     * lineage — conversationId, parentMessageId, and the minted optimistic user-message id —
     * is recomputed from the *current* tree, so a drained item chains onto the freshly-
     * finalized turn.
     *
     * [clearComposerOnSend] clears the composer only once the streaming guard has passed — set
     * true on the live-send path (so a lost readiness race can't wipe an unsent message) and
     * false for drains (which must leave the user's in-progress composer untouched).
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun doSendWithSpec(spec: QueuedMessage, clearComposerOnSend: Boolean = false) {
        val fileRefs = spec.attachments.map { it.toFileReference() }
        val hasFiles = fileRefs.isNotEmpty()
        val messageText = spec.text
        if ((messageText.isBlank() && !hasFiles) || _uiState.value.isStreaming) return
        // Guard passed: safe to clear the composer for a live send without risking message loss.
        if (clearComposerOnSend) clearComposer()

        // Count one "used" tick for the picked model — the real usage signal for the most-used
        // ranking behind home-screen shortcuts. Fires on every dispatched send (live or a drained
        // queue item, since both land here). Agents are excluded: their selection is an opaque
        // agentId, which would surface as an unreadable shortcut label.
        if (spec.endpoint != EndpointConstants.AGENTS && !spec.model.isNullOrBlank()) {
            viewModelScope.launch { settingsDataStore.incrementModelUsage(spec.endpoint, spec.model) }
        }

        val conversationId = _uiState.value.conversationId
        val lastMessageId = _uiState.value.displayMessages.lastOrNull()?.message?.messageId

        // Add optimistic user message to display immediately
        val optimisticMessage = Message(
            messageId = Uuid.random().toString(),
            conversationId = conversationId ?: "",
            parentMessageId = lastMessageId,
            text = messageText,
            isCreatedByUser = true,
            sender = "User",
            createdAt = Clock.System.now().toString(),
            files = fileRefs.takeIf { it.isNotEmpty() },
            // The server persists and echoes them; painting them optimistically keeps the user
            // bubble's quote blocks from popping in a turn later.
            quotes = spec.quotes.takeIf { it.isNotEmpty() },
        )
        val isNewChat = conversationId == null
        _uiState.update {
            val updatedMessages = it.messages + optimisticMessage
            val updatedDisplay = buildActiveMessagePath(updatedMessages, it.activeBranches, optimisticMessage.messageId)
            it.copy(
                content = it.content.copy(
                    isStreaming = true,
                    streamingContent = "",
                    streamingThinking = "",
                    activeToolCalls = emptyList(),
                    streamingAttachments = emptyList(),
                    screenState = if (isNewChat) ChatScreenState.LANDING else ChatScreenState.ACTIVE,
                    messages = updatedMessages,
                    displayMessages = updatedDisplay,
                ),
                error = null,
            )
        }
        streamingManager.beginStreaming(
            isEdit = false,
            optimisticUserMessageId = optimisticMessage.messageId,
            // The spec this turn actually dispatches, not the composer's current state: a
            // human-review pause is resumed against the config the run was started with.
            turnSpec = spec,
        )

        val isAgent = spec.endpoint == EndpointConstants.AGENTS
        Logger.d {
            "sendMessage: webSearch=${spec.modelParameters.webSearch}, " +
                "endpoint=${spec.endpoint}, " +
                "model=${spec.model}, " +
                "files=${fileRefs.size}, " +
                "ephemeralAgent=${spec.ephemeralAgent}"
        }

        // Resolve effective endpoint/agentId for comparison mode.
        // All requests go through api/agents/chat/{endpoint} — the server's
        // middleware creates ephemeral agents for non-agent endpoints, so no
        // swapping is needed. Just keep the primary's original endpoint.
        val effectiveEndpoint = spec.endpoint
        val effectiveAgentId = if (isAgent) spec.agentId else null
        comparisonDelegate.onSendStart()

        val effectiveAddedConvo = comparisonDelegate.buildAddedConvo(parentMessageId = lastMessageId)
        val stream = chatRepository.startChat(
            text = messageText,
            conversationId = conversationId,
            endpoint = effectiveEndpoint,
            endpointType = spec.dispatch.endpointType,
            key = spec.dispatch.key,
            modelDisplayLabel = spec.dispatch.modelDisplayLabel,
            model = spec.model,
            userMessageId = optimisticMessage.messageId,
            parentMessageId = lastMessageId,
            agentId = effectiveAgentId,
            webSearch = spec.modelParameters.webSearch,
            files = fileRefs.takeIf { it.isNotEmpty() },
            addedConvo = effectiveAddedConvo,
            ephemeralAgent = spec.ephemeralAgent,
            isTemporary = spec.isTemporary,
            modelParams = spec.modelParamsPayload,
            quotes = spec.quotes.takeIf { it.isNotEmpty() },
        )
        streamingManager.launchStream(stream)
    }

    fun editMessage(messageId: String, newText: String) {
        if (_uiState.value.isEditingQueued) return
        editingDelegate.editMessage(messageId, newText)
    }

    fun regenerateMessage(messageId: String) {
        if (_uiState.value.isEditingQueued) return
        editingDelegate.regenerateMessage(messageId)
    }

    /**
     * Text-parts extraction for TTS and the edit prefill. NOT the copy path — whole-message copy
     * goes through [getMessageClipboardText], which serializes every part.
     */
    fun getMessageText(messageId: String): String {
        val message = _uiState.value.messages.find { it.messageId == messageId } ?: return ""
        val contentParts = message.content
        if (!contentParts.isNullOrEmpty()) {
            return contentParts.mapNotNull { part ->
                part.text ?: part.think
            }.joinToString("")
        }
        return message.text
    }

    /**
     * The clipboard serialization of a whole message — tool calls, reasoning and media parts as
     * labeled blocks, not just its text. Mirrors web `serializeMessageForClipboard`.
     */
    fun getMessageClipboardText(messageId: String): String {
        val message = _uiState.value.messages.find { it.messageId == messageId } ?: return ""
        return serializeMessageForClipboard(message)
    }

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

    fun continueGeneration() {
        if (_uiState.value.isEditingQueued) return
        editingDelegate.continueGeneration()
    }

    /** Manual context compaction (v0.8.8-rc3). See [ChatUiState.canCompactNow]. */
    fun compactConversation() = editingDelegate.compactConversation()

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
        streamingManager.onPause()
        queuedTurnDelegate.stopPolling()
    }

    fun onResume() {
        streamingManager.onResume()
        // A foreground is a reconciliation point, not just a stream resume: the server may have
        // admitted, dropped or added a queued turn while the app was away, and there is nothing
        // to hear it from.
        refreshQueuedTurns()
    }

    /**
     * Restarts the queued-turn reconcile poll, whose first read is unconditional.
     *
     * Deliberately not gated on the local queue being non-empty: the rows this exists to
     * rediscover are exactly the ones this process does not have.
     */
    private fun refreshQueuedTurns() {
        val state = _uiState.value
        val eligible = state.selectedEndpoint == EndpointConstants.AGENTS &&
            state.gates.serverQueueSupported
        queuedTurnDelegate.ensurePolling(state.conversationId.takeIf { eligible })
    }

    /**
     * Submits [feedback] for a message, or clears it when null.
     *
     * Gated on `!isStreaming` like `switchBranch` / `editMessage` / `regenerateMessage`, and for
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

    fun cancelEditing() = editingDelegate.cancelEditing()

    fun submitEdit() = editingDelegate.submitEdit()

    fun saveEditOnly() = editingDelegate.saveEditOnly()

    fun onPendingNavigationHandled() {
        streamingManager.reset()
        conversationLoadDelegate.stopObserving()
        _uiState.update { current ->
            ChatUiState(
                selection = ModelSelectionState(
                    selectedEndpoint = current.selectedEndpoint,
                    selectedModel = current.selectedModel,
                    availableModels = current.availableModels,
                    endpointConfigs = current.endpointConfigs,
                    agents = current.agents,
                    mcpServers = current.mcpServers,
                    selectedMcpServerNames = current.selectedMcpServerNames,
                    enabledTools = current.enabledTools,
                ),
                presetPrompts = current.presetPrompts,
                voice = VoiceState(serverSttEnabled = current.serverSttEnabled),
                account = AccountConfigState(
                    userName = current.userName,
                    userAvatarUrl = current.userAvatarUrl,
                ),
                conversation = ConversationMetaState(sharedLinksEnabled = current.sharedLinksEnabled),
            )
        }
    }

    fun toggleTemporaryChat() = treeDelegate.toggleTemporaryChat()

    fun refreshMessages() = conversationLoadDelegate.refreshMessages()

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

    /**
     * Suspends until [ChatUiState.isSendReady] becomes true, up to [timeoutMs]. Returns
     * true if the state became ready; false on timeout. Used as a pre-flight guard on all
     * send variants to avoid the cold-start race where endpoint/config hasn't arrived yet
     * and firing `chatRepository.startChat(...)` would produce a mislabeled 403.
     *
     * 3 s chosen to be snappier than the role-load timeout (5 s) since this only needs
     * one of the async inits (role OR availableModels) to complete enough to satisfy
     * `isSendReady` — usually both have landed by the time a human can tap send.
     */
    private suspend fun awaitSendReady(timeoutMs: Long = SEND_READY_TIMEOUT_MS): Boolean {
        if (_uiState.value.isSendReady) return true
        return withTimeoutOrNull(timeoutMs) {
            _uiState.map { it.isSendReady }.distinctUntilChanged().first { it }
        } != null
    }

    /**
     * Guard for each of the four send variants (send / edit / regenerate / continue).
     * Runs a synchronous pre-flight that fails fast on user-input errors (e.g., no model
     * selected, agents denied with role already loaded) so the user isn't made to wait
     * for the readiness timeout just to be told something they could have acted on
     * immediately. Otherwise, awaits readiness up to 3 s and falls back to a
     * selection-aware availability message if the wait times out.
     */
    /** Puts a drained item back at the head after the send gate refused it. */
    private fun requeueRefusedDrain(spec: QueuedMessage): Unit = queueDelegate.reinsert(0, spec)

    private fun runWhenSendReady(action: () -> Unit) = runWhenSendReady(onRefused = {}, action = action)

    private fun runWhenSendReady(onRefused: () -> Unit, action: () -> Unit) {
        val current = _uiState.value
        preflightSendBlockReason(current)?.let { reason ->
            surfaceModelSheet(reason)
            onRefused()
            return
        }
        if (current.isSendReady) {
            action()
            return
        }
        viewModelScope.launch {
            if (awaitSendReady()) {
                action()
            } else {
                surfaceModelSheet(sendReadinessTimeoutReason(_uiState.value))
                onRefused()
            }
        }
    }

    /**
     * Synchronous pre-flight. Returns a typed reason when sending is guaranteed
     * to fail regardless of outstanding async inits; null when we still need to wait
     * for the readiness signal. This keeps "no model selected" and "agents denied"
     * instantaneous instead of waiting out the readiness timeout.
     */
    private fun preflightSendBlockReason(state: ChatUiState): SendBlockReason? {
        if (state.selectedModel == null) {
            return if (state.selectedEndpoint == EndpointConstants.AGENTS) {
                SendBlockReason.SelectAgent
            } else {
                SendBlockReason.SelectModel
            }
        }
        if (state.selectedEndpoint == EndpointConstants.AGENTS && !state.agentsEnabled) {
            return SendBlockReason.AgentsUnavailable
        }
        return null
    }

    /**
     * Fallback for when readiness didn't resolve within the timeout. At this point the
     * async model list is most likely in its final shape, so we can confidently flag
     * stale selections that aren't in the available models.
     */
    private fun sendReadinessTimeoutReason(state: ChatUiState): SendBlockReason {
        if (state.selectedEndpoint == EndpointConstants.AGENTS) {
            return SendBlockReason.AgentNotAvailable
        }
        val modelsForEndpoint = state.availableModels[state.selectedEndpoint].orEmpty()
        val selectedModel = state.selectedModel
        return if (selectedModel != null && selectedModel !in modelsForEndpoint) {
            SendBlockReason.ModelNotAvailable
        } else {
            SendBlockReason.ModelLoadFailed
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
                selection = it.selection.copy(showModelSheet = true),
            )
        }
    }

    /** Dismisses the model-selector sheet. Called on sheet dismiss and model selection. */
    fun dismissModelSheet() {
        _uiState.update { it.copy(selection = it.selection.copy(showModelSheet = false)) }
    }

    /** Backs [com.garfiec.librechat.feature.chat.components.LocalAttachmentDownloader]; see [ChatConfigDelegate.downloadFileBytes]. */
    suspend fun downloadFileBytes(fileId: String): ByteArray? = chatConfigDelegate.downloadFileBytes(fileId)

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

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
        val preview = if (index >= 0) {
            MediaPreviewState(items = items, initialIndex = index)
        } else {
            MediaPreviewState(items = listOf(MediaItem(url = url, contentDescription = "")), initialIndex = 0)
        }
        _uiState.update { it.copy(mediaPreview = preview) }
    }

    fun closeMedia() {
        _uiState.update { it.copy(mediaPreview = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceDelegate.release()
        ttsDelegate.release()
        officePreviewDelegate.cancelPolls()
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
    fun onShareLinkHandled() = conversationActionsDelegate.onShareLinkHandled()
    fun showForkOptions(messageId: String) = conversationActionsDelegate.showForkOptions(messageId)
    fun dismissForkOptions() = conversationActionsDelegate.dismissForkOptions()
    fun forkFromMessage(messageId: String, option: String, splitAtTarget: Boolean = false) =
        conversationActionsDelegate.forkFromMessage(messageId, option, splitAtTarget)
    fun onForkedConversationHandled() = conversationActionsDelegate.onForkedConversationHandled()

    // TTS
    fun readAloud(messageId: String) = ttsDelegate.readAloud(messageId)
    fun stopReading() = ttsDelegate.stopReading()

    // Voice input
    fun startRecording() = voiceDelegate.startRecording()
    fun stopRecording() = voiceDelegate.stopRecording()
    fun cancelRecording() = voiceDelegate.cancelRecording()
    fun onDeviceSpeechResult(transcribedText: String) = voiceDelegate.onDeviceSpeechResult(transcribedText)

    // File attachments
    fun onFilesSelected(platformRefs: List<Any>) = uploadIntakeDelegate.intakePickedFiles(platformRefs, prompt = true)
    fun setPendingUploadRoute(index: Int, route: UploadRoute) = uploadIntakeDelegate.setPendingUploadRoute(index, route)
    fun setAllPendingUploadRoutes(route: UploadRoute) = uploadIntakeDelegate.setAllPendingUploadRoutes(route)
    fun confirmPendingUploadRouting() = uploadIntakeDelegate.confirmPendingUploadRouting()
    fun cancelPendingUploadRouting() = uploadIntakeDelegate.cancelPendingUploadRouting()
    fun cancelPendingUploadSend() = uploadIntakeDelegate.cancelPendingUploadSend()
    fun removeFile(file: AttachedFile) = uploadIntakeDelegate.removeFile(file)
    fun retryUpload(file: AttachedFile) = uploadIntakeDelegate.retryUpload(file)
    fun submitPdfPassword(password: String) = uploadIntakeDelegate.submitPdfPassword(password)
    fun dismissPdfPassword() = uploadIntakeDelegate.dismissPdfPassword()

    // Presets and prompts
    fun savePreset(name: String) = presetPromptDelegate.savePreset(name)
    fun loadPreset(displayData: PresetDisplayData) = presetPromptDelegate.loadPreset(displayData)
    fun deletePreset(presetId: String) = presetPromptDelegate.deletePreset(presetId)
    fun editPreset(preset: Preset) = presetPromptDelegate.editPreset(preset)
    fun handleSlashCommand(displayData: PromptMentionDisplayData) = presetPromptDelegate.handleSlashCommand(displayData)

    /**
     * Picks up prompt text staged by the prompts library. Called when the chat screen re-enters
     * composition after the library pops, which is the only moment the text can have been staged.
     */
    fun consumePendingPromptInsertion() {
        promptInsertionHandoff.take()?.let(presetPromptDelegate::insertPromptText)
    }

    fun confirmVariablePrompt(interpolated: String) = presetPromptDelegate.confirmVariablePrompt(interpolated)
    fun dismissVariablePrompt() = presetPromptDelegate.dismissVariablePrompt()

    // Favorites (v0.8.5)
    fun toggleAgentFavorite(agentId: String) = favoritesDelegate.toggleAgent(agentId)
    fun toggleModelFavorite(endpoint: String, model: String) = favoritesDelegate.toggleModel(endpoint, model)

    // Model selection and comparison
    fun onModelSelected(endpoint: String, model: String) = modelDelegate.onModelSelected(endpoint, model)
    fun toggleComparison() = comparisonDelegate.toggleComparison()
    fun setSecondaryModel(endpoint: String, model: String) = comparisonDelegate.setSecondaryModel(endpoint, model)
    fun getSecondaryModelDisplayName(): String? = comparisonDelegate.getSecondaryModelDisplayName()
    fun toggleMcpServer(serverName: String) = modelDelegate.toggleMcpServer(serverName)
    fun toggleTool(toolName: String) = modelDelegate.toggleTool(toolName)
    fun updateModelParameters(parameters: ModelParameters) = modelDelegate.updateModelParameters(parameters)

    fun branchFromComparison(agentId: String) = comparisonDelegate.branchFromComparison(agentId)

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
