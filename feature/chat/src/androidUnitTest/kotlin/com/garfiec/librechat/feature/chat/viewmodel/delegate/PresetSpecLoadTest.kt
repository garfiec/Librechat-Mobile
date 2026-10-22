package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A preset's stored spec is kept only while the server offers it on the pair the preset opens on. */
class PresetSpecLoadTest {

    private val fast = ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))

    private fun TestScope.loadPreset(
        preset: Preset,
        specs: ModelSpecs?,
        state: ModelSelectionState = ModelSelectionState(choice = ModelChoice.Raw("anthropic", "claude-3")),
    ): ModelSelectionState {
        val presetRepository = mockk<PresetRepository>()
        coEvery { presetRepository.getAll() } returns Result.Success(listOf(preset))
        val flow = MutableStateFlow(ChatUiState(selection = state.copy(modelSpecs = specs)))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(ChatStateHandle(flow, scope)),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )
        delegate.loadPresets()
        advanceUntilIdle()
        delegate.loadPreset(PresetDisplayData(presetId = preset.presetId, title = "p", endpointLabel = null, model = null))
        return flow.value.selection
    }

    @Test
    fun `a preset naming only a spec opens on the spec's pair`() = runTest {
        val selection = loadPreset(Preset(presetId = "p1", spec = "fast"), ModelSpecs(list = listOf(fast)))

        assertThat(selection.selectedEndpoint).isEqualTo("openAI")
        assertThat(selection.selectedModel).isEqualTo("gpt-4.1")
        assertThat(selection.selectedSpec).isEqualTo("fast")
    }

    @Test
    fun `a spec the server retargeted is dropped and the preset's own pair kept`() = runTest {
        val preset = Preset(presetId = "p1", endpoint = "openAI", model = "gpt-4o", spec = "fast")

        val selection = loadPreset(preset, ModelSpecs(list = listOf(fast)))

        assertThat(selection.selectedModel).isEqualTo("gpt-4o")
        assertThat(selection.selectedSpec).isNull()
    }

    @Test
    fun `a spec the server no longer lists is dropped`() = runTest {
        val preset = Preset(presetId = "p1", endpoint = "openAI", model = "gpt-4.1", spec = "fast")

        val selection = loadPreset(preset, ModelSpecs(list = emptyList()))

        assertThat(selection.selectedSpec).isNull()
    }

    @Test
    fun `before the spec config loads the stored spec is kept unchecked`() = runTest {
        val preset = Preset(presetId = "p1", endpoint = "openAI", model = "gpt-4o", spec = "fast")

        val selection = loadPreset(preset, specs = null)

        assertThat(selection.selectedSpec).isEqualTo("fast")
    }

    @Test
    fun `before the spec config loads a spec with no model to pair it with is raw`() = runTest {
        val selection = loadPreset(
            Preset(presetId = "p1", spec = "fast"),
            specs = null,
            state = ModelSelectionState(choice = ModelChoice.Raw(EndpointConstants.AGENTS, null)),
        )

        assertThat(selection.choice).isEqualTo(ModelChoice.Raw(EndpointConstants.AGENTS, null))
    }

    @Test
    fun `a preset carrying only parameters keeps the selected spec`() = runTest {
        val spec = ModelChoice.Spec("fast", "openAI", "gpt-4.1")

        val selection = loadPreset(
            Preset(presetId = "p1"),
            ModelSpecs(list = listOf(fast), enforce = true),
            state = ModelSelectionState(choice = spec),
        )

        assertThat(selection.choice).isEqualTo(spec)
    }
}
