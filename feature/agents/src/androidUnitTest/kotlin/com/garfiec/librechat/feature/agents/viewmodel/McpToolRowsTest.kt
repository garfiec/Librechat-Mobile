package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointToolApproval
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.feature.agents.util.ToolApprovalConstraint
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class McpToolRowsTest {

    private val catalog = listOf(McpTool(name = "search_code", serverName = "My GitHub"))
    private val toolId = "search_code_mcp_My_GitHub"
    private val chat = buildJsonObject {
        put("approval_mode", "chat")
        put("approval_revision", "6f1c1f3e-5a43-4c1e-9b1e-0c3f1b2a9d10")
    }

    private fun editing(agent: Agent, policy: EndpointToolApproval?) = AgentEditorUiState(
        mcpTools = catalog,
        isEditMode = true,
        agentId = agent.id,
        isToolApprovalVersionSupported = true,
        toolApprovalPolicy = policy,
    ).applyAgentData(agent)

    private val agent = Agent(
        id = "agent-1",
        tools = listOf(toolId, "sys__server__sys_mcp_other"),
        toolOptions = JsonObject(mapOf(toolId to chat)),
    )

    @Test
    fun `a tool with a known server gets its full id, mode and reset`() {
        val rows = editing(agent, EndpointToolApproval(enabled = true, agentModes = true, mode = "bypass"))
            .selectedMcpToolRows()

        val tool = rows.first { it.name == "search_code" }.approval!!
        assertThat(tool.toolId).isEqualTo(toolId)
        assertThat(tool.mode).isEqualTo(ToolApprovalMode.CHAT)
        assertThat(tool.constraint).isNull()
        assertThat(tool.canReset).isTrue()
        // A whole-server row gets no picker: modes are per tool.
        assertThat(rows.first { it.name == "other" }.approval).isNull()
    }

    @Test
    fun `no picker unless the server enables agent modes`() {
        val off = editing(agent, EndpointToolApproval(enabled = true, agentModes = false)).selectedMcpToolRows()
        assertThat(off.all { it.approval == null }).isTrue()

        val oldServer = editing(agent, EndpointToolApproval(enabled = true, agentModes = true))
            .copy(isToolApprovalVersionSupported = false)
            .selectedMcpToolRows()
        assertThat(oldServer.all { it.approval == null }).isTrue()
    }

    @Test
    fun `reset follows the SAVED mode, not an unsaved pick`() {
        val state = editing(Agent(id = "agent-1", tools = listOf(toolId)), EndpointToolApproval(enabled = true, agentModes = true))
            .copy(toolOptions = JsonObject(mapOf(toolId to chat)))

        val approval = state.selectedMcpToolRows().single().approval!!

        assertThat(approval.mode).isEqualTo(ToolApprovalMode.CHAT)
        assertThat(approval.canReset).isFalse()
        // Default policy: every unlisted tool is constrained to ask.
        assertThat(approval.constraint).isEqualTo(ToolApprovalConstraint.ASK)
    }
}
