package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningCatalogState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        enforcedSpecUnserved: Boolean = false,
        bareBedrockClaude: Boolean = false,
        thinkingLevelXhigh: Boolean = false,
    ) = composerReasoningSetting(
        version, parametersEnabled, endpointSupportsIt, comparing, target, configs, catalog, preset, enforcedSpecUnserved,
        bareBedrockClaude, thinkingLevelXhigh,
    )

    @Test
    fun anAzureDeploymentOffersTheEffortsItDeclares() {
        val azure = EndpointConfig(modelReasoningEfforts = mapOf("my-gpt" to listOf("low", "high", "")))
        val configs = mapOf("azureOpenAI" to azure)
        val deployed = setting(target = ReasoningTarget("azureOpenAI", "my-gpt"), configs = configs)
        assertEquals("reasoning_effort", deployed?.key)
        assertEquals(listOf("", "low", "high"), deployed?.options)
        // A deployment that declares none gets no control.
        assertNull(setting(target = ReasoningTarget("azureOpenAI", "other"), configs = configs))
    }

    @Test
    fun aBareBedrockClaudeIdTakesTheAnthropicControlOnlyWhereTheServerKeysIt() {
        val target = ReasoningTarget("bedrock", "claude-sonnet-4-6")
        assertEquals("effort", setting(target = target, bareBedrockClaude = true)?.key)
        assertNull(setting(target = target, bareBedrockClaude = false))
    }

    @Test
    fun geminiHydrogenOffersXhighOnlyWhereTheServerTakesIt() {
        val target = ReasoningTarget("google", "gemini-4-hydrogen")
        assertEquals(listOf("", "low", "medium", "high", "xhigh"), setting(target = target, thinkingLevelXhigh = true)?.options)
        assertEquals(listOf("", "minimal", "low", "medium", "high"), setting(target = target)?.options)
    }

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

    /** A hidden spec's preset isn't served, so which keys it locks is unknown and any level may be refused. */
    @Test
    fun hiddenUnderAnEnforcedSpecTheServerDoesNotServe() {
        assertNull(setting(enforcedSpecUnserved = true))
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

    private fun node(id: String, byUser: Boolean, override: ReasoningOverride? = null) = MessageNode(
        message = Message(messageId = id, conversationId = "c", isCreatedByUser = byUser, reasoningOverride = override),
        children = emptyList(),
        siblingIndex = 0,
        siblingCount = 1,
    )

    @Test
    fun theBranchRunsAtTheNearestUserTurnsLevel() {
        val high = ReasoningOverride.level("effort", "high")
        val low = ReasoningOverride.level("effort", "low")
        val path = listOf(
            node("u1", true, low),
            node("a1", false),
            node("u2", true, high),
            node("a2", false),
            node("u3", true),
        )
        assertEquals(high, nearestBranchReasoning(path))
        assertEquals(high, branchReasoning(high, thinkingOff = false))
        // Thinking switched off outranks a level that would turn it back on.
        assertNull(branchReasoning(high, thinkingOff = true))
        val effort = ReasoningOverride.level("reasoning_effort", "high")
        assertEquals(effort, branchReasoning(nearestBranchReasoning(listOf(node("u", true, effort))), thinkingOff = true))
        assertNull(nearestBranchReasoning(emptyList()))
    }

    @Test
    fun aConversationLevelChipShowsTheInheritedLevelAndOffersTheWayBack() {
        val inherited = ReasoningOverride.level("reasoning_effort", "high")
        val chip = thinkingChip(setting(), configured = "medium", pending = null, branch = inherited, lastsForConversation = true)!!
        assertEquals("high", chip.shown)
        assertTrue(chip.departs)
        assertTrue(chip.canReturnToChatSetting)
        // Under an inherited level, the chat's own setting is an explicit pick; the inherited one sends nothing.
        assertEquals(ReasoningOverride.level("reasoning_effort", "medium"), chip.overrideFor("medium"))
        assertNull(chip.overrideFor("high"))
        // A pending pick still wins over the inherited level.
        val picked = thinkingChip(
            setting(),
            configured = "medium",
            pending = ReasoningOverride.level("reasoning_effort", "low"),
            branch = inherited,
            lastsForConversation = true,
        )!!
        assertEquals("low", picked.shown)
    }

    @Test
    fun anOlderServerOrAnUnsupportedLevelInheritsNothing() {
        val inherited = ReasoningOverride.level("reasoning_effort", "high")
        val older = thinkingChip(setting(), configured = "medium", pending = null, branch = inherited, lastsForConversation = false)!!
        assertNull(older.inherited)
        assertFalse(older.canReturnToChatSetting)
        val otherKey = ReasoningOverride.level("effort", "high")
        val switched = thinkingChip(setting(), configured = "medium", pending = null, branch = otherKey, lastsForConversation = true)!!
        assertNull(switched.inherited)
        assertFalse(switched.departs)
    }
}
