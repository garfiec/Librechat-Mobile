package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewChatStartTest {

    private val prioritized = SpecSeed(endpoint = "openAI", model = "gpt-4.1", prioritized = true, spec = "marked")
    private val raw = ModelChoice.Raw("openAI", "gpt-4o")
    private val specs = ModelSpecs(prioritize = true)
    private val enforced = ModelSpecs(list = listOf(ModelSpec(name = "marked")), enforce = true)

    private fun demote(
        seed: SpecSeed? = prioritized,
        preferLastUsed: Boolean = true,
        modelSelect: Boolean = true,
        lastUsed: ModelChoice? = raw,
        modelSpecs: ModelSpecs? = specs,
    ) = seed.demotedForLastUsed(preferLastUsed, modelSelect, lastUsed, modelSpecs)

    @Test
    fun setting_on_demotes_a_prioritized_spec() {
        assertEquals(prioritized.copy(prioritized = false), demote())
    }

    @Test
    fun setting_off_leaves_the_spec_prioritized() {
        assertEquals(prioritized, demote(preferLastUsed = false))
    }

    @Test
    fun hidden_selector_leaves_the_spec_prioritized() {
        assertEquals(prioritized, demote(modelSelect = false))
    }

    @Test
    fun no_last_used_leaves_the_spec_prioritized() {
        assertEquals(prioritized, demote(lastUsed = null))
    }

    @Test
    fun enforce_with_a_raw_last_used_leaves_the_spec_prioritized() {
        assertEquals(prioritized, demote(modelSpecs = enforced))
    }

    @Test
    fun enforce_with_a_spec_last_used_demotes() {
        val lastSpec = ModelChoice.restore("openAI", "gpt-4o", "picked")
        assertEquals(prioritized.copy(prioritized = false), demote(lastUsed = lastSpec, modelSpecs = enforced))
    }

    @Test
    fun a_soft_or_missing_seed_is_unchanged() {
        val soft = prioritized.copy(prioritized = false)
        assertEquals(soft, demote(seed = soft))
        assertNull(demote(seed = null))
    }
}
