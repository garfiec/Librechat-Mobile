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
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComposerState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
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
}
