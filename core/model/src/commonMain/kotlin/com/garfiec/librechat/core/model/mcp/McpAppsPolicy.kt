package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The server's MCP Apps policy (`/api/config` → `mcpApps`, authenticated, v0.8.9-rc1).
 *
 * Read with [resolve], never decoded structurally: a wrong-shaped field would otherwise fail the
 * whole config. Like web's `getPublishedPolicy` (`MCPAppsPolicyContext.tsx`), a policy whose
 * `enabled` or `legacyHtmlEnabled` is not a Boolean is no policy, so Apps are off; pre-rc1 servers
 * send none. Each limit outside its range falls back to its default.
 *
 * `sandboxUrl` is read but not required: it is the web's dedicated sandbox origin, and the app
 * runs Apps in its own relay page (see `DISCOVERY.md`, "MCP Apps").
 */
data class McpAppsPolicy(
    val enabled: Boolean = false,
    val legacyHtmlEnabled: Boolean = false,
    val cspLimits: McpAppCspLimits = McpAppCspLimits(),
    val maxActiveViews: Int = DEFAULT_MAX_ACTIVE_VIEWS,
    val maxActionPreviewChars: Int = DEFAULT_ACTION_PREVIEW_CHARS,
    /** How many App actions may wait for a decision at once (`operationLimits.maxActive`). */
    val maxPendingActions: Int = DEFAULT_OPERATION_MAX_ACTIVE,
    val sandboxUrl: String? = null,
) {
    companion object {
        const val DEFAULT_MAX_ACTIVE_VIEWS = 3
        const val MAX_ACTIVE_VIEWS = 32
        const val DEFAULT_ACTION_PREVIEW_CHARS = 16_384
        const val MAX_ACTION_PREVIEW_CHARS = 131_072
        const val DEFAULT_OPERATION_MAX_ACTIVE = 16

        val DISABLED = McpAppsPolicy()

        fun resolve(raw: JsonElement?): McpAppsPolicy {
            val obj = raw as? JsonObject ?: return DISABLED
            val enabled = obj.strictBoolean("enabled") ?: return DISABLED
            val legacy = obj.strictBoolean("legacyHtmlEnabled") ?: return DISABLED
            val operation = obj["operationLimits"] as? JsonObject
            return McpAppsPolicy(
                enabled = enabled,
                legacyHtmlEnabled = legacy,
                cspLimits = McpAppCspLimits.resolve(obj["cspLimits"] as? JsonObject),
                maxActiveViews = obj.limit("maxActiveViews", DEFAULT_MAX_ACTIVE_VIEWS, MAX_ACTIVE_VIEWS),
                maxActionPreviewChars = obj.limit(
                    "maxActionPreviewChars",
                    DEFAULT_ACTION_PREVIEW_CHARS,
                    MAX_ACTION_PREVIEW_CHARS,
                ),
                maxPendingActions = operation?.limit("maxActive", DEFAULT_OPERATION_MAX_ACTIVE, Int.MAX_VALUE)
                    ?: DEFAULT_OPERATION_MAX_ACTIVE,
                sandboxUrl = (obj["sandboxUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            )
        }
    }
}

/** Upstream `MCPAppCspLimits`; defaults `DEFAULT_MCP_APP_CSP_LIMITS`. */
data class McpAppCspLimits(
    val maxSourcesPerDirective: Int = DEFAULT_MAX_SOURCES,
    val maxSerializedLength: Int = DEFAULT_MAX_SERIALIZED,
) {
    companion object {
        const val DEFAULT_MAX_SOURCES = 32
        const val DEFAULT_MAX_SERIALIZED = 4096

        /** Upstream `resolveMCPAppCspLimits`: a non-positive or non-integer value takes its default. */
        fun resolve(raw: JsonObject?): McpAppCspLimits = McpAppCspLimits(
            maxSourcesPerDirective = raw?.limit("maxSourcesPerDirective", DEFAULT_MAX_SOURCES, Int.MAX_VALUE)
                ?: DEFAULT_MAX_SOURCES,
            maxSerializedLength = raw?.limit("maxSerializedLength", DEFAULT_MAX_SERIALIZED, Int.MAX_VALUE)
                ?: DEFAULT_MAX_SERIALIZED,
        )
    }
}

private fun JsonObject.strictBoolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** A positive integer no larger than [max], else [fallback] (web's `validLimit`). */
private fun JsonObject.limit(key: String, fallback: Int, max: Int): Int {
    val value = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return fallback
    return if (value in 1..max.toLong()) value.toInt() else fallback
}
