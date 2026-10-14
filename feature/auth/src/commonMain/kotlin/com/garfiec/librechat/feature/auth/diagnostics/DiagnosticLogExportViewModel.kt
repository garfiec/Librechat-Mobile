package com.garfiec.librechat.feature.auth.diagnostics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.logging.diagnosticLogFileName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DiagnosticLogFile(val content: String, val fileName: String)

enum class DiagnosticLogOutcome { Exported, Failed }

@Immutable
data class DiagnosticLogExportState(
    val isExporting: Boolean = false,
    val pendingFile: DiagnosticLogFile? = null,
    val outcome: DiagnosticLogOutcome? = null,
)

/**
 * Exports the diagnostic log from the sign-in screens, where Settings → Data can't be reached.
 * The buffer is already redacted on write (see `LogRedactor`), so it is handed over as is.
 */
class DiagnosticLogExportViewModel(
    private val diagnosticLogRepository: DiagnosticLogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticLogExportState())
    val state: StateFlow<DiagnosticLogExportState> = _state.asStateFlow()

    private var exportJob: Job? = null

    fun export() {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true, outcome = null) }
        exportJob = viewModelScope.launch {
            suspendRunCatching { diagnosticLogRepository.exportText() }
                .onSuccess { content ->
                    val file = DiagnosticLogFile(content, diagnosticLogFileName())
                    _state.update { it.copy(pendingFile = file) }
                }
                .onFailure {
                    _state.update { it.copy(isExporting = false, outcome = DiagnosticLogOutcome.Failed) }
                }
        }
    }

    /** The saver has taken the file. */
    fun consumePendingFile() {
        _state.update { it.copy(pendingFile = null) }
    }

    /** The saver finished. A cancel ([cancelled]) reports nothing. */
    fun onSaveFinished(success: Boolean, cancelled: Boolean) {
        _state.update {
            it.copy(
                isExporting = false,
                outcome = when {
                    success -> DiagnosticLogOutcome.Exported
                    cancelled -> null
                    else -> DiagnosticLogOutcome.Failed
                },
            )
        }
    }

    /**
     * The link left the screen, so the error it sat under is gone. This ViewModel outlives it (it is
     * scoped to the nav entry): without a reset the next error would show this one's outcome, and a
     * file the saver never took would open a picker the user didn't ask for.
     */
    fun onHidden() {
        exportJob?.cancel()
        _state.value = DiagnosticLogExportState()
    }
}
