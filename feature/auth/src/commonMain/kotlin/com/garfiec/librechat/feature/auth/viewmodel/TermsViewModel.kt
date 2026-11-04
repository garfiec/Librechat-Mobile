package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TermsUiState(
    /** The server's `modalContent` (Markdown), or blank when it sent none. */
    val termsText: String = "",
    /** The server's `modalTitle`, or null for the app's own title. */
    val title: String? = null,
    /** A startup config has arrived, so [termsText] and [title] are what the server sends. */
    val termsLoaded: Boolean = false,
    val isAccepted: Boolean = false,
    val isAccepting: Boolean = false,
    /** Loading the terms failed with no config to fall back on, or accepting them failed. */
    val error: String? = null,
)

/**
 * The terms come from the startup config (`interface.termsOfService.modalTitle` / `modalContent`),
 * as web's acceptance modal reads them: `GET /api/user/terms` returns only the acceptance state.
 */
class TermsViewModel(
    private val userRepository: UserRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TermsUiState())
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.startupConfig.filterNotNull().collect { config ->
                val terms = config.interfaceConfig?.termsOfService
                _uiState.update {
                    it.copy(
                        termsText = terms?.modalContent.orEmpty(),
                        title = terms?.modalTitle?.takeIf(String::isNotBlank),
                        termsLoaded = true,
                        error = if (it.termsLoaded) it.error else null,
                    )
                }
            }
        }
        if (configRepository.startupConfig.value == null) loadTerms()
    }

    /** Fetches the startup config; the collector above takes the terms from it. */
    fun loadTerms() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            if (configRepository.fetchStartupConfig() is Result.Error && !_uiState.value.termsLoaded) {
                _uiState.update { it.copy(error = "Could not load terms of service. Please try again.") }
            }
        }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAccepting = true, error = null) }
            when (userRepository.acceptTerms()) {
                is Result.Success -> _uiState.update {
                    it.copy(isAccepting = false, isAccepted = true)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isAccepting = false, error = "Could not accept terms. Please try again.")
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun consumeAccepted() {
        _uiState.update { it.copy(isAccepted = false) }
    }
}
