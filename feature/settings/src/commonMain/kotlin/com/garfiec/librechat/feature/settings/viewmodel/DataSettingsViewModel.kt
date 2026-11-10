package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.DataManagementDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    val isClearing: Boolean = false,
    val isArchivingAll: Boolean = false,
    /** `POST /api/convos/archive/all` (v0.8.8-rc2). See VERSION_GATES.md. */
    val archiveAllSupported: Boolean = false,
    /** One-shot count from the last successful archive-all, for the confirmation snackbar. */
    val archivedAllCount: Int? = null,
    // Cache
    /** Cached images and files, in bytes; null until read. Excludes the database — see
     *  [com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner.cacheSizeBytes]. */
    val cacheSizeBytes: Long? = null,
    val isCacheClearing: Boolean = false,
    // Diagnostic logs (issue #96)
    val isLogsExporting: Boolean = false,
    val isLogsClearing: Boolean = false,
    val logsBufferBytes: Long = 0,
    /**
     * One-shot export payload. Set when [DataSettingsViewModel.exportLogs] finishes reading the
     * buffer; the screen observes it, hands it to the platform `TextFileSaver`, then calls
     * [DataSettingsViewModel.consumeLogsExport] to clear it (so a recomposition doesn't re-trigger
     * the save). Mirrors the conversations `ExportReady` event but state-based, since Settings
     * has no events SharedFlow.
     */
    val logsExportReady: LogsExportPayload? = null,
    /** `agents.capabilities` has `stateful_code_sessions`, web's gate for Code execution. */
    val codeExecutionEnabled: Boolean = false,
)

/** Backs Privacy & data and Advanced's troubleshooting rows: conversations, cache and diagnostic logs. */
// TooManyFunctions: one-line forwarders to the data delegate.
@Suppress("TooManyFunctions")
class DataSettingsViewModel(
    cacheCleaner: PlatformCacheCleaner,
    conversationRepository: ConversationRepository,
    diagnosticLogRepository: DiagnosticLogRepository,
    configRepository: ConfigRepository,
) : ViewModel() {

    // archiveAllSupported here means "the server hasn't 404'd it"; uiState ANDs in the version gate.
    private val _uiState = MutableStateFlow(DataSettingsUiState(archiveAllSupported = true))
    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    private val dataDelegate = DataManagementDelegate(
        stateHandle,
        cacheCleaner,
        conversationRepository,
        diagnosticLogRepository,
    )

    /**
     * `archiveAllSupported` is offered unless the server is KNOWN to predate the route: a dev build
     * reporting the previous release, or one built past the commit-map pin, is the population most
     * likely to HAVE it. See VERSION_GATES.md.
     */
    val uiState: StateFlow<DataSettingsUiState> = combine(
        _uiState,
        configRepository.detectedBackend,
        configRepository.endpointConfigs,
    ) { state, backend, endpoints ->
        state.copy(
            codeExecutionEnabled = endpoints["agents"]?.capabilities?.contains(STATEFUL_CODE_SESSIONS) == true,
            archiveAllSupported = state.archiveAllSupported &&
                !BackendVersion.featureSupport(backend, minVersion = "0.8.8-rc2").isRuledOut,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DataSettingsUiState())

    init {
        dataDelegate.loadLogsBufferSize()
    }

    /** Measures the cache for Privacy's Clear cache row. */
    fun loadCacheSize() = dataDelegate.loadCacheSize()

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // Data management
    fun clearAllChats() = dataDelegate.clearAllChats()
    fun archiveAllChats() = dataDelegate.archiveAllChats()
    fun consumeArchivedAllCount() = dataDelegate.consumeArchivedAllCount()
    fun clearCache() = dataDelegate.clearCache()

    // Diagnostic logs (issue #96)
    fun exportLogs() = dataDelegate.exportLogs()
    fun clearLogs() = dataDelegate.clearLogs()
    fun consumeLogsExport() = dataDelegate.consumeLogsExport()
}

private const val STATEFUL_CODE_SESSIONS = "stateful_code_sessions"
