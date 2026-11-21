package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelChoiceTest {

    private val fast = ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))
    private val helper = ModelSpec(name = "helper", preset = Preset(endpoint = "agents", agentId = "agent_1"))
    private val specs = ModelSpecs(list = listOf(fast, helper))

    @Test
    fun restore_pairs_a_spec_with_its_model() {
        assertEquals("fast", ModelChoice.restore("openAI", "gpt-4.1", "fast").spec)
    }

    @Test
    fun restore_without_a_model_is_raw_even_when_a_spec_is_named() {
        assertEquals(ModelChoice.Raw("openAI", null), ModelChoice.restore("openAI", null, "fast"))
    }

    @Test
    fun an_agent_spec_opens_on_its_agent() {
        val choice = helper.toSeed()!!.toChoice()

        assertEquals(EndpointConstants.AGENTS, choice.endpoint)
        assertEquals("agent_1", choice.model)
        assertEquals("helper", choice.spec)
    }

    @Test
    fun a_stored_spec_on_its_own_pair_is_kept() {
        assertEquals(ModelChoice.Spec("fast", "openAI", "gpt-4.1"), ModelChoice.stored("openAI", "gpt-4.1", "fast", specs))
    }

    @Test
    fun a_stored_spec_the_server_does_not_serve_is_kept_since_it_may_be_hidden() {
        assertEquals(ModelChoice.Spec("hidden", "openAI", "gpt-4o"), ModelChoice.stored("openAI", "gpt-4o", "hidden", specs))
        assertEquals(
            ModelChoice.Spec("hidden", "openAI", "gpt-4o"),
            ModelChoice.stored("openAI", "gpt-4o", "hidden", specs.copy(enforce = true)),
        )
    }

    @Test
    fun a_stored_spec_is_kept_while_there_is_no_config_to_check() {
        assertEquals("fast", ModelChoice.stored("openAI", "gpt-4o", "fast", specs = null).spec)
    }

    @Test
    fun a_stored_spec_served_on_another_endpoint_is_dropped() {
        assertEquals(ModelChoice.Raw("azureOpenAI", "gpt-4.1"), ModelChoice.stored("azureOpenAI", "gpt-4.1", "fast", specs))
        assertEquals(
            ModelChoice.Raw("azureOpenAI", "gpt-4.1"),
            ModelChoice.stored("azureOpenAI", "gpt-4.1", "fast", specs.copy(enforce = true)),
        )
    }

    @Test
    fun a_stored_spec_moved_to_another_model_on_its_endpoint_keeps_the_stored_model() {
        assertEquals(ModelChoice.Spec("fast", "openAI", "gpt-4o"), ModelChoice.stored("openAI", "gpt-4o", "fast", specs))
    }

    @Test
    fun under_enforce_a_stored_spec_moved_to_another_model_follows_it() {
        assertEquals(
            ModelChoice.Spec("fast", "openAI", "gpt-4.1"),
            ModelChoice.stored("openAI", "gpt-4o", "fast", specs.copy(enforce = true)),
        )
    }

    @Test
    fun stored_against_holds_a_spec_selection_and_leaves_a_raw_one() {
        assertEquals(ModelChoice.Raw("azureOpenAI", "gpt-4.1"), ModelChoice.Spec("fast", "azureOpenAI", "gpt-4.1").storedAgainst(specs))
        assertEquals(ModelChoice.Raw("openAI", "gpt-4o"), ModelChoice.Raw("openAI", "gpt-4o").storedAgainst(specs))
    }

    @Test
    fun seeded_against_keeps_a_spec_only_on_its_own_pair() {
        val current = ModelChoice.Spec("fast", "openAI", "gpt-4.1")
        assertEquals(current, current.seededAgainst(specs))
        assertEquals(ModelChoice.Raw("openAI", "gpt-4o"), ModelChoice.Spec("fast", "openAI", "gpt-4o").seededAgainst(specs))
        assertEquals(ModelChoice.Raw("openAI", "gpt-4o"), ModelChoice.Spec("gone", "openAI", "gpt-4o").seededAgainst(specs))
    }

    @Test
    fun inferred_for_adopts_the_one_spec_on_an_enforced_server() {
        val raw = ModelChoice.Raw("openAI", "gpt-4.1")
        val tier0 = "openAI" to "gpt-4.1"

        assertEquals("fast", raw.inferredFor(tier0, specs.copy(enforce = true)).spec)
        assertEquals(raw, raw.inferredFor(tier0, specs))
        assertEquals(raw, raw.inferredFor("openAI" to "gpt-4o", specs.copy(enforce = true)))
    }
}
