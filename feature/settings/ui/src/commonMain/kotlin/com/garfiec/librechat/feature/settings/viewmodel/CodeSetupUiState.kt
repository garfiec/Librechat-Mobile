package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

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
