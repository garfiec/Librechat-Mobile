package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.error.StreamErrorType

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_UNPROCESSABLE = 422
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Upstream `ErrorTypes.AUTH_FAILED`. LDAP sends it as the 404's `message`, not as a `code`. */
internal const val AUTH_FAILED_CODE = "auth_failed"

/**
 * Sign-in refusals that every sign-in screen words the same way, through `authErrorText`, instead of
 * showing the server's English. A 403 is read from the server's code or ban flag, never from the
 * status alone: on these routes it is just as likely the user-agent soft-ban, `terms_not_accepted`
 * or a proxy.
 */
enum class AuthRefusal {
    /** A federated account tried a password sign-in while 2FA is enforced (login, verify-temp). */
    FederatedBlocked,

    /**
     * Strict tenant isolation, and the request named no tenant. Every `/api/auth` route and `/oauth`
     * answers it (login, register, password reset, 2FA), so every sign-in screen maps it.
     */
    TenantRequired,

    /** The server's same-origin guard rejected the request (`auth_cross_origin`). */
    CrossOrigin,

    /** Any LibreChat 404 from the login route: unknown email, wrong password, failed validation. */
    WrongCredentials,

    /** LDAP: the account was created with another provider (`auth_failed`). */
    OtherSignInMethod,

    EmailLoginDisabled,

    /** The login route's 422: the address isn't verified yet. The screen offers a resend. */
    EmailNotVerified,

    /** The account or the network is banned. */
    Banned,

    RateLimited,
}

/** The refusal a 403 [body] carries, or null for any other answer. */
internal fun authRefusalOf(statusCode: Int?, body: String?): AuthRefusal? {
    if (statusCode != HTTP_FORBIDDEN) return null
    return authRefusalOfCode(ServerErrorCode.from(body))
}

/** The refusal named by a server error [code], or null. */
internal fun authRefusalOfCode(code: String?): AuthRefusal? = when (code) {
    ServerErrorCode.TWO_FACTOR_FEDERATED_LOGIN_BLOCKED -> AuthRefusal.FederatedBlocked
    ServerErrorCode.TENANT_CONTEXT_REQUIRED -> AuthRefusal.TenantRequired
    StreamErrorType.AUTH_CROSS_ORIGIN.wire -> AuthRefusal.CrossOrigin
    StreamErrorType.AUTH_BANNED.wire -> AuthRefusal.Banned
    StreamErrorType.AUTH_RATE_LIMITED.wire -> AuthRefusal.RateLimited
    else -> null
}

/** The refusal this failure carries, or null. See [authRefusalOf]. */
internal fun Result.Error.authRefusalOrNull(): AuthRefusal? =
    (exception as? ApiException)?.let { authRefusalOf(it.statusCode, it.body) }

/**
 * The refusal a `POST /api/auth/login` failure carries, or null to show its message. Only LibreChat's
 * own JSON ([ApiException.serverAuthored]) is read as wrong credentials or email login being off: a
 * proxy or WAF answers this route with a 404 or 403 page too, and that is not the user's password.
 */
internal fun Result.Error.loginRefusalOrNull(): AuthRefusal? {
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
