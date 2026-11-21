package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.model.EndpointConfig

data class EndpointDispatch(
    val endpointType: String?,
    val key: String?,
    val modelDisplayLabel: String?,
)

object EndpointClassifier {
    /**
     * Resolve the wire fields for a chat-send request, mirroring web's
     * getEndpointField(endpointsConfig, endpoint, 'type') + getExpiry() pattern.
     *
     * - endpointType: prefers EndpointConfig.type from /api/config; falls back to
     *   the endpoint name itself if it's a known built-in (covers cold-start
     *   race where /api/config hasn't loaded); otherwise "custom".
     * - key: when the endpoint is user_provided (`userProvide` or `userProvideURL`),
     *   passes through the pre-fetched [keyExpiry] (an ISO timestamp from
     *   `GET /api/keys?name=<endpoint>` or the literal string `"never"`); falls
     *   back to `"never"` when no key is stored. Null otherwise so the field is
     *   omitted from the wire body.
     * - modelDisplayLabel: from config; falls back to the endpoint name.
     *
     * [keyExpiry] is pre-fetched at the call site (see ChatViewModel /
     * ModelSelectionDelegate) so this function stays pure and synchronous.
     */
    fun classify(
        endpointName: String,
        configs: Map<String, EndpointConfig>,
        keyExpiry: String?,
    ): EndpointDispatch {
        val config = configs[endpointName]
        val endpointType = config?.type
            ?: endpointName.takeIf { it in EModelEndpoint.BUILT_IN_NAMES }
            ?: "custom"
        val key = if (config?.userProvide == true || config?.userProvideURL == true) {
            keyExpiry ?: "never"
        } else {
            null
        }
        val modelDisplayLabel = config?.modelDisplayLabel ?: endpointName
        return EndpointDispatch(endpointType, key, modelDisplayLabel)
    }
}
