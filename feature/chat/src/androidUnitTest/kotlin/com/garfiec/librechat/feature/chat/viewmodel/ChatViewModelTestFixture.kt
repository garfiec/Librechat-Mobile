package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.ConversationWebSearchStore
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.AgentToolsRepository
import com.garfiec.librechat.core.data.repository.ChatRepository
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
import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.repository.WebSearchReadinessRepository
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.DEFAULT_COMPACT_NUDGE_THRESHOLD
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformDelegateFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * The collaborators of a [ChatViewModel] under test, and the stubs it cannot be constructed
 * without.
 *
 * Every `ChatViewModel` test builds its subject from here, so adding a collaborator to the 30-odd
 * constructor arguments stays a one-file change.
 *
 * Mocks are `relaxed`, but the stubs in [stubDefaults] are not optional decoration:
 * - `uiState` is `combine(_uiState, <pref flows>).stateIn(Eagerly, ChatUiState())`, so until every
 *   preference flow has emitted, `uiState.value` is the untouched initial state no matter what the
 *   ViewModel did — an unstubbed pref makes a test assert against a value nothing produced.
 * - `StateFlow.collect` returns `Nothing`, which a relaxed mock cannot fabricate, so an unstubbed
 *   revision flow throws inside whichever collector reaches it first.
 *
 * A test that needs a different value just re-stubs after `stubDefaults()`; the later `every` wins.
 */
internal class ChatViewModelTestFixture {

    val agentRepository = mockk<AgentRepository>(relaxed = true)
    val chatRepository = mockk<ChatRepository>(relaxed = true)
    val messageRepository = mockk<MessageRepository>(relaxed = true)
    val fileRepository = mockk<FileRepository>(relaxed = true)
    val configRepository = mockk<ConfigRepository>(relaxed = true)
    val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    val endpointTokenRepository = mockk<EndpointTokenRepository>(relaxed = true)
    val draftRepository = mockk<DraftRepository>(relaxed = true)
    val favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
    val keyRepository = mockk<KeyRepository>(relaxed = true)
    val presetRepository = mockk<PresetRepository>(relaxed = true)
    val promptRepository = mockk<PromptRepository>(relaxed = true)
    val queuedTurnRepository = mockk<QueuedTurnRepository>(relaxed = true)

    // A real revision flow: the link-active lookup collects it, and a relaxed StateFlow can't be collected.
    val shareRepository = mockk<ShareRepository>(relaxed = true).also {
        every { it.revision } returns MutableStateFlow(0L)
    }
    val shareLinkControllerFactory = mockk<ShareLinkControllerFactory>(relaxed = true)

    // Real flows: the bookmark icon collects the cached tags and the checklist's tag list.
    val tagRepository = mockk<TagRepository>(relaxed = true).also {
        every { it.observeConversationTags(any()) } returns flowOf(emptyList())
        every { it.observeTags() } returns flowOf(emptyList())
    }
    val traceRepository = mockk<TraceRepository>(relaxed = true)
    val mcpRepository = mockk<McpRepository>(relaxed = true)
    val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    val roleRepository = mockk<RoleRepository>(relaxed = true)
    val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    val agentToolsRepository = mockk<AgentToolsRepository>(relaxed = true)

    /** The web-search key check's answer (`GET /api/agents/tools/web_search/auth`). */
    var webSearchAuthenticated = true

    /** Settings → Chat's web-search order. */
    val webSearchFirst = MutableStateFlow(WebSearchPreference.LIBRECHAT_FIRST)

    /** Each conversation's remembered web-search switch; nothing stored unless a test says so. */
    val conversationWebSearchStore = mockk<ConversationWebSearchStore>(relaxed = true)

    /** Shared by every ViewModel this fixture builds, like the app's single, so its cache carries over. */
    private var webSearchReadiness: WebSearchReadinessRepository? = null

    /** The shared memory opt-out; null until a test publishes one, as before any fetch. */
    val memoriesEnabled = MutableStateFlow<Boolean?>(null)

    /** Moves on every [toggleMemoriesInSettings], like MemoryRepositoryImpl's write generation. */
    private var memoryPreferenceWrites = 0

    /** A Settings toggle: a preference write the stubbed profile read must not undo. */
    fun toggleMemoriesInSettings(enabled: Boolean) {
        memoryPreferenceWrites++
        memoriesEnabled.value = enabled
    }
    val permissionGate = mockk<PermissionGate>(relaxed = true)
    val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true)
    val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    val preferenceStore = mockk<PreferenceStore>(relaxed = true)
    val platformDelegateFactory = mockk<PlatformDelegateFactory>(relaxed = true)
    val serverFileSelectionHandoff = mockk<ServerFileSelectionHandoff>(relaxed = true)

    val selectionHandoff = NewChatSelectionHandoff()
    val siteIconPromptSession = SiteIconPromptSession()
    val promptInsertionHandoff = PromptInsertionHandoff()

    /** The per-conversation Skills toggle store, by slot (a conversation id, `new` or `__defaults__`). */
    val skillsToggles = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Driven by hand where a test needs a prompt mutation to reach a retained ViewModel. */
    val promptRevision = MutableStateFlow(0L)

    fun stubDefaults() {
        every { configRepository.startupConfig } returns MutableStateFlow(null)
        every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.7")
        every { configRepository.detectedBackend } returns MutableStateFlow(null)
        every { configRepository.endpointConfigs } returns MutableStateFlow(emptyMap())
        every { configRepository.availableModels } returns MutableStateFlow(emptyMap())
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { memoryRepository.memoriesEnabled } returns memoriesEnabled
        // The real repository's contract: run the caller's profile GET and publish the opt-out it
        // carries, unless a preference write happened while it was in flight.
        coEvery { memoryRepository.fetchUserPublishingPreference(any()) } coAnswers {
            val generation = memoryPreferenceWrites
            val result = firstArg<suspend () -> Result<User>>().invoke()
            if (result is Result.Success && memoryPreferenceWrites == generation) {
                memoriesEnabled.value = result.data.personalization?.memories ?: true
            }
            result
        }
        every { favoritesRepository.favorites } returns MutableStateFlow(emptyList())
        every { keyRepository.keyInvalidations } returns MutableSharedFlow()
        every { agentRepository.revision } returns MutableStateFlow(0L)
        every { promptRepository.revision } returns promptRevision
        every { messageRepository.observeMessages(any()) } returns emptyFlow()
        // A v0.8.8+ server: no projection, and no token-config unless a test supplies one.
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Success(null)
        coEvery { endpointTokenRepository.getTokenConfig() } returns Result.Success(emptyMap())
        // A server WITHOUT queued turns by default, so the reconcile poll exits on its first tick
        // and no test that is not about the feature pays for it. A relaxed mock would instead hand
        // back an erased QueuedTurnOutcome whose payload fails the List cast inside applyReceipts.
        coEvery { queuedTurnRepository.isUnsupported() } returns true
        coEvery { queuedTurnRepository.list(any(), any()) } returns QueuedTurnOutcome.Unsupported
        coEvery { queuedTurnRepository.enqueue(any()) } returns QueuedTurnOutcome.Unsupported
        coEvery { queuedTurnRepository.cancel(any()) } returns QueuedTurnOutcome.Unsupported
        every { serverFileSelectionHandoff.selectionsFor(any()) } returns emptyFlow()
        every { platformDelegateFactory.createShareConsumer().sharesFor(any()) } returns emptyFlow()

        every { serverDataStore.currentUrlFlow } returns MutableStateFlow("https://example.test")
        every { settingsDataStore.selectedMcpServers } returns MutableStateFlow(emptySet())
        every { settingsDataStore.enabledTools } returns MutableStateFlow(emptySet())
        every { settingsDataStore.chatFontSize } returns MutableStateFlow(ChatFontSize.MEDIUM)
        every { settingsDataStore.starredModelsDisplay } returns MutableStateFlow(StarredModelsDisplay.OFF)
        every { settingsDataStore.chatHeaderContent } returns MutableStateFlow(ChatHeaderContent.MODEL)
        every { settingsDataStore.chatHeaderAlignment } returns MutableStateFlow(ChatHeaderAlignment.CENTER)
        every { settingsDataStore.contextBarPlacement } returns MutableStateFlow(ContextBarPlacement.HIDDEN)
        every { settingsDataStore.contextGaugeExpanded } returns MutableStateFlow(false)
        // The context prefs pre-fold reads these; an unstubbed relaxed Flow never emits and would
        // stall the whole uiState combine.
        every { settingsDataStore.effectiveContextSections } returns MutableStateFlow(ContextDetailPreset.STANDARD.sections)
        every { settingsDataStore.compactNudgeThreshold } returns MutableStateFlow(DEFAULT_COMPACT_NUDGE_THRESHOLD)
        every { settingsDataStore.compactNudgeSnoozes } returns MutableStateFlow(emptyMap())
        every { preferenceStore.observe(AppPreferences.SiteIcons) } returns MutableStateFlow(null)
        every { preferenceStore.observe(AppPreferences.PreferLastUsedModel) } returns MutableStateFlow(false)
        every { preferenceStore.initial(AppPreferences.SiteIcons) } returns null
        every { preferenceStore.observe(AppPreferences.WebSearchFirst) } returns webSearchFirst
        coEvery { conversationWebSearchStore.get(any()) } returns null
        coEvery { agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } answers {
            Result.Success(ToolAuthResult(authenticated = webSearchAuthenticated))
        }
        every { settingsDataStore.duringRunAction } returns MutableStateFlow(DuringRunAction.QUEUE)
        // A real slot map rather than a relaxed Flow, so a toggle written here reads back.
        every { settingsDataStore.skillsToggle(any()) } answers {
            val slot = firstArg<String>()
            skillsToggles.map { it[slot] }
        }
        coEvery { settingsDataStore.setSkillsToggle(any(), any()) } answers {
            skillsToggles.value = skillsToggles.value + (firstArg<String>() to secondArg<Boolean>())
        }
    }

    fun build(
        defaultDispatcher: CoroutineDispatcher,
        initialConversationId: String? = null,
        initialAgentId: String? = null,
        initialIsTemporary: Boolean = false,
        initialChatProjectId: String? = null,
        activeAccountProvider: ActiveAccountProvider =
            InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-1"))),
        clock: Clock = Clock.System,
    ): ChatViewModel = ChatViewModel(
        initialConversationId = initialConversationId,
        initialAgentId = initialAgentId,
        initialIsTemporary = initialIsTemporary,
        initialChatProjectId = initialChatProjectId,
        agentRepository = agentRepository,
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        fileRepository = fileRepository,
        resumePinStore = ResumePinStore(),
        configRepository = configRepository,
        conversationRepository = conversationRepository,
        endpointTokenRepository = endpointTokenRepository,
        draftRepository = draftRepository,
        favoritesRepository = favoritesRepository,
        keyRepository = keyRepository,
        presetRepository = presetRepository,
        promptRepository = promptRepository,
        queuedTurnRepository = queuedTurnRepository,
        shareRepository = shareRepository,
        shareLinkControllerFactory = shareLinkControllerFactory,
        tagRepository = tagRepository,
        traceRepository = traceRepository,
        mcpRepository = mcpRepository,
        skillsRepository = skillsRepository,
        userRepository = userRepository,
        roleRepository = roleRepository,
        memoryRepository = memoryRepository,
        webSearchReadiness = webSearchReadiness
            ?: WebSearchReadinessRepository(agentToolsRepository, activeAccountProvider).also { webSearchReadiness = it },
        conversationWebSearchStore = conversationWebSearchStore,
        permissionGate = permissionGate,
        connectivityObserver = connectivityObserver,
        serverDataStore = serverDataStore,
        settingsDataStore = settingsDataStore,
        platformDelegateFactory = platformDelegateFactory,
        json = Json { ignoreUnknownKeys = true },
        defaultDispatcher = defaultDispatcher,
        selectionHandoff = selectionHandoff,
        serverFileSelectionHandoff = serverFileSelectionHandoff,
        promptInsertionHandoff = promptInsertionHandoff,
        siteIconPromptSession = siteIconPromptSession,
        preferenceStore = preferenceStore,
        activeAccountProvider = activeAccountProvider,
        clock = clock,
    )
}
