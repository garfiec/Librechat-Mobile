package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ReasoningCapabilitiesRepository
import com.garfiec.librechat.core.data.repository.ReasoningCatalog
import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComposerSnapshot
import com.garfiec.librechat.feature.chat.viewmodel.ComposerState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedEditSession
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningCatalogState
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The staged Thinking choice follows the selection; an OpenRouter endpoint's efforts wait for its catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReasoningCatalogDelegateTest {

    private val high = ReasoningOverride.level("reasoning_effort", "high")

    private val openRouter = EndpointConfig(
        type = "custom",
        customParams = CustomParams(defaultParamsEndpoint = "openrouter", reasoningFormat = "reasoningEffort"),
    )

    /** Each call waits on its own answer, which the test completes through [answers]. */
    private class FakeRepository : ReasoningCapabilitiesRepository {
        val answers = mutableListOf<CompletableDeferred<Result<ReasoningCatalog>?>>()
        val calls: Int get() = answers.size

        override suspend fun catalog(endpoint: String): Result<ReasoningCatalog>? =
            CompletableDeferred<Result<ReasoningCatalog>?>().also { answers += it }.await()
    }

    private fun state(endpoint: String, model: String) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(endpoint, model),
            endpointConfigs = mapOf("OpenRouter" to openRouter, "openAI" to EndpointConfig()),
        ),
        gates = FeatureGatesState(backendVersion = "0.8.9-rc1"),
        composer = ComposerState(pendingReasoningOverride = high),
    )

    private fun delegateTest(
        initial: ChatUiState,
        body: suspend TestScope.(MutableStateFlow<ChatUiState>, FakeRepository) -> Unit,
    ) = runTest {
        val flow = MutableStateFlow(initial)
        val scope = TestScope(testScheduler)
        val repository = FakeRepository()
        ReasoningCatalogDelegate(ReasoningHandle(ChatStateHandle(flow, scope)), repository) { testScheduler.currentTime }
            .start()
        try {
            scope.runCurrent()
            body(scope, flow, repository)
        } finally {
            scope.cancel()
        }
    }

    private fun MutableStateFlow<ChatUiState>.select(endpoint: String, model: String) = update {
        it.copy(selection = it.selection.copy(choice = ModelChoice.Raw(endpoint, model)))
    }

    @Test
    fun `a switch to a model that takes no effort drops the choice`() = delegateTest(state("openAI", "gpt-5")) { flow, _ ->
        assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)
        flow.select("openAI", "gpt-4o")
        runCurrent()
        assertThat(flow.value.composer.pendingReasoningOverride).isNull()
    }

    /** Web's `Reasoning.tsx` clears a staged choice on any change of agent, endpoint or model. */
    @Test
    fun `a switch to another model drops the choice even when it takes the level`() =
        delegateTest(state("openAI", "gpt-5")) { flow, _ ->
            flow.select("openAI", "o3")
            runCurrent()
            assertThat(flow.value.composer.pendingReasoningOverride).isNull()
        }

    /** A queued edit swaps the model and its own staged choice in one emission. */
    @Test
    fun `a choice arriving with the new model stays`() = delegateTest(state("openAI", "gpt-5")) { flow, _ ->
        val low = ReasoningOverride.level("reasoning_effort", "low")
        flow.update {
            it.copy(
                selection = it.selection.copy(choice = ModelChoice.Raw("openAI", "o3")),
                composer = it.composer.copy(pendingReasoningOverride = low),
            )
        }
        runCurrent()
        assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(low)
    }

    /** Starting a queued edit brings the item's own choice, even one equal to the draft's. */
    @Test
    fun `a queued edit's choice equal to the draft's stays`() = delegateTest(state("openAI", "gpt-5")) { flow, _ ->
        val item = QueuedMessage(
            localId = "q1",
            text = "hi",
            choice = ModelChoice.Raw("openAI", "o3"),
            reasoningOverride = high,
            dispatch = EndpointDispatch(endpointType = null, key = null, modelDisplayLabel = null),
        )
        val stashed = ComposerSnapshot(text = "", choice = ModelChoice.Raw("openAI", "gpt-5"), reasoningOverride = high)
        flow.update {
            it.copy(
                selection = it.selection.copy(choice = item.choice),
                composer = it.composer.copy(editingQueuedItem = QueuedEditSession(item, 0, stashed)),
            )
        }
        runCurrent()
        assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)
    }

    @Test
    fun `the choice waits for the catalog and goes when it refuses the level`() =
        delegateTest(state("OpenRouter", "openai/gpt-5")) { flow, repository ->
            assertThat(repository.calls).isEqualTo(1)
            assertThat(flow.value.selection.reasoningCatalog).isEqualTo(ReasoningCatalogState("OpenRouter", null))
            // Unknown: the choice is neither confirmed nor refuted, so it stays.
            assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)

            repository.answers.last().complete(
                Result.Success(ReasoningCatalog(mapOf("openai/gpt-5" to ModelReasoning(listOf("low"))), expiresAtMs = 60_000)),
            )
            runCurrent()

            assertThat(flow.value.selection.reasoningCatalog?.models?.keys).containsExactly("openai/gpt-5")
            assertThat(flow.value.composer.pendingReasoningOverride).isNull()
        }

    @Test
    fun `a failed fetch stays unknown and is retried`() = delegateTest(state("OpenRouter", "openai/gpt-5")) { flow, repository ->
        repository.answers.last().complete(Result.Error(message = "503"))
        runCurrent()
        assertThat(flow.value.selection.reasoningCatalog?.models).isNull()
        assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)

        advanceTimeBy(2_001)
        runCurrent()
        assertThat(repository.calls).isEqualTo(2)
    }

    @Test
    fun `the catalog is refetched when the server's lifetime runs out`() =
        delegateTest(state("OpenRouter", "openai/gpt-5")) { flow, repository ->
            repository.answers.last().complete(
                Result.Success(ReasoningCatalog(mapOf("openai/gpt-5" to ModelReasoning(listOf("high"))), expiresAtMs = 10_000)),
            )
            runCurrent()
            assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)

            advanceTimeBy(10_001)
            runCurrent()
            assertThat(repository.calls).isEqualTo(2)
            assertThat(flow.value.selection.reasoningCatalog?.models).isNull()
        }

    @Test
    fun `a staged choice makes a mid-run send queue rather than steer`() {
        val steerable = state("openAI", "gpt-5").copy(
            conversation = ConversationMetaState(conversationId = "c1"),
            gates = FeatureGatesState(backendVersion = "0.8.9-rc1", steeringSupported = true),
            composer = ComposerState(),
        )
        assertThat(steerable.canSteerNow).isTrue()
        assertThat(steerable.copy(composer = ComposerState(pendingReasoningOverride = high)).canSteerNow).isFalse()
    }
}
