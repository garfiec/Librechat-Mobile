package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.config.McpTrustCheckbox
import com.garfiec.librechat.core.model.config.mcpTrustCheckbox
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.feature.settings.screen.apiKeyConfigFrom
import com.garfiec.librechat.feature.settings.screen.initialApiKeySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

/**
 * The full-screen add/edit form for one MCP server. [serverName] is the stored name of the server to
 * edit, or null to add one.
 */
class McpServerEditorViewModel(
    private val mcpRepository: McpRepository,
    configRepository: ConfigRepository,
    private val serverName: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        McpServerEditorUiState(
            isEditing = serverName != null,
            trustCheckbox = configRepository.startupConfig.value?.interfaceConfig?.mcpTrustCheckbox(),
        ),
    )
    val uiState: StateFlow<McpServerEditorUiState> = _uiState.asStateFlow()

    /** The stored server the draft came from; its unshown fields are resent on save. */
    private var original: McpServer? = null

    init {
        load()
    }

    private fun load() {
        if (serverName == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, loadedDraft = _uiState.value.draft)
            return
        }
        viewModelScope.launch {
            val server = (mcpRepository.listServers() as? Result.Success)?.data?.firstOrNull { it.name == serverName }
            original = server
            val draft = server?.let(McpServerDraft::from) ?: McpServerDraft()
            _uiState.value = _uiState.value.copy(
                draft = draft,
                loadedDraft = draft,
                isLoading = false,
                loadFailed = server == null,
            )
        }
    }

    fun reload() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
        load()
    }

    fun update(transform: (McpServerDraft) -> McpServerDraft) {
        _uiState.value = _uiState.value.copy(draft = transform(_uiState.value.draft))
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val draft = state.draft
        val editing = original
        val apiKey = draft.toApiKeyConfig()
        val oauth = draft.toOAuthConfig(editing)
        val preserved = preservedFieldsFor(editing, apiKey, oauth)
        _uiState.value = state.copy(
            isSaving = true,
            saveError = null,
            oauthSecretReentryRequired = false,
            apiKeyReentryRequired = false,
        )
        viewModelScope.launch {
            val name = draft.name.trim()
            val description = draft.description.trim().ifBlank { null }
            val url = draft.url.trim()
            // An edit addresses the stored server (PATCH), a new one does not (POST); see
            // McpRepository.updateServer for why an edit must not be sent as a create.
            val result = if (editing != null) {
                mcpRepository.updateServer(
                    serverName = editing.name,
                    name = name,
                    description = description,
                    url = url,
                    type = draft.type,
                    apiKey = apiKey,
                    oauth = oauth,
                    iconPath = preserved.iconPath,
                    obo = preserved.obo,
                )
            } else {
                mcpRepository.createServer(
                    name = name,
                    description = description,
                    url = url,
                    type = draft.type,
                    apiKey = apiKey,
                    oauth = oauth,
                )
            }
            _uiState.value = when (result) {
                is Result.Success -> _uiState.value.copy(isSaving = false, savedServerName = result.data.name)
                is Result.Error -> {
                    val failure = result.toMcpSaveFailure(fallback = "Failed to save server")
                    _uiState.value.copy(
                        isSaving = false,
                        oauthSecretReentryRequired = failure.secretReentry,
                        apiKeyReentryRequired = failure.keyReentry,
                        saveError = failure.message,
                    )
                }
                is Result.Loading -> _uiState.value
            }
        }
    }

    fun onBackRequested() {
        // A save navigates when it lands; leaving first would let it pop the screen behind this one.
        // Once it has landed, the save's own pop is under way, and a second would take the list with it.
        val state = _uiState.value
        if (state.isSaving || state.savedServerName != null || state.exitRequested) return
        _uiState.value = if (state.hasUnsavedChanges) {
            state.copy(showDiscardConfirm = true)
        } else {
            state.copy(exitRequested = true)
        }
    }

    fun discardChanges() {
        _uiState.value = _uiState.value.copy(showDiscardConfirm = false, exitRequested = true)
    }

    fun dismissDiscardConfirmation() {
        _uiState.value = _uiState.value.copy(showDiscardConfirm = false)
    }
}

private fun McpServerDraft.toApiKeyConfig(): McpApiKeyConfig? =
    if (authMode == McpAuthMode.API_KEY) apiKeyConfigFrom(apiKeySource, apiKeyAuthType, apiKey, apiKeyCustomHeader) else null

private fun McpServerDraft.toOAuthConfig(editing: McpServer?): McpOAuthConfig? =
    if (authMode != McpAuthMode.OAUTH) {
        null
    } else {
        McpOAuthConfig(
            clientId = oauthClientId.trim().ifBlank { null },
            clientSecret = oauthClientSecret.trim().ifBlank { null },
            authorizationUrl = oauthAuthUrl.trim().ifBlank { null },
            tokenUrl = oauthTokenUrl.trim().ifBlank { null },
            scope = oauthScope.trim().ifBlank { null },
            // Not editable here, but carried through: the update route replaces the stored config
            // and treats a dropped method as a changed binding.
            tokenExchangeMethod = editing?.oauth?.tokenExchangeMethod,
        )
    }
