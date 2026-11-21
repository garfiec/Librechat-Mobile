package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.error.StreamErrorType

/** Upstream `ErrorTypes.AUTH_FAILED`. LDAP sends it as the 404's `message`, not as a `code`. */
const val AUTH_FAILED_CODE = "auth_failed"

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

    /**
     * The server signed the user in, but its `refreshToken` cookie never arrived, so no session was
     * kept. Not a server code: read from `MissingRefreshCookieException`.
     */
    SessionCookieMissing,
}

/** The refusal named by a server error [code], or null. */
fun authRefusalOfCode(code: String?): AuthRefusal? = when (code) {
    ServerErrorCode.TWO_FACTOR_FEDERATED_LOGIN_BLOCKED -> AuthRefusal.FederatedBlocked
    ServerErrorCode.TENANT_CONTEXT_REQUIRED -> AuthRefusal.TenantRequired
    StreamErrorType.AUTH_CROSS_ORIGIN.wire -> AuthRefusal.CrossOrigin
    StreamErrorType.AUTH_BANNED.wire -> AuthRefusal.Banned
    StreamErrorType.AUTH_RATE_LIMITED.wire -> AuthRefusal.RateLimited
    else -> null
}
