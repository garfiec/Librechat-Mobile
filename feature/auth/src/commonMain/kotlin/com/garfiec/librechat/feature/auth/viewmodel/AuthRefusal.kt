package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.error.ServerErrorCode

private const val HTTP_FORBIDDEN = 403

/**
 * Coded refusals from the pre-auth routes (v0.8.9-rc1) that every sign-in screen words the same way,
 * through `authErrorText`. Read from the server's code, never from the status alone: a 403 on these
 * routes is just as likely the user-agent soft-ban, a ban, `terms_not_accepted` or a proxy.
 */
enum class AuthRefusal {
    /** A federated account tried a password sign-in while 2FA is enforced (login, verify-temp). */
    FederatedBlocked,

    /**
     * Strict tenant isolation, and the request named no tenant. Every `/api/auth` route and `/oauth`
     * answers it (login, register, password reset, 2FA), so every sign-in screen maps it.
     */
    TenantRequired,
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
    else -> null
}

/** The refusal this failure carries, or null. See [authRefusalOf]. */
internal fun Result.Error.authRefusalOrNull(): AuthRefusal? =
    (exception as? ApiException)?.let { authRefusalOf(it.statusCode, it.body) }
