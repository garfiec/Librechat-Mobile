package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.DataManagementDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    // Summary rows for the Memories and MCP servers pages; null until read.
    val memoryCount: Int? = null,
    /** The user's own opt-out (`user.personalization.memories`); off shows the row as "Off". */
    val memoriesEnabled: Boolean = true,
    val mcpServerCount: Int? = null,
    // Role-permission gates. `serverMemoriesEnabled` is the SERVER-level MEMORIES.USE
    // gate and is orthogonal to [memoriesEnabled]. Each hides its row entirely.
    val mcpServersEnabled: Boolean = true,
    val serverMemoriesEnabled: Boolean = true,
)

/** Backs the Data tab: conversations, cache, keys, diagnostic logs, and the Memories / MCP summary rows. */
// TooManyFunctions: one-line forwarders to the data delegate.
@Suppress("LongParameterList", "TooManyFunctions")
class DataSettingsViewModel(
    cacheCleaner: PlatformCacheCleaner,
    conversationRepository: ConversationRepository,
    keyRepository: KeyRepository,
    diagnosticLogRepository: DiagnosticLogRepository,
    private val mcpRepository: McpRepository,
    private val memoryRepository: MemoryRepository,
    private val userRepository: UserRepository,
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

    /**
     * Role gates are permissive while the role is null. `archiveAllSupported` is offered unless
     * the server is KNOWN to predate the route: a dev build reporting the previous release, or one
     * built past the commit-map pin, is the population most likely to HAVE it. See VERSION_GATES.md.
     */
    val uiState: StateFlow<DataSettingsUiState> = combine(
        _uiState,
        roleRepository.userPermissions,
        configRepository.detectedBackend,
    ) { state, role, backend ->
        state.copy(
            mcpServersEnabled = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.USE),
            serverMemoriesEnabled = role.hasAccessOrPermissive(PermissionType.MEMORIES, Permission.USE),
            archiveAllSupported = state.archiveAllSupported &&
                !BackendVersion.featureSupport(backend, minVersion = "0.8.8-rc2").isRuledOut,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DataSettingsUiState())

    init {
        dataDelegate.loadLogsBufferSize()
        dataDelegate.loadCacheSize()
    }

    /**
     * Re-reads what the Memories and MCP rows summarise. The screen calls it on every resume, so
     * the rows catch up after the user edits on either page and comes back.
     */
    fun refreshSummaries() {
        // The pager recomposes this page on tab swipes; one refresh at a time is enough.
        if (summariesJob?.isActive == true) return
        summariesJob = viewModelScope.launch {
            launch { loadOptOut() }
            loadRoleGatedCounts()
        }
    }

    private var summariesJob: Job? = null

    /** The opt-out lives on the user profile; an absent block is the server default (on). */
    private suspend fun loadOptOut() {
        when (val result = userRepository.getUser()) {
            is Result.Success -> _uiState.update {
                it.copy(memoriesEnabled = result.data.personalization?.memories ?: true)
            }
            is Result.Error -> Logger.d(result.exception) { "Failed to load memories opt-out: ${result.message}" }
            is Result.Loading -> { /* no-op */ }
        }
    }

    /**
     * Gated loads share a single 5-second role-await budget so offline/timeout launches
     * don't serialize into N×5s. `role?.hasAccess(...) != false` preserves permissive
     * default: null role → true, missing type/action → true.
     */
    private suspend fun loadRoleGatedCounts() {
        coroutineScope {
            val role = permissionGate.awaitRole()
            if (role?.hasAccess(PermissionType.MCP_SERVERS, Permission.USE) != false) {
                launch {
                    val result = mcpRepository.listServers()
                    if (result is Result.Success) _uiState.update { it.copy(mcpServerCount = result.data.size) }
                }
            }
            if (role?.hasAccess(PermissionType.MEMORIES, Permission.USE) != false) {
                launch {
                    val result = memoryRepository.getMemories()
                    if (result is Result.Success) _uiState.update { it.copy(memoryCount = result.data.size) }
                }
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
}
