package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsent
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsentStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.McpOAuthLauncher
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.serversMissingUserVars
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.mcp.McpFailureReasons
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerDiscovery
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.model.mcp.applyDiscoveryAuthorizationState
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val canCreateServer: Boolean = true,
    val connectionStatus: Map<String, McpServerStatus> = emptyMap(),
    /** Per-server verdicts from `GET /api/mcp/tools`, retained so either fetch can re-fold them. */
    val discovery: Map<String, McpServerDiscovery> = emptyMap(),
    val tools: List<McpTool> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val reinitializingServers: Set<String> = emptySet(),
    val error: String? = null,
    val errorRetry: McpRetry = McpRetry.Reload,
    /** The all-tools sheet, opened from the top bar. One server's tools live in its own sheet. */
    val showToolsSheet: Boolean = false,
    /** The server whose sheet is open, by stored name. */
    val selectedServerName: String? = null,
    /** Servers still owed one of their user variables (`auth-values`), which read as needing setup. */
    val missingUserVars: Set<String> = emptySet(),
    /** The server whose variables sheet is open, by stored name. */
    val variablesServerName: String? = null,
    val successMessage: String? = null,
    /**
     * An MCP server that answered a reinitialize by asking the user to authorize it
     * (v0.8.8 `oauthRequired` + `oauthUrl`).
     *
     * Held as state rather than acted on immediately because the consent has to be the user's:
     * silently launching a browser off a "reconnect" tap would hand a third-party provider the
     * session without anyone having agreed to it.
     */
    val pendingOAuth: McpOAuthPrompt? = null,
    /** The consent was given and the sign-in is being bound (`POST /oauth/bind`). */
    val isPreparingOAuth: Boolean = false,
    /**
     * A sign-in the user agreed to, ready to open: [McpOAuthLaunch.InApp] as the sign-in dialog,
     * [McpOAuthLaunch.External] in the system browser (servers before v0.8.9-rc1).
     */
    val oauthLaunch: McpOAuthLaunch? = null,
    /**
     * The user's permission bits per server `dbId`, or null while unknown (not loaded, or the
     * fetch failed). Read it through [mcpServerAccess], never directly.
     */
    val permissions: Map<String, Int>? = null,
    val searchQuery: String = "",
    val attentionOnly: Boolean = false,
    /** Every server, sorted for display; derived in [McpViewModel.uiState], never set directly. */
    val allRows: List<McpServerRow> = emptyList(),
    /** [allRows] narrowed by [searchQuery] and [attentionOnly]: what the list shows. */
    val rows: List<McpServerRow> = emptyList(),
    /** Each server's display title by stored name, for the all-tools sheet's headers. Derived. */
    val serverTitles: Map<String, String> = emptyMap(),
) {
    /** The open server's row; null once it is gone (deleted, or dropped by a reload), which closes its sheet. */
    val selectedRow: McpServerRow? get() = selectedServerName?.let { name -> allRows.firstOrNull { it.name == name } }

    /** The variables sheet's server; null once it is gone, which closes the sheet. */
    val variablesRow: McpServerRow?
        get() = variablesServerName?.let { name -> allRows.firstOrNull { it.name == name && it.userVars.isNotEmpty() } }

    val selectedTools: List<McpTool>
        get() = selectedServerName?.let { name -> tools.filter { it.serverName == name } }.orEmpty()

    val attentionCount: Int get() = allRows.count { it.status.needsAttention }
}

/** The action behind an error, so Retry repeats it rather than only reloading. */
sealed interface McpRetry {
    data object Reload : McpRetry
    data class Delete(val serverName: String) : McpRetry
    data class Reconnect(val serverName: String) : McpRetry
}

/** A server waiting on the user to authorize it, with the provider URL to send them to. */
data class McpOAuthPrompt(
    val serverName: String,
    val authorizationUrl: String,
)

class McpViewModel(
    private val mcpRepository: McpRepository,
    roleRepository: RoleRepository,
    private val permissionsRepository: PermissionsRepository,
    serverDataStore: ServerDataStore,
    private val oauthLauncher: McpOAuthLauncher,
    private val appLocationConsent: McpAppLocationConsentStore,
) : ViewModel() {

    /**
     * Each server's answer about its MCP Apps using the device's approximate location; a server
     * never asked has none, and its sheet shows no row.
     */
    val appLocationConsents: StateFlow<Map<String, McpAppLocationConsent>> = appLocationConsent.consents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The server sheet's Location switch. */
    fun setAppLocationAllowed(serverName: String, allowed: Boolean) {
        viewModelScope.launch {
            appLocationConsent.set(
                serverName,
                if (allowed) McpAppLocationConsent.ALLOWED else McpAppLocationConsent.REFUSED,
            )
        }
    }

    private val _uiState = MutableStateFlow(McpUiState())

    /** MCP_SERVERS CREATE gates adding a server; permissive while the role is unknown. */
    val uiState: StateFlow<McpUiState> =
        combine(_uiState, roleRepository.userPermissions, serverDataStore.currentUrlFlow) { state, role, baseUrl ->
            val allRows = mcpServerRows(
                servers = state.servers,
                connectionStatus = state.connectionStatus,
                toolCounts = state.tools.groupingBy { it.serverName.orEmpty() }.eachCount(),
                reinitializing = state.reinitializingServers,
                permissions = state.permissions,
                baseUrl = baseUrl,
                missingUserVars = state.missingUserVars,
            )
            // The filter outlives the servers needing attention: once none do, show everything.
            val attentionOnly = state.attentionOnly && allRows.any { it.status.needsAttention }
            state.copy(
                canCreateServer = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.CREATE),
                allRows = allRows,
                attentionOnly = attentionOnly,
                rows = filterMcpServerRows(allRows, state.searchQuery, attentionOnly),
                serverTitles = allRows.associate { it.name to it.title },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, McpUiState())

    /** The first resume follows `init`'s load; only later ones are returns from the editor. */
    private var resumedOnce = false

    init {
        loadAll()
    }

    /** Everything the page shows; permissions too, since a new server's id isn't in the map until refetched. */
    private fun loadAll() {
        loadServers()
        loadConnectionStatus()
        loadTools()
        loadPermissions()
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.servers.isEmpty())
            when (val result = mcpRepository.listServers()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        servers = result.data,
                        isLoading = false,
                        isRefreshing = false,
                        // With an empty list the error banner holds the error rather than a
                        // snackbar, so a successful load is what retires it.
                        error = if (_uiState.value.servers.isEmpty()) null else _uiState.value.error,
                    )
                    // Read before copying: the reads suspend, and a snapshot taken first would undo other loads.
                    val missing = mcpRepository.serversMissingUserVars(result.data)
                    _uiState.value = _uiState.value.copy(missingUserVars = missing)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message ?: "Failed to load servers",
                        errorRetry = McpRetry.Reload,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadConnectionStatus() {
        viewModelScope.launch {
            when (val result = mcpRepository.getConnectionStatus()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = applyDiscoveryAuthorizationState(
                            result.data,
                            _uiState.value.discovery,
                        ),
                    )
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to load connection status: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadTools() {
        viewModelScope.launch {
            when (val result = mcpRepository.getTools()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        tools = result.data.tools,
                        discovery = result.data.servers,
                        // Discovery can notice a lapsed authorization before the status route
                        // does, so re-fold whichever of the two arrived second.
                        connectionStatus = applyDiscoveryAuthorizationState(
                            _uiState.value.connectionStatus,
                            result.data.servers,
                        ),
                    )
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to load tools: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadPermissions() {
        viewModelScope.launch {
            when (val result = permissionsRepository.getAllEffectivePermissions(ResourceType.MCP_SERVER)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(permissions = result.data)
                is Result.Error -> {
                    // Keep the last known map: a failed refresh shouldn't flip every server's access.
                    Logger.d(result.exception) { "Failed to load MCP server permissions: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadAll()
    }

    /**
     * Reloads when the screen resumes after the first time, which is a return from the editor (or
     * from the browser after an OAuth sign-in), so a saved or authorized server shows its new state.
     * Quiet: no pull-to-refresh spinner for a reload the user didn't ask for.
     */
    fun refreshOnReturn() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        loadAll()
    }

    fun deleteServer(serverName: String) {
        viewModelScope.launch {
            when (val result = mcpRepository.deleteServer(serverName)) {
                is Result.Success -> {
                    if (_uiState.value.selectedServerName == serverName) dismissServer()
                    loadServers()
                    loadConnectionStatus()
                    // Otherwise the all-tools sheet still lists the deleted server's tools.
                    loadTools()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to delete server",
                        errorRetry = McpRetry.Delete(serverName),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun reinitializeServer(serverName: String) {
        // One at a time: the first to land would clear the spinner while the second still runs.
        if (serverName in _uiState.value.reinitializingServers) return
        _uiState.value = _uiState.value.copy(
            reinitializingServers = _uiState.value.reinitializingServers + serverName,
        )
        viewModelScope.launch {
            when (val result = mcpRepository.reinitialize(serverName)) {
                is Result.Success -> {
                    val response = result.data
                    // An oauthRequired ack is NOT a connection: the server is telling us it cannot
                    // proceed until the user authorizes it. Reporting it as "initialized
                    // successfully" leaves the user staring at a server that never connects.
                    val oauthUrl = response.authorizationUrl
                    // Owed variables open their sheet rather than echoing the server's message.
                    val needsVariables = response.failureReason == McpFailureReasons.MISSING_CUSTOM_USER_VARS &&
                        _uiState.value.servers.any { it.name == serverName && it.customUserVars.isNotEmpty() }
                    val message = when {
                        oauthUrl != null || needsVariables -> null
                        response.connectionDeferred == true -> DEFERRED_MARKER
                        else -> response.message ?: if (response.success) {
                            "Server initialized successfully"
                        } else {
                            "Failed to initialize server"
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        reinitializingServers = _uiState.value.reinitializingServers - serverName,
                        successMessage = message,
                        pendingOAuth = oauthUrl?.let { McpOAuthPrompt(serverName, it) },
                        variablesServerName = if (needsVariables) serverName else _uiState.value.variablesServerName,
                        // In place of the server sheet, as openVariables does.
                        selectedServerName = if (needsVariables) null else _uiState.value.selectedServerName,
                    )
                    loadServers()
                    loadConnectionStatus()
                    // A server that just connected has tools to count and list.
                    loadTools()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        reinitializingServers = _uiState.value.reinitializingServers - serverName,
                        error = result.message ?: "Failed to reinitialize server",
                        errorRetry = McpRetry.Reconnect(serverName),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /** Dismisses the consent prompt without authorizing. The server stays unconnected. */
    fun dismissOAuthPrompt() {
        // Also withdraws a bind still in flight, so the sign-in does not open after the user left.
        _uiState.value = _uiState.value.copy(pendingOAuth = null, isPreparingOAuth = false)
    }

    /**
     * The user agreed. Binds the browser to the flow (see [McpOAuthLauncher]) and hands the screen
     * what to open. The bind happens here, at the tap, because its CSRF cookie lives ten minutes.
     */
    fun continueOAuth() {
        val prompt = _uiState.value.pendingOAuth ?: return
        if (_uiState.value.isPreparingOAuth) return
        _uiState.value = _uiState.value.copy(isPreparingOAuth = true)
        viewModelScope.launch {
            val result = oauthLauncher.prepare(prompt.serverName, prompt.authorizationUrl)
            if (!_uiState.value.isPreparingOAuth) return@launch
            when (result) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    pendingOAuth = null,
                    isPreparingOAuth = false,
                    oauthLaunch = result.data,
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    pendingOAuth = null,
                    isPreparingOAuth = false,
                    error = result.message ?: OAUTH_FAILED_MARKER,
                    errorRetry = McpRetry.Reconnect(prompt.serverName),
                )
                Result.Loading -> Unit
            }
        }
    }

    /**
     * The system browser was opened (an [McpOAuthLaunch.External] sign-in). The connection is not
     * re-checked here: the authorization happens out of process, and the screen's ON_RESUME reload
     * picks it up when the user is back.
     */
    fun onOAuthLaunched() {
        _uiState.value = _uiState.value.copy(oauthLaunch = null)
    }

    /**
     * The in-app sign-in ended. It fires no ON_RESUME, so the reload happens here: a server that
     * just authorized shows connected with its tools.
     */
    fun onOAuthOutcome(outcome: McpOAuthOutcome) {
        val launch = _uiState.value.oauthLaunch ?: return
        _uiState.value = when (outcome) {
            McpOAuthOutcome.Success -> _uiState.value.copy(oauthLaunch = null, successMessage = OAUTH_SIGNED_IN_MARKER)
            is McpOAuthOutcome.Error -> _uiState.value.copy(
                oauthLaunch = null,
                error = OAUTH_FAILED_MARKER,
                errorRetry = McpRetry.Reconnect(launch.serverName),
            )
            McpOAuthOutcome.Abandoned -> _uiState.value.copy(oauthLaunch = null)
        }
        loadServers()
        loadConnectionStatus()
        loadTools()
    }

    /** A card's or server sheet's "Set up" / "Variables". */
    fun openVariables(serverName: String) {
        // In place of the server sheet: two modal sheets stacked would leave this one behind it.
        _uiState.value = _uiState.value.copy(variablesServerName = serverName, selectedServerName = null)
    }

    fun dismissVariables() {
        _uiState.value = _uiState.value.copy(variablesServerName = null)
    }

    /** The variables sheet's Set / Not set; null when the read failed. */
    suspend fun userVarFlags(serverName: String): Map<String, Boolean>? =
        (mcpRepository.getUserVarFlags(serverName) as? Result.Success)?.data

    /**
     * Saves the sheet's [values] (none: connect only), then reconnects, as saving drops the
     * connection. In the ViewModel's scope, so closing the sheet doesn't cancel it.
     */
    suspend fun saveVariables(serverName: String, values: Map<String, String>): Boolean = viewModelScope.async {
        if (values.isNotEmpty()) {
            val result = mcpRepository.saveUserVars(serverName, values)
            if (result is Result.Error) {
                Logger.d(result.exception) { "Failed to save MCP variables: ${result.message}" }
                return@async false
            }
        }
        reinitializeServer(serverName)
        true
    }.await()

    /** Deletes the server's variables (the server revokes its OAuth tokens too) and reloads. */
    suspend fun removeVariables(serverName: String): Boolean = viewModelScope.async {
        when (val result = mcpRepository.removeUserVars(serverName)) {
            is Result.Success -> {
                loadAll()
                true
            }
            is Result.Error -> {
                Logger.d(result.exception) { "Failed to remove MCP variables: ${result.message}" }
                false
            }
            Result.Loading -> false
        }
    }.await()

    fun showToolsSheet() {
        _uiState.value = _uiState.value.copy(showToolsSheet = true)
    }

    fun dismissToolsSheet() {
        _uiState.value = _uiState.value.copy(showToolsSheet = false)
    }

    fun openServer(name: String) {
        _uiState.value = _uiState.value.copy(selectedServerName = name)
    }

    fun dismissServer() {
        _uiState.value = _uiState.value.copy(selectedServerName = null)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setAttentionOnly(attentionOnly: Boolean) {
        _uiState.value = _uiState.value.copy(attentionOnly = attentionOnly)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null, errorRetry = McpRetry.Reload)
    }

    fun retry(action: McpRetry) {
        when (action) {
            McpRetry.Reload -> refresh()
            is McpRetry.Delete -> deleteServer(action.serverName)
            is McpRetry.Reconnect -> reinitializeServer(action.serverName)
        }
    }

    fun dismissSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    companion object {
        /**
         * Sentinel the screen swaps for a localized string.
         *
         * The VM has no access to compose resources, and a deferred connection is the one
         * reinitialize outcome whose wording is ours rather than the server's.
         */
        const val DEFERRED_MARKER = "mcp_connection_deferred"

        /** [McpUiState.successMessage] sentinel: the in-app sign-in reached the server's success page. */
        const val OAUTH_SIGNED_IN_MARKER = "mcp_oauth_signed_in"

        /** [McpUiState.error] sentinel: the sign-in could not be bound, or the server refused its callback. */
        const val OAUTH_FAILED_MARKER = "mcp_oauth_failed"
    }
}
