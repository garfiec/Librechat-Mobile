package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.prefetch.AttachmentWarmer
import com.garfiec.librechat.core.data.prefetch.PrefetchDepth
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsContract
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val contentReader = mockk<ContentReader>(relaxed = true)
    private val cacheCleaner = mockk<PlatformCacheCleaner>(relaxed = true)
    private val speechSettingsContract = mockk<SpeechSettingsContract>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val dateTimePrefsStore = mockk<DateTimePrefsStore>(relaxed = true)
    private val selectedLanguageFlow = MutableStateFlow(SettingsDataStore.DEFAULT_LANGUAGE)
    private val dateTimePrefsFlow = MutableStateFlow(DateTimeFormatPrefs())
    private val uploadRoutingModeFlow = MutableStateFlow(UploadRoutingMode.AUTO)
    private val siteIconsChoiceFlow = MutableStateFlow<Boolean?>(null)
    private val mcpRepository = mockk<McpRepository>(relaxed = true)
    private val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    private val speechRepository = mockk<SpeechRepository>(relaxed = true)
    private val balanceRepository = mockk<BalanceRepository>(relaxed = true)
    private val shareRepository = mockk<ShareRepository>(relaxed = true)
    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val diagnosticLogRepository = mockk<DiagnosticLogRepository>(relaxed = true)

    private val testUser = User(
        email = "test@example.com",
        name = "Test User",
        username = "testuser",
        avatar = "https://example.com/avatar.png",
        twoFactorEnabled = false,
    )

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup DataStore flows
        every { serverDataStore.currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
        every { settingsDataStore.chatFontSize } returns MutableStateFlow(ChatFontSize.MEDIUM)
        every { settingsDataStore.autoScrollEnabled } returns MutableStateFlow(true)
        every { settingsDataStore.showThinkingBlocks } returns MutableStateFlow(true)
        every { settingsDataStore.autoReadEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.selectedVoiceId } returns MutableStateFlow(null)
        every { settingsDataStore.showImageDescriptions } returns MutableStateFlow(false)
        every { settingsDataStore.dismissKeyboardOnSend } returns MutableStateFlow(false)
        every { settingsDataStore.ttsSource } returns MutableStateFlow("device")
        every { settingsDataStore.ttsSpeechRate } returns MutableStateFlow(1.0f)
        every { settingsDataStore.ttsPitch } returns MutableStateFlow(1.0f)
        every { settingsDataStore.ttsVoiceName } returns MutableStateFlow("")
        every { settingsDataStore.ttsEngine } returns MutableStateFlow("")
        every { settingsDataStore.ttsVoice } returns MutableStateFlow("")
        every { settingsDataStore.ttsCaching } returns MutableStateFlow(true)
        every { settingsDataStore.tabletSidebarGestureEnabled } returns MutableStateFlow(true)
        every { settingsDataStore.autoSendAfterStt } returns MutableStateFlow(false)
        every { settingsDataStore.sttEngine } returns MutableStateFlow("")
        every { settingsDataStore.sttLanguage } returns MutableStateFlow("")
        every { settingsDataStore.sttOnDevice } returns MutableStateFlow(true)
        every { settingsDataStore.sttEndOfSpeech } returns MutableStateFlow(false)
        // The preferences state is one combine chain, so a source that never emits stalls all of it —
        // an unstubbed flow here shows up as an unrelated setting silently keeping its default.
        every { settingsDataStore.prefetchEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.prefetchAttachmentsEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.prefetchOnMeteredEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.prefetchDepth } returns MutableStateFlow(PrefetchDepth.DEFAULT)
        every { settingsDataStore.siteIconsChoice } returns siteIconsChoiceFlow
        coEvery { settingsDataStore.setShowSiteIcons(any()) } answers { siteIconsChoiceFlow.value = firstArg() }
        every { settingsDataStore.chatLayoutStyle } returns MutableStateFlow(ChatLayoutConstants.THREAD)
        every { settingsDataStore.showAvatars } returns MutableStateFlow(true)
        every { settingsDataStore.showBubbles } returns MutableStateFlow(false)
        every { settingsDataStore.latexRenderer } returns MutableStateFlow(LatexRenderer.KATEX)
        every { settingsDataStore.selectedLanguage } returns selectedLanguageFlow
        every { settingsDataStore.uploadRoutingMode } returns uploadRoutingModeFlow
        coEvery { settingsDataStore.setUploadRoutingMode(any()) } answers { uploadRoutingModeFlow.value = firstArg() }
        coEvery { settingsDataStore.setSelectedLanguage(any()) } answers { selectedLanguageFlow.value = firstArg() }
        every { dateTimePrefsStore.prefs } returns dateTimePrefsFlow
        coEvery { dateTimePrefsStore.set(any()) } answers { dateTimePrefsFlow.value = firstArg() }

        // Setup default API responses
        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        coEvery { mcpRepository.listServers() } returns Result.Success(emptyList())
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { memoryRepository.getMemories() } returns Result.Success(emptyList())
        coEvery { speechRepository.getVoices() } returns Result.Success(emptyList())
        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(SpeechConfig())
        coEvery { balanceRepository.getBalance() } returns Result.Error(message = "Not available")

        // Permissive-null defaults so existing tests continue to exercise the
        // same load paths via the `?: != false` idiom.
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        coEvery { permissionGate.awaitRole() } returns null
        every { configRepository.startupConfig } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(
        contentReader = contentReader,
        cacheCleaner = cacheCleaner,
        userRepository = userRepository,
        authRepository = authRepository,
        conversationRepository = conversationRepository,
        serverDataStore = serverDataStore,
        settingsDataStore = settingsDataStore,
        dateTimePrefsStore = dateTimePrefsStore,
        mcpRepository = mcpRepository,
        memoryRepository = memoryRepository,
        speechSettingsFactory = SpeechSettingsFactory { speechSettingsContract },
        balanceRepository = balanceRepository,
        shareRepository = shareRepository,
        keyRepository = keyRepository,
        roleRepository = roleRepository,
        permissionGate = permissionGate,
        configRepository = configRepository,
        diagnosticLogRepository = diagnosticLogRepository,
        appInfo = object : AppInfo {
            override val versionName = "0.1.0"
            override val versionCode = 1L
            override val gitSha = "testsha0"
        },
        attachmentWarmer = object : AttachmentWarmer {
            override val isSupported = true
            override suspend fun warm(url: String) = Unit
        },
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `initial state loads user profile`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user).isNotNull()
        assertThat(state.user?.name).isEqualTo("Test User")
        assertThat(state.user?.email).isEqualTo("test@example.com")
        assertThat(state.profileLoadError).isNull()
    }

    @Test
    fun `user load failure routes to profileLoadError, not snackbar error`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "Network error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user).isNull()
        assertThat(state.profileLoadError).isEqualTo("Network error")
        assertThat(state.error).isNull()
    }

    @Test
    fun `retry after profile load failure clears profileLoadError on success`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "Network error")
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.profileLoadError).isEqualTo("Network error")

        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.profileLoadError).isNull()
        assertThat(state.user).isNotNull()
    }

    @Test
    fun `retry cancels in-flight load so its stale error cannot overwrite the new result`() = runTest {
        // First call hangs (simulates a 90s unreachable-server timeout) then resolves to an error.
        // Production correctness requires UserRepositoryImpl to wrap the call in safeApiCall so
        // CancellationException propagates; this test exercises the ViewModel-level guard given
        // that wrapping. A regression in UserRepositoryImpl bypassing safeApiCall would not be
        // caught here — covered separately by SafeApiCallTest.safeApiCallPropagatesCancellation.
        coEvery { userRepository.getUser() } coAnswers {
            delay(60_000)
            Result.Error(message = "stale cancelled error")
        }
        viewModel = createViewModel()
        advanceTimeBy(100) // let init { loadUser() } start the request and suspend inside delay()

        // Second call returns success immediately.
        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user?.name).isEqualTo("Test User")
        assertThat(state.profileLoadError).isNull()
    }

    @Test
    fun `logout sets isLoggedOut flag without calling authRepository`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.logout() }
        assertThat(viewModel.uiState.value.isLoggedOut).isTrue()
    }

    @Test
    fun `deleteAccount calls userRepository and sets isAccountDeleted without calling authRepository`() = runTest {
        coEvery { userRepository.deleteUser() } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        coVerify { userRepository.deleteUser() }
        coVerify(exactly = 0) { authRepository.logout() }
        assertThat(viewModel.uiState.value.isAccountDeleted).isTrue()
    }

    @Test
    fun `deleteAccount failure shows error`() = runTest {
        coEvery { userRepository.deleteUser() } returns Result.Error(message = "Server error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Server error")
        assertThat(viewModel.uiState.value.isAccountDeleted).isFalse()
    }

    @Test
    fun `site icons read as off until chosen, then follow the stored choice`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSiteIcons).isFalse()

        viewModel.setShowSiteIcons(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.setShowSiteIcons(true) }
        assertThat(viewModel.uiState.value.showSiteIcons).isTrue()
    }

    @Test
    fun `clearAllChats calls conversationRepository deleteAll`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearAllChats()
        advanceUntilIdle()

        coVerify { conversationRepository.deleteAll() }
        assertThat(viewModel.uiState.value.isClearing).isFalse()
    }

    @Test
    fun `clearAllChats failure shows error`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns
            Result.Error(message = "Failed to clear")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearAllChats()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Failed to clear")
    }

    @Test
    fun `dismissError clears error state`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns Result.Error(message = "Error")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.clearAllChats()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.dismissError()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `twoFactorEnabled state reflects user profile`() = runTest {
        val userWith2FA = testUser.copy(twoFactorEnabled = true)
        coEvery { userRepository.getUser() } returns Result.Success(userWith2FA)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isTwoFactorEnabled).isTrue()
    }

    @Test
    fun `memoriesEnabled is hydrated from the profile opt-out`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(
            testUser.copy(personalization = UserPersonalization(memories = false)),
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()
    }

    @Test
    fun `memoriesEnabled defaults on when the profile has no personalization block`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(personalization = null))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()
    }

    @Test
    fun `revokeAllKeys calls keyRepository`() = runTest {
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeAllKeys()
        advanceUntilIdle()

        coVerify { keyRepository.deleteAllKeys() }
        assertThat(viewModel.uiState.value.isKeyRevoking).isFalse()
    }

    @Test
    fun `revokeAllKeys failure shows error`() = runTest {
        coEvery { keyRepository.deleteAllKeys() } returns
            Result.Error(message = "Failed to revoke")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeAllKeys()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Failed to revoke")
    }

    @Test
    fun `setLanguage updates language and dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showLanguageDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showLanguageDialog).isTrue()

        viewModel.setLanguage("fr")
        advanceUntilIdle()

        coVerify { settingsDataStore.setSelectedLanguage("fr") }
        assertThat(viewModel.uiState.value.selectedLanguage).isEqualTo("fr")
        assertThat(viewModel.uiState.value.showLanguageDialog).isFalse()
    }

    @Test
    fun `saveDateTimePrefs writes all three choices and dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDateTimeDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDateTimeDialog).isTrue()

        val chosen = DateTimeFormatPrefs(TimestampStyle.SMART, ClockFormat.H24, DateFormatStyle.YMD_DASH)
        viewModel.saveDateTimePrefs(chosen)
        advanceUntilIdle()

        coVerify(exactly = 1) { dateTimePrefsStore.set(chosen) }
        assertThat(viewModel.uiState.value.dateTimePrefs).isEqualTo(chosen)
        assertThat(viewModel.uiState.value.showDateTimeDialog).isFalse()
    }

    @Test
    fun `dismissing the date-time dialog writes nothing`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDateTimeDialog()
        viewModel.dismissDateTimeDialog()
        advanceUntilIdle()

        coVerify(exactly = 0) { dateTimePrefsStore.set(any()) }
        assertThat(viewModel.uiState.value.showDateTimeDialog).isFalse()
    }

    @Test
    fun `setForkMode updates mode and dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showForkSettingsDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showForkSettingsDialog).isTrue()

        viewModel.setForkMode("allBranches")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.forkMode).isEqualTo("allBranches")
        assertThat(viewModel.uiState.value.showForkSettingsDialog).isFalse()
    }

    @Test
    fun `retry reloads user profile`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        // Called twice: once in init, once in retry
        coVerify(exactly = 2) { userRepository.getUser() }
    }

    @Test
    fun `exportAllData shows coming soon flag`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.exportAllData()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showExportComingSoon).isTrue()

        viewModel.dismissExportComingSoon()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showExportComingSoon).isFalse()
    }

    @Test
    fun `exportLogs offers the buffer under a log filename`() = runTest {
        val buffer = """{"ts":1,"msg":"first"}""" + "\n" + """{"ts":2,"msg":"second"}""" + "\n"
        coEvery { diagnosticLogRepository.exportText() } returns buffer

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.exportLogs()
        advanceUntilIdle()

        val payload = viewModel.uiState.value.logsExportReady
        assertThat(payload).isNotNull()
        assertThat(payload?.content).isEqualTo(buffer)
        assertThat(payload?.fileName).endsWith(".log")
        assertThat(viewModel.uiState.value.isLogsExporting).isFalse()
    }

    @Test
    fun `setUploadRoutingMode persists and surfaces the new mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.uploadRoutingMode).isEqualTo(UploadRoutingMode.AUTO)

        viewModel.setUploadRoutingMode(UploadRoutingMode.MANUAL)
        advanceUntilIdle()

        coVerify { settingsDataStore.setUploadRoutingMode(UploadRoutingMode.MANUAL) }
        assertThat(viewModel.uiState.value.uploadRoutingMode).isEqualTo(UploadRoutingMode.MANUAL)
    }

    /**
     * The Settings screen's own MCP dialog is the second save path (McpServerDelegate); a key
     * binding refusal has to reach its state too, or that dialog reports a failure every retry
     * repeats.
     */
    @Test
    fun `a refused MCP edit asks for the API key again`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Re-enter apiKey.key",
                    body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED","message":"Re-enter apiKey.key"}""",
                ),
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showEditMcpServerDialog(
            McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE),
        )

        viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isTrue()
        assertThat(viewModel.uiState.value.mcpOAuthSecretReentryRequired).isFalse()
        assertThat(viewModel.uiState.value.showMcpServerDialog).isTrue()
    }

    /** The Settings path's copy of the same rule: the prompt is cleared when its dialog closes or reopens. */
    @Test
    fun `MCP re-entry prompts do not survive the Settings dialog closing or reopening`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "x",
                    body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED"}""",
                ),
            )
        val server = McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        viewModel = createViewModel()
        advanceUntilIdle()
        fun refused() {
            viewModel.showEditMcpServerDialog(server)
            viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isTrue()
        }

        refused()
        viewModel.dismissMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isFalse()

        refused()
        viewModel.showAddMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isFalse()
        assertThat(viewModel.uiState.value.mcpOAuthSecretReentryRequired).isFalse()
    }

    /**
     * A failed save keeps the MCP dialog open, and `error` is a snackbar in the screen the dialog
     * covers: reported there, the failure times out unseen and the save looks like it did nothing.
     */
    @Test
    fun `a failed MCP save is reported inside the dialog, not behind it`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(exception = IllegalStateException("boom"), message = "Something went wrong")
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showMcpServerDialog).isTrue()
        assertThat(viewModel.uiState.value.mcpServerDialogError).isEqualTo("Something went wrong")
        assertThat(viewModel.uiState.value.error).isNull()

        viewModel.dismissMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpServerDialogError).isNull()
    }

    /** The Settings path's copy of the rule: a save whose dialog closed reports through the snackbar. */
    @Test
    fun `an MCP save failure after the Settings dialog closed is reported as a snackbar`() = runTest {
        val reply = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } coAnswers { reply.await() }
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()
        viewModel.saveMcpServer(name = "Docs", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpServerSaving).isTrue()

        viewModel.dismissMcpServerDialog()
        reply.complete(Result.Error(exception = IllegalStateException("boom"), message = "Something went wrong"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Something went wrong")
        assertThat(viewModel.uiState.value.mcpServerDialogError).isNull()
        assertThat(viewModel.uiState.value.mcpServerSaving).isFalse()
    }

    /** The Settings path reads the same `errors[]` reasons as the MCP screen. */
    @Test
    fun `a validation refusal shows the server's specific reason in the Settings dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Invalid configuration",
                    body = """{"message":"Invalid configuration","errors":[{"message":"url: Invalid url"}]}""",
                ),
                message = "Invalid configuration",
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "not a url", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpServerDialogError).isEqualTo("url: Invalid url")
    }

    /** The Settings path shows a coded refusal's own sentence too. */
    @Suppress("MaxLineLength") // single-line wire JSON fixture
    @Test
    fun `a coded refusal shows the server's message in the Settings dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(statusCode = 403, message = "x", body = """{"error":"MCP_DOMAIN_NOT_ALLOWED","message":"Domain \"http://evil.example.org\" is not allowed"}"""),
                message = "Something went wrong. Please try again.",
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "http://evil.example.org/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpServerDialogError)
            .isEqualTo("Domain \"http://evil.example.org\" is not allowed")
    }

    /** The Settings path resends the stored icon and OBO scopes on an edit too. */
    @Test
    fun `a Settings MCP edit resends the stored icon and OBO scopes`() = runTest {
        val server = McpServer(
            name = "obo_mcp",
            url = "https://obo.example.test/mcp",
            type = McpServerType.STREAMABLE_HTTP,
            title = "Obo",
            iconPath = "https://obo.example.test/icon.png",
            obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showEditMcpServerDialog(server)

        viewModel.saveMcpServer(name = "Obo (renamed)", url = server.url, type = server.type)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "obo_mcp",
                name = "Obo (renamed)",
                description = null,
                url = server.url,
                type = server.type,
                apiKey = null,
                oauth = null,
                iconPath = "https://obo.example.test/icon.png",
                obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
            )
        }
    }

    /** The Settings path reports a re-entry refusal by its field alone too. */
    @Test
    fun `a Settings MCP re-entry refusal is reported by its field alone`() = runTest {
        for (code in listOf("MCP_API_KEY_REENTRY_REQUIRED", "MCP_OAUTH_SECRET_REENTRY_REQUIRED")) {
            coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.Error(exception = ApiException(statusCode = 400, message = "x", body = """{"error":"$code"}"""))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showEditMcpServerDialog(
                McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE),
            )

            viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.mcpApiKeyReentryRequired || state.mcpOAuthSecretReentryRequired).isTrue()
            assertThat(state.mcpServerDialogError).isNull()
        }
    }
}
