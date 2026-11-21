package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.takesUserProvidedValue
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.model.error.parseUserKeyError
import com.garfiec.librechat.feature.chat.screen.endpointLabel
import com.garfiec.librechat.feature.chat.util.keyEndpointOf

/**
 * What a key error saved in the thread needs from the chat: the endpoint configs and agents that
 * name the provider, and [onAddKey], which opens Provider API Keys for an endpoint.
 */
@Immutable
class UserKeyErrors(
    val endpointConfigs: Map<String, EndpointConfig>,
    val agents: List<Agent>,
    val onAddKey: (endpoint: String) -> Unit,
)

/**
 * Provided by [ChatRootContent]. Null where the keys aren't the reader's (the shared-chat viewer) or no
 * host is in scope: a key error then reads without a provider name and offers no Add key.
 */
val LocalUserKeyErrors = compositionLocalOf<UserKeyErrors?> { null }

/** A key error as the thread shows it. [addKeyEndpoint] is null when there is no key to add. */
internal data class SavedUserKeyError(val error: UserKeyError, val label: String?, val addKeyEndpoint: String?)

/**
 * [raw] read as a key error, or null for any other failure. The reply's [endpoint] and [model] name
 * the key when the payload doesn't (on the agents endpoint, the agent's provider); a payload's own
 * endpoint outranks them. Add key is offered only for an endpoint that takes the user's own value.
 */
internal fun savedUserKeyError(raw: String, endpoint: String?, model: String?, keys: UserKeyErrors?): SavedUserKeyError? {
    val keyEndpoint = endpoint?.let { keyEndpointOf(it, model, keys?.agents.orEmpty()) }
    val error = parseUserKeyError(raw, fallbackEndpoint = keyEndpoint) ?: return null
    if (keys == null) return SavedUserKeyError(error, label = null, addKeyEndpoint = null)
    val name = error.endpoint?.takeIf { it.isNotBlank() }
    return SavedUserKeyError(
        error = error,
        label = error.endpointLabel(keys.endpointConfigs),
        addKeyEndpoint = name?.takeIf { keys.endpointConfigs[it]?.takesUserProvidedValue() == true },
    )
}
