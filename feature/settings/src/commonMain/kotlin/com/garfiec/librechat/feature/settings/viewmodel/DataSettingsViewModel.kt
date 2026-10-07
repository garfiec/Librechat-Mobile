package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.model.MEMORY_KEY_PATTERN_MIN_VERSION
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.DataManagementDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.McpServerDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.MemoryManagementDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot diagnostic-log export payload handed from the ViewModel to the platform file saver. */
@Immutable
data class LogsExportPayload(
    val content: String,
    val fileName: String,
)

@Immutable
data class DataSettingsUiState(
    /**
     * Transient errors surfaced via snackbar; cleared by [DataSettingsViewModel.dismissError]
     * after the snackbar is shown/acted on.
     */
    val error: String? = null,
    // Data management
    val archivedCount: Int = 0,
    val isClearing: Boolean = false,
    val isArchivingAll: Boolean = false,
    /** `POST /api/convos/archive/all` (v0.8.8-rc2). See VERSION_GATES.md. */
    val archiveAllSupported: Boolean = false,
    /** One-shot count from the last successful archive-all, for the confirmation snackbar. */
    val archivedAllCount: Int? = null,
    val showExportComingSoon: Boolean = false,
    // Cache / Keys
    /** Cached images and files, in bytes; null until read. Excludes the database — see
     *  [com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner.cacheSizeBytes]. */
    val cacheSizeBytes: Long? = null,
    val isCacheClearing: Boolean = false,
    val isKeyRevoking: Boolean = false,
    // Diagnostic logs (issue #96)
    val isLogsExporting: Boolean = false,
    val isLogsClearing: Boolean = false,
    val logsBufferBytes: Long = 0,
    /**
     * One-shot export payload. Set when [DataSettingsViewModel.exportLogs] finishes reading the
     * buffer; the screen observes it, hands it to the platform `LogFileSaver`, then calls
     * [DataSettingsViewModel.consumeLogsExport] to clear it (so a recomposition doesn't re-trigger
     * the save). Mirrors the conversations `ExportReady` event but state-based, since Settings
     * has no events SharedFlow.
     */
    val logsExportReady: LogsExportPayload? = null,
    // MCP
    val mcpServers: List<McpServer> = emptyList(),
    val mcpConnectionStatus: Map<String, McpServerStatus> = emptyMap(),
    val mcpError: String? = null,
    val showMcpServerDialog: Boolean = false,
    val editingMcpServer: McpServer? = null,
    val mcpReinitializingServers: Set<String> = emptySet(),
    val mcpReinitializeMessage: String? = null,
    /**
     * The last MCP server save was refused with `OAUTH_SECRET_REENTRY_REQUIRED` — the stored
     * client secret was bound to the OAuth endpoints it was issued for and one of them changed,
     * so the write keeps failing until the secret is supplied again.
     */
    val mcpOAuthSecretReentryRequired: Boolean = false,
    /**
     * The last MCP server save was refused with `API_KEY_REENTRY_REQUIRED` (v0.8.8-rc4) — the
     * retained admin API key was bound to the connection it was entered for and the edit changed
     * it, so the write keeps failing until the key is supplied again.
     */
    val mcpApiKeyReentryRequired: Boolean = false,
    /**
     * Why the last MCP server save failed, shown inside the server dialog rather than through
     * [error]: the dialog stays open on failure and would cover [error]'s snackbar.
     */
    val mcpServerDialogError: String? = null,
    /** The open MCP server dialog's save is in flight; see `McpUiState.isSavingServer`. */
    val mcpServerSaving: Boolean = false,
    // Memories
    val memories: List<Memory> = emptyList(),
    val memoriesEnabled: Boolean = true,
    val showMemoryDialog: Boolean = false,
    val editingMemory: Memory? = null,
    /**
     * Whether the server is KNOWN to enforce the memory-key shape (v0.8.8-rc3+). False on an older
     * or unresolved version, where the key field still shows the hint but does not refuse: rc1 and
     * rc2 accept keys rc3 rejects, so blocking there would withhold a key those servers take.
     */
    val memoryKeyPatternEnforced: Boolean = false,
    // Role-permission gates. `serverMemoriesEnabled` is the SERVER-level MEMORIES.USE
    // gate and is orthogonal to [memoriesEnabled], which is the user's own opt-out
    // stored on their profile (`user.personalization.memories`).
    val mcpServersEnabled: Boolean = true,
    val mcpServersCreateEnabled: Boolean = true,
    val serverMemoriesEnabled: Boolean = true,
)

/** Backs the Data tab: conversations, cache, keys, diagnostic logs, MCP servers and memories. */
// TooManyFunctions: one-line forwarders to the three delegates.
@Suppress("LongParameterList", "TooManyFunctions")
class DataSettingsViewModel(
    cacheCleaner: PlatformCacheCleaner,
    conversationRepository: ConversationRepository,
    keyRepository: KeyRepository,
    diagnosticLogRepository: DiagnosticLogRepository,
    mcpRepository: McpRepository,
    memoryRepository: MemoryRepository,
    userRepository: UserRepository,
    roleRepository: RoleRepository,
    private val permissionGate: PermissionGate,
    configRepository: ConfigRepository,
) : ViewModel() {

    // archiveAllSupported here means "the server hasn't 404'd it"; uiState ANDs in the version gate.
    private val _uiState = MutableStateFlow(DataSettingsUiState(archiveAllSupported = true))
    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    private val dataDelegate = DataManagementDelegate(
        stateHandle,
        cacheCleaner,
        conversationRepository,
        keyRepository,
        diagnosticLogRepository,
    )
    private val mcpDelegate = McpServerDelegate(stateHandle, mcpRepository)
    private val memoryDelegate = MemoryManagementDelegate(stateHandle, memoryRepository, userRepository)

    /**
     * Role gates are permissive while the role is null. `archiveAllSupported` is offered unless
     * the server is KNOWN to predate the route: a dev build reporting the previous release, or one
     * built past the commit-map pin, is the population most likely to HAVE it. See VERSION_GATES.md.
     */
    val uiState: StateFlow<DataSettingsUiState> = combine(
        _uiState,
        roleRepository.userPermissions,
        configRepository.detectedBackendVersion,
        configRepository.detectedBackend,
    ) { state, role, version, backend ->
        state.copy(
            mcpServersEnabled = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.USE),
            mcpServersCreateEnabled = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.CREATE),
            serverMemoriesEnabled = role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.USE),
            archiveAllSupported = state.archiveAllSupported &&
                !BackendVersion.featureSupport(backend, minVersion = "0.8.8-rc2").isRuledOut,
            // Fail-OPEN, unlike the gates above: this one refuses input rather than hiding an
            // affordance, and only rc3+ validates the key server-side.
            memoryKeyPatternEnforced = version != null &&
                BackendVersion.isCompatibleOrNewer(version, MEMORY_KEY_PATTERN_MIN_VERSION),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DataSettingsUiState())

    init {
        memoryDelegate.loadOptOut()
        loadRoleGatedData()
        dataDelegate.loadLogsBufferSize()
        dataDelegate.loadCacheSize()
    }

    /**
     * Gated loads share a single 5-second role-await budget so offline/timeout launches
     * don't serialize into N×5s. `role?.hasAccess(...) != false` preserves permissive
     * default: null role → true, missing type/action → true.
     */
    private fun loadRoleGatedData() {
        viewModelScope.launch {
            val role = permissionGate.awaitRole()
            if (role?.hasAccess(PermissionType.MCP_SERVERS, Permission.USE) != false) {
                mcpDelegate.loadMcpServers()
            }
            if (role?.hasAccess(PermissionType.MEMORIES, Permission.USE) != false) {
                memoryDelegate.loadMemories()
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // Data management
    fun clearAllChats() = dataDelegate.clearAllChats()
    fun archiveAllChats() = dataDelegate.archiveAllChats()
    fun consumeArchivedAllCount() = dataDelegate.consumeArchivedAllCount()
    fun exportAllData() = dataDelegate.exportAllData()
    fun dismissExportComingSoon() = dataDelegate.dismissExportComingSoon()
    fun clearCache() = dataDelegate.clearCache()
    fun revokeAllKeys() = dataDelegate.revokeAllKeys()

    // Diagnostic logs (issue #96)
    fun exportLogs() = dataDelegate.exportLogs()
    fun clearLogs() = dataDelegate.clearLogs()
    fun consumeLogsExport() = dataDelegate.consumeLogsExport()

    // Memory management
    fun showAddMemoryDialog() = memoryDelegate.showAddMemoryDialog()
    fun showEditMemoryDialog(memory: Memory) = memoryDelegate.showEditMemoryDialog(memory)
    fun dismissMemoryDialog() = memoryDelegate.dismissMemoryDialog()
    fun saveMemory(key: String, value: String) = memoryDelegate.saveMemory(key, value)
    fun deleteMemory(memory: Memory) = memoryDelegate.deleteMemory(memory)
    fun toggleMemoriesEnabled(enabled: Boolean) = memoryDelegate.toggleMemoriesEnabled(enabled)

    // MCP server management
    fun showAddMcpServerDialog() = mcpDelegate.showAddMcpServerDialog()
    fun showEditMcpServerDialog(server: McpServer) = mcpDelegate.showEditMcpServerDialog(server)
    fun dismissMcpServerDialog() = mcpDelegate.dismissMcpServerDialog()
    fun saveMcpServer(
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
    ) = mcpDelegate.saveMcpServer(name, description, url, type, apiKey, oauth)
    fun deleteMcpServer(serverName: String) = mcpDelegate.deleteMcpServer(serverName)
    fun reinitializeMcpServer(serverName: String) = mcpDelegate.reinitializeMcpServer(serverName)
    fun dismissMcpReinitializeMessage() = mcpDelegate.dismissMcpReinitializeMessage()
}
