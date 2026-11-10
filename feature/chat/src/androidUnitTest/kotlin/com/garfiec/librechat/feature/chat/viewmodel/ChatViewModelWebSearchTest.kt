package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.ChatPayloadBuilder
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.network.di.librechatJson
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
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.JsonPrimitive
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

    /** Each send's top-level `web_search`, `ephemeralAgent` and model parameters. */
    private val topLevel = mutableListOf<Boolean>()
    private val ephemeral = mutableListOf<EphemeralAgent?>()
    private val params = mutableListOf<JsonObject?>()

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
    fun `a server with no search provider is asked for keys and holds no send for the answer`() {
        // Per-user keys: the server pins no provider, so it serves no webSearch config.
        startupConfig.value = StartupConfig()
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } coAnswers {
            delay(5_000)
            Result.Success(ToolAuthResult(authenticated = false, authTypes = USER_KEYS))
        }
        webSearchTest { vm ->
            assertThat(vm.uiState.value.gates.librechatSearchChecking).isFalse()

            sendWithSearch(vm)

            assertThat(topLevel.single()).isTrue()
            coVerify(exactly = 1) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
        }
    }

    @Test
    fun `a config without search is asked for its own answer`() = webSearchTest { vm ->
        assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()

        fixture.webSearchAuthenticated = false
        startupConfig.value = StartupConfig()
        runCurrent()

        assertThat(vm.uiState.value.gates.librechatSearchReady).isFalse()
        coVerify(exactly = 2) { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) }
    }

    @Test
    fun `the keys the server leaves to the user reach the gates with the pinned provider`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns
            Result.Success(ToolAuthResult(authenticated = false, authTypes = USER_KEYS, rerankerType = "none"))
        webSearchTest { vm ->
            val keys = vm.uiState.value.gates.webSearchKeys

            assertThat(keys?.userCategories).containsExactly("providers", "scrapers").inOrder()
            assertThat(keys?.authenticated).isFalse()
            assertThat(keys?.pinnedSearchProvider).isEqualTo("serper")
        }
    }

    @Test
    fun `while the built-in search stands in the keys row names it`() {
        startupConfig.value = StartupConfig()
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns
            Result.Success(ToolAuthResult(authenticated = false, authTypes = USER_KEYS))
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)
            assertThat(vm.uiState.value.webSearchKeysRow?.fallbackProvider).isNull()

            vm.toggleTool(ToolConstants.WEB_SEARCH)

            val row = vm.uiState.value.webSearchKeysRow
            assertThat(row?.authenticated).isFalse()
            assertThat(row?.fallbackProvider).isEqualTo("OpenAI")
        }
    }

    @Test
    fun `on an endpoint with no built-in search the tile shows and turning it on asks for keys`() {
        startupConfig.value = StartupConfig()
        every { fixture.configRepository.endpointConfigs } returns
            MutableStateFlow(mapOf(ENDPOINT to EndpointConfig(), CUSTOM to EndpointConfig()))
        every { fixture.configRepository.availableModels } returns
            MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL), CUSTOM to listOf(CUSTOM_MODEL)))
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns
            Result.Success(ToolAuthResult(authenticated = false, authTypes = USER_KEYS))
        webSearchTest { vm ->
            vm.onModelSelected(CUSTOM, CUSTOM_MODEL)
            val state = vm.uiState.value

            assertThat(state.webSearchTarget).isEqualTo(WebSearchRoute.NONE)
            assertThat(state.sheetTools).contains(ToolConstants.WEB_SEARCH)
            assertThat(state.openTools).doesNotContain(ToolConstants.WEB_SEARCH)
            assertThat(state.webSearchKeysRow?.opensOnTurnOn).isTrue()
        }
    }

    @Test
    fun `saved keys are checked again and LibreChat search is turned on`() {
        startupConfig.value = StartupConfig()
        fixture.webSearchAuthenticated = false
        coEvery { fixture.agentToolsRepository.installToolKey(ToolConstants.WEB_SEARCH, any()) } answers {
            fixture.webSearchAuthenticated = true
            Result.Success(Unit)
        }
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)

            val keys = mapOf("selectedProvider" to "serper", "serperApiKey" to "k")
            val saved = vm.saveWebSearchKeys(keys)
            runCurrent()

            assertThat(saved).isTrue()
            assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()
            assertThat(vm.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.LIBRECHAT)
            coVerify { fixture.agentToolsRepository.installToolKey(ToolConstants.WEB_SEARCH, keys) }
        }
    }

    @Test
    fun `a save the server refuses leaves web search off and reports it`() {
        startupConfig.value = StartupConfig()
        fixture.webSearchAuthenticated = false
        coEvery { fixture.agentToolsRepository.installToolKey(ToolConstants.WEB_SEARCH, any()) } returns
            Result.Error(message = "500")
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)

            val saved = vm.saveWebSearchKeys(mapOf("serperApiKey" to "k"))

            assertThat(saved).isFalse()
            assertThat(vm.uiState.value.modelParameters.webSearch).isNotEqualTo(true)
        }
    }

    @Test
    fun `removed keys are checked again`() = webSearchTest { vm ->
        assertThat(vm.uiState.value.gates.librechatSearchReady).isTrue()
        coEvery { fixture.agentToolsRepository.removeToolKey(ToolConstants.WEB_SEARCH, any()) } answers {
            fixture.webSearchAuthenticated = false
            Result.Success(Unit)
        }

        val removed = vm.removeWebSearchKeys()
        runCurrent()

        assertThat(removed).isTrue()
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
    fun `a second chat opened while the first one's check is out sends no search until the answer`() {
        coEvery { fixture.agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH) } coAnswers {
            delay(5_000)
            Result.Success(ToolAuthResult(authenticated = true))
        }
        webSearchTest { first ->
            val second = fixture.build(defaultDispatcher = testDispatcher)
            try {
                runCurrent()
                sendWithSearch(second)

                assertThat(second.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.CHECKING)
                assertThat(topLevel.single()).isFalse()
                assertThat(ephemeral.single()?.webSearch).isNull()

                advanceTimeBy(5_001)

                assertThat(second.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.LIBRECHAT)
                assertThat(first.uiState.value.gates.librechatSearchReady).isTrue()
            } finally {
                second.viewModelScope.cancel()
            }
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
    fun `a reopened chat whose stored built-in search is on keeps it, so a send doesn't erase it`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns Result.Success(
            Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL, webSearch = true),
        )
        webSearchTest(CONVERSATION) { vm ->
            advanceUntilIdle()
            assertThat(vm.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.BUILT_IN)

            vm.onInputChanged("hello")
            vm.sendMessage()
            runCurrent()

            assertThat(topLevel.single()).isTrue()
            assertThat(ephemeral.single()?.webSearch).isNull()
        }
    }

    @Test
    fun `a chat whose built-in search was turned on in web keeps it over the switch stored here`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns Result.Success(
            Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL, webSearch = true),
        )
        coEvery { fixture.conversationWebSearchStore.get(CONVERSATION) } returns false
        webSearchTest(CONVERSATION) { vm ->
            advanceUntilIdle()
            assertThat(vm.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.BUILT_IN)

            send(vm)

            assertThat(sentBody()[ToolConstants.WEB_SEARCH]).isEqualTo(JsonPrimitive(true))
            assertThat(ephemeral.single()?.webSearch).isNull()
        }
    }

    @Test
    fun `a send with the control off says web_search false, so no admin default turns a search on`() =
        webSearchTest { vm ->
            vm.onModelSelected(ENDPOINT, MODEL)

            send(vm)

            assertThat(sentBody()[ToolConstants.WEB_SEARCH]).isEqualTo(JsonPrimitive(false))
            assertThat(ephemeral.single()?.webSearch).isNull()
        }

    @Test
    fun `LibreChat search goes with web_search false beside it`() = webSearchTest { vm ->
        sendWithSearch(vm)

        assertThat(ephemeral.single()?.webSearch).isTrue()
        assertThat(sentBody()[ToolConstants.WEB_SEARCH]).isEqualTo(JsonPrimitive(false))
    }

    @Test
    fun `an empty agents capabilities list runs the built-in search, not LibreChat's`() {
        every { fixture.configRepository.endpointConfigs } returns MutableStateFlow(
            mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig(capabilities = emptyList())),
        )
        webSearchTest { vm ->
            sendWithSearch(vm)

            assertThat(ephemeral.single()?.webSearch).isNull()
            assertThat(sentBody()[ToolConstants.WEB_SEARCH]).isEqualTo(JsonPrimitive(true))
        }
    }

    @Test
    fun `a spec's webSearch without the capability shows no control and sends no search`() {
        val spec = ModelSpec(name = "searching", preset = Preset(endpoint = ENDPOINT, model = MODEL), webSearch = true)
        serveSpecs(ModelSpecs(list = listOf(spec)))
        every { fixture.configRepository.endpointConfigs } returns MutableStateFlow(
            mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig(capabilities = emptyList())),
        )
        webSearchTest { vm ->
            vm.onSpecSelected(spec)
            runCurrent()
            assertThat(vm.uiState.value.webSearchAvailable).isFalse()

            send(vm)

            assertThat(ephemeral.single()?.webSearch).isNull()
            assertThat(topLevel.single()).isFalse()
        }
    }

    @Test
    fun `under enforce a spec preset's web_search runs the built-in search alone`() {
        val spec = ModelSpec(name = "grounded", preset = Preset(endpoint = ENDPOINT, model = MODEL, webSearch = true))
        serveSpecs(ModelSpecs(enforce = true, list = listOf(spec)))
        webSearchTest { vm ->
            vm.onSpecSelected(spec)
            runCurrent()
            assertThat(vm.uiState.value.webSearchRoute).isEqualTo(WebSearchRoute.BUILT_IN)

            send(vm)

            assertThat(sentBody()[ToolConstants.WEB_SEARCH]).isEqualTo(JsonPrimitive(true))
            assertThat(ephemeral.single()?.webSearch).isNull()
        }
    }

    @Test
    fun `turning a kept built-in search off and on again follows the preference`() {
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION, any()) } returns Result.Success(
            Conversation(conversationId = CONVERSATION, endpoint = ENDPOINT, model = MODEL, webSearch = true),
        )
        webSearchTest(CONVERSATION) { vm ->
            advanceUntilIdle()

            vm.toggleTool(ToolConstants.WEB_SEARCH)
            vm.toggleTool(ToolConstants.WEB_SEARCH)

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
        any(), captureNullable(params), any(), any(), any(),
    )

    /** The body the only send so far puts on the wire, encoded as the repository encodes it. */
    private fun sentBody(): JsonObject {
        val request = ChatPayloadBuilder.build(
            text = "hello",
            conversationId = null,
            endpoint = ENDPOINT,
            model = MODEL,
            webSearch = topLevel.single(),
            ephemeralAgent = ephemeral.single(),
        )
        return ChatPayloadBuilder.toBody(librechatJson, request, params.single())
    }

    private fun TestScope.send(vm: ChatViewModel) {
        vm.onInputChanged("hello")
        vm.sendMessage()
        runCurrent()
    }

    private fun TestScope.sendWithSearch(vm: ChatViewModel) {
        vm.onModelSelected(ENDPOINT, MODEL)
        vm.toggleTool(ToolConstants.WEB_SEARCH)
        vm.onInputChanged("hello")
        vm.sendMessage()
        runCurrent()
    }

    /** Serves [specs]; the seeder that applies them combines last-used, which must emit. */
    private fun serveSpecs(specs: ModelSpecs) {
        startupConfig.value = StartupConfig(webSearch = SEARCH_CONFIG, modelSpecs = specs)
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
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
        const val CUSTOM = "Mistral"
        const val CUSTOM_MODEL = "mistral-large"
        val USER_KEYS = listOf(
            listOf("providers", "user_provided"),
            listOf("scrapers", "user_provided"),
            listOf("rerankers", "system_defined"),
        )
    }
}
