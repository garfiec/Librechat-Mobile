package com.garfiec.librechat.core.ui.components

import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cases ported from upstream `parameterSettings.spec.ts` (`resolveReasoningSetting*`, OpenRouter efforts). */
class ReasoningSettingResolverTest {

    private fun def(key: String, options: List<String>? = null, type: String? = null) = JsonObject(
        buildMap {
            put("key", JsonPrimitive(key))
            options?.let { put("options", JsonArray(it.map(::JsonPrimitive))) }
            type?.let { put("type", JsonPrimitive(it)) }
        },
    )

    private fun params(
        defaultParamsEndpoint: String? = null,
        reasoningFormat: String? = null,
        vararg definitions: JsonObject,
    ) = CustomParams(
        defaultParamsEndpoint = defaultParamsEndpoint,
        paramDefinitions = definitions.toList().ifEmpty { null },
        reasoningFormat = reasoningFormat,
    )

    private fun key(endpoint: String, model: String, customParams: CustomParams? = null) =
        ReasoningSettingResolver.resolve(endpoint, model, customParams)?.key

    @Test
    fun openAiReasoningModelsTakeEffortAndOthersNothing() {
        assertEquals("reasoning_effort", key("openAI", "gpt-5"))
        assertEquals("reasoning_effort", key("openAI", "o3-mini"))
        assertEquals("reasoning_effort", key("openAI", "gpt-oss-120b"))
        assertNull(key("openAI", "gpt-4o"))
    }

    @Test
    fun azureNeedsADeclaredDefinition() {
        assertNull(key("azureOpenAI", "administrator-named-deployment"))
        assertEquals(
            "reasoning_effort",
            key("azureOpenAI", "administrator-named-deployment", params(null, null, def("reasoning_effort"))),
        )
    }

    @Test
    fun customAndOpenRouterAreNotInferred() {
        assertNull(key("custom", "deployment-model"))
        assertNull(key("openrouter", "deployment-model"))
        assertEquals("reasoning_effort", key("custom", "deployment-model", params(reasoningFormat = "reasoningObject")))
        assertNull(key("custom", "deployment-model", params(reasoningFormat = "disabled")))
    }

    @Test
    fun claudeTakesEffortWhenAdaptiveAndABudgetWhenManual() {
        assertEquals("effort", key("anthropic", "claude-sonnet-4-6"))
        assertEquals("effort", key("anthropic", "claude-opus-5-5"))
        assertEquals("thinkingBudget", key("anthropic", "claude-sonnet-4-5"))
        assertEquals("thinkingBudget", key("anthropic", "claude-3-7-sonnet-latest"))
        assertNull(key("anthropic", "claude-3-5-haiku"))
        assertTrue(ReasoningSettingResolver.resolve("anthropic", "claude-sonnet-4-5")!!.isNumeric)
    }

    @Test
    fun geminiThreeTakesALevelAndTwoFiveABudget() {
        assertEquals("thinkingLevel", key("google", "gemini-3-pro"))
        assertEquals("thinkingBudget", key("google", "gemini-2.5-flash"))
        assertNull(key("google", "gemini-1.5-pro"))
    }

    @Test
    fun bedrockRoutesByModelProvider() {
        assertEquals("effort", key("bedrock", "us.anthropic.claude-sonnet-4-6-v1:0"))
        assertEquals("effort", key("bedrock", "anthropic.claude-opus-4-6-v1:0"))
        val moonshot = ReasoningSettingResolver.resolve("bedrock", "moonshot.kimi-k2")
        assertEquals("reasoning_effort", moonshot?.key)
        assertTrue(moonshot!!.unsetIsOff)
        assertNull(key("bedrock", "meta.llama3-70b"))
    }

    @Test
    fun customEndpointsFollowTheirDefaultParameterSet() {
        assertEquals("effort", key("ClaudeProxy", "claude-sonnet-4-6", params("anthropic")))
        assertEquals("effort", key("custom", "claude-sonnet-4-6", params("anthropic")))
    }

    @Test
    fun declaredOptionsAreNarrowedToWhatAnOverrideCarries() {
        fun declared(options: List<String>) =
            ReasoningSettingResolver.resolve("custom", "deployment-model", params(null, null, def("thinkingLevel", options)))
        assertEquals(listOf("", "low", "high"), declared(listOf("", "low", "ultra", "high"))?.options)
        assertNull(declared(listOf("ultra", "turbo")))
        assertEquals(
            listOf("low", "high"),
            ReasoningSettingResolver.resolve(
                "custom",
                "deployment-model",
                params(null, null, def("reasoning_effort", listOf("low", "high"))),
            )?.options,
        )
    }

    @Test
    fun aKeyAnEnforcedSpecSetsIsBlocked() {
        assertNull(ReasoningSettingResolver.resolve("openAI", "gpt-5", blockedKeys = setOf("reasoning_effort")))
        assertEquals("reasoning_effort", ReasoningSettingResolver.resolve("openAI", "gpt-5", blockedKeys = setOf("effort"))?.key)
    }

    @Test
    fun modelFamilyRulesNarrowTheEffort() {
        assertFalse("minimal" in ReasoningSettingResolver.resolve("openAI", "gpt-6-sol")!!.options!!)
        assertEquals(
            listOf("", "low", "medium", "high", "xhigh"),
            ReasoningSettingResolver.resolve("custom", "x-ai/grok-4.7", params(reasoningFormat = "reasoningEffort"))?.options,
        )
    }

    private val openRouter = params("openrouter", "reasoningEffort")

    private fun openRouterOptions(catalog: CatalogEfforts, customParams: CustomParams = openRouter) =
        ReasoningSettingResolver.resolve("custom", "openai/gpt-6.1-sol", customParams, catalog = catalog)?.options

    @Test
    fun openRouterCatalogNarrowsTheEffort() {
        assertEquals(listOf("", "low", "high"), openRouterOptions(CatalogEfforts.Listed(ModelReasoning(listOf("low", "high")))))
        assertEquals(
            listOf("", "low", "high"),
            openRouterOptions(CatalogEfforts.Listed(ModelReasoning(listOf("none", "low", "high"), mandatory = true))),
        )
        assertTrue("none" in openRouterOptions(CatalogEfforts.Listed(ModelReasoning(listOf("none", "low"))))!!)
        assertNull(openRouterOptions(CatalogEfforts.Listed(ModelReasoning(emptyList()))))
        assertNull(openRouterOptions(CatalogEfforts.Listed(ModelReasoning(listOf("ultra")))))
    }

    @Test
    fun anUnknownCatalogHidesTheEffortAndAnUnlistedModelKeepsTheGenericList() {
        assertNull(openRouterOptions(CatalogEfforts.Unknown))
        assertTrue("none" in openRouterOptions(CatalogEfforts.Generic)!!)
    }

    @Test
    fun anAdministratorDefinedEffortIsNeverNarrowed() {
        val declared = params("openrouter", null, def("reasoning_effort", listOf("low", "max")))
        assertEquals(listOf("low", "max"), openRouterOptions(CatalogEfforts.Listed(ModelReasoning(emptyList())), declared))
        assertEquals(listOf("low", "max"), openRouterOptions(CatalogEfforts.Unknown, declared))
    }

    @Test
    fun catalogLookupMatchesARoutingVariantToItsBaseModel() {
        val models = mapOf("openai/gpt-5" to ModelReasoning(listOf("low")))
        assertEquals(CatalogEfforts.Listed(ModelReasoning(listOf("low"))), CatalogEfforts.of(models, "openai/gpt-5:nitro"))
        assertEquals(CatalogEfforts.Generic, CatalogEfforts.of(models, "openai/other"))
        assertEquals(CatalogEfforts.Unknown, CatalogEfforts.of(null, "openai/gpt-5"))
    }

    @Test
    fun usesCatalogOnlyForAnUndeclaredOpenRouterEffort() {
        assertTrue(ReasoningSettingResolver.usesCatalog(openRouter))
        assertFalse(ReasoningSettingResolver.usesCatalog(params("openrouter", "disabled")))
        assertFalse(ReasoningSettingResolver.usesCatalog(params("openrouter", null, def("reasoning_effort"))))
        assertFalse(ReasoningSettingResolver.usesCatalog(params("anthropic")))
    }

    @Test
    fun overrideSupportFollowsKeyAndOptions() {
        val setting = ReasoningSettingResolver.resolve("openAI", "gpt-5")
        assertTrue(ReasoningSettingResolver.isSupported(ReasoningOverride.level("reasoning_effort", "high"), setting))
        assertFalse(ReasoningSettingResolver.isSupported(ReasoningOverride.level("effort", "high"), setting))
        assertFalse(ReasoningSettingResolver.isSupported(ReasoningOverride.level("reasoning_effort", "ultra"), setting))
        assertFalse(ReasoningSettingResolver.isSupported(ReasoningOverride.level("reasoning_effort", "high"), null))
        val budget = ReasoningSettingResolver.resolve("anthropic", "claude-sonnet-4-5")
        assertTrue(ReasoningSettingResolver.isSupported(ReasoningOverride("thinkingBudget", JsonPrimitive(4096)), budget))
    }
}
