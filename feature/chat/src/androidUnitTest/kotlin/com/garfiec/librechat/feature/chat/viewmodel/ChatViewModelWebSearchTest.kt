package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The web-search control end to end through the ViewModel: the server's key check decides whether
 * LibreChat search can run, Settings decides which search goes first, and each send carries exactly
 * one of `ephemeralAgent.web_search` and the top-level `web_search`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelWebSearchTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(StartupConfig(webSearch = SEARCH_CONFIG))

    /** Each send's top-level `web_search` and `ephemeralAgent`. */
    private val topLevel = mutableListOf<Boolean>()
    private val ephemeral = mutableListOf<EphemeralAgent?>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.startupConfig } returns startupConfig
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every { startChatCapturing() } returns MutableSharedFlow()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with LibreChat search set up, a tap runs it alone`() = webSearchTest { vm ->
        assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()

        sendWithSearch(vm)

        assertThat(ephemeral.single()?.webSearch).isTrue()
        assertThat(topLevel.single()).isFalse()
    }

    @Test
    fun `when the server's key check fails, a tap runs the built-in search alone`() {
        fixture.webSearchAuthenticated = false
        webSearchTest { vm ->
            assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()

            sendWithSearch(vm)

            assertThat(topLevel.single()).isTrue()
            assertThat(ephemeral.single()?.webSearch).isNull()
        }
    }

    @Test
    fun `a server with no search provider is never asked for keys`() {
        startupConfig.value = StartupConfig()
        webSearchTest { vm ->
            assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
            coVerify(exactly = 0) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
        }
    }

    @Test
    fun `a config without search clears an earlier server's answer`() = webSearchTest { vm ->
        assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()

        startupConfig.value = StartupConfig()
        runCurrent()

        assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
    }

    @Test
    fun `another server with search set up is asked for its own keys`() = webSearchTest { vm ->
        assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()

        fixture.webSearchAuthenticated = false
        startupConfig.value = StartupConfig(appTitle = "Other server", webSearch = OTHER_SEARCH_CONFIG)
        runCurrent()

        coVerify(exactly = 2) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
        assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
    }

    @Test
    fun `a second chat has the answer at once without asking again`() = webSearchTest { first ->
        val second = fixture.build(defaultDispatcher = testDispatcher)
        try {
            assertThat(first.uiState.value.gates.librechatSearchReady).isTrue()
            assertThat(second.uiState.value.gates.librechatSearchReady).isTrue()
            coVerify(exactly = 1) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
        } finally {
            second.viewModelScope.cancel()
        }
    }

    @Test
    fun `a key check that failed is retried, not read as not set up`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returnsMany listOf(
            Result.Error(message = "offline"),
            Result.Success(ToolAuthResult(authenticated = true)),
        )
        webSearchTest { vm ->
            assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
            assertThat(vm.uiState.value.gates.librechatSearchChecking).isTrue()

            advanceTimeBy(2_001)

            assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()
            assertThat(vm.uiState.value.gates.librechatSearchChecking).isFalse()
        }
    }

    @Test
    fun `while the key check is unanswered a send runs no search, not the built-in one`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns
            Result.Error(message = "offline")
        webSearchTest { vm ->
            sendWithSearch(vm)

            assertThat(topLevel.single()).isFalse()
            assertThat(ephemeral.single()?.webSearch).isNull()
        }
    }

    @Test
    fun `once the key check gives up, the hold lifts and the built-in search runs`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns
            Result.Error(message = "offline")
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)
            assertThat(vm.uiState.value.webSearchTarget).isEqualTo(WebSearchRoute.CHECKING)

            advanceTimeBy(120_000)

            assertThat(vm.uiState.value.gates.librechatSearchChecking).isFalse()
            assertThat(vm.uiState.value.webSearchTarget).isEqualTo(WebSearchRoute.BUILT_IN)
        }
    }

    @Test
    fun `after the hold lifts the check keeps going, so a connection that comes back is noticed`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returnsMany
            List(6) { Result.Error(message = "offline") } + Result.Success(ToolAuthResult(authenticated = true))
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)
            advanceTimeBy(120_000)
            assertThat(vm.uiState.value.webSearchTarget).isEqualTo(WebSearchRoute.BUILT_IN)

            advanceTimeBy(60_001)

            assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()
            assertThat(vm.uiState.value.webSearchTarget).isEqualTo(WebSearchRoute.LIBRECHAT)
        }
    }

    @Test
    fun `a switch changed by any writer in an existing chat is remembered`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns
            Result.Success(Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL))
        webSearchTest(CONVERSATION) { vm ->
            advanceUntilIdle()
            vm.updateModelParameters(vm.uiState.value.modelParameters.copy(webSearch = true))
            runCurrent()

            coVerify { fixture.conversationWebSearchStore.set(CONVERSATION, true) }
        }
    }

    @Test
    fun `a role without web search is never asked for keys`() {
        every { fixture.roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "USER", permissions = mapOf(PermissionType.WEB_SEARCH.serverKey to mapOf("USE" to false))),
        )
        webSearchTest { vm ->
            assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
            coVerify(exactly = 0) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
        }
    }

    @Test
    fun `a toggle in an existing chat is remembered for it`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns
            Result.Success(Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL))
        webSearchTest(CONVERSATION) { vm ->
            vm.toggleTool(ToolConstants.WEB_SEARCH)
            runCurrent()

            coVerify { fixture.conversationWebSearchStore.set(CONVERSATION, true) }
        }
    }

    @Test
    fun `a preset that turns web search off in an existing chat is remembered for it`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns
            Result.Success(Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL))
        coEvery { fixture.presetRepository.getAll() } returns Result.Success(
            listOf(Preset(presetId = "off", title = "No search", endpoint = ENDPOINT, model = MODEL, webSearch = false)),
        )
        webSearchTest(CONVERSATION) { vm ->
            vm.toggleTool(ToolConstants.WEB_SEARCH)
            runCurrent()

            vm.loadPreset(PresetDisplayData(presetId = "off", title = "No search", endpointLabel = null, model = null))
            runCurrent()

            assertThat(vm.uiState.value.modelParameters.webSearch).isFalse()
            coVerify { fixture.conversationWebSearchStore.set(CONVERSATION, false) }
        }
    }

    @Test
    fun `a reopened chat has its remembered switch over the server's`() {
        // The server saved no web_search: the chat ran LibreChat's search.
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns
            Result.Success(Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL))
        coEvery { fixture.conversationWebSearchStore.get(CONVERSATION) } returns true
        webSearchTest(CONVERSATION) { vm ->
            advanceUntilIdle()

            assertThat(vm.uiState.value.modelParameters.webSearch).isTrue()
            assertThat(vm.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.LIBRECHAT)
        }
    }

    @Test
    fun `preferring the built-in search sends it alone`() = webSearchTest { vm ->
        fixture.webSearchFirst.value = WebSearchPreference.BUILT_IN_FIRST
        runCurrent()

        sendWithSearch(vm)

        assertThat(topLevel.single()).isTrue()
        assertThat(ephemeral.single()?.webSearch).isNull()
    }

    private fun MockKMatcherScope.startChatCapturing() = fixture.chatRepository.startChat(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), capture(topLevel), any(), any(), captureNullable(ephemeral),
        any(), any(), any(), any(), any(),
    )

    private fun TestScope.sendWithSearch(vm: ChatViewModel) {
        vm.onModelSelected(ENDPOINT, MODEL)
        vm.toggleTool(ToolConstants.WEB_SEARCH)
        vm.onInputChanged("hello")
        vm.sendMessage()
        runCurrent()
    }

    private fun webSearchTest(
        conversationId: String? = null,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = conversationId)
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val ENDPOINT = "openAI"
        const val MODEL = "gpt-4.1"
        const val CONVERSATION = "ws-1"
        val SEARCH_CONFIG: JsonObject = buildJsonObject { put("searchProvider", "serper") }
        val OTHER_SEARCH_CONFIG: JsonObject = buildJsonObject { put("searchProvider", "searxng") }
    }
}
