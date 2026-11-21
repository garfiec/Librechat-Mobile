package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.agents.util.ToolApprovalConstraint
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode

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
