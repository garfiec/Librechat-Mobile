package com.garfiec.librechat.feature.settings.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.LogsExportPayload
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.SettingsUiState
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Handles clear conversations, export, cache clearing, and key revocation.
 */
class DataManagementDelegate(
    private val stateHandle: SettingsStateHandle<SettingsUiState>,
    private val cacheCleaner: PlatformCacheCleaner,
    private val conversationRepository: ConversationRepository,
    private val keyRepository: KeyRepository,
    private val diagnosticLogRepository: DiagnosticLogRepository,
) {

    fun clearAllChats() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isClearing = true) }
            when (val result = conversationRepository.deleteAll()) {
                is Result.Success -> {
                    stateHandle.update { copy(isClearing = false) }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isClearing = false,
                            error = result.message ?: "Failed to clear conversations",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * v0.8.8-rc2. The gate offers this on every server the version check cannot rule out, so a 404
     * here is a real outcome rather than a failure: the route simply is not there, and saying
     * "failed" would send the user looking for a problem on their end.
     */
    fun archiveAllChats() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isArchivingAll = true, archivedAllCount = null) }
            when (val result = conversationRepository.archiveAll()) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(isArchivingAll = false, archivedAllCount = result.data)
                    }
                }
                is Result.Error -> {
                    val missing = (result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND
                    stateHandle.update {
                        copy(
                            isArchivingAll = false,
                            archiveAllSupported = !missing && archiveAllSupported,
                            error = if (missing) {
                                "This server doesn't support archiving everything at once."
                            } else {
                                result.message ?: "Failed to archive conversations"
                            },
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /** Clears the consumed count so a recomposition won't re-announce it. */
    fun consumeArchivedAllCount() {
        stateHandle.update { copy(archivedAllCount = null) }
    }

    fun exportAllData() {
        stateHandle.update { copy(showExportComingSoon = true) }
    }

    fun dismissExportComingSoon() {
        stateHandle.update { copy(showExportComingSoon = false) }
    }

    // ── Diagnostic logs (issue #96) ────────────────────────────────

    /** Loads the current on-disk buffer size into state so the UI can label the export button. */
    fun loadLogsBufferSize() {
        stateHandle.scope.launch {
            suspendRunCatching { diagnosticLogRepository.bufferSizeBytes() }
                .onSuccess { bytes -> stateHandle.update { copy(logsBufferBytes = bytes) } }
                .onFailure { e -> Logger.d(e) { "Failed to read diagnostic log buffer size" } }
        }
    }

    /**
     * Reads the redacted JSONL buffer and stashes it in [SettingsUiState.logsExportReady] as a
     * one-shot payload. The screen observes it, launches the platform file saver, and calls
     * [consumeLogsExport] once handled.
     */
    fun exportLogs() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isLogsExporting = true) }
            suspendRunCatching { diagnosticLogRepository.exportText() }
                .onSuccess { content ->
                    val fileName = "switchboard-logs-${Clock.System.now().toEpochMilliseconds()}.log"
                    stateHandle.update {
                        copy(
                            isLogsExporting = false,
                            logsExportReady = LogsExportPayload(content = content, fileName = fileName),
                        )
                    }
                }
                .onFailure { e ->
                    stateHandle.update {
                        copy(
                            isLogsExporting = false,
                            error = e.message ?: "Failed to export diagnostic logs",
                        )
                    }
                }
        }
    }

    /** Clears the consumed export payload so a recomposition won't re-trigger the file save. */
    fun consumeLogsExport() {
        stateHandle.update { copy(logsExportReady = null) }
    }

    /** Clears both log segments, then refreshes the displayed buffer size. */
    fun clearLogs() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isLogsClearing = true) }
            suspendRunCatching {
                diagnosticLogRepository.clear()
                diagnosticLogRepository.bufferSizeBytes()
            }.onSuccess { bytes ->
                stateHandle.update { copy(isLogsClearing = false, logsBufferBytes = bytes) }
            }.onFailure { e ->
                stateHandle.update {
                    copy(
                        isLogsClearing = false,
                        error = e.message ?: "Failed to clear diagnostic logs",
                    )
                }
            }
        }
    }

    fun loadCacheSize() {
        stateHandle.scope.launch {
            suspendRunCatching { cacheCleaner.cacheSizeBytes() }
                .onSuccess { bytes -> stateHandle.update { copy(cacheSizeBytes = bytes) } }
                .onFailure { e -> Logger.d(e) { "Failed to read cache size" } }
        }
    }

    fun clearCache() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isCacheClearing = true) }
            suspendRunCatching {
                cacheCleaner.clearCache()
                // Re-read rather than assume zero: the directory is shared, and something may have
                // written to it between the walk and the delete.
                cacheCleaner.cacheSizeBytes()
            }.onSuccess { bytes ->
                stateHandle.update { copy(isCacheClearing = false, cacheSizeBytes = bytes) }
            }.onFailure { e ->
                stateHandle.update {
                    copy(
                        isCacheClearing = false,
                        error = e.message ?: "Failed to clear cache",
                    )
                }
            }
        }
    }

    fun revokeAllKeys() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isKeyRevoking = true) }
            when (val result = keyRepository.deleteAllKeys()) {
                is Result.Success -> {
                    stateHandle.update { copy(isKeyRevoking = false) }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isKeyRevoking = false,
                            error = result.message ?: "Failed to revoke API keys",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

/** A server predating `POST /api/convos/archive/all`. */
private const val HTTP_NOT_FOUND = 404
