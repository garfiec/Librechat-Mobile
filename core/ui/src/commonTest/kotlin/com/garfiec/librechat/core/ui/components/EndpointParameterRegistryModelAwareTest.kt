package com.garfiec.librechat.core.ui.components

import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.param_desc_thinking
import com.garfiec.librechat.core.ui.resources.param_thinking_between_tools_description
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

    private fun google(model: String?, thinkingLevelXhigh: Boolean = true) =
        EndpointParameterRegistry.getDefinitions(endpoint = "google", model = model, thinkingLevelXhighSupported = thinkingLevelXhigh)

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
    fun geminiHydrogenDropsBudgetAndSamplingAndGetsItsOwnLevelsAndOutputCeiling() {
        for (model in listOf("gemini-4-hydrogen", "Gemini-4-Hydrogen-preview-11-2026", "google/gemini-4-hydrogen")) {
            val defs = google(model)
            listOf("thinkingBudget", "temperature", "topP", "topK").forEach {
                assertFalse(keysOf(defs).contains(it), "$model still offers $it")
            }
            assertEquals(listOf("", "low", "medium", "high", "xhigh"), defs.single { it.key == "thinkingLevel" }.options)
            val maxOutput = defs.single { it.key == "maxOutputTokens" }
            assertEquals(65536.0, maxOutput.max)
            assertEquals("65536", maxOutput.default)
            assertTrue(keysOf(defs).contains("thinking"), model)
        }
        // A server before ThinkingLevel.xhigh: Google's shared levels, the model-keyed drops stay.
        val older = google("gemini-4-hydrogen", thinkingLevelXhigh = false)
        assertEquals(listOf("", "minimal", "low", "medium", "high"), older.single { it.key == "thinkingLevel" }.options)
        assertFalse(keysOf(older).contains("thinkingBudget"))
        // Not a Hydrogen id: the shared Google panel stays.
        val argon = google("gemini-4-argon")
        assertTrue(keysOf(argon).contains("temperature"))
        assertEquals(listOf("", "minimal", "low", "medium", "high"), argon.single { it.key == "thinkingLevel" }.options)
        assertTrue(keysOf(google("gemini-4-hydrogenic")).contains("temperature"))
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
    fun alwaysOnThinkingModelsHideThinkingAndSamplingControls() {
        val hidden = listOf("thinking", "thinkingBudget", "temperature", "topP", "topK")
        for (
            model in listOf(
                "claude-opus-5-5",
                "claude-opus-5.5",
                "claude-5-5-opus",
                "global.anthropic.claude-opus-5-5",
                "claude-opus-5-6",
                "claude-opus-6",
                "claude-fable-5",
                "claude-mythos-5",
            )
        ) {
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
        for (model in listOf("claude-opus-5", "claude-opus-5-20260101", "claude-opus-4-5", "claude-opus-5-4")) {
            assertTrue(keysOf(anthropic(model)).contains("temperature"), model)
        }
    }

    @Test
    fun sonnet55KeepsTheThinkingToggleAsItsBetweenToolsFloor() {
        for (model in listOf("claude-sonnet-5-5", "claude-sonnet-5.5", "claude-5-5-sonnet", "claude-sonnet-6")) {
            val defs = anthropic(model)
            val keys = keysOf(defs)
            assertTrue(keys.contains("thinking"), model)
            listOf("thinkingBudget", "temperature", "topP", "topK").forEach {
                assertFalse(keys.contains(it), "$model still offers $it")
            }
            val thinking = defs.single { it.key == "thinking" }
            assertTrue(thinking.description.orEmpty().contains("between tool calls"), model)
            // Rendered through localizedDescription, so it must resolve to the translated string.
            assertEquals(Res.string.param_thinking_between_tools_description, thinking.descriptionRes(), model)
        }
        for (model in listOf("claude-sonnet-5", "claude-sonnet-5-20260101", "claude-sonnet-4-6")) {
            assertTrue(keysOf(anthropic(model)).contains("thinkingBudget"), model)
            assertEquals(
                Res.string.param_desc_thinking,
                anthropic(model).single { it.key == "thinking" }.descriptionRes(),
                model,
            )
        }
    }

    @Test
    fun haiku55KeepsTheThinkingToggleButHidesBudgetAndSampling() {
        val haikus = listOf(
            "claude-haiku-5-5", "claude-haiku-5.5", "claude-5-5-haiku", "global.anthropic.claude-haiku-5-5", "claude-haiku-6",
        )
        for (model in haikus) {
            val defs = anthropic(model)
            val keys = keysOf(defs)
            assertTrue(keys.contains("thinking"), model)
            listOf("thinkingBudget", "temperature", "topP", "topK").forEach {
                assertFalse(keys.contains(it), "$model still offers $it")
            }
            // Unlike Sonnet 5.5, "off" really is off: the ordinary description stays.
            assertEquals(Res.string.param_desc_thinking, defs.single { it.key == "thinking" }.descriptionRes(), model)
        }
        for (model in listOf("claude-haiku-4-5", "claude-haiku-4-5-20251001", "claude-haiku-5", "claude-haiku-5-4")) {
            assertTrue(keysOf(anthropic(model)).contains("temperature"), model)
        }
    }

    @Test
    fun thinkingDisplayOffersUpdatesOnBothAnthropicSitesOnlyWhenSupported() {
        fun options(endpoint: String, provider: String?, model: String, supported: Boolean) =
            EndpointParameterRegistry.getDefinitions(
                endpoint = endpoint,
                extendedEffortSupported = true,
                provider = provider,
                model = model,
                thinkingDisplayUpdatesSupported = supported,
            ).single { it.key == "thinkingDisplay" }.options
        val sites = listOf(
            Triple("anthropic", null, "claude-opus-4-7"),
            Triple("bedrock", null, "global.anthropic.claude-opus-4-7"),
            Triple("agents", "anthropic", "claude-opus-4-7"),
        )
        for ((endpoint, provider, model) in sites) {
            assertEquals(
                listOf("auto", "summarized", "omitted", "updates"),
                options(endpoint, provider, model, supported = true),
                endpoint,
            )
            assertEquals(listOf("auto", "summarized", "omitted"), options(endpoint, provider, model, supported = false), endpoint)
        }
    }

    @Test
    fun thinkingDisplayGateIsNotAModelRemoval() {
        // A stored `updates` survives the gate: it is filtered from the options, never dropped.
        assertTrue(
            EndpointParameterRegistry.modelRemovedOptions(endpoint = "anthropic", model = "claude-opus-4-7")
                .values.flatten().none { it == "updates" },
        )
    }

    @Test
    fun gpt6SolTerraAndLunaDropMinimalEffort() {
        val models = listOf(
            "gpt-6-sol", "gpt-6-luna", "GPT-6-Sol-2026-09-01", "gpt-6.1-sol", "gpt-6.1-luna-2026-10-01",
            "gpt-6-terra", "gpt-6.1-terra-2026-11-01",
        )
        for (model in models) {
            val options = effortOf("openAI", model)!!
            assertFalse(options.contains("minimal"), model)
            assertTrue(options.contains("none"), model)
        }
        assertTrue(effortOf("openAI", "gpt-6-astra")!!.contains("minimal"))
        assertTrue(effortOf("openAI", "gpt-6-solar")!!.contains("minimal"))
        assertTrue(effortOf("openAI", "gpt-6.1-astra")!!.contains("minimal"))
    }

    @Test
    fun gpt6CyberOnOpenAiTakesNoReasoningKeysAndCapsMaxTokens() {
        val reasoningKeys = setOf("reasoning_effort", "reasoning_summary", "reasoning_mode", "reasoning_context")
        val cyber = EndpointParameterRegistry.getDefinitions(endpoint = "openAI", model = "gpt-6-cyber", extendedEffortSupported = true)
        assertTrue(cyber.none { it.key in reasoningKeys })
        val maxTokens = cyber.single { it.key == "max_tokens" }
        assertEquals(1.0, maxTokens.min)
        assertEquals(128_000.0, maxTokens.max)
        assertEquals(1.0, maxTokens.step)
        // An agent on openAI reads the same rule; another endpoint or spelling keeps its reasoning.
        val agent = EndpointParameterRegistry.getDefinitions(endpoint = "agents", provider = "openAI", model = "gpt-6-cyber")
        assertTrue(agent.none { it.key in reasoningKeys })
        assertTrue(effortOf("azureOpenAI", "gpt-6-cyber") != null)
        assertTrue(effortOf("openAI", "GPT-6-Cyber") != null)
    }

    @Test
    fun numericInputIsClampedToTheDefinitionRange() {
        val maxTokens = EndpointParameterRegistry.getDefinitions(endpoint = "openAI", model = "gpt-6-cyber")
            .single { it.key == "max_tokens" }
        assertEquals("128000", clampNumericText("200000", maxTokens.min, maxTokens.max))
        assertEquals("1", clampNumericText("0", maxTokens.min, maxTokens.max))
        assertEquals("64000", clampNumericText("64000", maxTokens.min, maxTokens.max))
        // Blank, an in-progress sign and non-numbers are left for the field to keep.
        assertEquals("", clampNumericText("", maxTokens.min, maxTokens.max))
        assertEquals("-", clampNumericText("-", -1.0, 32_000.0))
        assertEquals("Auto", clampNumericText("Auto", 1024.0, 200_000.0))
        // Google's -1 (dynamic) is the floor itself, not lifted.
        assertEquals("-1", clampNumericText("-1", -1.0, 32_000.0))
        assertEquals("-1", clampNumericText("-5", -1.0, 32_000.0))
        // No range: nothing to enforce.
        assertEquals("999999", clampNumericText("999999", null, null))
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

    /**
     * What a stored value may be dropped for: only what a per-model rule took away. A value missing
     * from the options for any other reason — a version gate, or a newer backend's addition — is
     * the user's, and dropping it would delete it from the server on an agent save.
     */
    @Test
    fun modelRemovedOptionsNamesOnlyWhatAPerModelRuleTookAway() {
        assertEquals(
            mapOf("reasoning_effort" to setOf("minimal")),
            EndpointParameterRegistry.modelRemovedOptions(endpoint = "openAI", model = "gpt-6-sol"),
        )
        assertEquals(
            mapOf("reasoning_effort" to setOf("minimal")),
            EndpointParameterRegistry.modelRemovedOptions(endpoint = "agents", provider = "openAI", model = "gpt-6-luna"),
        )
        assertEquals(
            setOf("none", "minimal"),
            EndpointParameterRegistry.modelRemovedOptions(endpoint = "custom", model = "grok-4.7")["reasoning_effort"],
        )
    }

    @Test
    fun modelRemovedOptionsNeverNamesAVersionGatedValue() {
        // `xhigh`/`max` are filtered from the options while the server version is undetected; that
        // is a gate, not the model, so they are never "removed".
        for ((endpoint, model) in listOf("anthropic" to "claude-opus-4-5", "openAI" to "gpt-6-sol", "custom" to "grok-4.7")) {
            val removed = EndpointParameterRegistry.modelRemovedOptions(endpoint = endpoint, model = model).values.flatten()
            assertFalse(removed.contains("xhigh"), "$endpoint/$model")
            assertFalse(removed.contains("max"), "$endpoint/$model")
        }
        // A model with no per-model rule removes nothing.
        assertTrue(EndpointParameterRegistry.modelRemovedOptions(endpoint = "openAI", model = "gpt-4o").isEmpty())
    }
}
