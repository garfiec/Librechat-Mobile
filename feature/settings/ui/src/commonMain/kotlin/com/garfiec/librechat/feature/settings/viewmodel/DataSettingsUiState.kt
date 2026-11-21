package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

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
