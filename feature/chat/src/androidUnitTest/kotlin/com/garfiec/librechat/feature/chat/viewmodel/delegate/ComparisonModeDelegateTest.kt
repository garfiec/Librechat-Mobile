package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonHandle
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ComparisonModeDelegateTest {

    private fun delegateWith(
        state: ChatUiState = ChatUiState(),
        specs: () -> ModelSpecs? = { null },
    ): Pair<ComparisonModeDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(state)
        val handle = ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))
        val delegate = ComparisonModeDelegate(
            handle = ComparisonHandle(handle),
            messageRepository = mockk<MessageRepository>(relaxed = true),
            reloadConversation = {},
            currentModelSpecs = specs,
        )
        return delegate to flow
    }

    private fun textPart(text: String, agentId: String?) =
        MessageContentPart(type = ContentType.TEXT, text = text, agentId = agentId)

    private fun parallelMessage(secondaryAgentId: String) = Message(
        messageId = "m-parallel",
        conversationId = "c1",
        content = listOf(
            textPart("primary reply", agentId = "agent_primary"),
            textPart("secondary reply", agentId = secondaryAgentId),
        ),
    )

    @Test
    fun `rehydrate from a real added-agent id maps to AGENTS endpoint`() {
        val (delegate, flow) = delegateWith()

        delegate.rehydrateFromMessage(parallelMessage("agent_secondary____1"))

        val comparison = flow.value.comparisonState
        assertThat(comparison.isEnabled).isTrue()
        assertThat(comparison.secondaryEndpoint).isEqualTo("agents")
        assertThat(comparison.secondaryModel).isEqualTo("agent_secondary")
        assertThat(comparison.secondaryAgentId).isEqualTo("agent_secondary____1")
        assertThat(comparison.primaryAgentId).isEqualTo("agent_primary")
        assertThat(comparison.parallelMessageId).isEqualTo("m-parallel")
    }

    @Test
    fun `rehydrate from an ephemeral added-agent id decodes endpoint and model`() {
        val (delegate, flow) = delegateWith()

        delegate.rehydrateFromMessage(parallelMessage("openAI__gpt-4o___GPT-4o____1"))

        val comparison = flow.value.comparisonState
        assertThat(comparison.isEnabled).isTrue()
        assertThat(comparison.secondaryEndpoint).isEqualTo("openAI")
        assertThat(comparison.secondaryModel).isEqualTo("gpt-4o")
    }

    @Test
    fun `rehydrate switches a landing screen to active`() {
        val (delegate, flow) = delegateWith(ChatUiState(content = MessagesState(screenState = ChatScreenState.LANDING)))

        delegate.rehydrateFromMessage(parallelMessage("agent_secondary____1"))

        assertThat(flow.value.screenState).isEqualTo(ChatScreenState.ACTIVE)
    }

    @Test
    fun `rehydrate no-ops when the message has no added-agent part`() {
        val (delegate, flow) = delegateWith()
        val single = Message(
            messageId = "m1",
            conversationId = "c1",
            content = listOf(textPart("just one", agentId = "agent_primary")),
        )

        delegate.rehydrateFromMessage(single)

        assertThat(flow.value.comparisonState.isEnabled).isFalse()
    }

    private val research = ModelSpec(
        name = "research",
        label = "Research",
        preset = Preset(
            endpoint = "openAI",
            model = "gpt-4o",
            temperature = 0.2,
            topP = 0.9,
            maxOutputTokens = 2048,
        ),
    )
    private val quick =
        ModelSpec(name = "quick", label = "Quick", preset = Preset(endpoint = "openAI", model = "gpt-4o"))

    @Test
    fun `a reopened spec lane gets its spec back from the sender in its id`() {
        val specs = ModelSpecs(list = listOf(quick, research))
        val (delegate, flow) = delegateWith(specs = { specs })

        delegate.rehydrateFromMessage(parallelMessage("openAI__gpt-4o___Research____1"))

        assertThat(flow.value.comparisonState.secondarySpec).isEqualTo("research")
        assertThat(delegate.buildAddedConvo()?.spec).isEqualTo("research")
    }

    @Test
    fun `a reopen that lands before the spec config finds the spec once it loads`() {
        var specs: ModelSpecs? = null
        val (delegate, flow) = delegateWith(specs = { specs })

        delegate.rehydrateFromMessage(parallelMessage("openAI__gpt-4o___Research____1"))
        assertThat(flow.value.comparisonState.secondarySpec).isNull()
        specs = ModelSpecs(list = listOf(quick, research))
        flow.value = flow.value.copy(selection = flow.value.selection.copy(modelSpecs = specs))

        assertThat(delegate.buildAddedConvo()?.spec).isEqualTo("research")
        assertThat(flow.value.laneBSpec).isEqualTo("research")
    }

    @Test
    fun `a reopened saved-agent lane has no spec`() {
        val specs = ModelSpecs(list = listOf(research))
        val (delegate, flow) = delegateWith(specs = { specs })

        delegate.rehydrateFromMessage(parallelMessage("agent_secondary____1"))

        assertThat(flow.value.comparisonState.secondarySpec).isNull()
        assertThat(delegate.buildAddedConvo()?.spec).isNull()
    }

    @Test
    fun `a new lane B pick drops the reopened spec`() {
        val specs = ModelSpecs(list = listOf(quick, research))
        val (delegate, _) = delegateWith(specs = { specs })
        delegate.rehydrateFromMessage(parallelMessage("openAI__gpt-4o___Research____1"))

        delegate.setSecondaryModel("openAI", "gpt-4o")

        assertThat(delegate.buildAddedConvo()?.spec).isNull()
    }

    @Test
    fun `a spec lane carries its preset's sampling settings flat`() {
        val specs = ModelSpecs(list = listOf(research))
        val (delegate, _) = delegateWith(
            ChatUiState(
                comparisonState = ComparisonState(
                    isEnabled = true,
                    secondaryEndpoint = "openAI",
                    secondaryModel = "gpt-4o",
                    secondarySpec = "research",
                ),
            ),
            specs = { specs },
        )

        val added = delegate.buildAddedConvo("p1")!!

        assertThat(added.spec).isEqualTo("research")
        assertThat(added.temperature).isEqualTo(0.2)
        assertThat(added.topP).isEqualTo(0.9)
        assertThat(added.maxOutputTokens).isEqualTo(2048)
        // Read back from the sender on reopen, which a label would replace.
        assertThat(added.modelLabel).isNull()
    }

    @Test
    fun `a raw lane B carries no settings`() {
        val (delegate, _) = delegateWith(
            ChatUiState(comparisonState = ComparisonState(isEnabled = true, secondaryEndpoint = "openAI", secondaryModel = "gpt-4o")),
            specs = { ModelSpecs(list = listOf(research)) },
        )

        val added = delegate.buildAddedConvo("p1")!!

        assertThat(added.spec).isNull()
        assertThat(added.temperature).isNull()
    }

    private fun landingWithPrimary() = ChatUiState(
        content = MessagesState(screenState = ChatScreenState.LANDING),
        selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-5")),
    )

    @Test
    fun `begin setup enables nothing, clones nothing and stays on landing`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())

        delegate.beginComparisonSetup()

        val comparison = flow.value.comparisonState
        assertThat(comparison.isSettingUp).isTrue()
        assertThat(comparison.isEnabled).isFalse()
        assertThat(comparison.secondaryEndpoint).isNull()
        assertThat(comparison.secondaryModel).isNull()
        assertThat(flow.value.screenState).isEqualTo(ChatScreenState.LANDING)
        assertThat(delegate.buildAddedConvo()).isNull()
    }

    @Test
    fun `start enables the picked model and leaves landing`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())
        delegate.beginComparisonSetup()

        delegate.startComparison("anthropic", "claude-opus-4-5")

        val comparison = flow.value.comparisonState
        assertThat(comparison.isEnabled).isTrue()
        assertThat(comparison.isSettingUp).isFalse()
        assertThat(comparison.secondaryEndpoint).isEqualTo("anthropic")
        assertThat(comparison.secondaryModel).isEqualTo("claude-opus-4-5")
        assertThat(flow.value.screenState).isEqualTo(ChatScreenState.ACTIVE)
    }

    @Test
    fun `start carries a spec picked during setup`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())
        delegate.beginComparisonSetup()

        delegate.startComparison("openAI", "gpt-4.1", spec = "fast")

        assertThat(flow.value.comparisonState.secondarySpec).isEqualTo("fast")
    }

    @Test
    fun `start is refused while a reply streams`() {
        val streaming = landingWithPrimary().let { it.copy(content = it.content.copy(isStreaming = true)) }
        val (delegate, flow) = delegateWith(streaming)
        delegate.beginComparisonSetup()

        delegate.startComparison("anthropic", "claude-opus-4-5")

        assertThat(flow.value.comparisonState.isEnabled).isFalse()
        assertThat(flow.value.comparisonState.isSettingUp).isTrue()
    }

    @Test
    fun `stop is refused while a reply streams`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())
        delegate.startComparison("anthropic", "claude-opus-4-5")
        flow.value = flow.value.copy(content = flow.value.content.copy(isStreaming = true))

        delegate.stopComparison()

        assertThat(flow.value.comparisonState.isEnabled).isTrue()
    }

    @Test
    fun `start may pick the primary model itself`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())
        delegate.beginComparisonSetup()

        delegate.startComparison("openAI", "gpt-5")

        assertThat(flow.value.comparisonState.isEnabled).isTrue()
        assertThat(flow.value.comparisonState.secondaryModel).isEqualTo("gpt-5")
    }

    @Test
    fun `cancel ends setup with comparison still off`() {
        val (delegate, flow) = delegateWith(landingWithPrimary())
        delegate.beginComparisonSetup()

        delegate.cancelComparisonSetup()

        assertThat(flow.value.comparisonState).isEqualTo(ComparisonState())
        assertThat(flow.value.screenState).isEqualTo(ChatScreenState.LANDING)
    }

    @Test
    fun `begin setup is a no-op while already comparing`() {
        val enabled = ComparisonState(isEnabled = true, secondaryEndpoint = "anthropic", secondaryModel = "claude")
        val (delegate, flow) = delegateWith(ChatUiState(comparisonState = enabled))

        delegate.beginComparisonSetup()

        assertThat(flow.value.comparisonState).isEqualTo(enabled)
    }

    @Test
    fun `cancel does not turn off an active comparison`() {
        val enabled = ComparisonState(isEnabled = true, secondaryEndpoint = "anthropic", secondaryModel = "claude")
        val (delegate, flow) = delegateWith(ChatUiState(comparisonState = enabled))

        delegate.cancelComparisonSetup()

        assertThat(flow.value.comparisonState).isEqualTo(enabled)
    }

    @Test
    fun `stop turns comparison off`() {
        val enabled = ComparisonState(isEnabled = true, secondaryEndpoint = "anthropic", secondaryModel = "claude")
        val (delegate, flow) = delegateWith(ChatUiState(comparisonState = enabled))

        delegate.stopComparison()

        assertThat(flow.value.comparisonState).isEqualTo(ComparisonState())
    }

    @Test
    fun `cancelling a branch mid-request writes no error`() = runTest {
        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.branchMessage(any(), any(), any()) } coAnswers { awaitCancellation() }
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                comparisonState = ComparisonState(parallelMessageId = "m-parallel"),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val delegate = ComparisonModeDelegate(
            handle = ComparisonHandle(ChatStateHandle(flow, scope)),
            messageRepository = messageRepository,
            reloadConversation = {},
            currentModelSpecs = { null },
        )

        delegate.branchFromComparison("agent_secondary____1")
        runCurrent()
        scope.cancel()
        advanceUntilIdle()

        assertThat(flow.value.error).isNull()
    }

    private fun keepHarness(
        scope: CoroutineScope,
        onReload: (MutableStateFlow<ChatUiState>) -> Unit,
    ): Pair<ComparisonModeDelegate, MutableStateFlow<ChatUiState>> {
        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.branchMessage(any(), any(), any()) } returns
            Result.Success(Message(messageId = "m-kept", conversationId = "c1"))
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                comparisonState = ComparisonState(isEnabled = true, parallelMessageId = "m-parallel"),
            ),
        )
        val delegate = ComparisonModeDelegate(
            handle = ComparisonHandle(ChatStateHandle(flow, scope)),
            messageRepository = messageRepository,
            reloadConversation = { onReload(flow) },
            currentModelSpecs = { null },
        )
        return delegate to flow
    }

    @Test
    fun `keep and continue continues the kept reply once it is shown`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val (delegate, flow) = keepHarness(scope) { flow ->
            val kept = MessageNode(Message(messageId = "m-kept", conversationId = "c1"), emptyList(), 0, 1)
            flow.value = flow.value.copy(content = flow.value.content.copy(displayMessages = listOf(kept)))
        }
        var continued = 0

        delegate.keepAndContinue("agent_secondary____1") {
            continued++
            flow.value = flow.value.copy(content = flow.value.content.copy(isStreaming = true))
        }
        advanceUntilIdle()

        assertThat(continued).isEqualTo(1)
        // The lane was kept first, so the Continue runs on a single-model chat.
        assertThat(flow.value.comparisonState.isEnabled).isFalse()
        // Moving a new chat now would replace this ViewModel under the Continue; its `created`
        // event moves it instead.
        assertThat(flow.value.pendingNavigationConversationId).isNull()
        scope.cancel()
    }

    @Test
    fun `keep and continue still moves a new chat when the continue never starts`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val (delegate, flow) = keepHarness(scope) { flow ->
            val kept = MessageNode(Message(messageId = "m-kept", conversationId = "c1"), emptyList(), 0, 1)
            flow.value = flow.value.copy(content = flow.value.content.copy(displayMessages = listOf(kept)))
        }

        // A Continue the send preflight refused.
        delegate.keepAndContinue("agent_secondary____1") {}
        advanceUntilIdle()

        assertThat(flow.value.pendingNavigationConversationId).isEqualTo("c1")
        scope.cancel()
    }

    @Test
    fun `a plain branch moves a new chat to its own screen`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val (delegate, flow) = keepHarness(scope) {}

        delegate.branchFromComparison("agent_secondary____1")
        advanceUntilIdle()

        assertThat(flow.value.comparisonState.isEnabled).isFalse()
        assertThat(flow.value.pendingNavigationConversationId).isEqualTo("c1")
        scope.cancel()
    }

    @Test
    fun `keep and continue gives up with a notice when the kept reply never shows`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val (delegate, flow) = keepHarness(scope) {}
        var continued = 0

        delegate.keepAndContinue("agent_secondary____1") { continued++ }
        advanceUntilIdle()

        assertThat(continued).isEqualTo(0)
        assertThat(flow.value.error).isEqualTo(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
        assertThat(flow.value.pendingNavigationConversationId).isEqualTo("c1")
        scope.cancel()
    }

    @Test
    fun `a pane's completion ends its call's sandbox boot`() {
        // `on_sandbox_starting` carries no agent and reaches the shared live-reply set; the
        // completion that ends the boot is consumed here, so the flag must come off here too.
        val (delegate, flow) = delegateWith(
            ChatUiState(
                comparisonState = ComparisonState(isEnabled = true),
                content = MessagesState(sandboxStartingToolCallIds = setOf("call_1", "call_2")),
            ),
        )

        delegate.routeEvent(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "ok"))
        delegate.routeEvent(StreamEvent.ToolCallClosed(toolCallId = "call_2", status = RunStepStatus.COMPLETED))

        assertThat(flow.value.sandboxStartingToolCallIds).isEmpty()
    }
}
