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
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    fun `editing a message queued on a spec keeps the tools added there`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        serveSpecs(curated)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.onSpecSelected(curated)
            runCurrent()
            vm.toggleTool(ToolConstants.FILE_SEARCH)
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            vm.toggleTool(ToolConstants.FILE_SEARCH)
            assertThat(vm.uiState.value.effectiveEnabledTools).doesNotContain(ToolConstants.FILE_SEARCH)

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            assertThat(vm.uiState.value.effectiveEnabledTools).contains(ToolConstants.FILE_SEARCH)
            vm.onInputChanged("$TEXT Briefly.")
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().ephemeralAgent?.fileSearch).isTrue()
            assertThat(vm.uiState.value.effectiveEnabledTools).doesNotContain(ToolConstants.FILE_SEARCH)
        }
    }

    @Test
    fun `a queued edit holds the item's spec as the server would after the config changed`() {
        val curated = ModelSpec(name = "curated", preset = Preset(endpoint = ENDPOINT, model = MODEL))
        val config = MutableStateFlow<StartupConfig?>(StartupConfig(modelSpecs = ModelSpecs(list = listOf(curated))))
        every { fixture.configRepository.startupConfig } returns config
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.onSpecSelected(curated)
            runCurrent()
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            val queued = vm.uiState.value.messageQueue.single().localId

            // Hidden from the served list: the server still applies it, so the edit keeps it.
            config.value = StartupConfig(modelSpecs = ModelSpecs(list = emptyList()))
            runCurrent()
            vm.editQueued(queued)
            runCurrent()
            assertThat(vm.uiState.value.selectedSpec).isEqualTo("curated")
            vm.cancelQueuedEdit()
            runCurrent()

            // Moved to another endpoint: the server would refuse the name with this model.
            config.value = StartupConfig(
                modelSpecs = ModelSpecs(list = listOf(curated.copy(preset = Preset(endpoint = "elsewhere", model = MODEL)))),
            )
            runCurrent()
            vm.editQueued(queued)
            runCurrent()
            assertThat(vm.uiState.value.selection.choice).isEqualTo(ModelChoice.Raw(ENDPOINT, MODEL))
        }
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
            vm.removeDraftItem(DraftItem.Skill("pdf-tools"))
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

    @Test
    fun `a queued edit shows the item's own quotes and saves what is there`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingQuote("queued excerpt")
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            val queued = vm.uiState.value.messageQueue.single()
            assertThat(queued.quotes).containsExactly("queued excerpt")
            // An excerpt staged for the next new message, before the edit starts.
            vm.addPendingQuote("draft excerpt")

            vm.editQueued(queued.localId)
            runCurrent()
            assertThat(vm.uiState.value.pendingQuotes).containsExactly("queued excerpt")

            vm.removeDraftItem(DraftItem.Quote(0, vm.uiState.value.pendingQuotes[0]))
            vm.addPendingQuote("edited excerpt")
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().quotes).containsExactly("edited excerpt")
            assertThat(vm.uiState.value.pendingQuotes).containsExactly("draft excerpt")
        }

    @Test
    fun `cancelling a queued edit gives the item back its quotes and the draft its own`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.addPendingQuote("queued excerpt")
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            vm.addPendingQuote("draft excerpt")

            vm.editQueued(vm.uiState.value.messageQueue.single().localId)
            runCurrent()
            vm.addPendingQuote("thrown away")
            vm.cancelQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single().quotes).containsExactly("queued excerpt")
            assertThat(vm.uiState.value.pendingQuotes).containsExactly("draft excerpt")
        }

    @Test
    fun `undo puts a removed skill back where it was`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        listOf("first", "second", "third").forEach(vm::addPendingManualSkill)

        vm.removeDraftItem(DraftItem.Skill("second"))
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("first", "third").inOrder()
        val undo = checkNotNull(vm.uiState.value.pendingDraftUndo)

        vm.undoDraftRemoval(undo.id)

        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("first", "second", "third").inOrder()
        assertThat(vm.uiState.value.pendingDraftUndo).isNull()
    }

    @Test
    fun `a superseded undo neither drops nor restores, and only the current one drops`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            listOf("first", "second", "third").forEach(vm::addPendingManualSkill)
            vm.removeDraftItem(DraftItem.Skill("first"))
            val firstId = checkNotNull(vm.uiState.value.pendingDraftUndo).id
            vm.removeDraftItem(DraftItem.Skill("second"))
            val second = checkNotNull(vm.uiState.value.pendingDraftUndo)
            assertThat(second.id).isNotEqualTo(firstId)

            // The first snackbar's effect is cancelled after the second Undo is already pending,
            // and drops its own id on the way out.
            vm.dropDraftUndo(firstId)
            assertThat(vm.uiState.value.pendingDraftUndo).isEqualTo(second)

            // A leftover first snackbar's Undo restores nothing into the second's slot.
            vm.undoDraftRemoval(firstId)
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("third")
            assertThat(vm.uiState.value.pendingDraftUndo).isEqualTo(second)

            vm.dropDraftUndo(second.id)
            assertThat(vm.uiState.value.pendingDraftUndo).isNull()
            vm.undoDraftRemoval(second.id)
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("third")
        }

    @Test
    fun `undo puts a removed quote back where it was`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        listOf("one", "two", "three").forEach(vm::addPendingQuote)

        vm.removeDraftItem(DraftItem.Quote(index = 0, text = "one"))
        vm.undoDraftRemoval(checkNotNull(vm.uiState.value.pendingDraftUndo).id)

        assertThat(vm.uiState.value.pendingQuotes).containsExactly("one", "two", "three").inOrder()
    }

    @Test
    fun `undo puts a removed uploaded file back where it was`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        val first = AttachedFile(uri = "a", name = "a.pdf", fileId = "a", uploadProgress = 1f)
        val failed = AttachedFile(uri = "b", name = "b.pdf", uploadFailed = true)
        every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(first, failed))

        vm.removeDraftItem(DraftItem.File(failed, key = "file:b"))
        vm.undoDraftRemoval(checkNotNull(vm.uiState.value.pendingDraftUndo).id)

        verify { fileHandler.removeFile(failed) }
        verify { fileHandler.insertAt(1, failed) }
    }

    @Test
    fun `a removed file's undo that lapses or is replaced lets the handler forget the file`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            val failed = AttachedFile(uri = "b", name = "b.pdf", uploadFailed = true)
            val other = AttachedFile(uri = "c", name = "c.pdf", uploadFailed = true)
            every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(failed, other))

            vm.removeDraftItem(DraftItem.File(failed, key = "file:b"))
            verify(exactly = 0) { fileHandler.forgetRemoved(any()) }
            // A later removal replaces its Undo.
            vm.removeDraftItem(DraftItem.File(other, key = "file:c"))
            verify(exactly = 1) { fileHandler.forgetRemoved(failed) }

            // The snackbar lapses.
            vm.dropDraftUndo(checkNotNull(vm.uiState.value.pendingDraftUndo).id)
            verify(exactly = 1) { fileHandler.forgetRemoved(other) }
        }

    @Test
    fun `an undone file removal forgets nothing`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        val failed = AttachedFile(uri = "b", name = "b.pdf", uploadFailed = true)
        every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(failed))

        vm.removeDraftItem(DraftItem.File(failed, key = "file:b"))
        val undo = checkNotNull(vm.uiState.value.pendingDraftUndo)
        vm.undoDraftRemoval(undo.id)
        // The snackbar effect's own drop after an Undo finds nothing pending.
        vm.dropDraftUndo(undo.id)

        verify(exactly = 0) { fileHandler.forgetRemoved(any()) }
    }

    @Test
    fun `removing one copy of a file attached twice takes that copy alone and its undo puts it back`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            // One picked file, two tray entries told apart by their entry ids.
            val first = AttachedFile(uri = "same", name = "a.pdf", fileId = "a1", uploadProgress = 1f, entryId = "e1")
            val second = first.copy(fileId = "a2", entryId = "e2")
            every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(first, second))

            vm.removeDraftItem(DraftItem.File(second, key = "file:e2"))
            vm.undoDraftRemoval(checkNotNull(vm.uiState.value.pendingDraftUndo).id)

            verify { fileHandler.removeFile(second) }
            verify { fileHandler.insertAt(1, second) }
        }

    @Test
    fun `removing a file still uploading cancels it with no undo`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        val uploading = AttachedFile(uri = "c", name = "c.pdf", uploadProgress = 0.4f)
        every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(uploading))

        vm.removeDraftItem(DraftItem.File(uploading, key = "file:c"))

        verify { fileHandler.removeFile(uploading) }
        assertThat(vm.uiState.value.pendingDraftUndo).isNull()
    }

    @Test
    fun `a queue takes the message and drops the pending undo`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.addPendingManualSkill("translator")
        vm.removeDraftItem(DraftItem.Skill("translator"))
        val undo = checkNotNull(vm.uiState.value.pendingDraftUndo)

        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        assertThat(vm.uiState.value.pendingDraftUndo).isNull()

        vm.undoDraftRemoval(undo.id)
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
    }

    @Test
    fun `starting a queued edit drops the pending undo`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        vm.addPendingManualSkill("draft-skill")
        vm.removeDraftItem(DraftItem.Skill("draft-skill"))
        val undo = checkNotNull(vm.uiState.value.pendingDraftUndo)

        vm.editQueued(vm.uiState.value.messageQueue.single().localId)
        runCurrent()
        assertThat(vm.uiState.value.pendingDraftUndo).isNull()

        vm.undoDraftRemoval(undo.id)
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
    }

    @Test
    fun `a conversation switch drops the pending undo`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.removeDraftItem(DraftItem.Skill("pdf-tools"))
        assertThat(vm.uiState.value.pendingDraftUndo).isNotNull()

        vm.onPendingNavigationHandled()

        assertThat(vm.uiState.value.pendingDraftUndo).isNull()
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
