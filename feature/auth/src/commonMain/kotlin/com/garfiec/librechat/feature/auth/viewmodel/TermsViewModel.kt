package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class TermsUiState(
    val termsText: String = "",
    val isLoading: Boolean = false,
    val isAccepted: Boolean = false,
    /** The acceptance request is in flight; set with [isLoading], which also covers loading the text. */
    val isAccepting: Boolean = false,
    val error: String? = null,
)

class TermsViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TermsUiState())
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    init {
        loadTerms()
    }

    fun loadTerms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = userRepository.getTerms()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        termsText = result.data.termsOfService ?: "",
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not load terms of service. Please try again.",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isAccepting = true, error = null)
            when (userRepository.acceptTerms()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAccepting = false,
                        isAccepted = true,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAccepting = false,
                        error = "Could not accept terms. Please try again.",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun consumeAccepted() {
        _uiState.value = _uiState.value.copy(isAccepted = false)
    }
}
