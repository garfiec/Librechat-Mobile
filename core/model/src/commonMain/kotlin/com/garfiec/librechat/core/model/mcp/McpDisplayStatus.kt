package com.garfiec.librechat.core.model.mcp

/**
 * What an MCP server's connection means to the user, folded from [McpServerStatus] and the server's
 * own flags. MIRRORED from upstream `MCPStatusBadge` / `getStatusDotColor`
 * (`client/src/components/SidePanel/MCPBuilder/MCPStatusBadge.tsx`), split further where upstream
 * paints different situations one colour: a request-scoped server idle between chats, and one
 * waiting on per-user variables, are neither faults nor plain disconnects.
 */
enum class McpDisplayStatus {
    CONNECTED,
    CONNECTING,

    /** The user has to authorize it with the provider. Reconnecting starts that. */
    NEEDS_SIGN_IN,

    /** Waiting on per-user variables, which only the web can set today. */
    NEEDS_SETUP,

    /** Connects inside a chat request only; disconnected the rest of the time by design. */
    ON_DEMAND,

    DISCONNECTED,
    FAILED,

    /** No status has been reported for it yet. */
    UNKNOWN,
    ;

    /** A state the user can fix from the list, which therefore sorts first. */
    val needsAttention: Boolean get() = this == NEEDS_SIGN_IN || this == FAILED
}

/**
 * [status] is the entry from the connection-status map after `applyDiscoveryAuthorizationState`,
 * so a lapsed authorization noticed by tool discovery already reads as needing sign-in.
 * [isReinitializing] is a reconnect the user started from this screen.
 */
fun mcpDisplayStatus(server: McpServer, status: McpServerStatus?, isReinitializing: Boolean): McpDisplayStatus {
    if (isReinitializing) return McpDisplayStatus.CONNECTING
    if (status == null) return if (server.requestScoped) McpDisplayStatus.ON_DEMAND else McpDisplayStatus.UNKNOWN
    val authorization = status.authorizationState
    return when {
        status.connectionState == "connecting" || authorization == McpAuthorizationStates.AUTHORIZING ->
            McpDisplayStatus.CONNECTING
        status.isConnected -> McpDisplayStatus.CONNECTED
        // Upstream reads requiresOAuth only while disconnected: an errored OAuth server is a failure.
        (status.requiresOAuth && status.connectionState != "error") ||
            authorization == McpAuthorizationStates.NEEDS_AUTHORIZATION -> McpDisplayStatus.NEEDS_SIGN_IN
        status.configurationState == McpConfigurationStates.NEEDS_CONFIGURATION -> McpDisplayStatus.NEEDS_SETUP
        status.connectionState == "error" || authorization == McpAuthorizationStates.ERROR -> McpDisplayStatus.FAILED
        status.requestScoped == true || server.requestScoped -> McpDisplayStatus.ON_DEMAND
        !status.error.isNullOrBlank() || !server.error.isNullOrBlank() -> McpDisplayStatus.FAILED
        else -> McpDisplayStatus.DISCONNECTED
    }
}
