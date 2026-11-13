package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.auth_error_banned
import com.garfiec.librechat.feature.auth.resources.auth_error_cross_origin
import com.garfiec.librechat.feature.auth.resources.auth_error_rate_limited
import com.garfiec.librechat.feature.auth.resources.auth_error_session_cookie_missing
import com.garfiec.librechat.feature.auth.resources.auth_error_tenant_required
import com.garfiec.librechat.feature.auth.resources.login_error_email_login_disabled
import com.garfiec.librechat.feature.auth.resources.login_error_email_not_verified
import com.garfiec.librechat.feature.auth.resources.login_error_federated_blocked
import com.garfiec.librechat.feature.auth.resources.login_error_other_sign_in_method
import com.garfiec.librechat.feature.auth.resources.login_error_wrong_credentials
import com.garfiec.librechat.feature.auth.resources.login_error_wrong_credentials_username
import com.garfiec.librechat.feature.auth.viewmodel.AuthRefusal
import org.jetbrains.compose.resources.stringResource

/**
 * The error line a sign-in screen shows: the app's own wording for a coded [refusal], else [error].
 * Shown only while [error] is set, so the edits that clear a screen's error clear the refusal's
 * wording with it.
 */
@Composable
internal fun authErrorText(refusal: AuthRefusal?, error: String?, usernameLogin: Boolean = false): String? {
    if (error == null) return null
    return refusal?.let { authRefusalText(it, usernameLogin) } ?: error
}

@Composable
internal fun authRefusalText(refusal: AuthRefusal, usernameLogin: Boolean = false): String = stringResource(
    when (refusal) {
        AuthRefusal.FederatedBlocked -> Res.string.login_error_federated_blocked
        AuthRefusal.TenantRequired -> Res.string.auth_error_tenant_required
        AuthRefusal.CrossOrigin -> Res.string.auth_error_cross_origin
        AuthRefusal.WrongCredentials ->
            if (usernameLogin) Res.string.login_error_wrong_credentials_username else Res.string.login_error_wrong_credentials
        AuthRefusal.OtherSignInMethod -> Res.string.login_error_other_sign_in_method
        AuthRefusal.EmailLoginDisabled -> Res.string.login_error_email_login_disabled
        AuthRefusal.EmailNotVerified -> Res.string.login_error_email_not_verified
        AuthRefusal.Banned -> Res.string.auth_error_banned
        AuthRefusal.RateLimited -> Res.string.auth_error_rate_limited
        AuthRefusal.SessionCookieMissing -> Res.string.auth_error_session_cookie_missing
    },
)
