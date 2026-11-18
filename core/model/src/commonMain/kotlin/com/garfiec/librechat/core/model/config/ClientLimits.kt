package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

/**
 * `/api/config` `clientLimits` (pre-login and signed in): the operator's request deadlines in
 * milliseconds. Only the two with a request here are decoded; `uploadConcurrency`, `sharepointMs`
 * and `cloudfrontMs` bound web-only work. The server drops invalid values; an absent one keeps this
 * client's own deadline (the refresh client's 15 s, the main client's 30 s), not web's defaults.
 */
@Serializable
data class ClientLimits(
    val refreshMs: Long? = null,
    val ttsMs: Long? = null,
) {
    /** The token refresh POST's deadline. */
    val refreshTimeoutMs: Long get() = refreshMs ?: DEFAULT_REFRESH_MS

    /** A text-to-speech request's deadline. */
    val ttsTimeoutMs: Long get() = ttsMs ?: DEFAULT_TTS_MS

    companion object {
        const val DEFAULT_REFRESH_MS: Long = 15_000
        const val DEFAULT_TTS_MS: Long = 30_000
    }
}
