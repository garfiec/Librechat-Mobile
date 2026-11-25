package com.garfiec.librechat.feature.settings.viewmodel.delegate

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.auth.EmailChangeError
import com.garfiec.librechat.core.model.auth.EmailChangeField

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
