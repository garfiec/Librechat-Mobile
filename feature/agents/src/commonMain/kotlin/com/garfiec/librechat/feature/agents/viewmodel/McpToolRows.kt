package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.feature.agents.util.approvalModeOf
import com.garfiec.librechat.feature.agents.util.mcpToolKey
import com.garfiec.librechat.feature.agents.util.toolApprovalConstraint

/**
 * The selected MCP rows, with each tool's approval mode resolved here rather than in composition:
 * the tool id, the admin constraint (a glob scan of the policy) and the saved mode are all derived
 * from the agent and the config, which change far less often than the form recomposes.
 */
internal fun AgentEditorUiState.selectedMcpToolRows(): List<McpToolRowDisplay> {
    val byKey = mcpTools.associateBy { mcpToolKey(it.name, it.serverName) }
    val modesOn = isToolApprovalModesAvailable
    val savedOptions = loadedAgent?.toolOptions
    return selectedMcpTools.map { key ->
        val tool = byKey[key]
        val server = tool?.serverName
        val approval = if (modesOn && server != null) {
            McpToolApprovalDisplay(
                toolId = key,
                mode = toolOptions.approvalModeOf(key),
                constraint = toolApprovalConstraint(toolApprovalPolicy, key),
                canReset = isEditMode && agentId != null && savedOptions.approvalModeOf(key)?.remembers == true,
                isResetting = resettingApprovalToolId == key,
            )
        } else {
            null
        }
        McpToolRowDisplay(
            selectionKey = key,
            name = tool?.name ?: key.substringBefore(MCP_DELIMITER),
            subtitle = server?.let { mcpServerTitles[it] ?: it } ?: tool?.description,
            approval = approval,
        )
    }
}

private const val MCP_DELIMITER = "_mcp_"
