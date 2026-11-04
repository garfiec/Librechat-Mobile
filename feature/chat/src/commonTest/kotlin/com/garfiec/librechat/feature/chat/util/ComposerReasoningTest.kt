package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningCatalogState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ComposerReasoningTest {

    private val openRouter = EndpointConfig(
        type = "custom",
        customParams = CustomParams(defaultParamsEndpoint = "openrouter", reasoningFormat = "reasoningEffort"),
    )

    @Suppress("LongParameterList")
    private fun setting(
        version: String? = "0.8.9-rc1",
        parametersEnabled: Boolean = true,
        endpointSupportsIt: Boolean = true,
        comparing: Boolean = false,
        target: ReasoningTarget? = ReasoningTarget("openAI", "gpt-5"),
        configs: Map<String, EndpointConfig> = emptyMap(),
        catalog: ReasoningCatalogState? = null,
        preset: Preset? = null,
    ) = composerReasoningSetting(version, parametersEnabled, endpointSupportsIt, comparing, target, configs, catalog, preset)

    @Test
    fun resolvesForASupportedServerAndSelection() {
        assertEquals("reasoning_effort", setting()?.key)
    }

    @Test
    fun hiddenOnAnOlderOrUnknownServer() {
        assertNull(setting(version = "0.8.8"))
        assertNull(setting(version = null))
    }

    @Test
    fun hiddenWhenParametersAreOffOnAssistantsOrInAComparison() {
        assertNull(setting(parametersEnabled = false))
        assertNull(setting(endpointSupportsIt = false))
        assertNull(setting(comparing = true))
    }

    @Test
    fun hiddenWhenAnEnforcedSpecSetsTheKey() {
        assertNull(setting(preset = Preset(reasoningEffort = "high")))
        assertNotNull(setting(preset = Preset(effort = "high")))
    }

    @Test
    fun anAgentResolvesAgainstItsProviderAndModelAndWaitsForThem() {
        assertEquals(ReasoningTarget("anthropic", "claude-opus-4-6"), reasoningTarget("agents", "agent_1", "anthropic", "claude-opus-4-6"))
        assertNull(reasoningTarget("agents", "agent_1", null, null))
        assertEquals("effort", setting(target = ReasoningTarget("anthropic", "claude-opus-4-6"))?.key)
    }

    @Test
    fun anOpenRouterEndpointWaitsForItsCatalog() {
        val target = ReasoningTarget("OpenRouter", "openai/gpt-5")
        val configs = mapOf("OpenRouter" to openRouter)
        assertNull(setting(target = target, configs = configs))
        assertNull(setting(target = target, configs = configs, catalog = ReasoningCatalogState("OpenRouter", null)))
        // Another endpoint's catalog says nothing about this one.
        assertNull(setting(target = target, configs = configs, catalog = ReasoningCatalogState("Other", emptyMap())))
        val known = ReasoningCatalogState("OpenRouter", mapOf("openai/gpt-5" to ModelReasoning(listOf("low", "high"))))
        assertEquals(listOf("", "low", "high"), setting(target = target, configs = configs, catalog = known)?.options)
    }

    @Test
    fun aBudgetModelGetsNoChip() {
        val budget = setting(target = ReasoningTarget("anthropic", "claude-sonnet-4-5"))
        assertNotNull(budget)
        assertNull(thinkingChip(budget, configured = "", pending = null))
    }

    @Test
    fun theChipShowsThePendingChoiceOverTheChatsSetting() {
        val chip = thinkingChip(setting(), configured = "medium", pending = ReasoningOverride.level("reasoning_effort", "high"))!!
        assertEquals("high", chip.shown)
        assertEquals("medium", thinkingChip(setting(), configured = "medium", pending = null)!!.shown)
        // A choice staged for another key (the model changed under it) is not shown as this one's.
        assertNull(thinkingChip(setting(), configured = "", pending = ReasoningOverride.level("effort", "high"))!!.pending)
    }

    @Test
    fun pickingTheChatsSettingClearsTheChoice() {
        val chip = thinkingChip(setting(), configured = "medium", pending = null)!!
        assertNull(chip.overrideFor("medium"))
        assertEquals(ReasoningOverride.level("reasoning_effort", "low"), chip.overrideFor("low"))
    }
}
