package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelRefReconcileTest {

    private val raw = ModelRef(endpoint = "openAI", model = "gpt-5")
    private val coder = ModelRef(endpoint = "openAI", model = "gpt-5", spec = "coder", label = "Coder")
    private val writer = ModelRef(endpoint = "anthropic", model = "claude-sonnet-4-5", spec = "writer")

    @Test
    fun a_spec_the_server_no_longer_offers_is_dropped() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "coder", label = "Coder")))
        assertEquals(listOf(coder, raw), listOf(coder, writer, raw).reconciledWith(specs))
    }

    @Test
    fun a_kept_spec_takes_its_current_label() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "coder", label = "Code Helper")))
        assertEquals(listOf(coder.copy(label = "Code Helper")), listOf(coder).reconciledWith(specs))
    }

    @Test
    fun a_spec_without_a_label_takes_its_name() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "coder")))
        assertEquals(listOf(coder.copy(label = "coder")), listOf(coder).reconciledWith(specs))
    }

    @Test
    fun an_unknown_config_leaves_the_list_untouched() {
        val refs = listOf(coder, writer, raw)
        assertEquals(refs, refs.reconciledWith(null))
    }

    @Test
    fun a_config_with_no_specs_drops_every_spec_and_keeps_raw_models() {
        assertEquals(listOf(raw), listOf(coder, raw, writer).reconciledWith(ModelSpecs()))
    }

    @Test
    fun a_spec_recorded_under_two_models_keeps_its_higher_ranked_entry() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "coder", label = "Coder")))
        val older = coder.copy(model = "gpt-4o")
        assertEquals(listOf(coder, raw), listOf(coder, raw, older).reconciledWith(specs))
    }

    @Test
    fun a_loaded_config_without_model_specs_offers_none() {
        assertEquals(listOf(raw), listOf(coder, raw).reconciledWith(StartupConfig().offeredSpecs))
        assertEquals(listOf(coder, raw), listOf(coder, raw).reconciledWith((null as StartupConfig?).offeredSpecs))
    }
}
