package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The provider lists hold the keys upstream `parameterSettings.ts` lists, no more and no fewer. A key
 * missing here is never sent and so is unset on the next send; a key upstream doesn't list is, on
 * Bedrock, forwarded to AWS by `bedrockInputParser`'s catch-all.
 */
class EndpointParameterRegistryListsTest {

    private fun keys(endpoint: String, model: String? = null, provider: String? = null) =
        EndpointParameterRegistry.getDefinitions(endpoint = endpoint, model = model, provider = provider).map { it.key }

    @Test
    fun openAIOffersReasoningModeAndContext() {
        val keys = keys("openAI")
        assertTrue("reasoning_mode" in keys)
        assertTrue("reasoning_context" in keys)
        val mode = EndpointParameterRegistry.getDefinitions("openAI").single { it.key == "reasoning_mode" }
        assertEquals(listOf("", "standard", "pro"), mode.options)
        val context = EndpointParameterRegistry.getDefinitions("openAI").single { it.key == "reasoning_context" }
        assertEquals(listOf("", "auto", "current_turn", "all_turns"), context.options)
    }

    @Test
    fun openRouterIsOpenAIsListPlusPromptCaching() {
        assertEquals(keys("openAI") + listOf("promptCache", "promptCacheTtl"), keys("openrouter"))
        // An agent on an OpenRouter-typed endpoint gets the same list.
        assertTrue("promptCacheTtl" in keys("agents", provider = "openrouter"))
    }

    @Test
    fun customTakesOpenAIsSnakeCaseKeys() {
        val keys = keys("custom")
        assertTrue("top_p" in keys && "max_tokens" in keys)
        assertTrue("topP" !in keys && "maxOutputTokens" !in keys)
    }

    @Test
    fun bedrockAnthropicUsesAnthropicsEffort() {
        val keys = keys("bedrock", model = "anthropic.claude-sonnet-4-5-20250929-v1:0")
        assertTrue("effort" in keys)
        assertTrue("reasoning_effort" !in keys)
        assertTrue("promptCacheTtl" in keys)
        assertTrue("imageDetail" !in keys && "tags" !in keys)
        assertTrue("stop" in keys)
    }

    @Test
    fun bedrockVariantsHoldOnlyUpstreamsKeys() {
        val mistral = keys("bedrock", model = "mistral.mistral-large-2407-v1:0")
        val cohere = keys("bedrock", model = "cohere.command-r-plus-v1:0")
        val general = keys("bedrock", model = "meta.llama3-70b-instruct-v1:0")
        val moonshot = keys("bedrock", model = "moonshot.kimi-k2-thinking")
        val zai = keys("bedrock", model = "zai.glm-4.6")
        listOf(mistral, cohere, general, moonshot, zai).forEach { list ->
            assertTrue("tags" !in list && "imageDetail" !in list, list.toString())
        }
        listOf(mistral, cohere, general, zai).forEach { assertTrue("stop" !in it, it.toString()) }
        assertTrue("promptCacheTtl" in general)
        assertTrue("stop" in moonshot && "reasoning_effort" in moonshot)
    }

    @Test
    fun bedrockSchemaKeysHiddenFromAPanelAreStillSent() {
        fun wireOnly(model: String) =
            EndpointParameterRegistry.wireOnlyDefinitions("bedrock", provider = null, model = model).map { it.key }.toSet()

        assertEquals(setOf("stop", "topK"), wireOnly("mistral.mistral-large-2407-v1:0"))
        assertEquals(setOf("stop", "topK"), wireOnly("cohere.command-r-plus-v1:0"))
        assertEquals(setOf("stop", "maxTokens", "topK"), wireOnly("meta.llama3-70b-instruct-v1:0"))
        assertEquals(setOf("stop", "maxTokens", "topK"), wireOnly("zai.glm-4.6"))
        assertEquals(setOf("topK"), wireOnly("moonshot.kimi-k2-thinking"))
        assertEquals(emptySet(), wireOnly("anthropic.claude-sonnet-4-5-20250929-v1:0"))
        assertEquals(
            setOf("stop", "topK"),
            EndpointParameterRegistry.wireOnlyDefinitions("agents", provider = "bedrock", model = "mistral.mistral-large-2407-v1:0")
                .map { it.key }.toSet(),
        )
        assertEquals(emptyList(), EndpointParameterRegistry.wireOnlyDefinitions("openAI", provider = null, model = "gpt-4.1"))
        assertEquals(
            setOf("topK"),
            EndpointParameterRegistry.wireOnlyDefinitions("bedrock", null, "mistral.mistral-large-2407-v1:0", dropParams = listOf("stop"))
                .map { it.key }.toSet(),
        )
    }

    @Test
    fun wireKeyForReadsTheWholeProviderList() {
        fun topP(endpoint: String, model: String? = null, provider: String? = null) =
            EndpointParameterRegistry.wireKeyFor(endpoint, provider, model, EndpointParameterRegistry.TOP_P_KEYS)
        fun maxTokens(endpoint: String, model: String? = null) =
            EndpointParameterRegistry.wireKeyFor(endpoint, null, model, EndpointParameterRegistry.MAX_OUTPUT_TOKENS_KEYS)

        assertEquals("top_p", topP("openAI"))
        assertEquals("top_p", topP("custom"))
        assertEquals("topP", topP("anthropic"))
        assertEquals("topP", topP("google"))
        assertEquals("topP", topP("agents", provider = "anthropic"))
        // Opus 5.5 hides Top P, but a preset saved on it keeps Anthropic's spelling.
        assertEquals("topP", topP("anthropic", model = "claude-opus-5-5"))
        assertEquals("max_tokens", maxTokens("openAI"))
        assertEquals("maxOutputTokens", maxTokens("anthropic"))
        assertEquals("maxTokens", maxTokens("bedrock", model = "anthropic.claude-sonnet-4-5-20250929-v1:0"))
        // Upstream's general Bedrock list has no max tokens control.
        assertNull(maxTokens("bedrock", model = "meta.llama3-70b-instruct-v1:0"))
    }
}
