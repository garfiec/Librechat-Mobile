package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Banner

@Immutable
data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    val isReset: Boolean = false,
    /** The new password is active, but some earlier sessions may stay signed in until they expire. */
    val sessionsNotRetired: Boolean = false,
    /** The server's published policy links, as the sign-in footer shows them; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
    val banner: Banner? = null,
)
