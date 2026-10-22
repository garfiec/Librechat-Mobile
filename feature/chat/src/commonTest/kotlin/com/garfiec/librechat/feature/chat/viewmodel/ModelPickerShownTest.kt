package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelPickerShownTest {

    private fun state(modelSelect: Boolean, specs: ModelSpecs?) = ChatUiState(
        gates = FeatureGatesState(modelSelectEnabled = modelSelect),
        selection = ModelSelectionState(modelSpecs = specs),
    )

    @Test
    fun picker_hides_only_with_model_select_off_and_no_specs() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "fast")))

        assertEquals(true, state(modelSelect = true, specs = null).modelPickerShown)
        assertEquals(true, state(modelSelect = false, specs = specs).modelPickerShown)
        assertEquals(false, state(modelSelect = false, specs = null).modelPickerShown)
        assertEquals(false, state(modelSelect = false, specs = ModelSpecs()).modelPickerShown)
    }

    @Test
    fun the_tools_sheet_row_follows_the_picker() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "fast")))

        assertEquals(true, state(modelSelect = false, specs = specs).chatInputGates.modelPickerShown)
        assertEquals(false, state(modelSelect = false, specs = null).chatInputGates.modelPickerShown)
    }
}
