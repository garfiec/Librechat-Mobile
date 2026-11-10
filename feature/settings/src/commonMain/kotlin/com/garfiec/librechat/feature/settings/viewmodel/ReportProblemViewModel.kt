package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.logging.PlatformInfo
import com.garfiec.librechat.core.logging.diagnosticLogFileName
import com.garfiec.librechat.core.ui.media.EmailAttachment
import com.garfiec.librechat.core.ui.media.EmailComposeResult
import com.garfiec.librechat.core.ui.media.EmailDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where every report goes, on every distribution channel. */
internal const val SUPPORT_EMAIL = "appsupport@garfiec.com"

enum class ReportKind { PROBLEM, SUGGESTION }

@Immutable
data class ReportProblemUiState(
    val kind: ReportKind = ReportKind.PROBLEM,
    val text: String = "",
    val includeLog: Boolean = true,
    val logBytes: Long = 0,
    /** Exactly About's Copy diagnostics text. */
    val details: String = "",
    val preparing: Boolean = false,
    val mailOpened: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val showDetailsSheet: Boolean = false,
    val showSendAnotherWay: Boolean = false,
    val exitRequested: Boolean = false,
    /** A read log waiting for the platform file saver (the details sheet's Save a copy). */
    val logExport: LogsExportPayload? = null,
) {
    val canContinue: Boolean get() = text.isNotBlank() && !preparing

    /** Typed text that hasn't been handed to a mail app since it last changed; back asks first. */
    val hasDraftToLose: Boolean get() = text.isNotBlank() && !mailOpened
}

/**
 * Settings › About › Report a problem: a short form that becomes an email in the user's own mail app.
 * The subject prefix and the details block stay English whatever the app language, for inbox filters.
 */
class ReportProblemViewModel(
    private val diagnosticLogRepository: DiagnosticLogRepository,
    configRepository: ConfigRepository,
    appInfo: AppInfo,
    platformInfo: PlatformInfo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportProblemUiState())
    val uiState: StateFlow<ReportProblemUiState> = _uiState.asStateFlow()

    init {
        val device = diagnosticsDevice(platformInfo)
        viewModelScope.launch {
            combine(configRepository.startupConfig, configRepository.detectedBackendVersion) { config, version ->
                aboutDiagnostics(appInfo.versionName, appInfo.gitSha, version, config?.buildInfo, device)
            }.collect { details -> _uiState.update { it.copy(details = details) } }
        }
        viewModelScope.launch {
            suspendRunCatching { diagnosticLogRepository.bufferSizeBytes() }
                .onSuccess { bytes -> _uiState.update { it.copy(logBytes = bytes) } }
                .onFailure { e -> Logger.d(e) { "Failed to read diagnostic log buffer size" } }
        }
    }

    fun setKind(kind: ReportKind) = _uiState.update { it.copy(kind = kind) }

    fun setText(text: String) = _uiState.update { it.copy(text = text, mailOpened = false) }

    fun setIncludeLog(include: Boolean) = _uiState.update { it.copy(includeLog = include) }

    fun showDetails() = _uiState.update { it.copy(showDetailsSheet = true) }

    fun dismissDetails() = _uiState.update { it.copy(showDetailsSheet = false) }

    fun dismissSendAnotherWay() = _uiState.update { it.copy(showSendAnotherWay = false) }

    /**
     * The email as it will be sent. The log is read only when it is included; an empty or unreadable
     * log goes as not included, and the body says so.
     */
    suspend fun buildDraft(): EmailDraft {
        _uiState.update { it.copy(preparing = true) }
        try {
            val state = _uiState.value
            val log = if (state.includeLog) {
                suspendRunCatching {
                    diagnosticLogRepository.exportText()
                        .takeIf { it.isNotEmpty() }
                        ?.let { withContext(Dispatchers.Default) { it.encodeToByteArray() } }
                }
                    .onFailure { e -> Logger.w(e) { "Failed to read the diagnostic log for a report" } }
                    .getOrNull()
            } else {
                null
            }
            return EmailDraft(
                to = SUPPORT_EMAIL,
                subject = reportSubject(state.kind, state.text),
                body = reportBody(state.text, state.details, logIncluded = log != null),
                attachment = log?.let { EmailAttachment(diagnosticLogFileName(), "text/plain", it) },
            )
        } finally {
            _uiState.update { it.copy(preparing = false) }
        }
    }

    /** Subject and body for pasting into another app, which carries no log. */
    fun reportText(): String {
        val state = _uiState.value
        return reportSubject(state.kind, state.text) + "\n\n" + reportBody(state.text, state.details, logIncluded = false)
    }

    fun onComposeResult(result: EmailComposeResult) {
        when (result) {
            EmailComposeResult.Opened -> _uiState.update { it.copy(mailOpened = true) }
            EmailComposeResult.Sent -> _uiState.update { it.copy(exitRequested = true) }
            EmailComposeResult.Cancelled -> Unit
            EmailComposeResult.NoMailApp -> _uiState.update { it.copy(showSendAnotherWay = true) }
        }
    }

    /** System back and the top bar's back (the only back on iOS). */
    fun onBackRequested() = _uiState.update {
        if (it.hasDraftToLose) it.copy(showDiscardConfirm = true) else it.copy(exitRequested = true)
    }

    fun discardChanges() = _uiState.update { it.copy(showDiscardConfirm = false, exitRequested = true) }

    fun dismissDiscardConfirmation() = _uiState.update { it.copy(showDiscardConfirm = false) }

    fun saveLogCopy() {
        viewModelScope.launch {
            suspendRunCatching { diagnosticLogRepository.exportText() }
                .onSuccess { content ->
                    _uiState.update { it.copy(logExport = LogsExportPayload(content, diagnosticLogFileName())) }
                }
                .onFailure { e -> Logger.w(e) { "Failed to read the diagnostic log for a copy" } }
        }
    }

    fun consumeLogExport() = _uiState.update { it.copy(logExport = null) }
}

private const val SUBJECT_MAX = 60

internal fun reportSubject(kind: ReportKind, text: String): String {
    val prefix = when (kind) {
        ReportKind.PROBLEM -> "Switchboard problem: "
        ReportKind.SUGGESTION -> "Switchboard suggestion: "
    }
    val firstLine = text.trim().lineSequence().first().trim()
    val cut = if (firstLine.length > SUBJECT_MAX) {
        firstLine.take(SUBJECT_MAX).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }.trimEnd() + "…"
    } else {
        firstLine
    }
    return prefix + cut
}

internal fun reportBody(text: String, details: String, logIncluded: Boolean): String =
    text.trim() + "\n\n——\n" + details + "\nDiagnostic log: " + if (logIncluded) "attached" else "not included"
