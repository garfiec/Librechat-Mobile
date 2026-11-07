package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewChatStartTest {

    private val prioritized = SpecSeed(endpoint = "openAI", model = "gpt-4.1", prioritized = true, spec = "marked")
    private val raw = ModelChoice.Raw("openAI", "gpt-4o")

    private fun demote(
        seed: SpecSeed? = prioritized,
        preferLastUsed: Boolean = true,
        modelSelect: Boolean = true,
        lastUsed: ModelChoice? = raw,
    ) = seed.demotedForLastUsed(preferLastUsed, modelSelect, lastUsed)

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
    fun a_spec_last_used_demotes() {
        val lastSpec = ModelChoice.restore("openAI", "gpt-4o", "picked")
        assertEquals(prioritized.copy(prioritized = false), demote(lastUsed = lastSpec))
    }

    @Test
    fun an_unprioritized_default_spec_opens_new_chats_as_on_web() {
        val marked =
            ModelSpec(name = "marked", preset = Preset(endpoint = "openAI", model = "gpt-4.1"), isDefault = true)
        val other = ModelSpec(name = "other", preset = Preset(endpoint = "openAI", model = "gpt-4o"))
        val specs = ModelSpecs(list = listOf(other, marked), prioritize = false)

        assertEquals(prioritized, (null as SpecSeed?).withUngatedDefault(specs))
        // An answer the resolver gave stands.
        val soft = prioritized.copy(spec = "soft", prioritized = false)
        assertEquals(soft, soft.withUngatedDefault(specs))
        assertNull((null as SpecSeed?).withUngatedDefault(ModelSpecs(list = listOf(other), prioritize = false)))
    }

    @Test
    fun a_soft_or_missing_seed_is_unchanged() {
        val soft = prioritized.copy(prioritized = false)
        assertEquals(soft, demote(seed = soft))
        assertNull(demote(seed = null))
    }

    private fun spec(name: String, model: String, isDefault: Boolean = false, softDefault: Boolean = false) =
        ModelSpec(name = name, preset = Preset(endpoint = "openAI", model = model), isDefault = isDefault, softDefault = softDefault)

    @Test
    fun a_raw_last_used_never_seeds_under_enforce() {
        val added =
            ModelSpecs(list = listOf(spec("fast", "gpt-4.1")), enforce = true, addedEndpoints = listOf("openAI"))

        assertNull(added.seedableLastUsed(LastUsedSelection("openAI", "gpt-4o", null)))
        assertEquals(raw, added.copy(enforce = false).seedableLastUsed(LastUsedSelection("openAI", "gpt-4o", null)))
    }

    @Test
    fun a_last_spec_seeds_under_enforce() {
        val enforcedFast = ModelSpecs(list = listOf(spec("fast", "gpt-4.1")), enforce = true)

        assertEquals(
            ModelChoice.Spec("fast", "openAI", "gpt-4.1"),
            enforcedFast.seedableLastUsed(LastUsedSelection("openAI", "gpt-4.1", "fast")),
        )
    }

    @Test
    fun without_enforce_only_a_soft_seed_is_a_fallback() {
        val soft = prioritized.copy(prioritized = false)
        val open = ModelSpecs(list = listOf(spec("fast", "gpt-4.1")))

        assertEquals(listOf(soft), open.fallbackSpecSeeds(soft))
        assertEquals(emptyList(), open.fallbackSpecSeeds(prioritized))
    }

    @Test
    fun under_enforce_the_fallbacks_are_the_soft_seed_then_the_default_then_list_order() {
        val soft = SpecSeed(endpoint = "openAI", model = "gpt-5", prioritized = false, spec = "soft")
        val specs = ModelSpecs(
            list = listOf(ModelSpec(name = "unpaired"), spec("first", "gpt-4o"), spec("marked", "gpt-4.1", isDefault = true)),
            enforce = true,
        )

        assertEquals(listOf("soft", "marked", "first"), specs.fallbackSpecSeeds(soft).map { it.spec })
    }
}
