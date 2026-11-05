package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComposerState
import com.garfiec.librechat.feature.chat.viewmodel.DraftUndo
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.RemovedDraftItem
import com.garfiec.librechat.feature.chat.viewmodel.SendDispatchHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The live send takes the composer's chips only once it is committed, and counts usage for shortcuts. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendDispatchDelegateTest {

    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true) {
        every { attachedFiles } returns MutableStateFlow(emptyList())
    }
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val coderSpec = ModelSpec(
        name = "coder",
        label = "Coder",
        preset = Preset(endpoint = "openAI", model = "gpt-5"),
    )

    private fun state(streaming: Boolean) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw("anthropic", "claude-haiku-4-5"),
            modelSpecs = ModelSpecs(list = listOf(coderSpec)),
            endpointConfigs = mapOf(
                "anthropic" to EndpointConfig(),
                EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS)),
            ),
        ),
        composer = ComposerState(
            inputText = "hello",
            pendingQuotes = listOf("an excerpt"),
            pendingManualSkills = listOf("pdf-tools"),
        ),
        content = MessagesState(isStreaming = streaming),
    )

    private fun dispatchTest(
        streaming: Boolean,
        body: TestScope.(MutableStateFlow<ChatUiState>, SendDispatchDelegate, Recorder) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(state(streaming))
        val scope = TestScope(testScheduler)
        val recorder = Recorder()
        val delegate = SendDispatchDelegate(
            handle = SendDispatchHandle(ChatStateHandle(flow, scope)),
            chatRepository = mockk<ChatRepository>(relaxed = true),
            draftRepository = mockk<DraftRepository>(relaxed = true),
            settingsDataStore = settingsDataStore,
            fileHandler = fileHandler,
            requestBuilder = ChatRequestBuilder { flow.value },
            activeAccountProvider = mockk<ActiveAccountProvider>(relaxed = true),
            beginStreaming = { _, spec -> recorder.begun += spec },
            launchStream = {},
            onComparisonSendStart = {},
            buildAddedConvo = { _, _, _ -> null },
            turnSkillsOn = { recorder.skillsTurnedOn++ },
        )
        try {
            scope.body(flow, delegate, recorder)
        } finally {
            scope.cancel()
        }
    }

    private fun dispatch(endpoint: String) = EndpointDispatch(endpointType = endpoint, key = null, modelDisplayLabel = null)

    private class Recorder {
        val begun = mutableListOf<QueuedMessage>()
        var skillsTurnedOn = 0
    }

    @Test
    fun `a send the streaming guard refuses keeps the chips and the toggle as they were`() =
        dispatchTest(streaming = true) { flow, delegate, recorder ->
            delegate.sendNow("hello")

            assertThat(recorder.begun).isEmpty()
            assertThat(flow.value.pendingManualSkills).containsExactly("pdf-tools")
            assertThat(flow.value.pendingQuotes).containsExactly("an excerpt")
            assertThat(recorder.skillsTurnedOn).isEqualTo(0)
            assertThat(flow.value.error).isNull()
        }

    /**
     * The failed-upload hold sits in the upload gate only. A steer's spec (re-homed to the queue when
     * it can't be injected) and an MCP App's message are built here and must still be built.
     */
    @Test
    fun `a failed upload in the tray doesn't stop a spec from being built`() =
        dispatchTest(streaming = false) { _, delegate, _ ->
            val uploaded = AttachedFile(uri = "a", name = "a.pdf", fileId = "a", uploadProgress = 1f)
            val failed = AttachedFile(uri = "b", name = "b.pdf", uploadFailed = true)
            every { fileHandler.attachedFiles } returns MutableStateFlow(listOf(uploaded, failed))

            val spec = delegate.buildSendSpec("hello")

            assertThat(spec).isNotNull()
            assertThat(spec!!.attachments).containsExactly(uploaded)
        }

    @Test
    fun `restoring a skill puts it back at its index unless it is staged again`() =
        dispatchTest(streaming = false) { flow, delegate, _ ->
            delegate.restorePendingManualSkill(0, "translator", heldBack = false)
            delegate.restorePendingManualSkill(5, "pdf-tools", heldBack = false)

            assertThat(flow.value.pendingManualSkills).containsExactly("translator", "pdf-tools").inOrder()
        }

    @Test
    fun `a send clears a pending undo`() = dispatchTest(streaming = false) { flow, delegate, _ ->
        flow.value = flow.value.copy(
            composer = flow.value.composer.copy(
                pendingDraftUndo = DraftUndo(id = 1, removed = RemovedDraftItem.Quote("gone", 0)),
            ),
        )

        delegate.sendNow("hello")

        assertThat(flow.value.pendingDraftUndo).isNull()
    }

    @Test
    fun `a committed send takes the chips and turns Skills on`() =
        dispatchTest(streaming = false) { flow, delegate, recorder ->
            delegate.sendNow("hello")

            assertThat(recorder.begun.single().manualSkills).containsExactly("pdf-tools")
            assertThat(recorder.begun.single().quotes).containsExactly("an excerpt")
            assertThat(flow.value.pendingManualSkills).isEmpty()
            assertThat(flow.value.pendingQuotes).isEmpty()
            assertThat(recorder.skillsTurnedOn).isEqualTo(1)
        }

    @Test
    fun `a spec send counts under the spec with its label`() =
        dispatchTest(streaming = false) { _, delegate, _ ->
            val sent = delegate.doSendWithSpec(
                QueuedMessage(
                    localId = "q1",
                    text = "hi",
                    choice = ModelChoice.Spec("coder", "openAI", "gpt-5"),
                    dispatch = dispatch("openAI"),
                ),
            )

            assertThat(sent).isTrue()
            testScheduler.runCurrent()
            coVerify(exactly = 1) { settingsDataStore.incrementModelUsage("openAI", "gpt-5", "coder", "Coder") }
        }

    @Test
    fun `a bare agent send is not counted`() =
        dispatchTest(streaming = false) { _, delegate, _ ->
            val sent = delegate.doSendWithSpec(
                QueuedMessage(
                    localId = "q1",
                    text = "hi",
                    choice = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_abc"),
                    dispatch = dispatch(EndpointConstants.AGENTS),
                ),
            )

            assertThat(sent).isTrue()
            testScheduler.runCurrent()
            coVerify(exactly = 0) { settingsDataStore.incrementModelUsage(any(), any(), any(), any()) }
        }

    private fun withReasoning(flow: MutableStateFlow<ChatUiState>, model: String, override: ReasoningOverride) {
        flow.value = flow.value.copy(
            selection = flow.value.selection.copy(choice = ModelChoice.Raw("anthropic", model)),
            gates = FeatureGatesState(backendVersion = "0.8.9-rc1"),
            composer = flow.value.composer.copy(pendingReasoningOverride = override),
        )
    }

    @Test
    fun `a committed send takes the Thinking choice and clears it`() =
        dispatchTest(streaming = false) { flow, delegate, recorder ->
            val high = ReasoningOverride.level("effort", "high")
            withReasoning(flow, "claude-opus-4-6", high)

            delegate.sendNow("hello")

            assertThat(recorder.begun.single().reasoningOverride).isEqualTo(high)
            assertThat(flow.value.composer.pendingReasoningOverride).isNull()
        }

    @Test
    fun `a choice the selected model refuses is cleared and not sent`() =
        dispatchTest(streaming = false) { flow, delegate, recorder ->
            withReasoning(flow, "claude-haiku-4-5", ReasoningOverride.level("effort", "high"))

            delegate.sendNow("hello")

            assertThat(recorder.begun.single().reasoningOverride).isNull()
            assertThat(flow.value.composer.pendingReasoningOverride).isNull()
        }

    @Test
    fun `a refused send keeps the Thinking choice`() =
        dispatchTest(streaming = true) { flow, delegate, _ ->
            val high = ReasoningOverride.level("effort", "high")
            withReasoning(flow, "claude-opus-4-6", high)

            delegate.sendNow("hello")

            assertThat(flow.value.composer.pendingReasoningOverride).isEqualTo(high)
        }
}
