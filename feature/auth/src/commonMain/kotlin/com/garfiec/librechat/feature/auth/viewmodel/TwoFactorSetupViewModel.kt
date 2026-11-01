package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.ui.components.OTP_LENGTH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four steps of upstream's required-enrollment screen (`TwoFactorSetupScreen.tsx`). */
enum class TwoFactorSetupPhase { Start, Authenticator, Verify, BackupCodes }

/** What went wrong, for the screen to word. */
enum class TwoFactorSetupError {
    /** The server could not stage a secret; retryable. */
    StartFailed,

    /** The code did not match the staged secret; enter a fresh one. */
    InvalidCode,

    /** Too many attempts; wait and retry. */
    RateLimited,

    /** Saving the backup codes and enabling 2FA failed in a way a retry can fix. */
    FinalizeFailed,

    /** No answer from the server. */
    Connection,
}

@Immutable
data class TwoFactorSetupUiState(
    val phase: TwoFactorSetupPhase = TwoFactorSetupPhase.Start,
    val isLoading: Boolean = false,
    val otpauthUrl: String? = null,
    /** The `secret` of [otpauthUrl], for an authenticator's "enter a setup key" screen. */
    val setupKey: String? = null,
    val code: String = "",
    /** Bumped when a rejected code is cleared, so the input can take focus again. */
    val codeAttempt: Int = 0,
    val backupCodes: List<String> = emptyList(),
    /** The user saved the codes to a file or confirmed they have them; gates completing setup. */
    val codesSaved: Boolean = false,
    val error: TwoFactorSetupError? = null,
    /**
     * The setup credential is dead (expired, retired by a password reset, already spent, or the
     * server stopped requiring setup). No step can succeed any more; the user starts over from
     * sign-in, as upstream's screen does.
     */
    val mustSignInAgain: Boolean = false,
    /** Setup finished and the session is committed; leave the auth flow. */
    val isComplete: Boolean = false,
)

/**
 * Required 2FA enrollment (v0.8.9-rc1): stage a secret, add it to an authenticator, confirm a code,
 * keep the backup codes, then acknowledge + finalize, which signs the user in.
 *
 * The acknowledgement and finalization credentials are single-use and live only here, never in
 * navigation or saved state. A finalization credential is kept across a failed finalize so a retry
 * reuses it rather than spending the (already consumed) acknowledgement again.
 */
class TwoFactorSetupViewModel(
    private val authRepository: AuthRepository,
    private val tempToken: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TwoFactorSetupUiState())
    val uiState: StateFlow<TwoFactorSetupUiState> = _uiState.asStateFlow()

    private var acknowledgementToken: String? = null
    private var finalizationToken: String? = null

    fun start() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.startTwoFactorSetup(tempToken)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        otpauthUrl = result.data.otpauthUrl,
                        setupKey = otpauthSecret(result.data.otpauthUrl),
                        phase = TwoFactorSetupPhase.Authenticator,
                    )
                }
                is Result.Error -> fail(result, setupCredentialDeadOn = SETUP_CREDENTIAL_DEAD, TwoFactorSetupError.StartFailed)
                is Result.Loading -> Unit
            }
        }
    }

    fun continueToVerify() {
        _uiState.update { it.copy(phase = TwoFactorSetupPhase.Verify, error = null) }
    }

    fun backToAuthenticator() {
        _uiState.update { it.copy(phase = TwoFactorSetupPhase.Authenticator, error = null, code = "") }
    }

    fun onCodeChanged(code: String) {
        val previous = _uiState.value.code
        _uiState.update { it.copy(code = code, error = null) }
        if (code.length == OTP_LENGTH && code != previous) confirm()
    }

    fun confirm() {
        val state = _uiState.value
        if (state.code.length != OTP_LENGTH || state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.confirmTwoFactorSetup(tempToken, state.code)) {
                is Result.Success -> {
                    acknowledgementToken = result.data.acknowledgementToken
                    // Confirming rotates the codes server-side, retiring any earlier credential.
                    finalizationToken = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupCodes = result.data.backupCodes,
                            codesSaved = false,
                            phase = TwoFactorSetupPhase.BackupCodes,
                        )
                    }
                }
                is Result.Error -> {
                    val status = (result.exception as? ApiException)?.statusCode
                    if (status == HTTP_BAD_REQUEST) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = TwoFactorSetupError.InvalidCode,
                                code = "",
                                codeAttempt = it.codeAttempt + 1,
                            )
                        }
                    } else {
                        fail(result, setupCredentialDeadOn = SETUP_CREDENTIAL_DEAD, TwoFactorSetupError.InvalidCode)
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun onCodesSaved() {
        _uiState.update { it.copy(codesSaved = true) }
    }

    fun setCodesAcknowledged(acknowledged: Boolean) {
        _uiState.update { it.copy(codesSaved = acknowledged) }
    }

    fun complete() {
        val state = _uiState.value
        if (state.isLoading || !state.codesSaved) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val finalization = finalizationToken ?: run {
                val ack = acknowledgementToken ?: return@launch restart()
                when (val result = authRepository.acknowledgeTwoFactorSetup(ack)) {
                    is Result.Success -> result.data.also { finalizationToken = it }
                    is Result.Error -> {
                        return@launch fail(result, setupCredentialDeadOn = TRANSITION_CREDENTIAL_SPENT, TwoFactorSetupError.FinalizeFailed)
                    }
                    is Result.Loading -> return@launch
                }
            }
            when (val result = authRepository.finalizeTwoFactorSetup(finalization)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, isComplete = true) }
                is Result.Error -> fail(result, setupCredentialDeadOn = TRANSITION_CREDENTIAL_SPENT, TwoFactorSetupError.FinalizeFailed)
                is Result.Loading -> Unit
            }
        }
    }

    private fun restart() {
        _uiState.update { it.copy(isLoading = false, mustSignInAgain = true) }
    }

    private fun fail(error: Result.Error, setupCredentialDeadOn: Set<Int>, otherwise: TwoFactorSetupError) {
        val api = error.exception as? ApiException
        val status = api?.statusCode
        when {
            status != null && status in setupCredentialDeadOn -> restart()
            status == HTTP_TOO_MANY_REQUESTS -> _uiState.update {
                it.copy(isLoading = false, error = TwoFactorSetupError.RateLimited)
            }
            api == null && error.exception !is IllegalStateException -> _uiState.update {
                it.copy(isLoading = false, error = TwoFactorSetupError.Connection)
            }
            else -> _uiState.update { it.copy(isLoading = false, error = otherwise) }
        }
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_TOO_MANY_REQUESTS = 429

        /**
         * Every setup route checks the enforcement policy and the ban list before its controller and
         * answers both with a 403, and an expired or retired setup token with a 401. Either way the
         * flow is over (upstream `isExpiredSetupCredential`).
         */
        val SETUP_CREDENTIAL_DEAD = setOf(401, 403)

        /**
         * The acknowledgement and finalization nonces are single-use and answer 400 once consumed;
         * a lost response leaves nothing the server will accept again (upstream
         * `isSpentTransitionCredential`).
         */
        val TRANSITION_CREDENTIAL_SPENT = setOf(400, 401, 403)
    }
}

/** The `secret` query parameter of an `otpauth://` URI, percent-decoded, or null. */
internal fun otpauthSecret(uri: String): String? {
    val query = uri.substringAfter('?', "")
    val raw = query.split('&').firstOrNull { it.startsWith("secret=") }?.substringAfter('=') ?: return null
    return percentDecode(raw).takeIf { it.isNotEmpty() }
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            out.append(hex.toChar())
            i += 3
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
