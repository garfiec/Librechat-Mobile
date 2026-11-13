package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class VerifyEmailUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val resendCooldownSeconds: Int = 0,
    val resendSuccess: Boolean = false,
)

class VerifyEmailViewModel(
    private val userRepository: UserRepository,
    private val accountSwitcher: AccountSwitcher,
    initialEmail: String? = null,
    sendNow: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VerifyEmailUiState(email = initialEmail ?: ""),
    )
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    init {
        if (sendNow) resendVerification()
    }

    fun resendVerification() {
        val email = _uiState.value.email
        if (email.isBlank() || _uiState.value.resendCooldownSeconds > 0) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, resendSuccess = false)
            // From an add-account sign-in, the link comes from the server being added.
            val result = accountSwitcher.withPendingIdentity { userRepository.resendVerification(email) }
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resendSuccess = true,
                    )
                    startCooldown()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.rateLimitMessageOrNull()
                            ?: "Could not resend verification email. Please try again later.",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (seconds in COOLDOWN_DURATION_SECONDS downTo 1) {
                _uiState.value = _uiState.value.copy(resendCooldownSeconds = seconds)
                delay(1000L)
            }
            _uiState.value = _uiState.value.copy(resendCooldownSeconds = 0)
        }
    }

    companion object {
        private const val COOLDOWN_DURATION_SECONDS = 60
    }
}
