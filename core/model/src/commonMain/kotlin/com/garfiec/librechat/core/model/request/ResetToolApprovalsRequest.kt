package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * `POST /api/agents/tools/approvals/reset` (v0.8.9-rc1): forget the caller's own remembered
 * approvals for an agent, or for one of its tools.
 *
 * The server's schema is strict, so the body is exactly these two keys and is NOT `arg`-wrapped.
 * [toolName] is the full MCP id (`<tool>_mcp_<server>`), and must be left out (not sent as null)
 * for a whole-agent reset; the network JSON's `explicitNulls = false` does that.
 */
@Serializable
data class ResetToolApprovalsRequest(
    val agentId: String,
    val toolName: String? = null,
)
