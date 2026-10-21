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
    private val helper = ModelSpec(name = "helper", preset = Preset(agentId = "agent_1"))
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
    fun offered_or_unchecked_keeps_a_spec_only_on_its_own_pair() {
        assertEquals("fast", "fast".offeredOrUnchecked("openAI", "gpt-4.1", specs, checkable = true).spec)
        assertEquals(
            ModelChoice.Raw("openAI", "gpt-4o"),
            "fast".offeredOrUnchecked("openAI", "gpt-4o", specs, checkable = true),
        )
    }

    @Test
    fun offered_or_unchecked_keeps_the_spec_while_there_is_no_config_to_check() {
        assertEquals("fast", "fast".offeredOrUnchecked("openAI", "gpt-4o", specs = null, checkable = false).spec)
    }

    @Test
    fun without_stale_drops_a_spec_retargeted_off_the_pair() {
        val stored = ModelChoice.restore("openAI", "gpt-4o", "fast")

        assertEquals(ModelChoice.Raw("openAI", "gpt-4o"), stored.withoutStale(specs))
        val current = ModelChoice.restore("openAI", "gpt-4.1", "fast")
        assertEquals(current, current.withoutStale(specs))
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
