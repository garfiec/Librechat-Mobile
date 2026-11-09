package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PickerModelsTest {

    private fun spec(name: String, group: String? = null, endpoint: String = "openAI") =
        ModelSpec(name = name, label = name, preset = Preset(endpoint = endpoint, model = "gpt-4o"), group = group)

    private val configs = mapOf(
        "openAI" to EndpointConfig(modelDisplayLabel = "OpenAI"),
        "anthropic" to EndpointConfig(modelDisplayLabel = "Anthropic"),
    )

    private val open = PickerInputs(
        endpointConfigs = configs,
        availableModels = mapOf("openAI" to listOf("gpt-4o", "gpt-4o-mini"), "anthropic" to listOf("claude")),
        agents = listOf(Agent(id = "a1", name = "Helper")),
        agentsLoaded = true,
        modelSpecs = ModelSpecs(list = listOf(spec("General"))),
        favoriteModelKeys = setOf("anthropic::claude"),
        selectedEndpoint = "openAI",
        selectedModel = "gpt-4o-mini",
    )

    @Test
    fun everySectionIsListedFlatWithTheStarredFirst() {
        val models = pickerModelsOf(open, serverUrl = "https://chat.example")
        assertEquals(
            listOf("claude", "General", "Helper", "gpt-4o", "gpt-4o-mini"),
            models.map { it.title },
        )
        val byTitle = models.associateBy { it.title }
        assertEquals(HeaderLabel.Text("Anthropic"), byTitle.getValue("claude").group)
        assertEquals(null, byTitle.getValue("General").group)
        assertEquals(HeaderLabel.MyAgents, byTitle.getValue("Helper").group)
        assertEquals(ModelPick.Raw(EndpointConstants.AGENTS, "a1"), byTitle.getValue("Helper").pick)
        assertEquals(listOf("gpt-4o-mini"), models.filter { it.selected }.map { it.title })
        assertTrue(byTitle.getValue("claude").starred)
    }

    @Test
    fun underEnforceOnlyTheSpecsAreListed() {
        val inputs = open.copy(
            modelSpecs = ModelSpecs(
                list = listOf(spec("General"), spec("Fast", group = "openAI"), spec("Writer", group = "Writing")),
                enforce = true,
                addedEndpoints = listOf("openAI"),
            ),
        )
        val models = pickerModelsOf(inputs, serverUrl = "")
        assertEquals(listOf("General", "Fast", "Writer"), models.map { it.title })
        assertEquals(HeaderLabel.Text("OpenAI"), models[1].group)
        assertEquals(HeaderLabel.Text("Writing"), models[2].group)
    }

    @Test
    fun anEndpointWithoutItsKeyAndItsSpecsOpenTheKey() {
        val inputs = open.copy(
            modelSpecs = ModelSpecs(list = listOf(spec("Fast", group = "openAI"))),
            endpointKeyStates = mapOf("openAI" to KeyState.Unset),
        )
        val models = pickerModelsOf(inputs, serverUrl = "")
        val fast = models.single { it.title == "Fast" }
        assertEquals(ModelPick.AddKey("openAI"), fast.pick)
        assertEquals(KeyHint.NEEDS_KEY, fast.keyHint)
        assertTrue(models.none { it.title.startsWith("gpt") })
        val endpoint = models.single { it.title == "OpenAI" }
        assertEquals(ModelPick.AddKey("openAI"), endpoint.pick)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), endpoint.hiddenModels)
    }
}
