package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecSkills
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The chat half of skills that rides the send path: the per-conversation Skills toggle
 * (`ephemeralAgent.skills`), the pending skill chips (`manualSkills`), and their replay on
 * regenerate and edit. Driven through the real ViewModel so what is asserted is what
 * `ChatRepository.startChat` is actually handed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSkillsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    private val endpointConfigs = MutableStateFlow(
        mapOf(
            ENDPOINT to EndpointConfig(),
            EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS)),
        ),
    )
    private val messages = MutableStateFlow<List<Message>>(emptyList())

    /** Each turn's stream, so a test can end it. */
    private val streams = mutableListOf<MutableSharedFlow<StreamEvent>>()

    /** Every `startChat` the ViewModel fires, by the arguments this feature owns. */
    private val sent = mutableListOf<Sent>()

    private data class Sent(
        val ephemeralAgent: EphemeralAgent?,
        val manualSkills: List<String>?,
        val isRegenerate: Boolean,
        val isEdited: Boolean,
        val isContinued: Boolean,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns endpointConfigs
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
            every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
            every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
            every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
            every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
            every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
            every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every { messageRepository.observeMessages(any()) } returns messages
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.checkStreamStatus(any(), any()) } returns ChatStatusResponse(active = false)
            every {
                chatRepository.startChat(
                    text = any(), conversationId = any(), endpoint = any(), endpointType = any(), key = any(),
                    modelDisplayLabel = any(), model = any(), userMessageId = any(), parentMessageId = any(),
                    agentId = any(), overrideParentMessageId = any(), responseMessageId = any(),
                    isEdited = any(), isRegenerate = any(), compact = any(), isContinued = any(),
                    webSearch = any(), files = any(), addedConvo = any(), ephemeralAgent = any(),
                    isTemporary = any(), modelParams = any(), quotes = any(), spec = any(),
                    chatProjectId = any(), manualSkills = any(),
                )
            } answers {
                sent += Sent(
                    ephemeralAgent = arg(19),
                    manualSkills = arg(25),
                    isRegenerate = arg(13),
                    isEdited = arg(12),
                    isContinued = arg(15),
                )
                MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8).also { streams += it }
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── the toggle ──────────────────────────────────────────────────

    @Test
    fun `the toggle is offered and sends the flag on a new chat, stored in the new-chat slot`() = skillsTest { vm ->
        assertThat(vm.uiState.value.isSkillsToolAvailable).isTrue()
        assertThat(vm.uiState.value.skillsToggle).isFalse()

        vm.toggleTool(ToolConstants.SKILLS)
        runCurrent()

        assertThat(fixture.skillsToggles.value).containsExactly(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT, true)
        assertThat(vm.uiState.value.effectiveEnabledTools).contains(ToolConstants.SKILLS)
        // Per conversation: the global enabled-tools store is never written.
        coVerify(exactly = 0) { fixture.settingsDataStore.setEnabledTools(any()) }

        send(vm)
        assertThat(sent.single().ephemeralAgent?.skills).isTrue()
        assertThat(sent.single().manualSkills).isNull()
    }

    @Test
    fun `a fresh new chat starts from the last new-chat setting`() {
        fixture.skillsToggles.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to true)
        skillsTest { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
        }
    }

    @Test
    fun `an existing conversation reads its own value, not the new-chat slot`() {
        fixture.skillsToggles.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to true, "conv-1" to false)
        skillsTest(conversationId = "conv-1") { vm ->
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(fixture.skillsToggles.value["conv-1"]).isTrue()
            assertThat(fixture.skillsToggles.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isTrue()
        }
    }

    @Test
    fun `the handoff seeds a just-created chat before its carried value is stored`() {
        fixture.selectionHandoff.put("conv-new", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-new") { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
        }
    }

    @Test
    fun `a handed-off chat turned off stays off when the user then picks a spec`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.selectionHandoff.put("conv-new", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-new") { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(fixture.skillsToggles.value["conv-new"]).isFalse()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            vm.onSpecSelected(curated)
            runCurrent()
            assertThat(vm.uiState.value.conversationId).isEqualTo("conv-new")
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isFalse()
        }
    }

    @Test
    fun `a handed-off chat picking a spec before the carried write lands starts off`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.selectionHandoff.put("conv-new", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-new") { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            assertThat(fixture.skillsToggles.value).isEmpty()

            vm.onSpecSelected(curated)
            runCurrent()
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isFalse()
        }
    }

    @Test
    fun `a handed-off chat toggled under a spec keeps that value on leaving the spec`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.selectionHandoff.put("conv-new", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-new") { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()

            vm.onSpecSelected(curated)
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            // Web writes the conversation's slot under a spec too, so leaving it reads what was
            // set there rather than the handoff's seed.
            vm.onModelSelected(ENDPOINT, MODEL)
            runCurrent()
            assertThat(fixture.skillsToggles.value["conv-new"]).isFalse()
            assertThat(vm.uiState.value.selectedSpec).isNull()
            assertThat(vm.uiState.value.skillsToggle).isFalse()
        }
    }

    @Test
    fun `a handed-off temporary chat turned off stays off when the user then picks a spec`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.selectionHandoff.put("conv-temp", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-temp", temporary = true) { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            vm.onSpecSelected(curated)
            runCurrent()
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            assertThat(fixture.skillsToggles.value).isEmpty()
        }
    }

    @Test
    fun `a new chat's first send carries Skills on to the created conversation and its handoff`() = skillsTest { vm ->
        vm.toggleTool(ToolConstants.SKILLS)
        runCurrent()
        send(vm)
        assertThat(sent.single().ephemeralAgent?.skills).isTrue()

        streams.single().emit(created("conv-new"))
        runCurrent()

        assertThat(fixture.skillsToggles.value["conv-new"]).isTrue()
        assertThat(fixture.selectionHandoff.take("conv-new")?.skillsToggle).isTrue()
        // The next new chat still starts from the shared slot.
        assertThat(fixture.skillsToggles.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isTrue()
    }

    @Test
    fun `a pick on a new chat turns Skills on for that chat alone`() = skillsTest { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.removePendingManualSkill("pdf-tools")
        runCurrent()
        assertThat(vm.uiState.value.skillsToggle).isTrue()
        assertThat(fixture.skillsToggles.value).isEmpty()

        send(vm)
        assertThat(sent.single().ephemeralAgent?.skills).isTrue()
        streams.single().emit(created("conv-new"))
        runCurrent()

        // The created chat keeps it; the shared new-chat slot never learned of it.
        assertThat(fixture.skillsToggles.value).containsExactly("conv-new", true)
        vm.onPendingNavigationHandled()
        runCurrent()
        assertThat(vm.uiState.value.skillsToggle).isFalse()
    }

    @Test
    fun `a pick turns Skills on in a new chat whose slot was left off`() {
        fixture.skillsToggles.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to false)
        skillsTest { vm ->
            vm.addPendingManualSkill("pdf-tools")
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isTrue()

            send(vm)
            assertThat(sent.single().ephemeralAgent?.skills).isTrue()
            assertThat(fixture.skillsToggles.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isFalse()
        }
    }

    @Test
    fun `a new chat under a spec the config sends with skills carries nothing`() {
        val all = spec("all-skills", JsonPrimitive(true))
        serveSpecs(all)
        skillsTest { vm ->
            vm.onSpecSelected(all)
            runCurrent()
            assertThat(vm.uiState.value.activeSpecSkills).isEqualTo(ModelSpecSkills.All)

            sendKeepingSelection(vm)
            streams.single().emit(created("conv-new"))
            runCurrent()

            assertThat(fixture.skillsToggles.value).doesNotContainKey("conv-new")
            val handoff = fixture.selectionHandoff.take("conv-new")
            assertThat(handoff).isNotNull()
            assertThat(handoff?.skillsToggle).isNull()
        }
    }

    @Test
    fun `a new chat under a spec without skills starts off and stores nothing`() {
        // v0.8.8's shape: the spec carries no skills key, so it does not govern the toggle.
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.skillsToggles.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to true)
        skillsTest { vm ->
            vm.onSpecSelected(curated)
            runCurrent()
            assertThat(vm.uiState.value.activeSpecSkills).isNull()
            assertThat(vm.uiState.value.isSkillsToolAvailable).isTrue()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            sendKeepingSelection(vm)
            assertThat(sent.single().ephemeralAgent?.skills).isTrue()
            streams.single().emit(created("conv-new"))
            runCurrent()

            // Nothing written: not the created id, and the new-chat slot keeps what it held.
            assertThat(fixture.skillsToggles.value)
                .containsExactly(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT, true)
            // The session's value still reaches the Chat(id) ViewModel through the handoff.
            assertThat(fixture.selectionHandoff.take("conv-new")?.skillsToggle).isTrue()
        }
    }

    @Test
    fun `the handoff seeds a just-created spec chat ahead of the toggle's start`() = runTest(testDispatcher) {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        fixture.selectionHandoff.put("conv-new", ModelChoice.Spec("curated", ENDPOINT, MODEL), skillsToggle = true)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-new")
        runCurrent()
        try {
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            assertThat(fixture.skillsToggles.value).isEmpty()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a temporary chat keeps the toggle in memory only`() {
        fixture.selectionHandoff.put("conv-temp", ModelChoice.Raw(ENDPOINT, MODEL), skillsToggle = true)
        skillsTest(conversationId = "conv-temp", temporary = true) { vm ->
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            assertThat(fixture.skillsToggles.value).isEmpty()
        }
    }

    @Test
    fun `without the agents skills capability nothing is offered or sent`() = skillsTest { vm ->
        endpointConfigs.value = mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig())
        runCurrent()
        assertThat(vm.uiState.value.isSkillsToolAvailable).isFalse()
        vm.toggleTool(ToolConstants.SKILLS)
        vm.addPendingManualSkill("pdf-tools")
        runCurrent()
        assertThat(fixture.skillsToggles.value).isEmpty()
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
    }

    @Test
    fun `a role without SKILLS USE hides the toggle once the role loads`() {
        every { fixture.roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(
                name = "USER",
                permissions = mapOf(PermissionType.SKILLS.serverKey to mapOf(Permission.USE.serverKey to false)),
            ),
        )
        skillsTest { vm ->
            assertThat(vm.uiState.value.isSkillsToolAvailable).isFalse()
        }
    }

    @Test
    fun `the landing keeps a role's Skills denial after its chat is created`() {
        every { fixture.roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(
                name = "USER",
                permissions = mapOf(PermissionType.SKILLS.serverKey to mapOf(Permission.USE.serverKey to false)),
            ),
        )
        skillsTest { vm ->
            send(vm)
            streams.single().emit(created("conv-new"))
            runCurrent()
            vm.onPendingNavigationHandled()
            runCurrent()

            // Back on the landing: the role still denies skills, so nothing offers them.
            assertThat(vm.uiState.value.gates.skillsEnabled).isFalse()
            assertThat(vm.uiState.value.isSkillsToolAvailable).isFalse()
        }
    }

    // ─── pending skill chips ─────────────────────────────────────────

    @Test
    fun `a fresh send takes the skill chips and turns the conversation's Skills on`() = skillsTest { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.addPendingManualSkill("pdf-tools")
        vm.addPendingManualSkill("brand-voice")
        runCurrent()
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools", "brand-voice").inOrder()
        // Picking a skill turns Skills on, as web's picker does.
        assertThat(vm.uiState.value.skillsToggle).isTrue()

        send(vm)

        assertThat(sent.single().manualSkills).containsExactly("pdf-tools", "brand-voice").inOrder()
        assertThat(sent.single().ephemeralAgent?.skills).isTrue()
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
        // The optimistic user bubble carries the picks for its pills.
        assertThat(vm.uiState.value.messages.last { it.isCreatedByUser }.manualSkills)
            .containsExactly("pdf-tools", "brand-voice")
    }

    @Test
    fun `a failed send puts its picks back beside the chips it held back`() {
        val only = spec("brand", JsonArray(listOf(JsonPrimitive("brand-voice"))))
        serveSpecs(only)
        skillsTest { vm ->
            streamEvery(StreamEvent.Error("Request failed (HTTP 302)"))
            vm.addPendingManualSkill("pdf-tools")
            vm.addPendingManualSkill("brand-voice")
            vm.onSpecSelected(only)
            runCurrent()

            sendKeepingSelection(vm)

            // Only brand-voice went; pdf-tools was held back. Both are staged for the retry.
            assertThat(vm.uiState.value.inputText).isEqualTo("hello")
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("brand-voice", "pdf-tools")
        }
    }

    @Test
    fun `chips stop at the server's cap`() = skillsTest { vm ->
        repeat(MAX_MANUAL_SKILLS + 2) { vm.addPendingManualSkill("skill-$it") }
        assertThat(vm.uiState.value.pendingManualSkills).hasSize(MAX_MANUAL_SKILLS)
    }

    @Test
    fun `a later turn sends no skills again`() = skillsTest(conversationId = "conv-1") { vm ->
        vm.addPendingManualSkill("pdf-tools")
        send(vm)
        assertThat(sent.single().manualSkills).containsExactly("pdf-tools")
        streams.single().emit(
            StreamEvent.Final(
                requestMessage = null,
                responseMessage = Message(messageId = "a1", conversationId = "conv-1", text = "done"),
                conversation = null,
            ),
        )
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()

        // The server re-primes earlier picks from history; the next turn names none.
        send(vm, "and now?")
        assertThat(sent).hasSize(2)
        assertThat(sent.last().manualSkills).isNull()
        // The toggle the pick turned on stays on.
        assertThat(sent.last().ephemeralAgent?.skills).isTrue()
    }

    @Test
    fun `assistants endpoints never take the chips`() = skillsTest { vm ->
        vm.addPendingManualSkill("pdf-tools")
        val taken = vm.uiState.value.pendingManualSkills
        assertThat(taken).containsExactly("pdf-tools")
        endpointConfigs.value = endpointConfigs.value + ("assistants" to EndpointConfig())
        every { fixture.configRepository.availableModels } returns
            MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL), "assistants" to listOf("asst_1")))
        vm.onModelSelected("assistants", "asst_1")
        runCurrent()
        assertThat(vm.uiState.value.isSkillsToolAvailable).isFalse()
        assertThat(vm.uiState.value.canPickSkills).isFalse()
    }

    @Test
    fun `chips staged before a spec the config sends with skills off stay staged and are not sent`() {
        val off = spec("locked", JsonPrimitive(false))
        serveSpecs(off)
        skillsTest { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.onSpecSelected(off)
            runCurrent()
            assertThat(vm.uiState.value.activeSpecSkills).isEqualTo(ModelSpecSkills.Off)

            sendKeepingSelection(vm)

            assertThat(sent.single().manualSkills).isNull()
            assertThat(sent.single().ephemeralAgent?.skills).isNotEqualTo(true)
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.messages.last { it.isCreatedByUser }.manualSkills).isNull()
            // Held back, not silently: the notice says the chip did not go.
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())
        }
    }

    @Test
    fun `a spec allowlist the config sends, picked after staging, sends only its names`() {
        val only = spec("brand", JsonArray(listOf(JsonPrimitive("brand-voice"))))
        serveSpecs(only)
        skillsTest { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.addPendingManualSkill("brand-voice")
            vm.onSpecSelected(only)
            runCurrent()

            sendKeepingSelection(vm)

            assertThat(sent.single().manualSkills).containsExactly("brand-voice")
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())
        }
    }

    @Test
    fun `chips a new chat's first send held back stay staged on the created chat, with the notice`() {
        val only = spec("brand", JsonArray(listOf(JsonPrimitive("brand-voice"))))
        serveSpecs(only)
        skillsTest { landing ->
            landing.addPendingManualSkill("pdf-tools")
            landing.addPendingManualSkill("brand-voice")
            landing.onSpecSelected(only)
            runCurrent()
            sendKeepingSelection(landing)
            assertThat(landing.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())

            streams.single().emit(created("conv-new"))
            runCurrent()
            assertThat(landing.uiState.value.pendingNavigationConversationId).isEqualTo("conv-new")
            landing.onPendingNavigationHandled()
            runCurrent()

            // The new chat's own config loads succeed, so nothing else writes its banner.
            coEvery { fixture.configRepository.fetchEndpoints() } returns Result.Success(endpointConfigs.value)
            coEvery { fixture.configRepository.fetchModels() } returns Result.Success(mapOf(ENDPOINT to listOf(MODEL)))
            val chat = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-new")
            runCurrent()
            try {
                assertThat(chat.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
                assertThat(chat.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())
            } finally {
                chat.viewModelScope.cancel()
            }
        }
    }

    // ─── replay ──────────────────────────────────────────────────────

    @Test
    fun `regenerate replays the user turn's picks`() =
        skillsTest(conversationId = "conv-1") { vm ->
            messages.value = listOf(
                Message(
                    messageId = "u1",
                    conversationId = "conv-1",
                    text = "summarize",
                    isCreatedByUser = true,
                    manualSkills = listOf("pdf-tools"),
                ),
                Message(messageId = "a1", conversationId = "conv-1", parentMessageId = "u1", text = "done"),
            )
            runCurrent()

            vm.regenerateMessage("a1")
            runCurrent()
            assertThat(sent.single { it.isRegenerate && !it.isContinued }.manualSkills).containsExactly("pdf-tools")
        }

    @Test
    fun `editing the user turn keeps its picks on the new turn and its bubble`() =
        skillsTest(conversationId = "conv-1") { vm ->
            messages.value = listOf(
                Message(
                    messageId = "u1",
                    conversationId = "conv-1",
                    text = "summarize",
                    isCreatedByUser = true,
                    manualSkills = listOf("pdf-tools"),
                ),
                Message(messageId = "a1", conversationId = "conv-1", parentMessageId = "u1", text = "done"),
            )
            runCurrent()

            vm.startEditing("u1")
            vm.onEditTextChanged("summarize briefly")
            vm.rerunEdit()
            runCurrent()

            assertThat(sent.single { it.isEdited }.manualSkills).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.messages.single { it.text == "summarize briefly" }.manualSkills)
                .containsExactly("pdf-tools")
        }

    @Test
    fun `continue sends no skills`() = skillsTest(conversationId = "conv-1") { vm ->
        messages.value = listOf(
            Message(
                messageId = "u1",
                conversationId = "conv-1",
                text = "summarize",
                isCreatedByUser = true,
                manualSkills = listOf("pdf-tools"),
            ),
            Message(messageId = "a1", conversationId = "conv-1", parentMessageId = "u1", text = "done"),
        )
        runCurrent()

        vm.continueGeneration()
        runCurrent()
        assertThat(sent.single { it.isContinued }.manualSkills).isNull()
    }

    // ─── helpers ─────────────────────────────────────────────────────

    /** Every turn from here on streams [events] and ends. */
    private fun streamEvery(vararg events: StreamEvent) {
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
            )
        } returns flowOf(*events)
    }

    private fun TestScope.send(vm: ChatViewModel, text: String = "hello") {
        vm.onModelSelected(ENDPOINT, MODEL)
        vm.onInputChanged(text)
        vm.sendMessage()
        runCurrent()
    }

    private fun created(conversationId: String) = StreamEvent.Created(
        conversationId = conversationId,
        messageId = "u-created",
        parentMessageId = "00000000-0000-0000-0000-000000000000",
    )

    /** A send that leaves the current selection (a spec, say) as it is. */
    private fun TestScope.sendKeepingSelection(vm: ChatViewModel, text: String = "hello") {
        vm.onInputChanged(text)
        vm.sendMessage()
        runCurrent()
    }

    private fun spec(name: String, skills: kotlinx.serialization.json.JsonElement) =
        ModelSpec(name = name, preset = Preset(endpoint = ENDPOINT, model = MODEL), skills = skills)

    /**
     * Serves [specs]; the selection seeder, which publishes them to the chat, needs its inputs.
     * A spec built with `skills` stands for a future server: v0.8.8's config route strips the key
     * (`sanitizeModelSpecs`), so these cases cover forward-compatible handling, not today's wire.
     */
    private fun serveSpecs(vararg specs: ModelSpec) {
        every { fixture.configRepository.startupConfig } returns
            MutableStateFlow(StartupConfig(modelSpecs = ModelSpecs(list = specs.toList())))
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
    }

    private fun skillsTest(
        conversationId: String? = null,
        temporary: Boolean = false,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        val vm = fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = conversationId,
            initialIsTemporary = temporary,
        )
        runCurrent()
        vm.onModelSelected(ENDPOINT, MODEL)
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
    }
}
