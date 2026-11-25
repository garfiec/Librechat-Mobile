package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.config.McpTrustCheckbox
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.feature.settings.screen.initialApiKeySource

/** What the editor's fields hold. Compared against the loaded copy to decide whether leaving discards anything. */
@Immutable
data class McpServerDraft(
    val name: String = "",
    val description: String = "",
    val url: String = "",
    val type: McpServerType = McpServerType.SSE,
    val authMode: McpAuthMode = McpAuthMode.NONE,
    val apiKeySource: McpApiKeySource = McpApiKeySource.ADMIN,
    val apiKeyAuthType: McpAuthorizationType = McpAuthorizationType.BEARER,
    val apiKeyCustomHeader: String = "",
    /** Never pre-filled: reads don't return it, and a blank key on an admin edit keeps the stored one. */
    val apiKey: String = "",
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val oauthAuthUrl: String = "",
    val oauthTokenUrl: String = "",
    val oauthScope: String = "",
    /**
     * The user vouched for the server. Asked on create only, as upstream's form does: an existing
     * server was trusted when it was added, so an edit starts checked.
     */
    val trusted: Boolean = false,
) {
    companion object {
        fun from(server: McpServer): McpServerDraft = McpServerDraft(
            name = server.title?.takeIf { it.isNotBlank() } ?: server.name,
            description = server.description.orEmpty(),
            url = server.url,
            type = server.type,
            authMode = when {
                // Upstream's form gives OBO precedence over every other auth; with no OBO choice here,
                // "none" is where an OBO server opens, and a save from it resends the stored `obo`.
                server.obo != null -> McpAuthMode.NONE
                server.oauth != null -> McpAuthMode.OAUTH
                server.apiKey != null -> McpAuthMode.API_KEY
                else -> McpAuthMode.NONE
            },
            apiKeySource = initialApiKeySource(server),
            apiKeyAuthType = server.apiKey?.authorizationType ?: McpAuthorizationType.BEARER,
            apiKeyCustomHeader = server.apiKey?.customHeader.orEmpty(),
            oauthClientId = server.oauth?.clientId.orEmpty(),
            oauthClientSecret = server.oauth?.clientSecret.orEmpty(),
            oauthAuthUrl = server.oauth?.authorizationUrl.orEmpty(),
            oauthTokenUrl = server.oauth?.tokenUrl.orEmpty(),
            oauthScope = server.oauth?.scope.orEmpty(),
            trusted = true,
        )
    }
}

@Immutable
data class McpServerEditorUiState(
    val draft: McpServerDraft = McpServerDraft(),
    val loadedDraft: McpServerDraft? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * The server being edited could not be read. Save stays off: over a blank form it would take
     * the create branch and add a second server instead of changing this one.
     */
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    /** Why the last save failed, shown at the top of the form. Null for a re-entry refusal, which its field reports. */
    val saveError: String? = null,
    /** `MCP_OAUTH_SECRET_REENTRY_REQUIRED`: the client secret must be typed again. */
    val oauthSecretReentryRequired: Boolean = false,
    /** `MCP_API_KEY_REENTRY_REQUIRED` (v0.8.8-rc4): the admin API key must be typed again. */
    val apiKeyReentryRequired: Boolean = false,
    /** The deployment's own trust wording, or null for ours. */
    val trustCheckbox: McpTrustCheckbox? = null,
    val savedServerName: String? = null,
    val showDiscardConfirm: Boolean = false,
    val exitRequested: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = !isLoading && loadedDraft != null && draft != loadedDraft

    val canSave: Boolean
        // A landed save is already popping the screen; a second tap would create or patch again.
        get() = !isLoading && !loadFailed && !isSaving && savedServerName == null &&
            draft.name.isNotBlank() && draft.url.isNotBlank() && (isEditing || draft.trusted)
}
