package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.applyAgentData
import com.garfiec.librechat.feature.agents.viewmodel.toUpdateAgentRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class CodeBackgroundTest {

    private val off = buildJsonObject { put("run_in_background", false) }
    private val chatMode = buildJsonObject {
        put("approval_mode", "chat")
        put("approval_revision", "6f1c1f3e-5a43-4c1e-9b1e-0c3f1b2a9d10")
    }

    @Test
    fun `no entry is on`() {
        assertThat((null as JsonObject?).codeBackgroundEnabled()).isTrue()
        assertThat(JsonObject(emptyMap()).codeBackgroundEnabled()).isTrue()
    }

    @Test
    fun `bash_tool's own key wins over execute_code's`() {
        val options = JsonObject(
            mapOf(
                "bash_tool" to buildJsonObject { put("run_in_background", true) },
                "execute_code" to off,
            ),
        )
        assertThat(options.codeBackgroundEnabled()).isTrue()
        assertThat(JsonObject(mapOf("execute_code" to off)).codeBackgroundEnabled()).isFalse()
    }

    @Test
    fun `off writes false on both tools and keeps every other key`() {
        val options = JsonObject(
            mapOf(
                "execute_code" to buildJsonObject { put("allowed_callers", "code_execution") },
                "create_issue_mcp_github" to chatMode,
            ),
        )

        val updated = options.withCodeBackground(false)!!

        assertThat(updated["create_issue_mcp_github"]).isEqualTo(chatMode)
        assertThat(updated["bash_tool"]).isEqualTo(off)
        assertThat(updated["execute_code"]).isEqualTo(
            buildJsonObject {
                put("allowed_callers", "code_execution")
                put("run_in_background", false)
            },
        )
        assertThat(updated.codeBackgroundEnabled()).isFalse()
    }

    @Test
    fun `on removes the key, drops entries left empty and keeps the rest`() {
        val options = JsonObject(
            mapOf(
                "execute_code" to buildJsonObject {
                    put("allowed_callers", "code_execution")
                    put("run_in_background", false)
                },
                "bash_tool" to off,
                "create_issue_mcp_github" to chatMode,
            ),
        )

        val updated = options.withCodeBackground(true)!!

        assertThat(updated.keys).containsExactly("execute_code", "create_issue_mcp_github")
        assertThat(updated["execute_code"]).isEqualTo(buildJsonObject { put("allowed_callers", "code_execution") })
        assertThat(updated.codeBackgroundEnabled()).isTrue()
    }

    @Test
    fun `on over nothing leaves no map`() {
        assertThat((null as JsonObject?).withCodeBackground(true)).isNull()
        assertThat(JsonObject(mapOf("bash_tool" to off)).withCodeBackground(true)).isNull()
    }

    @Test
    fun `a save keeps bash_tool's opt-out while the code interpreter is on`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("execute_code"),
            toolOptions = JsonObject(mapOf("execute_code" to off, "bash_tool" to off)),
        )
        val state = AgentEditorUiState(isEditMode = true, agentId = agent.id).applyAgentData(agent)

        assertThat(state.toUpdateAgentRequest().toolOptions).isEqualTo(agent.toolOptions)
    }

    @Test
    fun `a save drops the pair with the code interpreter`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("execute_code"),
            toolOptions = JsonObject(mapOf("execute_code" to off, "bash_tool" to off)),
        )
        val state = AgentEditorUiState(isEditMode = true, agentId = agent.id).applyAgentData(agent)
            .copy(codeInterpreterEnabled = false)

        assertThat(state.toUpdateAgentRequest().toolOptions).isEqualTo(JsonObject(emptyMap()))
    }

    @Test
    fun `the switch shows only with the capability and the code interpreter on`() {
        val base = AgentEditorUiState(codeInterpreterEnabled = true)
        assertThat(base.codeBackground).isNull()
        assertThat(base.copy(isCodeBackgroundAvailable = true).codeBackground).isTrue()
        assertThat(base.copy(isCodeBackgroundAvailable = true, codeInterpreterEnabled = false).codeBackground).isNull()
        assertThat(
            base.copy(isCodeBackgroundAvailable = true, toolOptions = JsonObject(mapOf("bash_tool" to off)))
                .codeBackground,
        ).isFalse()
    }
}
