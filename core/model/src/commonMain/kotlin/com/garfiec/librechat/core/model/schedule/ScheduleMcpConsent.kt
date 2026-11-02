package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `GET|POST /api/schedules/:id/mcp-consent` (v0.8.9-rc1, upstream `ScheduleMCPConsentView` in
 * `packages/data-provider/src/types/scheduleConsent.ts`): whether the owner has authorized the MCP
 * servers a schedule calls to run unattended, and the offer to (re)authorize them.
 *
 * [state] and [ScheduledMcpResource.credentialMode] are wire enums kept as strings: a value a newer
 * server adds degrades to a generic status or the raw text, never a decode failure. Mirrored by
 * hand (scripts/mirrors.json: schedule-mcp-consent-view).
 */
@Serializable
data class ScheduleMcpConsentView(
    /** One of [ScheduleMcpConsentState]. */
    val state: String = ScheduleMcpConsentState.MISSING,
    /** The current enrollment's revision; null when none was ever recorded. Revoke needs it. */
    val revision: String? = null,
    /** Epoch MILLISECONDS, not ISO: the absolute deadline of the current authorization. */
    val expiresAtMs: Long? = null,
    val targets: List<ScheduledMcpTarget> = emptyList(),
    /** What Confirm would authorize; null when there is nothing this deployment can offer. */
    val offer: ScheduleMcpConsentOffer? = null,
)

/** The `state` values of [ScheduleMcpConsentView]. */
object ScheduleMcpConsentState {
    const val MISSING = "missing"
    const val ACTIVE = "active"
    const val EXPIRED = "expired"
    const val REVOKED = "revoked"
    const val CHANGED = "changed"
    const val UNSUPPORTED = "unsupported"
}

@Serializable
data class ScheduleMcpConsentOffer(
    /** Sent back unchanged as `offerDigest`, binding the confirmation to what was shown. */
    val digest: String = "",
    val maxLifetimeHours: Int = 1,
)

@Serializable
data class ScheduledMcpTarget(
    val resource: ScheduledMcpResource = ScheduledMcpResource(),
    val permittedTools: List<ScheduledMcpToolSelection> = emptyList(),
)

@Serializable
data class ScheduledMcpResource(
    val serverName: String = "",
    val url: String = "",
    /** One of [ScheduledMcpCredentialMode]. */
    val credentialMode: String = "",
    val issuer: String? = null,
    val audience: String? = null,
    val scopes: List<String> = emptyList(),
)

/** The `credentialMode` values of [ScheduledMcpResource]. */
object ScheduledMcpCredentialMode {
    const val STORED_OAUTH = "stored_oauth"
    const val BROWSER_BEARER = "browser_bearer"
    const val RENEWABLE_OBO = "renewable_obo"
    const val RESOURCE_BEARER = "resource_bearer"
    const val STATIC = "static"
    const val ANONYMOUS = "anonymous"
}

@Serializable
data class ScheduledMcpToolSelection(
    val agentId: String = "",
    val tools: List<String> = emptyList(),
)

/**
 * Body of `POST /api/schedules/:id/mcp-consent`. The route's schema is `.strict()` and
 * `expectedRevision` is nullable but NOT optional: a first confirmation must send
 * `"expectedRevision": null`, and the network module's `explicitNulls = false` would drop a null
 * property. Hence a [JsonObject] built here rather than a data class.
 */
fun confirmScheduleMcpConsentBody(offerDigest: String, expectedRevision: String?, lifetimeHours: Int): JsonObject =
    JsonObject(
        mapOf(
            "offerDigest" to JsonPrimitive(offerDigest),
            "expectedRevision" to (expectedRevision?.let(::JsonPrimitive) ?: JsonNull),
            "lifetimeHours" to JsonPrimitive(lifetimeHours),
        ),
    )

/** Body of `DELETE /api/schedules/:id/mcp-consent`: the revision being revoked. */
@Serializable
data class RevokeScheduleMcpConsentRequest(val expectedRevision: String)
