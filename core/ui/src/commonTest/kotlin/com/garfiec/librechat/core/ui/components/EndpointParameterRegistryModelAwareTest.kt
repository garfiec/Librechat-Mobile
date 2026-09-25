package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The model-aware and drop-params passes over the registry, both mirrored from upstream
 * `parameterSettings.ts`. Both change which controls the user is offered, and both are silent when
 * wrong — an out-of-range value is rejected at send, and a dropped parameter is accepted and
 * discarded — so neither shows up as a failure anywhere else.
 */
class EndpointParameterRegistryModelAwareTest {

    private fun google(model: String?) =
        EndpointParameterRegistry.getDefinitions(endpoint = "google", model = model)

    private fun anthropic(model: String?) =
        EndpointParameterRegistry.getDefinitions(endpoint = "anthropic", model = model)

    private fun keysOf(defs: List<com.garfiec.librechat.core.model.ParameterDefinition>) =
        defs.map { it.key }

    @Test
    fun geminiThinkingBudgetBoundsAreResolvedPerModel() {
        assertEquals(32768.0, google("gemini-2.5-pro").single { it.key == "thinkingBudget" }.max)
        assertEquals(24576.0, google("gemini-2.5-flash").single { it.key == "thinkingBudget" }.max)
        assertEquals(24576.0, google("gemini-2.5-flash-lite").single { it.key == "thinkingBudget" }.max)
    }

    @Test
    fun theThinkingBudgetFloorIsShownWithoutDisplacingTheAutomaticSentinel() {
        // -1 means "decide automatically" and is not subject to the positive floor, so `min` has to
        // stay -1 while the floor still reaches the reader.
        val flashLite = google("gemini-2.5-flash-lite-preview-09-2025").single { it.key == "thinkingBudget" }
        assertEquals(-1.0, flashLite.min)
        assertTrue(flashLite.description!!.contains("512-24576"), flashLite.description!!)

        val pro = google("gemini-2.5-pro-preview-05-06").single { it.key == "thinkingBudget" }
        assertTrue(pro.description!!.contains("128-32768"), pro.description!!)
    }

    @Test
    fun aModelWithNoDocumentedBoundsKeepsTheSharedDefinition() {
        val shared = google(null).single { it.key == "thinkingBudget" }
        assertEquals(shared.max, google("gemini-2.0-flash").single { it.key == "thinkingBudget" }.max)
        assertEquals(shared.description, google("gemini-2.0-flash").single { it.key == "thinkingBudget" }.description)
    }

    @Test
    fun promptCacheControlsAreWithheldFromModelsThatCannotCache() {
        assertTrue(keysOf(anthropic("claude-sonnet-4-5")).contains("promptCache"))
        assertTrue(keysOf(anthropic("claude-fable-5")).contains("promptCache"))

        // Upstream excludes this one spelling explicitly; its dated siblings do cache.
        val latest = keysOf(anthropic("claude-3-5-sonnet-latest"))
        assertFalse(latest.contains("promptCache"))
        assertFalse(latest.contains("promptCacheTtl"))
        assertTrue(keysOf(anthropic("claude-3-5-sonnet-20241022")).contains("promptCache"))
    }

    @Test
    fun anAnthropicAgentIsSubjectToTheSameModelRulesAsTheEndpoint() {
        val agent = EndpointParameterRegistry.getDefinitions(
            endpoint = "agents",
            provider = "anthropic",
            model = "claude-3-5-sonnet-latest",
        )
        assertFalse(keysOf(agent).contains("promptCache"))
    }

    @Test
    fun droppedParamsAreAliasedToUiKeysOnlyForOpenAiCompatibleSets() {
        // The admin names the backend field. OpenAI-compatible panels spell it `top_p`; Anthropic
        // spells its own control `topP`, so aliasing there would hide the wrong row.
        val openAi = EndpointParameterRegistry.getDefinitions(
            endpoint = "openAI",
            model = "gpt-4o",
            dropParams = listOf("topP", "maxTokens"),
        )
        assertFalse(keysOf(openAi).contains("top_p"))
        assertFalse(keysOf(openAi).contains("max_tokens"))

        val claude = anthropic("claude-sonnet-4-5")
        assertNotNull(claude.firstOrNull { it.key == "topP" })
        val claudeDropped = EndpointParameterRegistry.getDefinitions(
            endpoint = "anthropic",
            model = "claude-sonnet-4-5",
            dropParams = listOf("topP"),
        )
        assertNull(claudeDropped.firstOrNull { it.key == "topP" })
    }

    @Test
    fun anEmptyDropListLeavesTheRegistryUntouched() {
        assertEquals(
            keysOf(EndpointParameterRegistry.getDefinitions(endpoint = "openAI", model = "gpt-4o")),
            keysOf(
                EndpointParameterRegistry.getDefinitions(
                    endpoint = "openAI",
                    model = "gpt-4o",
                    dropParams = emptyList(),
                ),
            ),
        )
    }
}
