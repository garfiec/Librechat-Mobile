package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result

/**
 * The server's own explanation of this failure, or null when [Result.Error.message] is the app's.
 *
 * `toSafeError` always fills `message`: with the server's text when it sent some that reads as a
 * sentence, else with an English fallback for the failure's kind ("Something went wrong…"). That
 * fallback must not stand in for the screen's own translated wording, so only text that came out
 * of the response body ([ApiException.serverAuthored]) and survived the screen counts.
 */
internal fun Result.Error.serverMessage(): String? {
    val api = exception as? ApiException ?: return null
    if (!api.serverAuthored) return null
    return message?.takeIf { it == api.message && it.isNotBlank() }
}

/** A failure as one text: the screen's [summary] (translated), then the server's [detail] when it sent one. */
internal fun failureText(summary: String, detail: String?): String =
    if (detail.isNullOrBlank()) summary else "$summary\n$detail"
