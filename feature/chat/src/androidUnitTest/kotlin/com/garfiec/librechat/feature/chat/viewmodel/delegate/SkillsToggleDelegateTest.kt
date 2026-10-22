package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecSkills
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.SkillsToggleHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsToggleDelegateTest {

    private val stored = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val settings = mockk<SettingsDataStore> {
        every { skillsToggle(any()) } answers { val slot = firstArg<String>(); stored.map { it[slot] } }
        coEvery { setSkillsToggle(any(), any()) } answers {
            stored.value = stored.value + (firstArg<String>() to secondArg<Boolean>())
        }
    }

    private val skillsServer = mapOf(
        "anthropic" to EndpointConfig(),
        EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS)),
    )

    /** The raw haiku pick, or [spec] opening on it. */
    private fun choice(spec: String?): ModelChoice =
        if (spec == null) ModelChoice.Raw("anthropic", "claude-haiku-4-5") else ModelChoice.Spec(spec, "anthropic", "claude-haiku-4-5")

    private fun state(
        conversationId: String? = null,
        temporary: Boolean = false,
        spec: String? = null,
        specs: List<ModelSpec> = emptyList(),
    ) = ChatUiState(
        selection = ModelSelectionState(
            choice = choice(spec),
            modelSpecs = ModelSpecs(list = specs),
            endpointConfigs = skillsServer,
        ),
        conversation = ConversationMetaState(conversationId = conversationId, isTemporaryChat = temporary),
    )

    // ─── slots ───────────────────────────────────────────────────────

    @Test
    fun `a new chat uses the shared new-chat slot`() {
        assertThat(skillsToggleSlot(state()))
            .isEqualTo(SkillsToggleSlot.Stored(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT))
    }

    @Test
    fun `with specs offered and none selected a new chat uses the defaults slot`() {
        val specs = listOf(ModelSpec(name = "curated"))
        assertThat(skillsToggleSlot(state(specs = specs)))
            .isEqualTo(SkillsToggleSlot.Stored(SettingsDataStore.SKILLS_TOGGLE_SPEC_DEFAULTS_SLOT))
        // A selected spec that leaves skills alone stores nothing, new chat or not.
        assertThat(skillsToggleSlot(state(spec = "curated", specs = specs)))
            .isEqualTo(SkillsToggleSlot.SpecSelected("curated"))
        assertThat(skillsToggleSlot(state(conversationId = "c1", spec = "curated", specs = specs)))
            .isEqualTo(SkillsToggleSlot.SpecSelected("curated"))
    }

    @Test
    fun `a selected spec starts off, keeps the session value in memory and writes nothing`() =
        delegateTest(state()) { flow, delegate ->
            stored.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to true, "c1" to true)
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()

            // The server enforces specs: one is selected for the new chat, and it has no skills key.
            val specs = ModelSpecs(listOf(ModelSpec(name = "curated"), ModelSpec(name = "other")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
            assertThat(flow.value.isSkillsToolAvailable).isTrue()

            delegate.toggle()
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()
            delegate.carryToCreatedConversation("c9", isTemporary = false)
            flow.update { it.copy(conversation = it.conversation.copy(conversationId = "c9")) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()
            assertThat(stored.value)
                .containsExactly(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT, true, "c1", true)

            // Another spec starts off again, as web rebuilds the ephemeral agent from it.
            flow.update { it.copy(selection = it.selection.copy(choice = choice("other"))) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
        }

    @Test
    fun `an existing conversation under a spec ignores its stored value`() =
        delegateTest(state(conversationId = "c1")) { flow, _ ->
            stored.value = mapOf("c1" to true)
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()

            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
        }

    @Test
    fun `a toggle under a spec in an existing conversation is written to its slot, as web writes it`() =
        delegateTest(state(conversationId = "c1")) { flow, delegate ->
            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()

            delegate.toggle()
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()
            assertThat(stored.value).containsExactly("c1", true)

            // The spec is cleared: the conversation reads what was set under it, as on web.
            flow.update { it.copy(selection = it.selection.copy(choice = choice(null))) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()
        }

    @Test
    fun `a toggle under a spec in a temporary chat is never written`() =
        delegateTest(state(conversationId = "t1", temporary = true)) { flow, delegate ->
            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            delegate.toggle()
            runCurrent()
            assertThat(flow.value.skillsToggle).isTrue()
            assertThat(stored.value).isEmpty()
        }

    @Test
    fun `the new-chat handoff seed survives a spec chat's start`() {
        val specs = listOf(ModelSpec(name = "curated"), ModelSpec(name = "other"))
        delegateTest(
            state(conversationId = "c9", spec = "curated", specs = specs),
            seedBeforeStart = "c9" to true,
        ) { flow, _ ->
            assertThat(flow.value.skillsToggle).isTrue()
            assertThat(stored.value).isEmpty()

            // Used once: the next spec change starts off.
            flow.update { it.copy(selection = it.selection.copy(choice = choice("other"))) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
        }
    }

    @Test
    fun `an existing conversation uses its own id`() {
        assertThat(skillsToggleSlot(state(conversationId = "c1"))).isEqualTo(SkillsToggleSlot.Stored("c1"))
    }

    @Test
    fun `a temporary chat lives in memory`() {
        assertThat(skillsToggleSlot(state(conversationId = "c1", temporary = true))).isEqualTo(SkillsToggleSlot.Memory)
    }

    @Test
    fun `a spec the config sends with skills governs and hides the toggle`() {
        val governing = listOf(ModelSpec(name = "locked", skills = JsonPrimitive(false)))
        val s = state(conversationId = "c1", spec = "locked", specs = governing)
        assertThat(skillsToggleSlot(s)).isEqualTo(SkillsToggleSlot.SpecGoverned)
        assertThat(s.activeSpecSkills).isEqualTo(ModelSpecSkills.Off)
        assertThat(s.isSkillsToolAvailable).isFalse()
        assertThat(s.canPickSkills).isFalse()
        // Even an "on" toggle left over from before is not sent: the spec overrides it.
        val withToggle = s.copy(selection = s.selection.copy(skillsToggle = true))
        assertThat(ChatRequestBuilder { withToggle }.buildEphemeralAgent()?.skills).isNull()
    }

    // ─── reads, writes and the carry ─────────────────────────────────

    @Test
    fun `toggling writes the slot and a spec-governed chat writes nothing`() = delegateTest(state()) { flow, delegate ->
        delegate.toggle()
        runCurrent()
        assertThat(stored.value).containsExactly(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT, true)
        assertThat(flow.value.skillsToggle).isTrue()

        val governing = listOf(ModelSpec(name = "all", skills = JsonPrimitive(true)))
        flow.update { it.copy(selection = it.selection.copy(choice = choice("all"), modelSpecs = ModelSpecs(governing))) }
        runCurrent()
        delegate.toggle()
        delegate.turnOn()
        runCurrent()
        assertThat(stored.value).containsExactly(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT, true)
    }

    @Test
    fun `a pick on a new chat stays in memory until the user toggles`() = delegateTest(state()) { flow, delegate ->
        stored.value = mapOf(SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT to false)
        runCurrent()
        delegate.turnOn()
        runCurrent()
        assertThat(flow.value.skillsToggle).isTrue()
        // An unrelated write re-emits the store; the pick still holds.
        stored.value = stored.value + ("other" to true)
        runCurrent()
        assertThat(flow.value.skillsToggle).isTrue()
        assertThat(stored.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isFalse()

        delegate.resetNewChat()
        runCurrent()
        assertThat(flow.value.skillsToggle).isFalse()

        // An explicit toggle still writes the shared slot.
        delegate.turnOn()
        delegate.toggle()
        delegate.toggle()
        runCurrent()
        assertThat(flow.value.skillsToggle).isTrue()
        assertThat(stored.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isTrue()
    }

    @Test
    fun `a pick in an existing conversation is stored for it`() = delegateTest(state(conversationId = "c1")) { flow, delegate ->
        delegate.turnOn()
        runCurrent()
        assertThat(flow.value.skillsToggle).isTrue()
        assertThat(stored.value).containsExactly("c1", true)
    }

    @Test
    fun `the first send carries the new-chat value to the created conversation`() =
        delegateTest(state()) { flow, delegate ->
            delegate.toggle()
            runCurrent()

            delegate.carryToCreatedConversation("c9", isTemporary = false)
            flow.update { it.copy(conversation = it.conversation.copy(conversationId = "c9")) }
            runCurrent()

            assertThat(stored.value["c9"]).isTrue()
            assertThat(flow.value.skillsToggle).isTrue()
            // The next new chat still starts from the shared slot.
            assertThat(stored.value[SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT]).isTrue()
        }

    @Test
    fun `an off new chat carries nothing to store`() = delegateTest(state()) { flow, delegate ->
        delegate.carryToCreatedConversation("c9", isTemporary = false)
        flow.update { it.copy(conversation = it.conversation.copy(conversationId = "c9")) }
        runCurrent()

        assertThat(stored.value).isEmpty()
        assertThat(flow.value.skillsToggle).isFalse()
    }

    @Test
    fun `a seed holds until the carried write lands rather than reading absent as off`() =
        delegateTest(state(conversationId = "c9"), seedBeforeStart = "c9" to true) { flow, _ ->
            assertThat(flow.value.skillsToggle).isTrue()
            assertThat(stored.value).isEmpty()
        }

    @Test
    fun `a seed taken on a stored slot does not carry into a later spec switch`() =
        // The carried write hasn't landed: the store is still empty when the spec is picked.
        delegateTest(state(conversationId = "c9"), seedBeforeStart = "c9" to true) { flow, _ ->
            assertThat(flow.value.skillsToggle).isTrue()
            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
            assertThat(stored.value).isEmpty()
        }

    @Test
    fun `a stored value wins over a seed taken before start, and a spec switch starts off`() =
        delegateTest(state(conversationId = "c9"), seedBeforeStart = "c9" to true) { flow, _ ->
            assertThat(flow.value.skillsToggle).isTrue()
            stored.value = mapOf("c9" to false)
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()

            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
        }

    @Test
    fun `a temporary chat's seed turned off stays off when the user then picks a spec`() =
        delegateTest(state(conversationId = "t1", temporary = true), seedBeforeStart = "t1" to true) { flow, delegate ->
            assertThat(flow.value.skillsToggle).isTrue()
            delegate.toggle()
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()

            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
            assertThat(stored.value).isEmpty()
        }

    @Test
    fun `a temporary chat's seed does not turn a later spec pick on`() =
        delegateTest(state(conversationId = "t1", temporary = true), seedBeforeStart = "t1" to true) { flow, _ ->
            assertThat(flow.value.skillsToggle).isTrue()

            val specs = ModelSpecs(listOf(ModelSpec(name = "curated")))
            flow.update { it.copy(selection = it.selection.copy(choice = choice("curated"), modelSpecs = specs)) }
            runCurrent()
            assertThat(flow.value.skillsToggle).isFalse()
        }

    @Test
    fun `a temporary chat's carry stores nothing`() = delegateTest(state(temporary = true)) { flow, delegate ->
        delegate.toggle()
        delegate.carryToCreatedConversation("t1", isTemporary = true)
        runCurrent()
        assertThat(flow.value.skillsToggle).isTrue()
        assertThat(stored.value).isEmpty()
    }

    private fun delegateTest(
        initial: ChatUiState,
        seedBeforeStart: Pair<String, Boolean>? = null,
        body: suspend TestScope.(MutableStateFlow<ChatUiState>, SkillsToggleDelegate) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(initial)
        val scope = TestScope(testScheduler)
        val delegate = SkillsToggleDelegate(SkillsToggleHandle(ChatStateHandle(flow, scope)), settings)
        // The ViewModel seeds from the new-chat handoff before it starts the delegate.
        seedBeforeStart?.let { (id, on) -> delegate.seed(id, on) }
        delegate.start()
        runCurrent()
        try {
            body(flow, delegate)
        } finally {
            scope.cancel()
        }
    }
}
