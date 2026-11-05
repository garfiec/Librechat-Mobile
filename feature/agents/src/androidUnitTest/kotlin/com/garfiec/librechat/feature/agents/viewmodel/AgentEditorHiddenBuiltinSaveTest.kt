package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Agent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A builtin the role may not use is hidden, not removed: web's form keeps the flag of a row its
 * catalog doesn't list, so an unrelated edit by such a user saves the agent's tools unchanged.
 */
class AgentEditorHiddenBuiltinSaveTest {

    private val agent = Agent(
        id = "agent-1",
        tools = listOf(ToolConstants.EXECUTE_CODE, ToolConstants.WEB_SEARCH, ToolConstants.FILE_SEARCH),
    )

    private fun denied() = AgentEditorUiState(
        isEditMode = true,
        agentId = agent.id,
        isWebSearchAvailable = true,
        canUseRunCode = false,
        canUseWebSearch = false,
        canUseFileSearch = false,
    ).applyAgentData(agent)

    @Test
    fun `hidden builtins are not offered`() {
        val state = denied()

        assertThat(state.isCodeInterpreterOffered).isFalse()
        assertThat(state.isWebSearchOffered).isFalse()
        assertThat(state.isFileSearchOffered).isFalse()
    }

    @Test
    fun `a save keeps the hidden builtins the agent already has`() {
        val sent = denied().copy(name = "Renamed").toUpdateAgentRequest().tools

        assertThat(sent).containsAtLeast(ToolConstants.EXECUTE_CODE, ToolConstants.WEB_SEARCH, ToolConstants.FILE_SEARCH)
    }
}
