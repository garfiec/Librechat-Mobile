package com.garfiec.librechat.feature.chat.tchat

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.TchatSettingsStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.TchatModelCatalogRepository
import com.garfiec.librechat.core.model.AgentTool
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TchatModelDelegateTest {

    private val catalog = MutableStateFlow<TchatModelCatalog?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(
        StartupConfig(
            modelSpecs = ModelSpecs(
                list = listOf(
                    ModelSpec(
                        name = "tensorgrid-default",
                        default = true,
                        preset = Preset(endpoint = "TensorGrid", model = "gpt-6-luna"),
                    ),
                ),
                imageList = ModelOwnersTest.IMAGE_SPECS,
            ),
        ),
    )
    private val storedImageGen = MutableStateFlow<String?>(null)
    private val settings = mockk<TchatSettingsStore>(relaxed = true) {
        every { imageGenSelection } returns storedImageGen
        coEvery { setImageGenSelection(any()) } answers { storedImageGen.value = firstArg() }
    }
    private val configRepository = mockk<ConfigRepository>(relaxed = true) {
        every { startupConfig } returns this@TchatModelDelegateTest.startupConfig
    }
    private val catalogRepository = mockk<TchatModelCatalogRepository>(relaxed = true) {
        every { catalog } returns this@TchatModelDelegateTest.catalog
    }
    private val agentRepository = mockk<AgentRepository>(relaxed = true) {
        coEvery { getAvailableTools() } returns Result.Success(listOf(AgentTool(pluginKey = IMAGE_GEN_TOOL_ID)))
    }
    private val selected = mutableListOf<Pair<String, String>>()
    private val repairs = mutableListOf<ImageModelRepair>()

    private fun TestScope.start(state: ChatUiState): Pair<MutableStateFlow<ChatUiState>, TchatModelDelegate> {
        val flow = MutableStateFlow(state)
        val handle = ModelSelectionHandle(ChatStateHandle(flow, backgroundScope))
        val delegate = TchatModelDelegate(
            handle = handle,
            configRepository = configRepository,
            catalogRepository = catalogRepository,
            agentRepository = agentRepository,
            tchatSettings = settings,
            selectModel = { endpoint, model ->
                selected += endpoint to model
                handle.update { selection = selection.copy(selectedEndpoint = endpoint, selectedModel = model) }
            },
            onRepaired = { repairs += it },
        )
        delegate.start()
        return flow to delegate
    }

    private fun chatOn(model: String, conversationId: String? = "convo-1") = ChatUiState(
        conversation = ConversationMetaState(conversationId = conversationId),
        selection = ModelSelectionState(
            selectedEndpoint = "TensorGrid",
            selectedModel = model,
            availableModels = mapOf("TensorGrid" to listOf("gpt-image-2-4k", "gpt-5.6-terra", "gpt-6-luna")),
        ),
    )

    @Test
    fun `a chat set to an image model moves to the default chat model and turns Image Gen on`() =
        runTest(UnconfinedTestDispatcher()) {
            val (state, _) = start(chatOn("gpt-image-2-4k"))
            // Nothing happens until the catalog has answered.
            assertTrue(selected.isEmpty())

            catalog.value = TchatModelCatalog()

            assertEquals(listOf("TensorGrid" to "gpt-6-luna"), selected)
            assertEquals(ImageGenSelection("gpt-image"), state.value.tchat.imageGen)
            assertEquals("gpt-image", storedImageGen.value)
            assertEquals(listOf(ImageModelRepair(fromModel = "gpt-image-2-4k", toModel = "gpt-6-luna")), repairs)
        }

    @Test
    fun `a new chat is repaired silently`() = runTest(UnconfinedTestDispatcher()) {
        start(chatOn("gpt-image-2-4k", conversationId = null))
        catalog.value = TchatModelCatalog()
        assertEquals(listOf("TensorGrid" to "gpt-6-luna"), selected)
        assertTrue(repairs.isEmpty())
    }

    @Test
    fun `a chat model is left alone`() = runTest(UnconfinedTestDispatcher()) {
        start(chatOn("gpt-5.6-terra"))
        catalog.value = TchatModelCatalog()
        assertTrue(selected.isEmpty())
        assertTrue(repairs.isEmpty())
    }

    @Test
    fun `without the image tool the chat model is still repaired but Image Gen stays off`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { agentRepository.getAvailableTools() } returns Result.Success(emptyList())
            val (state, _) = start(chatOn("gpt-image-2-4k"))
            catalog.value = TchatModelCatalog()
            assertEquals(listOf("TensorGrid" to "gpt-6-luna"), selected)
            assertNull(state.value.tchat.imageGen)
        }

    @Test
    fun `picking an Image Gen row toggles it, and the tools switch uses the default entry`() =
        runTest(UnconfinedTestDispatcher()) {
            val (state, delegate) = start(chatOn("gpt-5.6-terra"))

            delegate.onImageRowPicked("gemini-image")
            assertEquals(ImageGenSelection("gemini-image"), state.value.tchat.imageGen)
            assertEquals("gemini-image", storedImageGen.value)

            delegate.onImageRowPicked("gemini-image")
            assertNull(state.value.tchat.imageGen)
            assertNull(storedImageGen.value)

            delegate.toggleImageGen()
            assertEquals(ImageGenSelection(), state.value.tchat.imageGen)
            assertEquals(TchatSettingsStore.IMAGE_GEN_DEFAULT, storedImageGen.value)
            // The chat model never moved.
            assertTrue(selected.isEmpty())
        }

    @Test
    fun `the request carries image_gen as true for the default entry or the entry's name`() =
        runTest(UnconfinedTestDispatcher()) {
            val (state, delegate) = start(chatOn("gpt-5.6-terra"))
            val builder = ChatRequestBuilder { state.value }
            assertNull(builder.buildEphemeralAgent())

            delegate.toggleImageGen()
            assertEquals(JsonPrimitive(true), builder.buildEphemeralAgent()?.imageGen)

            delegate.onImageRowPicked("gemini-image")
            assertEquals(JsonPrimitive("gemini-image"), builder.buildEphemeralAgent()?.imageGen)

            // Not sent for an agent: the server ignores ephemeral tools there.
            state.value = state.value.copy(selection = state.value.selection.copy(selectedEndpoint = "agents"))
            assertNull(builder.buildEphemeralAgent())
        }
}
