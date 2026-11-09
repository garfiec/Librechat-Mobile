package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * Response of upstream `GET /api/agents/tools/:toolId/auth`
 * (controllers/tools.js:62). `message` carries an [AuthType] wire value when
 * authenticated -- `user_provided` means the user has installed their own key,
 * `system_defined` means the server has a key configured for the user.
 *
 * For `web_search` the server answers instead with [authTypes], one `[category, AuthType]` pair per
 * category (`providers`, `scrapers`, `rerankers`), and the provider of each it would run.
 */
@Serializable
data class ToolAuthResult(
    val authenticated: Boolean? = null,
    val message: String? = null,
    val authTypes: List<List<String>>? = null,
    val searchProvider: String? = null,
    val scraperProvider: String? = null,
    val rerankerType: String? = null,
) {
    val isUserProvided: Boolean get() = message == AuthType.USER_PROVIDED
    val isSystemDefined: Boolean get() = message == AuthType.SYSTEM_DEFINED

    /** The `web_search` categories whose keys the user enters, in the server's order. */
    val userProvidedCategories: List<String>
        get() = authTypes.orEmpty().filter { it.getOrNull(1) == AuthType.USER_PROVIDED }.mapNotNull { it.firstOrNull() }
}

/** Upstream `AuthType` wire values (schemas.ts:10). */
object AuthType {
    const val OVERRIDE_AUTH = "override_auth"
    const val USER_PROVIDED = "user_provided"
    const val SYSTEM_DEFINED = "system_defined"
}
