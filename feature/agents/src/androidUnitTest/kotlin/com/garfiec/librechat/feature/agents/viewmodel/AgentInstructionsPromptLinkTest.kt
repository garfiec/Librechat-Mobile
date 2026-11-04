package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** v0.8.9-rc1 agents can take their instructions from a linked prompt group (3c90cde7d). */
class AgentInstructionsPromptLinkTest {

    @Test
    fun `a linked agent opens with read-only instructions and saves without touching the link`() {
        val agent = Agent(
            id = "a",
            name = "n",
            instructions = "stale",
            instructionsPrompt = buildJsonObject { put("source", "native"); put("groupId", "g") },
        )
        val state = AgentEditorUiState().applyAgentData(agent)
        assertThat(state.instructionsLinkedToPrompt).isTrue()
        // The request carries no instructionsPrompt at all, so the update leaves the link alone,
        // and the stored text goes back unchanged.
        assertThat(state.toUpdateAgentRequest().instructions).isEqualTo("stale")
    }

    @Test
    fun `an absent or removed link keeps the field editable`() {
        val plain = Agent(id = "a", name = "n")
        assertThat(AgentEditorUiState().applyAgentData(plain).instructionsLinkedToPrompt).isFalse()
        val removed = plain.copy(instructionsPrompt = JsonNull)
        assertThat(AgentEditorUiState().applyAgentData(removed).instructionsLinkedToPrompt).isFalse()
    }
}
