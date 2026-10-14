package com.garfiec.librechat.feature.auth.diagnostics

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result

/**
 * Whether a failed sign-in request should offer the diagnostic log: no answer came back, something
 * other than LibreChat answered (a proxy or firewall page), or LibreChat itself failed (5xx).
 *
 * Not when LibreChat judged the request — a wrong password, a taken email, a rate limit, a ban, email
 * sign-in turned off. The server already said why, and the log has nothing to add.
 */
internal fun Result.Error.offersDiagnosticLog(): Boolean {
    val api = exception as? ApiException ?: return true
    return api.statusCode >= 500 || !api.serverAuthored
}
