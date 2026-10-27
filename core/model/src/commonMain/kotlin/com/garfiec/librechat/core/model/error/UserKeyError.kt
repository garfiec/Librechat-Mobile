package com.garfiec.librechat.core.model.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Typed user-provided-key error envelope sent by the backend over the SSE stream.
 *
 * The codes are upstream's `ErrorTypes` user-key members (`packages/data-provider/src/config.ts`),
 * registered in `scripts/mirrors.json`. Mirrors web's emission sites in upstream
 * `packages/data-schemas/src/methods/key.ts`,
 * `packages/api/src/utils/key.ts`, and the per-endpoint `initialize.ts` files. See
 * `DISCOVERY.md` for the authoritative wire-shape reference.
 *
 * Note: [ExpiredUserKey.expiredAt] is kept as the raw string the server sent. The pinned server
 * (`checkUserKeyExpiry`) sends an ISO 8601 instant (`"2026-10-26T14:03:00.000Z"`) for the client to
 * format in the reader's locale; servers before it sent `toLocaleString()` output (e.g.
 * `"4/30/2026, 8:30:00 PM"`), which doesn't parse. Display code parses it when it can and shows it
 * unchanged when it can't.
 */
sealed class UserKeyError {
    /**
     * Endpoint is parsed from the server's JSON payload `endpoint` field. The server omits
     * the field on some emission paths, so it is nullable across all variants. Snackbar
     * callers fall back to the list view (no auto-open) when null.
     */
    abstract val endpoint: String?

    data class NoUserKey(override val endpoint: String?) : UserKeyError()
    data class ExpiredUserKey(override val endpoint: String?, val expiredAt: String) : UserKeyError()
    data class InvalidUserKey(override val endpoint: String?) : UserKeyError()

    /** The endpoint takes a user-provided base URL and none is stored with the key (`no_base_url`). */
    data class NoBaseUrl(override val endpoint: String?) : UserKeyError()

    /** The stored base URL was rejected (`invalid_base_url`). */
    data class InvalidBaseUrl(override val endpoint: String?) : UserKeyError()
}

private val parserJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a raw stream-error message into a [UserKeyError]. Returns null when the message is
 * not a JSON object, has no `type` field, the `type` value is unknown, or — for
 * `expired_user_key` — required fields are missing.
 *
 * Adversarial substring inputs like `"foo no_user_key bar"` correctly return null because the
 * parse step fails. This is the entire reason the parser is JSON-based, not substring-based.
 *
 * [fallbackEndpoint] names the endpoint when the server sent none (null or blank): at the pinned
 * server only `expired_user_key` carries one.
 */
fun parseUserKeyError(rawMessage: String, fallbackEndpoint: String? = null): UserKeyError? {
    val element = runCatching { parserJson.parseToJsonElement(rawMessage) }.getOrNull() ?: return null
    val obj = element as? JsonObject ?: return null
    // Safe-cast to JsonPrimitive: the `.jsonPrimitive` extension throws on JsonObject /
    // JsonArray values, which would crash the SSE error-mapping coroutine.
    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return null
    val sent = (obj["endpoint"] as? JsonPrimitive)?.contentOrNull
    val endpoint = if (sent.isNullOrBlank() && !fallbackEndpoint.isNullOrBlank()) fallbackEndpoint else sent
    val expiredAt = (obj["expiredAt"] as? JsonPrimitive)?.contentOrNull
    return when (type) {
        "no_user_key" -> UserKeyError.NoUserKey(endpoint)
        "expired_user_key" -> UserKeyError.ExpiredUserKey(
            endpoint = endpoint,
            expiredAt = expiredAt ?: return null,
        )
        "invalid_user_key" -> UserKeyError.InvalidUserKey(endpoint)
        "no_base_url" -> UserKeyError.NoBaseUrl(endpoint)
        "invalid_base_url" -> UserKeyError.InvalidBaseUrl(endpoint)
        else -> null
    }
}
