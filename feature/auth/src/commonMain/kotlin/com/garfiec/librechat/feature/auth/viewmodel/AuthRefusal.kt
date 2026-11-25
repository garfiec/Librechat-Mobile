package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MissingRefreshCookieException
import com.garfiec.librechat.core.model.error.ServerErrorCode

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_UNPROCESSABLE = 422
private const val HTTP_TOO_MANY_REQUESTS = 429

/** The refusal a 403 [body] carries, or null for any other answer. */
internal fun authRefusalOf(statusCode: Int?, body: String?): AuthRefusal? {
    if (statusCode != HTTP_FORBIDDEN) return null
    return authRefusalOfCode(ServerErrorCode.from(body))
}

/** The change was made (new password, 2FA on), but no earlier session was retired and none handed back. */
internal fun Result.Error.isCredentialRetirementIncomplete(): Boolean =
    ServerErrorCode.from((exception as? ApiException)?.body) == ServerErrorCode.CREDENTIAL_RETIREMENT_INCOMPLETE

/** The refusal this failure carries, or null. See [authRefusalOf]. */
internal fun Result.Error.authRefusalOrNull(): AuthRefusal? =
    (exception as? ApiException)?.let { authRefusalOf(it.statusCode, it.body) }

/**
 * The refusal a `POST /api/auth/login` failure carries, or null to show its message. Only LibreChat's
 * own JSON ([ApiException.serverAuthored]) is read as wrong credentials or email login being off: a
 * proxy or WAF answers this route with a 404 or 403 page too, and that is not the user's password.
 */
internal fun Result.Error.loginRefusalOrNull(): AuthRefusal? {
    if (exception is MissingRefreshCookieException) return AuthRefusal.SessionCookieMissing
    val api = exception as? ApiException ?: return null
    return when (api.statusCode) {
        HTTP_FORBIDDEN -> authRefusalOf(api.statusCode, api.body) ?: when {
            api.isBanned -> AuthRefusal.Banned
            api.serverAuthored -> AuthRefusal.EmailLoginDisabled
            else -> null
        }
        HTTP_NOT_FOUND -> when {
            !api.serverAuthored -> null
            api.message == AUTH_FAILED_CODE -> AuthRefusal.OtherSignInMethod
            else -> AuthRefusal.WrongCredentials
        }
        HTTP_UNPROCESSABLE -> AuthRefusal.EmailNotVerified.takeIf { api.serverAuthored }
        HTTP_TOO_MANY_REQUESTS -> AuthRefusal.RateLimited
        else -> null
    }
}
