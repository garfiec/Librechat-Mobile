package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.Serializable

/**
 * Response from GET /api/mcp/connection/status.
 * Backend returns: { success: true, connectionStatus: { "serverName": { connectionState, requiresOAuth, error? } } }
 */
@Serializable
data class McpConnectionStatusResponse(
    val success: Boolean = false,
    val connectionStatus: Map<String, McpServerStatus> = emptyMap(),
    /**
     * Server-configured OAuth completion window in ms (`MCP_OAUTH_HANDLING_TIMEOUT`), so a client
     * waiting on a flow can give up on the same schedule the server does instead of guessing.
     */
    val oauthTimeout: Long? = null,
)

@Serializable
data class McpServerStatus(
    val connectionState: String = "disconnected",
    val requiresOAuth: Boolean = false,
    val error: String? = null,
    /**
     * Where the server is in authorizing this connection:
     * `not_required` | `authorizing` | `authorized` | `needs_authorization` | `error`.
     * See [McpAuthorizationStates]. This is the only signal separating "a flow is already
     * running" from "start one" — a distinction [connectionState] cannot express.
     */
    val authorizationState: String? = null,
    /**
     * The shared credential/catalog generation this status was observed at (v0.8.8-rc2). Its only
     * job is to stop a STALE discovery verdict from overriding a live one — see
     * [applyDiscoveryAuthorizationState].
     */
    val authorizationGeneration: String? = null,
    /**
     * The server connects only inside a chat request, because its config reads body placeholders
     * (v0.8.8-rc2). Such a server is legitimately `disconnected` between requests, so its state is
     * not a fault to report.
     */
    val requestScoped: Boolean? = null,
    /**
     * Whether every declared per-user variable is present for an on-demand connection
     * (v0.8.8-rc2): `configured` | `needs_configuration`. See [McpConfigurationStates].
     */
    val configurationState: String? = null,
) {
    val isConnected: Boolean get() = connectionState == "connected"
}

/** Wire values of [McpServerStatus.configurationState]. */
object McpConfigurationStates {
    const val CONFIGURED = "configured"
    const val NEEDS_CONFIGURATION = "needs_configuration"
}

/**
 * One server's entry in `GET /api/mcp/tools`. Passive discovery, distinct from the live
 * connection status — see [applyDiscoveryAuthorizationState].
 */
@Serializable
data class McpServerDiscovery(
    val authenticated: Boolean = true,
    /** Only ever [McpAuthorizationStates.REAUTH_REQUIRED], and only when it applies. */
    val authorizationState: String? = null,
    val authorizationGeneration: String? = null,
)

/** `GET /api/mcp/tools`: the flattened tool list plus each server's discovery verdict. */
data class McpToolCatalog(
    val tools: List<McpTool> = emptyList(),
    val servers: Map<String, McpServerDiscovery> = emptyMap(),
)

/**
 * MIRRORED from upstream `applyMCPDiscoveryAuthorizationState` (`client/src/hooks/MCP/polling.ts`).
 *
 * Tool discovery can notice that a server's stored OAuth authorization has lapsed before the
 * connection status does. Rather than adding a state every surface would have to learn,
 * `reauth_required` is folded into the status map as `needs_authorization` — which already means
 * "the user has to act" — so the existing rendering becomes correct instead of growing a case.
 *
 * A discovery verdict is skipped when the live status contradicts it, because discovery is the
 * older observation of the two:
 *  - a connection actively being made or authorized right now is the newer fact;
 *  - a connected/authorized server is left alone unless BOTH sides carry a generation to compare;
 *  - differing generations mean the verdict describes credentials that have since been replaced.
 *
 * Returns [connectionStatus] unchanged when nothing applies, so a caller can compare by identity.
 */
fun applyDiscoveryAuthorizationState(
    connectionStatus: Map<String, McpServerStatus>,
    discovery: Map<String, McpServerDiscovery>,
): Map<String, McpServerStatus> {
    val reauthRequired = discovery.filterValues {
        it.authorizationState == McpAuthorizationStates.REAUTH_REQUIRED
    }
    if (reauthRequired.isEmpty()) return connectionStatus

    val next = connectionStatus.toMutableMap()
    var changed = false
    for ((serverName, discovered) in reauthRequired) {
        val current = next[serverName]
        val statusIsActive = current?.connectionState == "connecting" ||
            current?.authorizationState == McpAuthorizationStates.AUTHORIZING
        if (statusIsActive) continue

        val statusAuthorizes = current?.connectionState == "connected" ||
            current?.authorizationState == McpAuthorizationStates.AUTHORIZED
        val discoveryGeneration = discovered.authorizationGeneration
        val statusGeneration = current?.authorizationGeneration
        if (statusAuthorizes && (discoveryGeneration == null || statusGeneration == null)) continue
        if (discoveryGeneration != null && statusGeneration != null &&
            discoveryGeneration != statusGeneration
        ) {
            continue
        }

        changed = true
        next[serverName] = (current ?: McpServerStatus()).copy(
            requiresOAuth = true,
            connectionState = "disconnected",
            authorizationState = McpAuthorizationStates.NEEDS_AUTHORIZATION,
        )
    }
    return if (changed) next else connectionStatus
}
