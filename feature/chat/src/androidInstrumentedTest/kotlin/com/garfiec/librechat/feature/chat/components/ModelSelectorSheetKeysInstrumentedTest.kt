package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The model selector's rows are keyed from server data, and Compose throws on a duplicate
 * LazyColumn key (#541). Each case feeds the sheet a shape that produced a duplicate key before, and
 * passes only if the sheet composes. Searching expands every group, so collapsed rows are composed
 * too.
 */
@RunWith(AndroidJUnit4::class)
class ModelSelectorSheetKeysInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet(
        availableModels: Map<String, List<String>>,
        endpointConfigs: Map<String, EndpointConfig>,
        agents: List<Agent> = emptyList(),
        favoriteModelKeys: Set<String> = emptySet(),
    ) {
        composeRule.setContent {
            LibreChatTheme {
                ModelSelectorSheetContent(
                    endpointConfigs = endpointConfigs,
                    availableModels = availableModels,
                    agents = agents,
                    selectedEndpoint = EndpointConstants.AGENTS,
                    selectedModel = null,
                    onModelSelect = { _, _ -> },
                    modelSelect = true,
                    onSetApiKey = {},
                    favoriteModelKeys = favoriteModelKeys,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun search(query: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        composeRule.waitForIdle()
    }

    private fun assertShown(text: String) {
        val nodes = composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("expected a row reading \"$text\"", nodes.isNotEmpty())
    }

    @Test
    fun anAgentsKeyInTheModelMapDoesNotDuplicateTheAgentsHeader() {
        // An agents-only server that lists its agents under /api/models.
        setSheet(
            availableModels = mapOf(EndpointConstants.AGENTS to listOf("a1")),
            endpointConfigs = mapOf(EndpointConstants.AGENTS to EndpointConfig()),
            agents = listOf(Agent(id = "a1", name = "Helper")),
        )
        search("Help")
        assertShown("Helper")
    }

    @Test
    fun anEndpointNamedStarredDoesNotCollideWithTheStarredSection() {
        setSheet(
            availableModels = mapOf("starred" to listOf("m1")),
            endpointConfigs = mapOf("starred" to EndpointConfig()),
            favoriteModelKeys = setOf("starred::m1"),
        )
        assertShown("starred")
    }

    @Test
    fun modelNamesContainingUnderscoresDoNotCollideAcrossEndpoints() {
        // "a_b" + "c" and "a" + "b_c" joined with "_" are both "a_b_c".
        setSheet(
            availableModels = mapOf("a_b" to listOf("c"), "a" to listOf("b_c")),
            endpointConfigs = mapOf("a_b" to EndpointConfig(), "a" to EndpointConfig()),
        )
        search("c")
        assertShown("b_c")
    }

    @Test
    fun repeatedModelsAndAgentsDoNotRepeatRows() {
        setSheet(
            availableModels = mapOf("openAI" to listOf("gpt-4o", "gpt-4o")),
            endpointConfigs = mapOf("openAI" to EndpointConfig()),
            agents = listOf(Agent(id = "a1", name = "gpt helper"), Agent(id = "a1", name = "gpt helper")),
        )
        search("gpt")
        assertShown("gpt-4o")
        assertShown("gpt helper")
    }
}
