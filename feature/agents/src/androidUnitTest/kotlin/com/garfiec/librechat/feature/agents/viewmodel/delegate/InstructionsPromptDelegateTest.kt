package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.EndpointLinkedInstructions
import com.garfiec.librechat.core.model.EndpointSwitch
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.response.PromptRevisionChoice
import com.garfiec.librechat.core.model.response.PromptRevisionChoices
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.GroupRevisions
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptLink
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptSelection
import com.garfiec.librechat.feature.agents.viewmodel.PromptLinkFailure
import com.garfiec.librechat.feature.agents.viewmodel.PromptLinkLoad
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstructionsPromptDelegateTest {

    private val prompts = mockk<PromptRepository>()

    private val native = PromptGroup(id = "g-native", name = "Native", author = "u", authorName = "U")
    private val langfuse = PromptGroup(id = "g-lf", name = "LF", author = "u", authorName = "U", source = "langfuse")
    private val lfRevisions =
        PromptRevisionChoices("langfuse", "g-lf", listOf(PromptRevisionChoice("langfuse", version = 2)))

    private suspend fun TestScope.editor(
        detected: DetectedBackend? = DetectedBackend("0.8.9", BackendBuildClass.OFFICIAL),
        langfuseEnabled: Boolean = true,
        block: suspend (InstructionsPromptDelegate, MutableStateFlow<AgentEditorUiState>) -> Unit,
    ) {
        val config = mockk<ConfigRepository>(relaxed = true)
        every { config.detectedBackend } returns MutableStateFlow(detected)
        every { config.endpointConfigs } returns MutableStateFlow(
            mapOf("agents" to EndpointConfig(linkedInstructions = EndpointLinkedInstructions(EndpointSwitch(langfuseEnabled)))),
        )
        coEvery { prompts.getAllGroups() } returns Result.Success(listOf(native, langfuse))
        coEvery { prompts.getRevisionChoices("g-lf") } returns Result.Success(lfRevisions)
        val flow = MutableStateFlow(AgentEditorUiState())
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val delegate = InstructionsPromptDelegate(AgentEditorStateHandle(flow, scope), config, prompts)
        delegate.start()
        try {
            block(delegate, flow)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the section opens on a v0_8_9 server whatever the Langfuse switch says`() = runTest {
        editor { _, state -> assertThat(state.value.isInstructionsLinkEditable).isTrue() }
        editor(langfuseEnabled = false) { _, state -> assertThat(state.value.isInstructionsLinkEditable).isTrue() }
        editor(detected = DetectedBackend("0.8.9-rc1", BackendBuildClass.RC)) { _, state ->
            assertThat(state.value.isInstructionsLinkEditable).isFalse()
        }
    }

    @Test
    fun `the library is read once Prompt mode shows, not before`() = runTest {
        editor { delegate, state ->
            coVerify(exactly = 0) { prompts.getAllGroups() }
            delegate.onSourceChanged(true)
            assertThat(state.value.promptGroups).isEqualTo(PromptLinkLoad.Loaded(listOf(native, langfuse)))
            delegate.onSourceChanged(false)
            delegate.onSourceChanged(true)
            coVerify(exactly = 1) { prompts.getAllGroups() }
        }
    }

    @Test
    fun `picking a Langfuse group links it at Production and reads its revisions`() = runTest {
        editor { delegate, state ->
            delegate.onSourceChanged(true)
            delegate.onGroupSelected("g-lf")
            assertThat(state.value.instructionsLink)
                .isEqualTo(InstructionsPromptLink("langfuse", "g-lf", InstructionsPromptSelection.Production))
            assertThat(state.value.promptRevisions).isEqualTo(PromptLinkLoad.Loaded(GroupRevisions("g-lf", lfRevisions)))

            delegate.onVersionSelected(InstructionsPromptSelection.ExactVersion(2))
            assertThat(state.value.instructionsLink?.selection).isEqualTo(InstructionsPromptSelection.ExactVersion(2))
            // A native-shaped pin on a Langfuse link is refused.
            delegate.onVersionSelected(InstructionsPromptSelection.ExactPrompt("p"))
            assertThat(state.value.instructionsLink?.selection).isEqualTo(InstructionsPromptSelection.ExactVersion(2))
            // Re-picking the linked group keeps its pin.
            delegate.onGroupSelected("g-lf")
            assertThat(state.value.instructionsLink?.selection).isEqualTo(InstructionsPromptSelection.ExactVersion(2))
        }
    }

    @Test
    fun `with the Langfuse switch off a Langfuse group can't be picked, a native one can`() = runTest {
        coEvery { prompts.getRevisionChoices("g-native") } returns
            Result.Success(PromptRevisionChoices("native", "g-native", emptyList()))
        editor(langfuseEnabled = false) { delegate, state ->
            delegate.onSourceChanged(true)
            delegate.onGroupSelected("g-lf")
            assertThat(state.value.instructionsLink).isNull()
            coVerify(exactly = 0) { prompts.getRevisionChoices("g-lf") }
            delegate.onGroupSelected("g-native")
            assertThat(state.value.instructionsLink)
                .isEqualTo(InstructionsPromptLink("native", "g-native", InstructionsPromptSelection.Production))
        }
    }

    @Test
    fun `a forbidden revisions read offers no retry, an unavailable source does`() = runTest {
        editor { delegate, state ->
            coEvery { prompts.getRevisionChoices("g-native") } returns Result.Error(ApiException(403, "no"))
            delegate.onSourceChanged(true)
            delegate.onGroupSelected("g-native")
            assertThat(state.value.promptRevisions).isEqualTo(PromptLinkLoad.Failed(PromptLinkFailure.FORBIDDEN))
            delegate.retryRevisions()
            coVerify(exactly = 1) { prompts.getRevisionChoices("g-native") }

            coEvery { prompts.getRevisionChoices("g-native") } returns
                Result.Error(ApiException(404, "x", body = """{"code":"not_available"}"""))
            state.value = state.value.copy(promptRevisions = PromptLinkLoad.Failed(PromptLinkFailure.SOURCE_UNAVAILABLE))
            delegate.retryRevisions()
            coVerify(exactly = 2) { prompts.getRevisionChoices("g-native") }
        }
    }
}
