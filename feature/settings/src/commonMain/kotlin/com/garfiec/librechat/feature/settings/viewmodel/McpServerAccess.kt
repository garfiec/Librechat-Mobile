package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.mcp.McpServer

/** What the user may do to one MCP server's stored definition. */
@Immutable
data class McpServerAccess(val canEdit: Boolean, val canDelete: Boolean) {
    companion object {
        val READ_ONLY = McpServerAccess(canEdit = false, canDelete = false)
    }
}

/**
 * Mirrors upstream `useMCPServerManager`'s gating. A server with no `dbId` comes from the server's
 * YAML and has no record to change; a `consumeOnly` one reaches the user only through an agent.
 * Otherwise the effective permission bits decide.
 *
 * [permissions] is null when they haven't loaded or the fetch failed, and a server can be missing
 * from a loaded map when it was created after the fetch. The web shows those as view-only; we allow
 * them instead, because the server still refuses a change it doesn't permit, and hiding Edit on a
 * server the user just created, or on every server after a transient failure, is worse than one
 * refused save. A server shared view-only is in the map with just VIEW, so it stays read-only.
 */
fun mcpServerAccess(server: McpServer, permissions: Map<String, Int>?): McpServerAccess {
    val dbId = server.dbId ?: return McpServerAccess.READ_ONLY
    if (server.consumeOnly) return McpServerAccess.READ_ONLY
    val bits = permissions?.get(dbId) ?: return McpServerAccess(canEdit = true, canDelete = true)
    return McpServerAccess(
        canEdit = PermissionBits.has(bits, PermissionBits.EDIT),
        canDelete = PermissionBits.has(bits, PermissionBits.DELETE),
    )
}
