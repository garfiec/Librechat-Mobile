package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Banner

@Immutable
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** [error] came from a failure the diagnostic log can help with; see `offersDiagnosticLog`. */
    val offerDiagnosticLog: Boolean = false,
    /** A coded refusal ([AuthRefusal]) the screen words itself, in place of [error], while [error] is set. */
    val refusal: AuthRefusal? = null,
    val isSent: Boolean = false,
    /** The server has no mail transport, so it sends no reset link: the user asks an administrator. */
    val emailUnavailable: Boolean = false,
    /** The server's published policy links, as the sign-in footer shows them; null hides each. */
    val policyLinks: PolicyLinks = PolicyLinks(),
    val banner: Banner? = null,
)
