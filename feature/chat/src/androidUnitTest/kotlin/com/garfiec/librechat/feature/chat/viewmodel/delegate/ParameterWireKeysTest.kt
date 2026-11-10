package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.util.parametersFor
import com.garfiec.librechat.feature.chat.util.reopenedParameters
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Every parameter reaches the server under the key its provider's schema keeps (#667). The server
 * unsets each stored field a request leaves out, so a value dropped or misspelled here is also erased
 * from what web set. Each case goes through the real path: spec preset or stored conversation →
 * `mergedFromPreset` → the chat's parameter target → `ModelParamPayload`.
 */
class ParameterWireKeysTest {

    private val groq = mapOf("Groq" to EndpointConfig(type = "custom"))

    private fun state(
        choice: ModelChoice,
        params: ModelParameters,
        specs: ModelSpecs? = null,
        configs: Map<String, EndpointConfig> = emptyMap(),
    ) = ChatUiState(
        selection = ModelSelectionState(choice = choice, modelSpecs = specs, modelParameters = params, endpointConfigs = configs),
    )

    private fun payload(state: ChatUiState): JsonObject = ChatRequestBuilder { state }.buildModelParams() ?: JsonObject(emptyMap())

    private fun specState(preset: Preset, configs: Map<String, EndpointConfig> = emptyMap()): ChatUiState {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "s", preset = preset)))
        return state(
            ModelChoice.Spec("s", preset.endpoint!!, preset.model!!),
            specs.parametersFor("s")!!,
            specs,
            configs,
        )
    }

    @Test
    fun aGoogleSpecsTopPIsSentAsTopP() {
        val sent = payload(specState(Preset(endpoint = "google", model = "gemini-2.5-pro", topPCamel = 0.5)))

        assertThat(sent["topP"]?.jsonPrimitive?.content).isEqualTo("0.5")
        assertThat(sent.keys).doesNotContain("top_p")
    }

    @Test
    fun aCustomEndpointSpecSendsTopPReasoningEffortAndMaxTokensSnakeCased() {
        val preset = Preset(
            endpoint = "Groq",
            model = "llama-3.3-70b",
            topPSnake = 0.5,
            reasoningEffort = "high",
            maxTokensSnake = 4000,
        )

        val sent = payload(specState(preset, groq))

        assertThat(sent["top_p"]?.jsonPrimitive?.content).isEqualTo("0.5")
        assertThat(sent["reasoning_effort"]?.jsonPrimitive?.content).isEqualTo("high")
        assertThat(sent["max_tokens"]?.jsonPrimitive?.content).isEqualTo("4000")
        assertThat(sent.keys).containsNoneOf("topP", "maxOutputTokens")
    }

    @Test
    fun aCustomEndpointTakesTheParameterSetItsDefaultParamsEndpointNames() {
        val configs = mapOf(
            "Claude Proxy" to EndpointConfig(type = "custom", customParams = CustomParams(defaultParamsEndpoint = "anthropic")),
        )
        val params = ModelParameters.DEFAULT.copy(topP = 0.4f, maxOutputTokens = 1000)

        val sent = payload(state(ModelChoice.Raw("Claude Proxy", "claude-sonnet-4-5"), params, configs = configs))

        assertThat(sent["topP"]?.jsonPrimitive?.content).isEqualTo("0.4")
        assertThat(sent["maxOutputTokens"]?.jsonPrimitive?.content).isEqualTo("1000")
        assertThat(sent.keys).containsNoneOf("top_p", "max_tokens")
    }

    @Test
    fun aBedrockSpecsMaxTokensAndEffortAreSent() {
        val preset = Preset(
            endpoint = "bedrock",
            model = "us.anthropic.claude-sonnet-4-5-20250929-v1:0",
            maxTokensCamel = 4000,
            effort = "high",
        )

        val sent = payload(specState(preset))

        assertThat(sent["maxTokens"]?.jsonPrimitive?.content).isEqualTo("4000")
        // `output_config.effort` on the server; it deletes `reasoning_effort` for Claude.
        assertThat(sent["effort"]?.jsonPrimitive?.content).isEqualTo("high")
        assertThat(sent.keys).doesNotContain("reasoning_effort")
    }

    @Test
    fun aSpecsReasoningModeIsSentAndRestoredOnReopen() {
        val preset = Preset(endpoint = "openAI", model = "gpt-5", reasoningMode = "pro", reasoningContext = "all_turns")

        val fromSpec = payload(specState(preset))
        assertThat(fromSpec["reasoning_mode"]?.jsonPrimitive?.content).isEqualTo("pro")
        assertThat(fromSpec["reasoning_context"]?.jsonPrimitive?.content).isEqualTo("all_turns")

        val stored =
            Conversation(endpoint = "openAI", model = "gpt-5", reasoningMode = "pro", reasoningContext = "all_turns")
        val selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-5"))
        val reopened = payload(state(selection.choice, selection.reopenedParameters(stored)))
        assertThat(reopened["reasoning_mode"]?.jsonPrimitive?.content).isEqualTo("pro")
        assertThat(reopened["reasoning_context"]?.jsonPrimitive?.content).isEqualTo("all_turns")
    }

    @Test
    fun anOpenAIChatReopenedWithMaxTokensKeepsItOnTheNextSend() {
        val stored = Json { ignoreUnknownKeys = true }.decodeFromString(
            Conversation.serializer(),
            """{"conversationId":"c","endpoint":"openAI","model":"gpt-4.1","max_tokens":5000,"top_p":0.3}""",
        )
        val selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4.1"))

        val sent = payload(state(selection.choice, selection.reopenedParameters(stored)))

        assertThat(sent["max_tokens"]?.jsonPrimitive?.content).isEqualTo("5000")
        assertThat(sent["top_p"]?.jsonPrimitive?.content).isEqualTo("0.3")
    }

    @Test
    fun bedrockSpecsSendTheSchemaKeysTheirPanelHides() {
        val mistral = payload(
            specState(Preset(endpoint = "bedrock", model = "mistral.mistral-large-2407-v1:0", stop = listOf("END"), topK = 40)),
        )
        val general = payload(
            specState(Preset(endpoint = "bedrock", model = "meta.llama3-70b-instruct-v1:0", stop = listOf("END"), maxTokensCamel = 900)),
        )
        val zai = payload(specState(Preset(endpoint = "bedrock", model = "zai.glm-4.6", stop = listOf("END", "STOP"))))

        assertThat(mistral["stop"].toString()).isEqualTo("""["END"]""")
        assertThat(mistral["topK"]?.jsonPrimitive?.content).isEqualTo("40")
        assertThat(general["stop"].toString()).isEqualTo("""["END"]""")
        assertThat(general["maxTokens"]?.jsonPrimitive?.content).isEqualTo("900")
        assertThat(zai["stop"].toString()).isEqualTo("""["END","STOP"]""")
    }

    // --- Save → load: the saved preset carries the provider's spelling, so web reads it back ---

    private val wire = Json { explicitNulls = false }

    private fun TestScope.saveThenLoad(
        choice: ModelChoice,
        configs: Map<String, EndpointConfig> = emptyMap(),
    ): Pair<JsonObject, ChatUiState> {
        var saved: Preset? = null
        val presetRepository = mockk<PresetRepository>()
        coEvery { presetRepository.create(any()) } answers {
            saved = firstArg<Preset>().copy(presetId = "p1")
            Result.Success(saved!!)
        }
        coEvery { presetRepository.getAll() } answers { Result.Success(listOfNotNull(saved)) }
        val tweaked = ModelParameters.DEFAULT.copy(topP = 0.4f, maxOutputTokens = 1234)
        val flow = MutableStateFlow(state(choice, tweaked, configs = configs))
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(
                ChatStateHandle(flow, CoroutineScope(StandardTestDispatcher(testScheduler) + Job())),
                persistDraft = {},
            ),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )
        delegate.savePreset("mine")
        advanceUntilIdle()
        val json = wire.encodeToJsonElement(Preset.serializer(), saved!!).jsonObject

        // Load it back onto a chat with the defaults: what the preset restores is what gets sent.
        flow.value = state(choice, ModelParameters.DEFAULT, configs = configs)
        delegate.loadPreset(PresetDisplayData(presetId = "p1", title = "mine", endpointLabel = null, model = null))
        return json to flow.value
    }

    private fun assertRoundTrip(choice: ModelChoice, topPKey: String, maxKey: String?, configs: Map<String, EndpointConfig> = emptyMap()) =
        runTest {
            val (json, loaded) = saveThenLoad(choice, configs)
            val spellings = setOf("top_p", "topP", "max_tokens", "maxTokens", "maxOutputTokens")

            assertThat(json.keys.intersect(spellings)).containsExactlyElementsIn(listOfNotNull(topPKey, maxKey))
            val sent = payload(loaded)
            assertThat(sent[topPKey]?.jsonPrimitive?.content).isEqualTo("0.4")
            if (maxKey != null) assertThat(sent[maxKey]?.jsonPrimitive?.content).isEqualTo("1234")
        }

    @Test
    fun openAISavesSnakeCase() = assertRoundTrip(ModelChoice.Raw("openAI", "gpt-4.1"), "top_p", "max_tokens")

    @Test
    fun anthropicSavesCamelCase() = assertRoundTrip(ModelChoice.Raw("anthropic", "claude-sonnet-4-5"), "topP", "maxOutputTokens")

    @Test
    fun googleSavesCamelCase() = assertRoundTrip(ModelChoice.Raw("google", "gemini-2.5-pro"), "topP", "maxOutputTokens")

    @Test
    fun bedrockSavesItsOwnMaxTokens() =
        assertRoundTrip(ModelChoice.Raw("bedrock", "anthropic.claude-sonnet-4-5-20250929-v1:0"), "topP", "maxTokens")

    @Test
    fun aCustomEndpointSavesSnakeCase() = assertRoundTrip(ModelChoice.Raw("Groq", "llama-3.3-70b"), "top_p", "max_tokens", groq)
}
