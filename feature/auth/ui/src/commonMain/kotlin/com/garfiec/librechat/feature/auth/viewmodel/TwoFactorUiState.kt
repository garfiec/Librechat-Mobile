package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class TwoFactorUiState(
    val code: String = "",
    val isBackupMode: Boolean = false,
    val backupCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    val isVerified: Boolean = false,
    // Bumped whenever the entered code is cleared as spent (evaluated-and-rejected, or accepted
    // with an unusable session); the code input keys its focus-reset effect on this.
    val codeAttempt: Int = 0,
)
