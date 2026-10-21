package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result

/**
 * Checks whether a [Result.Error] was caused by a specific HTTP status code.
 * Shared across the settings module (e.g. 2FA delegate, account deletion).
 */
internal fun Result.Error.isHttpStatus(statusCode: Int): Boolean =
    (exception as? ApiException)?.statusCode == statusCode

/**
 * Whether the server refused a request because the account has 2FA on and no code was sent.
 *
 * `verifyOTPOrBackupCode` answers a missing code with 400 and the controller's wording, and a wrong
 * code with 401, so the status alone would also catch an unrelated validation failure. Matched on
 * the message's gist rather than verbatim so a rewording upstream fails open to the plain error
 * rather than to a prompt that cannot help.
 */
internal fun Result.Error.isTwoFactorCodeRequired(): Boolean {
    if (!isHttpStatus(400)) return false
    val message = message ?: exception?.message ?: return false
    return message.contains("backup code", ignoreCase = true) && message.contains("required", ignoreCase = true)
}
