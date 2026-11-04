package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.garfiec.librechat.feature.agents.util.withApprovalMode
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * Upstream keys an MCP tool's `tool_options` by its full id (`<tool>_mcp_<server>`), and the update
 * route stores the map it is sent as is. A save must therefore keep those keys, or it wipes every
 * approval mode and `defer_loading` a web user set.
 */
class AgentEditorToolOptionsSaveTest {

    private val catalog = listOf(
        McpTool(name = "create_issue", serverName = "github"),
        McpTool(name = "search_code", serverName = "github"),
    )

    private val chatMode = buildJsonObject {
        put("approval_mode", "chat")
        put("approval_revision", "6f1c1f3e-5a43-4c1e-9b1e-0c3f1b2a9d10")
    }
    private val deferred = buildJsonObject { put("defer_loading", true) }

    private fun editing(agent: Agent) =
        AgentEditorUiState(mcpTools = catalog, isEditMode = true, agentId = agent.id).applyAgentData(agent)

    @Test
    fun `full MCP ids survive a save next to a plain tool's options`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("wolfram", "create_issue_mcp_github", "search_code_mcp_github"),
            toolOptions = JsonObject(
                mapOf(
                    "wolfram" to deferred,
                    "create_issue_mcp_github" to chatMode,
                    "search_code_mcp_github" to deferred,
                ),
            ),
        )

        val sent = editing(agent).toUpdateAgentRequest().toolOptions

        assertThat(sent).isEqualTo(agent.toolOptions)
    }

    @Test
    fun `a removed MCP tool's options are dropped and the rest kept`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("create_issue_mcp_github", "search_code_mcp_github"),
            toolOptions = JsonObject(
                mapOf("create_issue_mcp_github" to chatMode, "search_code_mcp_github" to deferred),
            ),
        )
        val state = editing(agent).let { it.copy(selectedMcpTools = it.selectedMcpTools - "search_code") }

        val sent = state.toUpdateAgentRequest().toolOptions

        assertThat(sent?.keys).containsExactly("create_issue_mcp_github")
    }

    @Test
    fun `options under a whole-server selection are kept`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("sys__server__sys_mcp_github"),
            toolOptions = JsonObject(mapOf("create_issue_mcp_github" to chatMode)),
        )

        val sent = editing(agent).toUpdateAgentRequest().toolOptions

        assertThat(sent?.keys).containsExactly("create_issue_mcp_github")
    }

    @Test
    fun `removing the last configured tool clears the stored map on update`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("create_issue_mcp_github"),
            toolOptions = JsonObject(mapOf("create_issue_mcp_github" to chatMode)),
        )
        val state = editing(agent).copy(selectedMcpTools = emptySet())

        val sent = state.toUpdateAgentRequest().toolOptions

        assertThat(sent).isEqualTo(JsonObject(emptyMap()))
    }

    @Test
    fun `an agent that never had options sends none`() {
        val agent = Agent(id = "agent-1", tools = listOf("create_issue_mcp_github"))

        assertThat(editing(agent).toUpdateAgentRequest().toolOptions).isNull()
        assertThat(editing(agent).hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `a mode change makes the form dirty and saves under the full id, and inherit clears it`() {
        val agent = Agent(id = "agent-1", tools = listOf("create_issue_mcp_github"))
        val picked = editing(agent).let {
            it.copy(toolOptions = it.toolOptions.withApprovalMode("create_issue_mcp_github", ToolApprovalMode.CHAT))
        }

        assertThat(picked.hasUnsavedChanges()).isTrue()
        val sent = picked.toUpdateAgentRequest().toolOptions?.get("create_issue_mcp_github") as JsonObject
        assertThat(sent["approval_mode"]).isEqualTo(JsonPrimitive("chat"))

        // Saved with a mode, then set back to inherit: the stored map must be cleared, not left alone.
        val saved = editing(agent.copy(toolOptions = picked.toolOptions))
        val inherited = saved.copy(toolOptions = saved.toolOptions.withApprovalMode("create_issue_mcp_github", null))
        assertThat(inherited.toUpdateAgentRequest().toolOptions).isEqualTo(JsonObject(emptyMap()))
    }

    @Test
    fun `a selected tool whose catalog has not loaded keeps its full-id options`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("create_issue_mcp_github"),
            toolOptions = JsonObject(mapOf("create_issue_mcp_github" to chatMode)),
        )
        val state = AgentEditorUiState(isEditMode = true, agentId = agent.id).applyAgentData(agent)

        assertThat(state.toUpdateAgentRequest().toolOptions?.keys).containsExactly("create_issue_mcp_github")
    }

    @Test
    fun `a legacy bare key on a selected MCP tool is kept`() {
        val agent = Agent(
            id = "agent-1",
            tools = listOf("create_issue_mcp_github"),
            toolOptions = JsonObject(mapOf("create_issue" to JsonObject(mapOf("defer_loading" to JsonPrimitive(true))))),
        )

        assertThat(editing(agent).toUpdateAgentRequest().toolOptions?.keys).containsExactly("create_issue")
    }
}
