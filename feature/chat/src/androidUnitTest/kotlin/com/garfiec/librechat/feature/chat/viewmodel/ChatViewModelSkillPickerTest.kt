package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.feature.chat.components.SKILL_TOOL_NAME
import com.garfiec.librechat.feature.chat.components.parsePickerQuery
import com.garfiec.librechat.feature.chat.components.pendingSkillRows
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The composer's combined `/` + `$` picker on the skill side: the lazy catalog load, what a skill
 * pick does to the composer, and a streamed turn whose final message gains its skill cards at the
 * front. Driven through the real ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSkillPickerTest {

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
    private val streams = mutableListOf<MutableSharedFlow<StreamEvent>>()
    private val sentSkills = mutableListOf<List<String>?>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns endpointConfigs
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every { messageRepository.observeMessages(any()) } returns messages
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.checkStreamStatus(any(), any()) } returns ChatStatusResponse(active = false)
            coEvery { permissionGate.awaitRole() } returns UserRolePermissions(name = "USER")
            coEvery { promptRepository.getAllGroups() } returns Result.Success(listOf(promptGroup))
            coEvery { userRepository.getUser() } returns Result.Success(User(id = OWNER_ID, email = "me@example.test"))
            coEvery { skillsRepository.getSkillStates() } returns Result.Success(mapOf("id-off" to false))
            coEvery { skillsRepository.listSkills(any(), any(), any(), any()) } answers {
                when (arg<String?>(3)) {
                    null -> Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cursor-2"))
                    "cursor-2" -> Result.Success(SkillListResponse(skills = page2, hasMore = false))
                    else -> Result.Error(message = "unexpected cursor")
                }
            }
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
                sentSkills += arg<List<String>?>(25)
                MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8).also { streams += it }
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── the lazy catalog ────────────────────────────────────────────

    @Test
    fun `nothing loads until a trigger, then every page once`() = pickerTest { vm ->
        coVerify(exactly = 0) { fixture.skillsRepository.listSkills(any(), any(), any(), any()) }
        vm.onInputChanged("hello")
        vm.onInputChanged("what costs \$5")
        coVerify(exactly = 0) { fixture.skillsRepository.listSkills(any(), any(), any(), any()) }

        vm.onInputChanged("$")
        runCurrent()
        vm.onInputChanged("/pd")
        runCurrent()

        coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), null) }
        coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), "cursor-2") }
        coVerify(exactly = 1) { fixture.skillsRepository.getSkillStates() }
        assertThat(vm.uiState.value.presetPrompts.isLoadingSkills).isFalse()
        // The override turns id-off off; the shared skill is off by default; the page-2 skill
        // is the user's own and on.
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).containsExactly("pdf-tools", "brand-voice").inOrder()
    }

    @Test
    fun `no load where no skill could be picked`() = pickerTest { vm ->
        endpointConfigs.value = mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig())
        runCurrent()
        vm.onInputChanged("$")
        runCurrent()
        coVerify(exactly = 0) { fixture.skillsRepository.listSkills(any(), any(), any(), any()) }
        assertThat(vm.uiState.value.pickerSkills).isEmpty()
    }

    @Test
    fun `an agent chat whose agent offers no skills neither opens the dollar picker nor loads`() {
        coEvery { fixture.agentRepository.getAgents() } returns Result.Success(
            listOf(
                Agent(id = "agent_closed", name = "Closed", skillsEnabled = false),
                Agent(id = "agent_none", name = "None", skillsEnabled = true, skillsScope = "none"),
                Agent(id = "agent_open", name = "Open", skillsEnabled = true),
            ),
        )
        pickerTest { vm ->
            for (agent in listOf("agent_closed", "agent_none")) {
                vm.onModelSelected(EndpointConstants.AGENTS, agent)
                runCurrent()
                assertThat(vm.uiState.value.selectedModel).isEqualTo(agent)
                assertThat(vm.uiState.value.canPickSkills).isFalse()
                vm.onInputChanged("\$AAPL")
                runCurrent()
                assertThat(parsePickerQuery(vm.uiState.value.inputText, vm.uiState.value.canPickSkills)).isNull()
                vm.onInputChanged("")
                runCurrent()
            }
            coVerify(exactly = 0) { fixture.skillsRepository.listSkills(any(), any(), any(), any()) }

            // An agent that offers skills opens it, with the trigger still in the field.
            vm.onInputChanged("\$pd")
            vm.onModelSelected(EndpointConstants.AGENTS, "agent_open")
            runCurrent()
            assertThat(vm.uiState.value.canPickSkills).isTrue()
            coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), null) }
        }
    }

    @Test
    fun `chips a new chat held back are checked against the catalog on the created chat`() {
        fixture.selectionHandoff.put(
            "conv-new",
            ModelChoice.Raw(ENDPOINT, MODEL),
            stagedManualSkills = listOf("turned-off", "pdf-tools"),
            skillsNotSentNotice = true,
        )
        pickerTest(conversationId = "conv-new") { vm ->
            // Loaded without the picker opening: the send needs it to recheck the chips.
            coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), null) }

            vm.onInputChanged("summarize this")
            vm.sendMessage()
            runCurrent()

            // turned-off is off for this user, so it stays held back; pdf-tools goes.
            assertThat(sentSkills.single()).containsExactly("pdf-tools")
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("turned-off")
        }
    }

    @Test
    fun `chips a new chat held back stay held back on the created chat while no catalog loads`() {
        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Error(message = "offline")
        fixture.selectionHandoff.put(
            "conv-new",
            ModelChoice.Raw(ENDPOINT, MODEL),
            stagedManualSkills = listOf("turned-off", "pdf-tools"),
            skillsNotSentNotice = true,
        )
        pickerTest(conversationId = "conv-new") { vm ->
            coVerify(exactly = 1) { fixture.skillsRepository.getSkillStates() }
            assertThat(vm.uiState.value.skillsLoadFailed).isTrue()

            // Back on the chat with the picker closed: the chips still need the catalog.
            vm.onResume()
            runCurrent()
            coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }

            vm.onInputChanged("summarize this")
            vm.sendMessage()
            runCurrent()

            // Only their names could be checked, so neither goes out, and the user is told.
            assertThat(sentSkills.single()).isNull()
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("turned-off", "pdf-tools").inOrder()
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SKILLS_NOT_SENT.marker())
            // And the send asked for the catalog again.
            coVerify(exactly = 3) { fixture.skillsRepository.getSkillStates() }
        }
    }

    @Test
    fun `coming back while a held-back chip's catalog load fails retries it once it ends`() {
        val firstLoad = CompletableDeferred<Unit>()
        coEvery { fixture.skillsRepository.getSkillStates() } coAnswers {
            firstLoad.await()
            Result.Error(message = "offline")
        }
        fixture.selectionHandoff.put("conv-new", ModelChoice.Raw(ENDPOINT, MODEL), stagedManualSkills = listOf("pdf-tools"))
        pickerTest(conversationId = "conv-new") { vm ->
            assertThat(vm.uiState.value.isLoadingSkills).isTrue()
            coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Success(mapOf("id-off" to false))
            vm.onResume()
            runCurrent()
            firstLoad.complete(Unit)
            runCurrent()

            coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
            assertThat(vm.uiState.value.skillsLoadFailed).isFalse()
            assertThat(vm.uiState.value.skillCatalog).isNotNull()
        }
    }

    @Test
    fun `a trigger typed before skills could be picked loads once the gate opens`() = pickerTest { vm ->
        val withSkills = endpointConfigs.value
        endpointConfigs.value = mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig())
        runCurrent()
        vm.onInputChanged("\$pd")
        runCurrent()
        coVerify(exactly = 0) { fixture.skillsRepository.listSkills(any(), any(), any(), any()) }

        // The capability arrives late, with the trigger still in the field.
        endpointConfigs.value = withSkills
        runCurrent()
        coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), null) }
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).contains("pdf-tools")
    }

    @Test
    fun `a restored draft that opens the picker loads the catalog with no keystroke`() {
        coEvery { fixture.draftRepository.awaitDraft(any()) } returns "\$pd"
        pickerTest(conversationId = "conv-1") { vm ->
            assertThat(vm.uiState.value.inputText).isEqualTo("\$pd")
            coVerify(exactly = 1) { fixture.skillsRepository.listSkills(any(), any(), any(), null) }
            assertThat(vm.uiState.value.presetPrompts.isLoadingSkills).isFalse()
            assertThat(vm.uiState.value.pickerSkills.map { it.name }).contains("pdf-tools")
        }
    }

    @Test
    fun `a failed load says so, typing on does not reload, and the next trigger retries`() = pickerTest { vm ->
        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Error(message = "offline")
        vm.onInputChanged("$")
        runCurrent()
        assertThat(vm.uiState.value.presetPrompts.skillCatalog).isNull()
        assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isTrue()
        assertThat(vm.uiState.value.presetPrompts.isLoadingSkills).isFalse()
        assertThat(vm.uiState.value.pickerSkills).isEmpty()

        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Success(emptyMap())
        // Still the same open picker: no load per keystroke.
        vm.onInputChanged("\$p")
        vm.onInputChanged("\$pd")
        runCurrent()
        coVerify(exactly = 1) { fixture.skillsRepository.getSkillStates() }
        assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isTrue()

        // Closing and reopening the picker is a new trigger.
        vm.onInputChanged("")
        vm.onInputChanged("\$p")
        runCurrent()
        assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isFalse()
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).contains("pdf-tools")
    }

    @Test
    fun `the retry reloads a failed catalog`() = pickerTest { vm ->
        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Error(message = "offline")
        vm.onInputChanged("\$pd")
        runCurrent()
        assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isTrue()

        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Success(emptyMap())
        vm.retrySkillCatalog()
        runCurrent()
        assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isFalse()
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).contains("pdf-tools")
    }

    @Test
    fun `coming back to the chat reloads the catalog quietly on the next trigger`() = pickerTest { vm ->
        vm.onInputChanged("$")
        runCurrent()
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).contains("pdf-tools")
        vm.onInputChanged("")

        // On the Skills screen meanwhile: pdf-tools turned off.
        coEvery { fixture.skillsRepository.getSkillStates() } returns
            Result.Success(mapOf("id-off" to false, "id-pdf" to false))
        vm.onResume()
        runCurrent()
        // Nothing reloads until the picker is used again; the old rows stay until then.
        coVerify(exactly = 1) { fixture.skillsRepository.getSkillStates() }

        vm.onInputChanged("$")
        runCurrent()
        coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
        assertThat(vm.uiState.value.presetPrompts.isLoadingSkills).isFalse()
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).containsExactly("brand-voice")

        // Fresh again: typing on reloads nothing.
        vm.onInputChanged("\$b")
        runCurrent()
        coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
    }

    @Test
    fun `coming back with the picker open reloads at once, and a failed quiet reload keeps the rows`() =
        pickerTest { vm ->
            vm.onInputChanged("$")
            runCurrent()
            coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Error(message = "offline")

            vm.onResume()
            runCurrent()

            coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
            assertThat(vm.uiState.value.presetPrompts.skillsLoadFailed).isFalse()
            assertThat(vm.uiState.value.pickerSkills.map { it.name }).containsExactly("pdf-tools", "brand-voice")
        }

    @Test
    fun `a failed quiet reload leaves the catalog stale, so reopening the picker reloads it`() = pickerTest { vm ->
        vm.onInputChanged("$")
        runCurrent()
        coEvery { fixture.skillsRepository.getSkillStates() } returns Result.Error(message = "offline")
        vm.onResume()
        runCurrent()
        coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
        // Typing on in the open picker doesn't hammer a failing server.
        vm.onInputChanged("\$p")
        runCurrent()
        coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }

        // Back online, with pdf-tools turned off meanwhile.
        coEvery { fixture.skillsRepository.getSkillStates() } returns
            Result.Success(mapOf("id-off" to false, "id-pdf" to false))
        vm.onInputChanged("")
        vm.onInputChanged("$")
        runCurrent()
        coVerify(exactly = 3) { fixture.skillsRepository.getSkillStates() }
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).containsExactly("brand-voice")
    }

    @Test
    fun `coming back while the catalog loads reloads it once that load ends`() = pickerTest { vm ->
        val firstLoad = CompletableDeferred<Unit>()
        coEvery { fixture.skillsRepository.getSkillStates() } coAnswers {
            firstLoad.await()
            Result.Success(mapOf("id-off" to false))
        }
        vm.onInputChanged("$")
        runCurrent()
        assertThat(vm.uiState.value.isLoadingSkills).isTrue()

        // The user leaves, turns pdf-tools off and comes back before the first load lands.
        coEvery { fixture.skillsRepository.getSkillStates() } returns
            Result.Success(mapOf("id-off" to false, "id-pdf" to false))
        vm.onResume()
        runCurrent()
        firstLoad.complete(Unit)
        runCurrent()

        coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).containsExactly("brand-voice")
    }

    @Test
    fun `coming back with a chip staged reloads at once, so a skill turned off meanwhile is not sent`() =
        pickerTest { vm ->
            vm.onInputChanged("\$pd")
            runCurrent()
            vm.selectPickerItem(ComposerPickerItem.Skill(PickerSkill(name = "pdf-tools", title = "PDF tools", description = null)))
            runCurrent()
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")

            // On the Skills screen meanwhile: pdf-tools turned off.
            coEvery { fixture.skillsRepository.getSkillStates() } returns
                Result.Success(mapOf("id-off" to false, "id-pdf" to false))
            vm.onResume()
            runCurrent()
            coVerify(exactly = 2) { fixture.skillsRepository.getSkillStates() }

            // Send without opening the picker again.
            vm.onInputChanged("summarize this")
            vm.sendMessage()
            runCurrent()
            assertThat(sentSkills.single()).isNull()
            assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
        }

    // ─── picking ─────────────────────────────────────────────────────

    @Test
    fun `a skill pick strips the query, stages a chip and turns Skills on`() = pickerTest { vm ->
        vm.onInputChanged("\$pd")
        runCurrent()
        vm.selectPickerItem(ComposerPickerItem.Skill(PickerSkill(name = "pdf-tools", title = "PDF tools", description = null)))
        runCurrent()

        assertThat(vm.uiState.value.inputText).isEmpty()
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.skillsToggle).isTrue()
        // Picked once, it isn't offered again.
        assertThat(vm.uiState.value.pickerSkills.map { it.name }).doesNotContain("pdf-tools")

        vm.onInputChanged("summarize this")
        vm.sendMessage()
        runCurrent()
        assertThat(sentSkills.single()).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
    }

    @Test
    fun `a prompt pick from either trigger does what slash always did`() = pickerTest { vm ->
        vm.onInputChanged("\$fir")
        runCurrent()
        vm.selectPickerItem(ComposerPickerItem.Prompt(vm.uiState.value.availablePrompts.single()))
        runCurrent()
        assertThat(vm.uiState.value.inputText).isEqualTo("body of First")
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
    }

    // ─── the streamed turn ───────────────────────────────────────────

    @Test
    fun `the final message gains the skill cards at the front and the stand-ins end with it`() =
        pickerTest(conversationId = "conv-1") { vm ->
            vm.addPendingManualSkill("pdf-tools")
            vm.addPendingManualSkill("brand-voice")
            vm.onInputChanged("summarize")
            vm.sendMessage()
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isTrue()

            // While streaming: the turn's user message is the last displayed node and names the
            // picks, so each gets a stand-in row; the stream itself carries no skill cards.
            val turn = vm.uiState.value.displayMessages.last().message
            assertThat(turn.isCreatedByUser).isTrue()
            assertThat(pendingSkillRows(turn.manualSkills, emptyList())).containsExactly("pdf-tools", "brand-voice").inOrder()

            val stream = streams.single()
            stream.emit(StreamEvent.ContentDelta("Here is"))
            stream.emit(
                StreamEvent.ToolCallStart(toolCallId = "t1", toolName = "web_search", input = "{}"),
            )
            runCurrent()

            val replyParts = listOf(
                MessageContentPart(type = ContentType.TEXT, text = "Here is the summary."),
                MessageContentPart(
                    type = ContentType.TOOL_CALL,
                    toolCall = AgentToolCall(id = "t1", name = "web_search", output = "results"),
                ),
            )
            val final = listOf(prime(0, "pdf-tools"), prime(1, "brand-voice")) + replyParts
            stream.emit(
                StreamEvent.Final(
                    requestMessage = null,
                    responseMessage = Message(
                        messageId = "a1",
                        conversationId = "conv-1",
                        parentMessageId = turn.messageId,
                        text = "Here is the summary.",
                        content = final,
                    ),
                    conversation = null,
                ),
            )
            runCurrent()

            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            // No live part survives the swap to carry an index-keyed state onto the shifted parts.
            assertThat(state.content.activeToolCalls).isEmpty()
            assertThat(state.content.streamingContent).isEmpty()
            val reply = state.messages.single { it.messageId == "a1" }
            assertThat(reply.content).isEqualTo(final)
            assertThat(reply.content!!.take(2).map { it.toolCall?.name }).containsExactly(SKILL_TOOL_NAME, SKILL_TOOL_NAME)
            // The reply, not the user turn, is now the last node: no stand-ins remain.
            val last = state.displayMessages.last().message
            assertThat(last.messageId).isEqualTo("a1")
            assertThat(last.content).isEqualTo(final)
        }

    // ─── helpers ─────────────────────────────────────────────────────

    private fun prime(index: Int, name: String) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(
            id = "call_manual_skill_a1_$index",
            name = SKILL_TOOL_NAME,
            args = JsonPrimitive("""{"skillName":"$name"}"""),
            output = "Skill \"$name\" loaded. Follow the instructions below.",
        ),
    )

    private val promptGroup = PromptGroup(
        id = "g-1",
        name = "First",
        author = "author-1",
        authorName = "Author",
        productionId = "g-1-p",
        prompts = listOf(Prompt(id = "g-1-p", groupId = "g-1", author = "author-1", prompt = "body of First", type = "text")),
    )

    private val page1 = listOf(
        SkillSummary(id = "id-pdf", name = "pdf-tools", displayTitle = "PDF tools", author = OWNER_ID),
        SkillSummary(id = "id-off", name = "turned-off", author = OWNER_ID),
        SkillSummary(id = "id-shared", name = "shared-skill", author = OTHER_ID),
    )
    private val page2 = listOf(
        SkillSummary(id = "id-voice", name = "brand-voice", author = OWNER_ID),
    )

    private fun pickerTest(
        conversationId: String? = null,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = conversationId)
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
        const val OWNER_ID = "64f0c0ffee0000000000aaaa"
        const val OTHER_ID = "64f0c0ffee0000000000bbbb"
    }
}
