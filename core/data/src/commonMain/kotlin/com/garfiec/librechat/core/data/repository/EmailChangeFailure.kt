package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.auth.EmailChangeError
import com.garfiec.librechat.core.model.error.ServerErrorCode

/**
 * Why [UserRepository.requestEmailChange] failed, from the body's `code`. Keyed on the code, never
 * the status alone: the route's 403s (`current_password_invalid`, `local_account_required`, …) are
 * all local to the dialog, and none of them is a ban or an auth gate.
 */
fun Result.Error.toEmailChangeError(): EmailChangeError {
    val api = exception as? ApiException ?: return when (kind) {
        FailureKind.Network, FailureKind.Timeout, FailureKind.Tls -> EmailChangeError.Network
        else -> EmailChangeError.Unknown
    }
    if (api.statusCode == HTTP_TOO_MANY_REQUESTS) return EmailChangeError.RateLimited
    return EmailChangeError.fromCode(ServerErrorCode.from(api.body))
}

/** The server's `Retry-After` on a 429, when it sent one. */
val Result.Error.retryAfterSeconds: Long?
    get() = (exception as? ApiException)?.retryAfterSeconds

private const val HTTP_TOO_MANY_REQUESTS = 429
