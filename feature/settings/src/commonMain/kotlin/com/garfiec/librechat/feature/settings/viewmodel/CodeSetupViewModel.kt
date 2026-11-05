package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeMachinesRepository
import com.garfiec.librechat.core.data.repository.CodeSetupError
import com.garfiec.librechat.core.data.repository.CodeSetupOutcome
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.code.CodeSetupAttempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Web's status alert for an attempt. */
enum class CodeSetupStatusText { PENDING, PROVISIONING, APPROVED, COMPLETED, DENIED, CANCELLED, EXPIRED, FAILED, RETRYABLE }

@Immutable
data class CodeSetupDetails(
    val machineName: String,
    val verificationCode: String,
    val keyFingerprint: String,
    /** `private` or `directory`. */
    val directoryWorkspace: Boolean,
    /** ISO 8601, formatted by the page. */
    val expiresIso: String?,
    val allowWrites: Boolean,
    val allowCommands: Boolean,
    /** The bound plane's name; null when none is bound, [planeMissing] when it is gone. */
    val planeName: String?,
    val planeMissing: Boolean,
)

@Immutable
data class CodeSetupUiState(
    val account: String? = null,
    val loading: Boolean = true,
    /** The attempt can't be shown: not this account's, expired or withdrawn. No retry. */
    val unavailable: Boolean = false,
    /** A load failed for another reason: Retry. */
    val loadFailed: Boolean = false,
    val status: CodeSetupStatusText? = null,
    val details: CodeSetupDetails? = null,
    /** Approve / Deny are offered (pending, or a retryable failure, and not expired). */
    val decisionOpen: Boolean = false,
    val planes: List<CodeControlPlaneOption> = emptyList(),
    val selectedPlaneId: String? = null,
    val deciding: Boolean = false,
    /** A decision failed for a reason that leaves the attempt as it was. */
    val decisionFailed: Boolean = false,
) {
    val canApprove: Boolean get() = decisionOpen && !deciding && planes.any { it.id == selectedPlaneId }
}

/**
 * Approves or denies a machine's setup request, web's `client/src/components/Code/Setup.tsx`. The
 * attempt is re-read every [POLL_MS] while it is pending, provisioning or approved, and flips to
 * expired at its deadline. Nothing is approved until the user taps Approve with a plane picked.
 */
class CodeSetupViewModel(
    private val attemptId: String,
    private val machines: CodeMachinesRepository,
    private val users: UserRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodeSetupUiState())
    val uiState: StateFlow<CodeSetupUiState> = _uiState.asStateFlow()

    private var attempt: CodeSetupAttempt? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val user = (users.getUser() as? Result.Success)?.data
            _uiState.update { it.copy(account = user?.email?.takeIf(String::isNotBlank) ?: user?.name ?: user?.id) }
        }
        load()
    }

    fun load() {
        _uiState.update { it.copy(loadFailed = false, loading = attempt == null) }
        viewModelScope.launch { apply(machines.setupAttempt(attemptId), fromDecision = false) }
    }

    fun selectPlane(id: String) = _uiState.update { it.copy(selectedPlaneId = id) }

    fun approve() {
        val plane = _uiState.value.selectedPlaneId ?: return
        if (!_uiState.value.canApprove) return
        decide { machines.approveSetup(attemptId, plane) }
    }

    fun deny() {
        if (!_uiState.value.decisionOpen || _uiState.value.deciding) return
        decide { machines.denySetup(attemptId) }
    }

    private fun decide(call: suspend () -> CodeSetupOutcome) {
        _uiState.update { it.copy(deciding = true, decisionFailed = false) }
        viewModelScope.launch {
            val outcome = call()
            _uiState.update { it.copy(deciding = false) }
            apply(outcome, fromDecision = true)
            // Web always re-reads after a decision, so the page shows the attempt's real state.
            if (outcome is CodeSetupOutcome.Failed && outcome.error != CodeSetupError.UNAVAILABLE) {
                apply(machines.setupAttempt(attemptId), fromDecision = false)
            }
        }
    }

    private fun apply(outcome: CodeSetupOutcome, fromDecision: Boolean) {
        when (outcome) {
            is CodeSetupOutcome.Attempt -> show(outcome.attempt)
            is CodeSetupOutcome.Failed -> {
                pollJob?.cancel()
                _uiState.update {
                    when {
                        outcome.error == CodeSetupError.UNAVAILABLE -> it.copy(loading = false, unavailable = true, decisionOpen = false)
                        fromDecision -> it.copy(loading = false, decisionFailed = true)
                        else -> it.copy(loading = false, loadFailed = attempt == null)
                    }
                }
            }
        }
    }

    private fun show(next: CodeSetupAttempt) {
        attempt = next
        val expiresAt = next.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val expired = expiresAt != null && expiresAt <= clock.now()
        val status = statusOf(next, expired)
        val planes = next.controlPlanes.map { CodeControlPlaneOption(it.id, it.displayName) }
        val bound = next.controlPlaneId
        _uiState.update { current ->
            current.copy(
                loading = false,
                loadFailed = false,
                status = status,
                details = CodeSetupDetails(
                    machineName = next.machineName.orEmpty(),
                    verificationCode = next.verificationCode.orEmpty(),
                    keyFingerprint = next.keyFingerprint.orEmpty(),
                    directoryWorkspace = next.workspace?.type == "directory",
                    expiresIso = next.expiresAt,
                    allowWrites = next.allowWorkspaceWrites == true,
                    allowCommands = next.allowWorkspaceCommands == true,
                    planeName = bound?.let { id -> next.controlPlanes.firstOrNull { it.id == id }?.displayName },
                    planeMissing = bound != null && next.controlPlanes.none { it.id == bound },
                ),
                decisionOpen = !expired && (status == CodeSetupStatusText.PENDING || status == CodeSetupStatusText.RETRYABLE),
                planes = planes,
                // Nothing is pre-selected, as on web: approving names a plane on purpose.
                selectedPlaneId = current.selectedPlaneId?.takeIf { id -> planes.any { it.id == id } },
            )
        }
        schedule(status, expiresAt)
    }

    private fun statusOf(attempt: CodeSetupAttempt, expired: Boolean): CodeSetupStatusText = when (attempt.status) {
        CodeSetupAttempt.PENDING -> if (expired) CodeSetupStatusText.EXPIRED else CodeSetupStatusText.PENDING
        CodeSetupAttempt.PROVISIONING -> CodeSetupStatusText.PROVISIONING
        CodeSetupAttempt.APPROVED -> CodeSetupStatusText.APPROVED
        CodeSetupAttempt.COMPLETED -> CodeSetupStatusText.COMPLETED
        CodeSetupAttempt.DENIED -> CodeSetupStatusText.DENIED
        CodeSetupAttempt.CANCELLED -> CodeSetupStatusText.CANCELLED
        CodeSetupAttempt.EXPIRED -> CodeSetupStatusText.EXPIRED
        else -> if (attempt.failure?.retryable == true && !expired) CodeSetupStatusText.RETRYABLE else CodeSetupStatusText.FAILED
    }

    /** Poll while the attempt can still move; re-read once at its deadline. */
    private fun schedule(status: CodeSetupStatusText, expiresAt: Instant?) {
        pollJob?.cancel()
        val moving = status == CodeSetupStatusText.PENDING || status == CodeSetupStatusText.PROVISIONING ||
            status == CodeSetupStatusText.APPROVED
        if (!moving && status != CodeSetupStatusText.RETRYABLE) return
        pollJob = viewModelScope.launch {
            val untilExpiry = expiresAt?.let { (it - clock.now()).inWholeMilliseconds } ?: Long.MAX_VALUE
            delay(if (moving) minOf(POLL_MS, untilExpiry.coerceAtLeast(0L)) else untilExpiry.coerceAtLeast(0L))
            apply(machines.setupAttempt(attemptId), fromDecision = false)
        }
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
