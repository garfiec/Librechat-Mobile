package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.model.error.parseUserKeyError
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

/**
 * Behavior tests for the chat-send wire-format resolution and the user-key
 * stream-error parsing path.
 *
 * The dispatch resolution lives in [resolveEndpointDispatch] (extracted out of
 * `ChatViewModel`) and reads the cached `KeyState` out of `endpointKeyStates`
 * — populated by `EndpointKeyStatusDelegate` — so the chat-send hot path stays
 * off the network. Each test pins a `KeyState` shape and asserts the resulting
 * `EndpointDispatch.key` matches the wire string the chat-send body needs.
 *
 * The user-key error parsing path is covered via [parseUserKeyError] so a
 * regression that drops the typed-error envelope handling fails here regardless
 * of how the snackbar is wired in `ChatViewModel`.
 */
class ChatViewModelEndpointDispatchTest {

    @Test
    fun userProvidedEndpointForwardsCachedWireExpiryOnTheWire() {
        // Behavior guarded: when an endpoint is user_provided AND a key is
        // cached as KeyState.Set, the resolver must round-trip the original
        // ISO wire string into `EndpointDispatch.key` instead of an extra
        // `getKeyExpiry` HTTP call.
        val expiry = "2026-12-31T23:59:59Z"
        val keyState = KeyState.Set(
            expiresAt = Instant.parse(expiry),
            neverExpires = false,
            wire = expiry,
        )

        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = mapOf("openAI" to keyState),
        )

        assertThat(dispatch.key).isEqualTo(expiry)
    }

    @Test
    fun userProvidedEndpointWithUnsetKeyFallsBackToNeverOnTheWire() {
        // Behavior guarded: when there is no cached key, the dispatch wire `key`
        // must be the literal `"never"` (matches web's `expiresAt || "never"`).
        // KeyState.Unset and absence both resolve to null at the resolver, then
        // the classifier substitutes `"never"`.
        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = mapOf("openAI" to KeyState.Unset),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun userProvidedEndpointWithMissingCacheEntryFallsBackToNeverOnTheWire() {
        // Built-out of an extra round-trip: even before the delegate's
        // initial fan-out completes, the resolver must produce a valid wire
        // body. Absence in the cache map degrades to `"never"`.
        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = emptyMap(),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun nonUserProvidedEndpointOmitsKeyField() {
        // Behavior guarded: built-in endpoints (no userProvide / userProvideURL)
        // ignore any cached state and the dispatch returns `key = null` so the
        // wire body omits the field.
        val dispatch = resolveEndpointDispatch(
            endpointName = "anthropic",
            endpointConfigs = mapOf("anthropic" to EndpointConfig(userProvide = null)),
            endpointKeyStates = emptyMap(),
        )

        assertThat(dispatch.key).isNull()
    }

    @Test
    fun userProvideUrlOnlyEndpointStillReadsCachedKey() {
        // userProvideURL alone is enough to qualify the endpoint as user_provided.
        val dispatch = resolveEndpointDispatch(
            endpointName = "custom",
            endpointConfigs = mapOf("custom" to EndpointConfig(userProvide = null, userProvideURL = true)),
            endpointKeyStates = mapOf("custom" to KeyState.Unset),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun setNeverExpiresMapsToLiteralNeverOnTheWire() {
        // KeyState.Set with neverExpires=true must always produce the `"never"`
        // wire literal, regardless of the captured wire string.
        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = mapOf(
                "openAI" to KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"),
            ),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun expiredKeyStateOmitsExpiryAndDegradesToNever() {
        // KeyState.Expired returns null at the resolver -> the classifier
        // substitutes `"never"`. The server will reject the chat-send in
        // either shape; the user-facing UX is identical to Unset.
        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = mapOf("openAI" to KeyState.Expired),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun loadingKeyStateOmitsExpiryAndDegradesToNever() {
        // KeyState.Loading is treated as fail-open at the model selector and
        // fail-soft here: degrade to `"never"` rather than blocking the send.
        val dispatch = resolveEndpointDispatch(
            endpointName = "openAI",
            endpointConfigs = mapOf("openAI" to EndpointConfig(userProvide = true)),
            endpointKeyStates = mapOf("openAI" to KeyState.Loading),
        )

        assertThat(dispatch.key).isEqualTo("never")
    }

    @Test
    fun dispatchPopulatesEndpointTypeAndModelDisplayLabel() {
        // Behavior guarded: every chat-send call site reads `endpointType` and
        // `modelDisplayLabel` off the dispatch — they must be populated from
        // `EndpointConfig.type` and `EndpointConfig.modelDisplayLabel`.
        val dispatch = resolveEndpointDispatch(
            endpointName = "myCustomGpt",
            endpointConfigs = mapOf(
                "myCustomGpt" to EndpointConfig(
                    userProvide = true,
                    type = "custom",
                    modelDisplayLabel = "My Custom GPT",
                ),
            ),
            endpointKeyStates = emptyMap(),
        )

        assertThat(dispatch.endpointType).isEqualTo("custom")
        assertThat(dispatch.modelDisplayLabel).isEqualTo("My Custom GPT")
    }

    @Test
    fun streamErrorMessageWithNoUserKeyEnvelopeParsesToTypedError() {
        // Behavior guarded: the StreamEvent.Error branch must recognize JSON-encoded
        // user-key envelopes via `parseUserKeyError` so the chat screen can show a
        // deep-link CTA snackbar instead of the generic error toast.
        val parsed = parseUserKeyError("""{"type":"no_user_key","endpoint":"openAI"}""")

        assertThat(parsed).isInstanceOf(UserKeyError.NoUserKey::class.java)
        assertThat((parsed as UserKeyError.NoUserKey).endpoint).isEqualTo("openAI")
    }

    @Test
    fun streamErrorMessageWithExpiredUserKeyParsesEndpointAndExpiredAt() {
        val parsed = parseUserKeyError(
            """{"type":"expired_user_key","endpoint":"openAI","expiredAt":"4/30/2026, 8:30:00 PM"}""",
        )

        assertThat(parsed).isInstanceOf(UserKeyError.ExpiredUserKey::class.java)
        val expired = parsed as UserKeyError.ExpiredUserKey
        assertThat(expired.endpoint).isEqualTo("openAI")
        assertThat(expired.expiredAt).isEqualTo("4/30/2026, 8:30:00 PM")
    }

    @Test
    fun unrecognizedStreamErrorMessageReturnsNull() {
        // Behavior guarded: a plain-text error message must not be misinterpreted
        // as a typed user-key error — the snackbar fallback to `error = event.message`
        // must run for unrecognized payloads.
        assertThat(parseUserKeyError("rate limit exceeded")).isNull()
        assertThat(parseUserKeyError("""{"type":"server_error"}""")).isNull()
    }
}
