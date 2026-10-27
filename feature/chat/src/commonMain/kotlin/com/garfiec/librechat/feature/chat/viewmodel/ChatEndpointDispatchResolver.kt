package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.data.endpoint.EndpointClassifier
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState

/**
 * Resolves the chat-send dispatch fields for [endpointName] against the supplied
 * [endpointConfigs] map, reading the cached user-provided-key state out of
 * [endpointKeyStates] (populated by `EndpointKeyStatusDelegate`) so the wire
 * body carries the real expiry without an extra HTTP round-trip on the
 * chat-send hot path.
 *
 * Wire-string mapping:
 * - [KeyState.Set] with `neverExpires=true` -> `"never"`
 * - [KeyState.Set] with `neverExpires=false` -> the original ISO wire string
 *   ([KeyState.Set.wire]) so we round-trip exactly what the server emitted.
 * - [KeyState.Expired] -> its past ISO wire string ([KeyState.Expired.wire]), so the server's
 *   expiry check answers with a typed `expired_user_key` instead of trying the key as if it
 *   never expired.
 * - [KeyState.Unset], [KeyState.Loading], or absent -> null (the classifier substitutes
 *   `"never"` when the endpoint is user-provided).
 */
internal fun resolveEndpointDispatch(
    endpointName: String,
    endpointConfigs: Map<String, EndpointConfig>,
    endpointKeyStates: Map<String, KeyState>,
): EndpointDispatch {
    val expiry: String? = when (val state = endpointKeyStates[endpointName]) {
        is KeyState.Set -> if (state.neverExpires) "never" else state.wire
        is KeyState.Expired -> state.wire
        else -> null
    }
    return EndpointClassifier.classify(endpointName, endpointConfigs, keyExpiry = expiry)
}
