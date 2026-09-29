package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Maps to backend apiKey object: { source, authorization_type, key?, custom_header? }. */
@Serializable
data class McpApiKeyConfig(
    /** Upstream reads an absent source as `admin` (`source || 'admin'`), and so does the read here. */
    val source: McpApiKeySource = McpApiKeySource.ADMIN,
    @SerialName("authorization_type") val authorizationType: McpAuthorizationType = McpAuthorizationType.BEARER,
    val key: String? = null,
    @SerialName("custom_header") val customHeader: String? = null,
)

@Serializable
enum class McpApiKeySource {
    @SerialName("admin")
    ADMIN,

    @SerialName("user")
    USER,
}

@Serializable
enum class McpAuthorizationType {
    @SerialName("bearer")
    BEARER,

    @SerialName("basic")
    BASIC,

    @SerialName("custom")
    CUSTOM,
}

/** Maps to backend oauth object. Only the most common fields are exposed in the UI. */
@Serializable
data class McpOAuthConfig(
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("token_url") val tokenUrl: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scope: String? = null,
    /**
     * Set on the web, never edited here, but it must survive an edit: the update route replaces
     * the stored config, and it counts this field among the ones the stored client secret is
     * bound to, so dropping it refuses the save with `MCP_OAUTH_SECRET_REENTRY_REQUIRED`.
     */
    @SerialName("token_exchange_method") val tokenExchangeMethod: String? = null,
)

/**
 * Maps to the backend `obo` object: `{ scopes }` (upstream `OboOptionsSchema`). Configuring it needs
 * the `CONFIGURE_OBO` permission; without it, an edit of an OBO server may change only its title,
 * description and icon, and every other field — this one included — must be resent exactly as
 * stored, or the server answers 403.
 */
@Serializable
data class McpOboConfig(
    val scopes: String,
)
