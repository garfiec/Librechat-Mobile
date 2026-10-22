package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
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

    // The production entry point takes the two config fields it reads; this reads them as it does.
    private fun resolve(config: StartupConfig?, lastSpec: String? = null) =
        resolveDefaultModelSpec(config?.modelSpecs, config?.interfaceConfig?.modelSelect ?: true, lastSpec)

    private fun agent(id: String, isDefault: Boolean = false, softDefault: Boolean = false) =
        ModelSpec(name = id, preset = Preset(agentId = id), isDefault = isDefault, softDefault = softDefault)

    private fun model(name: String, endpoint: String?, model: String?) =
        ModelSpec(name = name, preset = Preset(endpoint = endpoint, model = model))

    @Test
    fun no_config_or_no_specs_resolves_nothing() {
        assertNull(resolve(null))
        assertNull(resolve(StartupConfig()))
        assertNull(resolve(config(prioritize = true)))
    }

    @Test
    fun a_default_spec_applies_only_when_prioritized() {
        val specs = arrayOf(agent("a"), agent("b", isDefault = true))
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "b", prioritized = true, spec = "b"),
            resolve(config(*specs, prioritize = true)),
        )
        assertNull(resolve(config(*specs, prioritize = false)))
        assertNull(resolve(config(*specs, prioritize = null)))
    }

    @Test
    fun without_a_default_the_first_spec_applies_when_prioritized() {
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "a", prioritized = true, spec = "a"),
            resolve(config(agent("a"), agent("b"), prioritize = true)),
        )
        assertNull(resolve(config(agent("a"), agent("b"), prioritize = false)))
    }

    @Test
    fun the_menu_off_acts_as_prioritized() {
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "a", prioritized = true, spec = "a"),
            resolve(config(agent("a"), prioritize = false, modelSelect = false)),
        )
    }

    @Test
    fun a_soft_default_yields_unless_the_menu_is_off() {
        val specs = arrayOf(agent("a"), agent("soft", softDefault = true))
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = false, spec = "soft"),
            resolve(config(*specs, prioritize = false)),
        )
        // prioritize does not upgrade a soft default: upstream checks the soft branch first.
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = false, spec = "soft"),
            resolve(config(*specs, prioritize = true)),
        )
        assertEquals(
            SpecSeed(EndpointConstants.AGENTS, "soft", prioritized = true, spec = "soft"),
            resolve(config(*specs, modelSelect = false)),
        )
    }

    @Test
    fun a_hard_default_outranks_a_soft_one() {
        val specs = arrayOf(agent("soft", softDefault = true), agent("hard", isDefault = true))
        assertEquals("hard", resolve(config(*specs, prioritize = true))?.model)
        assertNull(resolve(config(*specs, prioritize = false)))
    }

    @Test
    fun an_endpoint_model_preset_maps_to_its_pair_and_an_incomplete_one_to_nothing() {
        assertEquals(
            SpecSeed("openAI", "gpt-4.1", prioritized = true, spec = "fast"),
            resolve(config(model("fast", "openAI", "gpt-4.1"), prioritize = true)),
        )
        assertNull(resolve(config(model("broken", "openAI", null), prioritize = true)))
        assertNull(resolve(config(ModelSpec(name = "bare"), prioritize = true)))
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
            SpecSeed(EndpointConstants.AGENTS, "agent_x", prioritized = true, spec = "claude"),
            resolve(decoded),
        )
    }

    @Test
    fun specs_with_the_same_model_keep_distinct_names() {
        val first = model("first", "custom", "shared-model").toSeed()
        val second = model("second", "custom", "shared-model").toSeed()
        assertEquals(first?.model, second?.model)
        assertEquals("first", first?.spec)
        assertEquals("second", second?.spec)
    }

    @Test
    fun added_endpoints_limit_raw_rows_and_bare_enforce_hides_them_all() {
        assertEquals(false, ModelSpecs(addedEndpoints = listOf("agents")).showsRawEndpoint("custom", modelSelect = true))
        assertEquals(true, ModelSpecs(addedEndpoints = listOf("agents")).showsRawEndpoint("agents", modelSelect = true))
        assertEquals(true, ModelSpecs(enforce = true, addedEndpoints = listOf("agents")).showsRawEndpoint("agents", modelSelect = true))
        assertEquals(false, ModelSpecs(enforce = true).showsRawEndpoint("custom", modelSelect = true))
        assertEquals(true, ModelSpecs().showsRawEndpoint("custom", modelSelect = true))
        assertEquals(true, (null as ModelSpecs?).showsRawEndpoint("custom", modelSelect = true))
    }

    @Test
    fun model_select_off_hides_every_raw_row() {
        assertEquals(false, ModelSpecs().showsRawEndpoint("custom", modelSelect = false))
        assertEquals(false, ModelSpecs(addedEndpoints = listOf("agents")).showsRawEndpoint("agents", modelSelect = false))
        assertEquals(false, (null as ModelSpecs?).showsRawEndpoint("custom", modelSelect = false))
    }

    @Test
    fun enforce_requires_a_spec_even_for_added_endpoints() {
        val spec = model("fast", "openAI", "gpt-4.1")
        assertEquals(true, ModelSpecs(list = listOf(spec), enforce = true, addedEndpoints = listOf("agents")).requiresSpec)
        // The server checks the list too: enforce over an empty list enforces nothing.
        assertEquals(false, ModelSpecs(enforce = true).requiresSpec)
        assertEquals(false, ModelSpecs(list = listOf(spec)).requiresSpec)
        assertEquals(false, (null as ModelSpecs?).requiresSpec)
    }

    @Test
    fun last_spec_wins_unless_a_spec_is_marked_default() {
        val specs = arrayOf(model("first", "openAI", "a"), model("picked", "openAI", "b"))
        assertEquals("picked", resolve(config(*specs, prioritize = true), lastSpec = "picked")?.spec)
        // Upstream applies a stored spec whatever `prioritize` says.
        assertEquals("picked", resolve(config(*specs, prioritize = false), lastSpec = "picked")?.spec)
        val marked = arrayOf(model("first", "openAI", "a").copy(isDefault = true), model("picked", "openAI", "b"))
        assertEquals("first", resolve(config(*marked, prioritize = true), lastSpec = "picked")?.spec)
        assertNull(resolve(config(*marked, prioritize = false), lastSpec = "picked"))
    }

    @Test
    fun last_spec_beats_a_soft_default_but_equal_to_it_stays_soft() {
        val specs = arrayOf(agent("soft", softDefault = true), model("picked", "openAI", "b"))
        val last = resolve(config(*specs, prioritize = false), lastSpec = "picked")
        assertEquals("picked", last?.spec)
        assertEquals(true, last?.prioritized)
        val soft = resolve(config(*specs, prioritize = false), lastSpec = "soft")
        assertEquals("soft", soft?.spec)
        assertEquals(false, soft?.prioritized)
    }

    @Test
    fun a_last_spec_the_server_no_longer_lists_is_ignored() {
        val specs = arrayOf(model("first", "openAI", "a"), model("second", "openAI", "b"))
        assertEquals("first", resolve(config(*specs, prioritize = true), lastSpec = "gone")?.spec)
    }

    @Test
    fun a_stored_spec_is_offered_only_on_its_own_pair() {
        val specs = ModelSpecs(list = listOf(model("fast", "openAI", "gpt-4.1"), agent("agent_w")))
        assertEquals("fast", "fast".offeredOn("openAI", "gpt-4.1", specs))
        assertNull("fast".offeredOn("openAI", "gpt-4o", specs))
        assertNull("fast".offeredOn("azureOpenAI", "gpt-4.1", specs))
        assertEquals("agent_w", "agent_w".offeredOn(EndpointConstants.AGENTS, "agent_w", specs))
        assertNull("gone".offeredOn("openAI", "gpt-4.1", specs))
        assertNull(null.offeredOn("openAI", "gpt-4.1", specs))
    }

    @Test
    fun only_a_single_matching_spec_is_inferred() {
        val specs = ModelSpecs(
            list = listOf(model("fast", "openAI", "gpt-4.1"), model("careful", "openAI", "gpt-4.1"), agent("agent_w")),
        )
        assertEquals("agent_w", specs.uniqueSpecFor(EndpointConstants.AGENTS, "agent_w"))
        assertNull(specs.uniqueSpecFor("openAI", "gpt-4.1"))
        assertNull(specs.uniqueSpecFor("openAI", "gpt-4o"))
        assertNull((null as ModelSpecs?).uniqueSpecFor("openAI", "gpt-4.1"))
    }

    @Test
    fun visible_specs_sort_by_order_with_unordered_ones_after_in_list_order() {
        val specs = ModelSpecs(
            list = listOf(
                model("c", "openAI", "c"),
                model("b", "openAI", "b").copy(order = 2.0),
                model("d", "openAI", "d"),
                model("a", "openAI", "a").copy(order = 1.0),
            ),
        )
        assertEquals(listOf("a", "b", "c", "d"), specs.visibleSpecs(emptyList(), agentsLoaded = false).map { it.name })
    }

    @Test
    fun an_agent_spec_hides_only_once_the_agents_list_has_loaded_without_its_agent() {
        val specs = ModelSpecs(list = listOf(agent("agent_w"), agent("agent_x"), model("fast", "openAI", "gpt-4.1")))
        val loaded = listOf(Agent(id = "agent_w"))

        assertEquals(
            listOf("agent_w", "agent_x", "fast"),
            specs.visibleSpecs(emptyList(), agentsLoaded = false).map { it.name },
        )
        assertEquals(listOf("agent_w", "fast"), specs.visibleSpecs(loaded, agentsLoaded = true).map { it.name })
    }

    @Test
    fun hidden_and_unusable_specs_are_not_listed() {
        val specs = ModelSpecs(
            list = listOf(
                model("hidden", "openAI", "a").copy(showInMenu = false),
                model("no-model", "openAI", null),
                model("shown", "openAI", "b"),
            ),
        )
        assertEquals(listOf("shown"), specs.visibleSpecs(emptyList(), agentsLoaded = true).map { it.name })
    }

    @Test
    fun a_full_page_of_agents_hides_no_agent_spec() {
        val specs = ModelSpecs(list = listOf(agent("agent_past_the_page")))
        val fullPage = List(100) { Agent(id = "agent_$it") }

        assertEquals(listOf("agent_past_the_page"), specs.visibleSpecs(fullPage, agentsLoaded = true).map { it.name })
    }
}
