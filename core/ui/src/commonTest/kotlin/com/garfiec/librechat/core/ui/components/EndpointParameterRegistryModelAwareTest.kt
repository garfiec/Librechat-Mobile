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

    private fun effortOf(endpoint: String, model: String, extended: Boolean = true) =
        EndpointParameterRegistry.getDefinitions(
            endpoint = endpoint,
            model = model,
            extendedEffortSupported = extended,
        ).single { it.key == "reasoning_effort" }.options

    @Test
    fun opus55HidesThinkingAndSamplingControls() {
        val hidden = listOf("thinking", "thinkingBudget", "temperature", "topP", "topK")
        for (model in listOf("claude-opus-5-5", "claude-opus-5.5", "claude-5-5-opus", "global.anthropic.claude-opus-5-5")) {
            val keys = keysOf(anthropic(model))
            hidden.forEach { assertFalse(keys.contains(it), "$model still offers $it") }
            // Only those five; the rest of the Anthropic panel stays.
            assertTrue(keys.contains("maxOutputTokens"), model)
        }
        val bedrock = keysOf(
            EndpointParameterRegistry.getDefinitions(endpoint = "bedrock", model = "global.anthropic.claude-opus-5-5"),
        )
        hidden.forEach { assertFalse(bedrock.contains(it), "bedrock still offers $it") }
    }

    @Test
    fun otherOpusVersionsKeepTheirControls() {
        // A missing minor is 0, and a date suffix is not a minor.
        for (model in listOf("claude-opus-5", "claude-opus-5-20260101", "claude-opus-4-5", "claude-opus-5-55")) {
            assertTrue(keysOf(anthropic(model)).contains("temperature"), model)
        }
    }

    @Test
    fun gpt6SolAndLunaDropMinimalEffort() {
        for (model in listOf("gpt-6-sol", "gpt-6-luna", "GPT-6-Sol-2026-09-01")) {
            val options = effortOf("openAI", model)!!
            assertFalse(options.contains("minimal"), model)
            assertTrue(options.contains("none"), model)
        }
        assertTrue(effortOf("openAI", "gpt-6-astra")!!.contains("minimal"))
        assertTrue(effortOf("openAI", "gpt-6-solar")!!.contains("minimal"))
    }

    @Test
    fun grok47OffersItsOwnEffortLadder() {
        assertEquals(listOf("", "low", "medium", "high", "xhigh"), effortOf("custom", "grok-4.7"))
        assertEquals(listOf("", "low", "medium", "high", "xhigh"), effortOf("custom", "xai/grok-4-7:beta"))
        // The extended-effort filter still applies on a server that predates `xhigh`.
        assertEquals(listOf("", "low", "medium", "high"), effortOf("custom", "grok-4.7", extended = false))
        // Anchored at the start of the last segment, and a longer version is a different model.
        assertEquals(effortOf("custom", "gpt-4o"), effortOf("custom", "grok-4.70"))
        assertEquals(effortOf("custom", "gpt-4o"), effortOf("custom", "my-grok-4.7"))
    }
}
