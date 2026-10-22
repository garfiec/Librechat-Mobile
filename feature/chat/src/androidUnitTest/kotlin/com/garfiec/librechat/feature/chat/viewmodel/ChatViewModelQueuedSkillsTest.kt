package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.SteerRequest
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
 * Skill chips on the local queue, in a non-agent chat (where skills are mostly used, and where a
 * queue goes through the client's own drain rather than the server's enqueue claim): the drain
 * sends the queued picks with the ephemeral flag, a queued edit keeps them, and a steer leaves
 * them staged. Driven through the real ViewModel over a live run, on the
 * [ChatViewModelDuringRunSendTest] harness shape; see its KDoc for why every case cancels the
 * ViewModel scope inside the body and drives with [runCurrent].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelQueuedSkillsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val chatRepository get() = fixture.chatRepository
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    /** The resumed run's SSE events; a test ends the run by emitting its Final. */
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    private data class SkillsSent(val manualSkills: List<String>?, val ephemeralAgent: EphemeralAgent?)

    /** The skill arguments of every `startChat` the ViewModel fires (a drain sends through it). */
    private val sent = mutableListOf<SkillsSent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            // Steering open: 0.8.8-rc1 clears the version gate outright.
            every { configRepository.detectedBackend } returns
                MutableStateFlow(DetectedBackend("0.8.8-rc1", BackendBuildClass.RC))
            every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc1")
            // The agents endpoint advertises skills, so chips can be staged in this anthropic chat.
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(
                    ENDPOINT to EndpointConfig(),
                    EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS)),
                ),
            )
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
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.resumeChat(any()) } returns Result.Success(ChatResumeResponse(status = "resuming"))
            // The conversation opens onto a live run, held open until a test emits its Final.
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { chatRepository.resumeStream(any()) } returns resumedStream
            every {
                chatRepository.startChat(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(),
                )
            } answers {
                sent += SkillsSent(manualSkills = arg(25), ephemeralAgent = arg(19))
                MutableSharedFlow()
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a queued message carries its skill chips through the local drain`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.onInputChanged(TEXT)
            vm.sendDuringRun()
            runCurrent()

            // A non-agent chat queues locally: the chips go with this item, not the next send.
            val queued = vm.uiState.value.messageQueue.single()
            assertThat(queued.server).isNull()
            assertThat(queued.manualSkills).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.pendingManualSkills).isEmpty()

            resumedStream.emit(cleanFinal())
            runCurrent()

            assertThat(vm.uiState.value.messageQueue).isEmpty()
            assertThat(sent.last().manualSkills).containsExactly("pdf-tools")
            // Not an agent chat: the run primes the picks only with the ephemeral flag on.
            assertThat(sent.last().ephemeralAgent?.skills).isTrue()
        }

    @Test
    fun `editing a queued message keeps its skill picks`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()

        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("$TEXT Briefly.")
        vm.commitQueuedEdit()
        runCurrent()

        val edited = vm.uiState.value.messageQueue.single()
        assertThat(edited.text).isEqualTo("$TEXT Briefly.")
        assertThat(edited.manualSkills).containsExactly("pdf-tools")
    }

    @Test
    fun `editing a message queued with Skills on keeps it on after the chat turns Skills off`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isTrue()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            // The composer shows the item as it was queued.
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.onInputChanged("$TEXT Briefly.")
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isTrue()
            // The chat's own toggle comes back with the draft.
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            resumedStream.emit(cleanFinal())
            runCurrent()
            assertThat(sent.last().ephemeralAgent?.skills).isTrue()
        }

    @Test
    fun `editing a message queued on a spec with Skills on keeps it on after a raw model pick`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.onSpecSelected(curated)
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isTrue()
            // A raw model reads the conversation's own slot, written under the spec as on web;
            // the user turns it off there.
            vm.onModelSelected(ENDPOINT, MODEL)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            // Swapping the item's spec in is not a spec pick: its own value stays on screen.
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isTrue()
            assertThat(vm.uiState.value.selectedSpec).isNull()
            assertThat(vm.uiState.value.skillsToggle).isFalse()
        }
    }

    @Test
    fun `finishing a queued edit gives back Skills turned on under the draft's spec`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            // Queued on a raw model with Skills off.
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            vm.onSpecSelected(curated)
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isTrue()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            vm.commitQueuedEdit()
            runCurrent()

            // The draft's spec comes back with the value the user set under it.
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            assertThat(vm.uiState.value.skillsToggle).isTrue()
        }
    }

    @Test
    fun `a skill picked during a queued edit turns Skills on for the item only`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            vm.addPendingManualSkill("pdf-tools")
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isTrue()
            // The conversation's setting is untouched, on screen and in the store.
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            assertThat(fixture.skillsToggles.value[CONVERSATION_ID]).isNotEqualTo(true)
            resumedStream.emit(cleanFinal())
            runCurrent()
            assertThat(sent.last().ephemeralAgent?.skills).isTrue()
        }

    @Test
    fun `an unrelated store write during a queued edit keeps the item's Skills value`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            // Another conversation's slot changes; this one's stored value is read again unchanged.
            fixture.skillsToggles.value += "conv-other" to true
            runCurrent()

            assertThat(vm.uiState.value.skillsToggle).isTrue()
        }

    @Test
    fun `toggling Skills during a queued edit leaves the conversation's setting alone`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            vm.toggleTool(ToolConstants.SKILLS)
            runCurrent()
            assertThat(vm.uiState.value.skillsToggle).isFalse()
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.skills).isNull()
            assertThat(vm.uiState.value.skillsToggle).isTrue()
            assertThat(fixture.skillsToggles.value[CONVERSATION_ID]).isTrue()
        }

    @Test
    fun `a queued edit shows the item's own chips and sends what was picked during it`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            val queued = vm.uiState.value.messageQueue.single()
            // A chip staged for the next new message, before the edit starts.
            vm.addPendingManualSkill("draft-skill")

            vm.editQueued(queued.localId)
            runCurrent()
            // The composer holds the queued message, so its chips are that message's picks.
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")

            vm.addPendingManualSkill("translator")
            vm.removePendingManualSkill("pdf-tools")
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("translator")
            // The draft gets its own chip back, and nothing picked for the edit leaks onto it.
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("draft-skill")

            resumedStream.emit(cleanFinal())
            runCurrent()
            assertThat(sent.last().manualSkills).containsExactly("translator")
        }

    @Test
    fun `saving a queued edit under a spec that refuses a chip removes it and says so`() {
        val brand = ModelSpec(
            name = "brand",
            preset = Preset(endpoint = ENDPOINT, model = MODEL),
            skills = JsonArray(listOf(JsonPrimitive("translator"))),
        )
        serveSpecs(brand)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.addPendingManualSkill("translator")
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            val queued = vm.uiState.value.messageQueue.single()

            vm.editQueued(queued.localId)
            runCurrent()
            vm.onSpecSelected(brand)
            runCurrent()
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("translator")
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SKILLS_REMOVED_FROM_QUEUED.marker())
        }
    }

    @Test
    fun `a queued edit that keeps every chip shows no notice`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        vm.editQueued(vm.uiState.value.messageQueue.single().localId)
        runCurrent()
        vm.commitQueuedEdit()
        runCurrent()

        assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.error).isNotEqualTo(ChatNotice.SKILLS_REMOVED_FROM_QUEUED.marker())
    }

    @Test
    fun `queueing under a spec that refuses the chips keeps them staged and says so`() {
        val locked = ModelSpec(
            name = "locked",
            preset = Preset(endpoint = ENDPOINT, model = MODEL),
            skills = JsonPrimitive(false),
        )
        serveSpecs(locked)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.onSpecSelected(locked)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().manualSkills).isEmpty()
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())
        }
    }

    @Test
    fun `cancelling a queued edit drops the picks made during it`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.addPendingManualSkill("draft-skill")

        vm.editQueued(queued.localId)
        runCurrent()
        vm.addPendingManualSkill("translator")
        vm.cancelQueuedEdit()
        runCurrent()

        assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("draft-skill")
    }

    @Test
    fun `a steer leaves the skill chips staged for the next message`() = queuedSkillsTest(DuringRunAction.STEER) { vm ->
        val request = slot<SteerRequest>()
        coEvery { chatRepository.steerChat(capture(request)) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "s1"))
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.sendDuringRun()
        runCurrent()

        // The steer route takes words (and quotes), never skills: the picks wait for a send.
        assertThat(request.captured.text).isEqualTo(TEXT)
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.messageQueue).isEmpty()
    }

    private fun cleanFinal() = StreamEvent.Final(
        requestMessage = null,
        responseMessage = Message(messageId = "assistant-1", conversationId = CONVERSATION_ID, text = "done"),
        conversation = null,
    )

    /** Serves [specs]; the selection seeder, which publishes them to the chat, needs its inputs. */
    private fun serveSpecs(vararg specs: ModelSpec) {
        every { fixture.configRepository.startupConfig } returns
            MutableStateFlow(StartupConfig(modelSpecs = ModelSpecs(list = specs.toList())))
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
    }

    private fun queuedSkillsTest(
        preference: DuringRunAction,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        every { fixture.settingsDataStore.duringRunAction } returns flowOf(preference)
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        try {
            assertThat(vm.uiState.value.isStreaming).isTrue()
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val TEXT = "Answer only in French."
    }
}
