package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class VerifyEmailUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val resendCooldownSeconds: Int = 0,
    val resendSuccess: Boolean = false,
)
