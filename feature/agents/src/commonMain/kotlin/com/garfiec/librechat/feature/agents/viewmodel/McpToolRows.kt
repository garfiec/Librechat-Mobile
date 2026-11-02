package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.agents.util.ToolApprovalConstraint
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.garfiec.librechat.feature.agents.util.approvalModeOf
import com.garfiec.librechat.feature.agents.util.normalizeMcpServerName
import com.garfiec.librechat.feature.agents.util.toolApprovalConstraint

/** One MCP row of the editor's "Tools & Actions", ready to draw. */
@Immutable
data class McpToolRowDisplay(
    /** What [AgentEditorViewModel.onMcpToolToggled] takes to remove the row. */
    val selectionKey: String,
    val name: String,
    val subtitle: String?,
    /** Null when the picker is off, or the row is a whole server or a tool whose server is gone. */
    val approval: McpToolApprovalDisplay?,
)

@Immutable
data class McpToolApprovalDisplay(
    /** The full MCP id, `<tool>_mcp_<normalizedServer>`: the `tool_options` key and the reset's `toolName`. */
    val toolId: String,
    /** The mode in the form; null is inherit. */
    val mode: ToolApprovalMode?,
    val constraint: ToolApprovalConstraint?,
    /** The SAVED mode remembers and the agent exists: the only case the reset route accepts. */
    val canReset: Boolean,
    val isResetting: Boolean,
)

/**
 * The selected MCP rows, with each tool's approval mode resolved here rather than in composition:
 * the tool id, the admin constraint (a glob scan of the policy) and the saved mode are all derived
 * from the agent and the config, which change far less often than the form recomposes.
 */
internal fun AgentEditorUiState.selectedMcpToolRows(): List<McpToolRowDisplay> {
    val byName = mcpTools.associateBy { it.name }
    val modesOn = isToolApprovalModesAvailable
    val savedOptions = loadedAgent?.toolOptions
    return selectedMcpTools.map { name ->
        val tool = byName[name]
        val server = tool?.serverName
        val approval = if (modesOn && server != null) {
            val toolId = "$name$MCP_DELIMITER${normalizeMcpServerName(server)}"
            McpToolApprovalDisplay(
                toolId = toolId,
                mode = toolOptions.approvalModeOf(toolId),
                constraint = toolApprovalConstraint(toolApprovalPolicy, toolId),
                canReset = isEditMode && agentId != null && savedOptions.approvalModeOf(toolId)?.remembers == true,
                isResetting = resettingApprovalToolId == toolId,
            )
        } else {
            null
        }
        McpToolRowDisplay(
            selectionKey = name,
            name = name,
            subtitle = server ?: tool?.description,
            approval = approval,
        )
    }
}

private const val MCP_DELIMITER = "_mcp_"
