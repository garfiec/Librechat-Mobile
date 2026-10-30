package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.util.SpecTools
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A preset saved in a spec chat holds what the chat showed, and loaded into a spec chat shows and
 * sends the same. A preset has a field for the built-in search only, so it records that search, or
 * search off; LibreChat search on leaves the field out.
 */
class PresetWebSearchTest {

    private val fast = ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))
    private val specs = ModelSpecs(list = listOf(fast))
    private val onFast = ModelChoice.Spec("fast", "openAI", "gpt-4.1")

    /** A spec chat with web search added; the user's own (hidden) toggle is off. */
    private fun specChat(librechatReady: Boolean, added: Set<String> = setOf(ToolConstants.WEB_SEARCH)) = ChatUiState(
        selection = ModelSelectionState(
            choice = onFast,
            modelSpecs = specs,
            specTools = SpecTools("fast", added = added),
            modelParameters = ModelParameters.DEFAULT,
        ),
        gates = FeatureGatesState(librechatSearchReady = librechatReady),
    )

    private class Harness(val flow: MutableStateFlow<ChatUiState>, val delegate: PresetPromptDelegate)

    private fun TestScope.harness(state: ChatUiState, stored: List<Preset> = emptyList(), saved: (Preset) -> Unit = {}): Harness {
        val presetRepository = mockk<PresetRepository>()
        val created = slot<Preset>()
        coEvery { presetRepository.getAll() } returns Result.Success(stored)
        coEvery { presetRepository.create(capture(created)) } answers {
            saved(created.captured)
            Result.Success(created.captured)
        }
        val flow = MutableStateFlow(state)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(ChatStateHandle(flow, scope)),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )
        return Harness(flow, delegate)
    }

    private fun TestScope.save(state: ChatUiState): Preset {
        var preset: Preset? = null
        harness(state) { preset = it }.delegate.savePreset("mine")
        advanceUntilIdle()
        return checkNotNull(preset)
    }

    @Test
    fun `a spec chat running the built-in search saves it, though the user's own toggle is off`() = runTest {
        assertThat(save(specChat(librechatReady = false)).webSearch).isTrue()
    }

    @Test
    fun `a chat running LibreChat search saves no web_search, which has no preset field`() = runTest {
        assertThat(save(specChat(librechatReady = true)).webSearch).isNull()
    }

    @Test
    fun `a saved preset loaded into a spec chat shows and sends the built-in search again`() = runTest {
        val preset = save(specChat(librechatReady = false)).copy(presetId = "p1")

        val h = harness(specChat(librechatReady = false, added = emptySet()), stored = listOf(preset))
        h.delegate.loadPresets()
        advanceUntilIdle()
        h.delegate.loadPreset(PresetDisplayData(presetId = "p1", title = "mine", endpointLabel = null, model = null))
        val state = h.flow.value

        assertThat(state.selection.effectiveTools).contains(ToolConstants.WEB_SEARCH)
        assertThat(state.webSearchRoute).isEqualTo(WebSearchRoute.BUILT_IN)
        assertThat(ChatRequestBuilder { state }.builtInWebSearch()).isTrue()
        // The user's own toggle, kept for when they leave the spec, is untouched.
        assertThat(state.modelParameters.webSearch).isFalse()
    }

    @Test
    fun `a chat with web search off saves it off, and loading that preset turns search off`() = runTest {
        fun raw(on: Boolean) = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw("openAI", "gpt-4.1"),
                modelParameters = ModelParameters.DEFAULT.copy(webSearch = on),
            ),
            gates = FeatureGatesState(librechatSearchReady = true),
        )
        val preset = save(raw(on = false)).copy(presetId = "p3")
        assertThat(preset.webSearch).isFalse()

        val h = harness(raw(on = true), stored = listOf(preset))
        h.delegate.loadPresets()
        advanceUntilIdle()
        h.delegate.loadPreset(PresetDisplayData(presetId = "p3", title = "mine", endpointLabel = null, model = null))

        assertThat(h.flow.value.modelParameters.webSearch).isFalse()
        assertThat(h.flow.value.webSearchRoute).isEqualTo(WebSearchRoute.NONE)
    }

    @Test
    fun `a preset with no web_search leaves a raw chat's web search on`() = runTest {
        val preset = Preset(presetId = "p2", title = "plain", endpoint = "openAI", model = "gpt-4.1", temperature = 0.3)
        val raw = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw("openAI", "gpt-4.1"),
                modelParameters = ModelParameters.DEFAULT.copy(webSearch = true),
            ),
        )

        val h = harness(raw, stored = listOf(preset))
        h.delegate.loadPresets()
        advanceUntilIdle()
        h.delegate.loadPreset(PresetDisplayData(presetId = "p2", title = "plain", endpointLabel = null, model = null))

        assertThat(h.flow.value.modelParameters.webSearch).isTrue()
        assertThat(h.flow.value.modelParameters.temperature).isEqualTo(0.3f)
    }
}
