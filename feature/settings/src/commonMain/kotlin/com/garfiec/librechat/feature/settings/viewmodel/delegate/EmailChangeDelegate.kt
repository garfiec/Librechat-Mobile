package com.garfiec.librechat.feature.settings.viewmodel.delegate

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.repository.retryAfterSeconds
import com.garfiec.librechat.core.data.repository.toEmailChangeError
import com.garfiec.librechat.core.model.auth.EMAIL_CHANGE_COOLDOWN_SECONDS
import com.garfiec.librechat.core.model.auth.EmailChangeError
import com.garfiec.librechat.core.model.auth.EmailChangeField
import com.garfiec.librechat.core.model.auth.MAX_EMAIL_CHANGE_PASSWORD_LENGTH
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The "Change email address" dialog while it is open. The password lives only here, in memory. */
@Immutable
data class EmailChangeDialogState(
    val newEmail: String = "",
    val password: String = "",
    val isSending: Boolean = false,
    val error: EmailChangeError? = null,
    /** Bumped when the password is cleared after a refusal, so the screen can refocus it. */
    val passwordResets: Int = 0,
) {
    val emailError: EmailChangeError? get() = error?.takeIf { it.field == EmailChangeField.Email }
    val passwordError: EmailChangeError? get() = error?.takeIf { it.field == EmailChangeField.Password }
    val dialogError: EmailChangeError? get() = error?.takeIf { it.field == null }
}

/** One-shot page notices of the email change, shown as a snackbar and then consumed. */
@Immutable
sealed interface EmailChangeNotice {
    data class LinkSent(val newEmail: String) : EmailChangeNotice
    data object Updated : EmailChangeNotice
}

/**
 * `POST /api/user/email/change` (v0.8.9-rc1). The request only mails a link to the new address;
 * the address changes when the user opens it on the web, which the profile refresh on resume picks
 * up. Nothing is retried: every attempt that passes validation mails the old address, and a 429
 * counts toward a ban, so Send stays off for the limiter's window after one.
 */
class EmailChangeDelegate(
    private val stateHandle: SettingsStateHandle<AccountSettingsUiState>,
    private val userRepository: UserRepository,
) {
    private var requestJob: Job? = null
    private var cooldownJob: Job? = null

    /** A link was sent in this ViewModel's life; only then is a changed address announced. */
    private var requestedThisSession = false

    fun open() {
        stateHandle.update { if (emailChange == null) copy(emailChange = EmailChangeDialogState()) else this }
    }

    /** Cancel, back and scrim. Ignored while sending: the answer belongs to this dialog. */
    fun dismiss() {
        stateHandle.update { if (emailChange?.isSending == true) this else copy(emailChange = null) }
    }

    fun onNewEmailChanged(value: String) = editDraft { copy(newEmail = value, error = null) }

    fun onPasswordChanged(value: String) = editDraft { copy(password = value, error = null) }

    private inline fun editDraft(crossinline edit: EmailChangeDialogState.() -> EmailChangeDialogState) {
        stateHandle.update {
            val draft = emailChange
            if (draft == null || draft.isSending) this else copy(emailChange = draft.edit())
        }
    }

    fun send() {
        val state = stateHandle.state
        val draft = state.emailChange ?: return
        if (draft.isSending || state.emailChangeCoolingDown) return
        val newEmail = draft.newEmail.trim()
        val password = draft.password
        if (newEmail.isEmpty() || password.isEmpty()) return
        val precheck = when {
            password.length > MAX_EMAIL_CHANGE_PASSWORD_LENGTH -> EmailChangeError.CurrentPasswordInvalid
            state.user?.email?.equals(newEmail, ignoreCase = true) == true -> EmailChangeError.SameEmail
            else -> null
        }
        if (precheck != null) {
            fail(precheck)
            return
        }
        stateHandle.update { copy(emailChange = emailChange?.copy(isSending = true, error = null)) }
        requestJob = stateHandle.scope.launch {
            val result = userRepository.requestEmailChange(newEmail = newEmail, currentPassword = password)
            // Dismissal is refused while sending, so the dialog is still there unless the page went.
            if (stateHandle.state.emailChange == null) return@launch
            when (result) {
                is Result.Success -> {
                    requestedThisSession = true
                    stateHandle.update {
                        copy(emailChange = null, emailChangeNotice = EmailChangeNotice.LinkSent(newEmail))
                    }
                }
                is Result.Error -> {
                    val error = result.toEmailChangeError()
                    if (error == EmailChangeError.RateLimited) {
                        startCooldown(result.retryAfterSeconds ?: EMAIL_CHANGE_COOLDOWN_SECONDS)
                    }
                    fail(error)
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun fail(error: EmailChangeError) {
        stateHandle.update {
            val draft = emailChange ?: return@update this
            val cleared = error == EmailChangeError.CurrentPasswordInvalid
            copy(
                emailChange = draft.copy(
                    isSending = false,
                    error = error,
                    password = if (cleared) "" else draft.password,
                    passwordResets = if (cleared) draft.passwordResets + 1 else draft.passwordResets,
                ),
            )
        }
    }

    private fun startCooldown(seconds: Long) {
        cooldownJob?.cancel()
        stateHandle.update { copy(emailChangeCoolingDown = true) }
        cooldownJob = stateHandle.scope.launch {
            delay(seconds.coerceAtLeast(1) * MILLIS_PER_SECOND)
            stateHandle.update { copy(emailChangeCoolingDown = false) }
        }
    }

    /** The profile was re-read; announces a new address once a link was sent in this session. */
    fun onProfileRefreshed(previousEmail: String?, email: String) {
        if (!requestedThisSession || previousEmail == null) return
        if (previousEmail.equals(email, ignoreCase = true)) return
        stateHandle.update { copy(emailChangeNotice = EmailChangeNotice.Updated) }
    }

    fun consumeNotice() {
        stateHandle.update { copy(emailChangeNotice = null) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
