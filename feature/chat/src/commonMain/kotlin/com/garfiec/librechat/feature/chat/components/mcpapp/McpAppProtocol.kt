package com.garfiec.librechat.feature.chat.components.mcpapp

/**
 * The MCP Apps wire protocol this host speaks: spec `2026-01-26`, as implemented by
 * `@modelcontextprotocol/ext-apps` 1.7.5 (`src/spec.types.ts`), the version upstream pins. JSON-RPC
 * 2.0 over the relay page; the method names are pinned by `McpAppProtocolTest`.
 */
internal object McpAppProtocol {
    const val PROTOCOL_VERSION = "2026-01-26"
    const val JSONRPC = "2.0"

    // App → host requests.
    const val INITIALIZE = "ui/initialize"
    const val OPEN_LINK = "ui/open-link"
    const val MESSAGE = "ui/message"
    const val REQUEST_DISPLAY_MODE = "ui/request-display-mode"
    const val TOOLS_CALL = "tools/call"
    const val RESOURCES_READ = "resources/read"
    const val RESOURCES_LIST = "resources/list"
    const val RESOURCE_TEMPLATES_LIST = "resources/templates/list"
    const val PING = "ping"

    // App → host notifications.
    const val INITIALIZED = "ui/notifications/initialized"
    const val SIZE_CHANGED = "ui/notifications/size-changed"
    const val REQUEST_TEARDOWN = "ui/notifications/request-teardown"
    const val LOGGING_MESSAGE = "notifications/message"

    // Host → app.
    const val TOOL_INPUT = "ui/notifications/tool-input"
    const val TOOL_RESULT = "ui/notifications/tool-result"
    const val HOST_CONTEXT_CHANGED = "ui/notifications/host-context-changed"
    const val RESOURCE_TEARDOWN = "ui/resource-teardown"

    // Relay (sandbox proxy) notifications.
    const val SANDBOX_PROXY_READY = "ui/notifications/sandbox-proxy-ready"
    const val SANDBOX_RESOURCE_READY = "ui/notifications/sandbox-resource-ready"

    /** The inner frame's `sandbox` tokens, from upstream's allowlist (never `allow-same-origin`). */
    const val SANDBOX_TOKENS = "allow-scripts allow-forms"

    const val ERROR_METHOD_NOT_FOUND = -32601
    const val ERROR_INVALID_PARAMS = -32602
    const val ERROR_INTERNAL = -32603
}
