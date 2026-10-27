package com.garfiec.librechat.core.model.endpoint

import kotlin.time.Instant

/**
 * Quad-state representation of a stored user-provided API key for an endpoint.
 *
 * - [Loading] — initial state before the per-endpoint `getKeyExpiry` GET has resolved.
 *   UI renders this as a neutral placeholder, NOT as "never expire".
 * - [Unset] — no key on file (`getKeyExpiry` returned `null`).
 * - [Set] — key on file. [Set.expiresAt] is the parsed `Instant?` for display
 *   (null when [Set.neverExpires] is true). [Set.wire] preserves the original
 *   wire string so chat-send dispatch can round-trip it losslessly into the
 *   request body without re-fetching from the server.
 * - [Expired] — key on file but past its expiry. The server's key collection has a TTL index,
 *   so this only lasts until the server deletes the key (about a minute), after which it reads
 *   as [Unset]. [Expired.expiresAt] and [Expired.wire] keep the past date so chat-send can
 *   send it back, and the server answers with a typed `expired_user_key`.
 */
sealed class KeyState {
    data object Loading : KeyState()
    data object Unset : KeyState()
    data class Set(
        val expiresAt: Instant?,
        val neverExpires: Boolean,
        val wire: String,
    ) : KeyState()
    data class Expired(
        val expiresAt: Instant,
        val wire: String,
    ) : KeyState()
    companion object
}

/**
 * Result of parsing a wire-string into a [KeyState]. [malformedSource] is non-null
 * only when the input was non-empty AND not `"never"` AND failed to parse as an
 * ISO instant — i.e. the fail-closed-to-Unset path. Callers can surface a warning
 * without re-implementing the mapping rule.
 */
data class WireParseResult(val state: KeyState, val malformedSource: String? = null)

/**
 * Canonical wire-string -> [KeyState] mapping. Single source of truth shared by the
 * chat-side `EndpointKeyStatusDelegate` and the settings-side provider-keys ViewModels.
 * Pass [now] explicitly so tests can pin "now" to a fixed instant.
 *
 * Cases:
 * - null or empty raw -> [KeyState.Unset]
 * - `"never"` literal -> [KeyState.Set] with `expiresAt = null`, `neverExpires = true`,
 *   `wire = "never"`
 * - parseable ISO instant strictly before [now] -> [KeyState.Expired] with that instant and
 *   `wire = raw`
 * - parseable ISO instant equal to or after [now] -> [KeyState.Set] (still live at the
 *   threshold instant) with that instant and `wire = raw`
 * - any other (malformed) string -> [KeyState.Unset] (fail-closed) plus the raw
 *   string in [WireParseResult.malformedSource].
 */
fun KeyState.Companion.fromWire(raw: String?, now: Instant): WireParseResult {
    if (raw.isNullOrEmpty()) return WireParseResult(KeyState.Unset)
    if (raw == "never") {
        return WireParseResult(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
    }
    val parsed = runCatching { Instant.parse(raw) }.getOrNull()
        ?: return WireParseResult(KeyState.Unset, malformedSource = raw)
    val state = if (parsed < now) {
        KeyState.Expired(expiresAt = parsed, wire = raw)
    } else {
        KeyState.Set(expiresAt = parsed, neverExpires = false, wire = raw)
    }
    return WireParseResult(state)
}
