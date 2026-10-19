package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ported from upstream web: `getDefaultModelSpec` (`utils/endpoints.ts`) + `useNewConvo`'s apply gate. */
class SpecSeedTest {

    private fun config(
        vararg specs: ModelSpec,
        prioritize: Boolean? = null,
        modelSelect: Boolean = true,
    ) = StartupConfig(
        modelSpecs = ModelSpecs(list = specs.toList(), prioritize = prioritize),
        interfaceConfig = InterfaceConfig(modelSelect = modelSelect),
    )

    private fun agent(id: String, isDefault: Boolean = false, softDefault: Boolean = false) =
        ModelSpec(name = id, preset = Preset(agentId = id), isDefault = isDefault, softDefault = softDefault)

    private fun model(name: String, endpoint: String?, model: String?) =
        ModelSpec(name = name, preset = Preset(endpoint = endpoint, model = model))

    @Test
    fun no_config_or_no_specs_resolves_nothing() {
        assertNull(resolveDefaultModelSpec(null))
        assertNull(resolveDefaultModelSpec(StartupConfig()))
        assertNull(resolveDefaultModelSpec(config(prioritize = true)))
    }

    @Test
    fun a_default_spec_applies_only_when_prioritized() {
        val specs = arrayOf(agent("a"), agent("b", isDefault = true))
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "b", prioritized = true),
            resolveDefaultModelSpec(config(*specs, prioritize = true)),
        )
        assertNull(resolveDefaultModelSpec(config(*specs, prioritize = false)))
        assertNull(resolveDefaultModelSpec(config(*specs, prioritize = null)))
    }

    @Test
    fun without_a_default_the_first_spec_applies_when_prioritized() {
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "a", prioritized = true),
            resolveDefaultModelSpec(config(agent("a"), agent("b"), prioritize = true)),
        )
        assertNull(resolveDefaultModelSpec(config(agent("a"), agent("b"), prioritize = false)))
    }

    @Test
    fun the_menu_off_acts_as_prioritized() {
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "a", prioritized = true),
            resolveDefaultModelSpec(config(agent("a"), prioritize = false, modelSelect = false)),
        )
    }

    @Test
    fun a_soft_default_yields_unless_the_menu_is_off() {
        val specs = arrayOf(agent("a"), agent("soft", softDefault = true))
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = false),
            resolveDefaultModelSpec(config(*specs, prioritize = false)),
        )
        // prioritize does not upgrade a soft default: upstream checks the soft branch first.
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = false),
            resolveDefaultModelSpec(config(*specs, prioritize = true)),
        )
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = true),
            resolveDefaultModelSpec(config(*specs, modelSelect = false)),
        )
    }

    @Test
    fun a_hard_default_outranks_a_soft_one() {
        val specs = arrayOf(agent("soft", softDefault = true), agent("hard", isDefault = true))
        assertEquals("hard", resolveDefaultModelSpec(config(*specs, prioritize = true))?.model)
        assertNull(resolveDefaultModelSpec(config(*specs, prioritize = false)))
    }

    @Test
    fun an_endpoint_model_preset_maps_to_its_pair_and_an_incomplete_one_to_nothing() {
        assertEquals(
            SpecSeed("openAI", "gpt-4.1", prioritized = true),
            resolveDefaultModelSpec(config(model("fast", "openAI", "gpt-4.1"), prioritize = true)),
        )
        assertNull(resolveDefaultModelSpec(config(model("broken", "openAI", null), prioritize = true)))
        assertNull(resolveDefaultModelSpec(config(ModelSpec(name = "bare"), prioritize = true)))
    }

    @Test
    fun the_wire_fields_decode() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            StartupConfig.serializer(),
            """
            {"modelSpecs":{"prioritize":true,"list":[
              {"name":"claude","default":true,"preset":{"endpoint":"agents","agent_id":"agent_x"}},
              {"name":"soft","softDefault":true,"preset":{"endpoint":"openAI","model":"gpt-4o"}}
            ]}}
            """.trimIndent(),
        )
        val specs = decoded.modelSpecs!!
        assertEquals(true, specs.prioritize)
        assertEquals(true, specs.list[0].isDefault)
        assertEquals(true, specs.list[1].softDefault)
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "agent_x", prioritized = true),
            resolveDefaultModelSpec(decoded),
        )
    }
}
