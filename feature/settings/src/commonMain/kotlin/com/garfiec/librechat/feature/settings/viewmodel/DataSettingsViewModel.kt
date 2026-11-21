package com.garfiec.librechat.feature.settings.viewmodel

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
