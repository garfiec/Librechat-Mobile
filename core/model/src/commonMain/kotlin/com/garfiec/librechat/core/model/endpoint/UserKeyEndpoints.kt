package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpecs

/**
 * Whether this endpoint takes any value from the user: an API key, a base URL, or one of
 * Bedrock's credentials.
 *
 * Port of upstream's `isUserProvidedEndpointConfig`
 * (`client/src/components/Nav/SettingsTabs/ProviderKeys/utils.ts`) with one deliberate
 * divergence: web leaves out an endpoint whose only user-provided value is the base URL
 * (`userProvideURL`), and this keeps it, because a chat on that endpoint still fails until the
 * URL is set and this screen is where it is set.
 */
fun EndpointConfig.takesUserProvidedValue(): Boolean =
    userProvide == true || userProvideURL == true || hasBedrockCredentialFlag()

/** Whether the server named any Bedrock credential as user-provided. */
fun EndpointConfig.hasBedrockCredentialFlag(): Boolean =
    userProvideAccessKeyId == true ||
        userProvideSecretAccessKey == true ||
        userProvideSessionToken == true ||
        userProvideBearerToken == true

/**
 * The endpoints whose user-provided values can be set from Provider Keys, in display order.
 *
 * Port of upstream's `getUserKeyEndpoints` and `getReachableEndpoints` (same file as above). An
 * endpoint is listed when the user can reach it and it [takesUserProvidedValue]:
 * - with no model specs, every configured endpoint is reachable;
 * - with model specs, only the endpoints the specs name plus `modelSpecs.addedEndpoints`;
 * - and when `agents` is among those and [hasAgentAccess], the agents' `allowedProviders` too,
 *   or every non-agents, non-assistants endpoint when that list is empty (unrestricted).
 *
 * Ordered by [EndpointConfig.order] ascending, endpoints without one last, then by display label
 * ignoring case. Web lists them in config order; the explicit sort makes the order independent of
 * the map the configs came in.
 */
fun userKeyEndpoints(
    endpointConfigs: Map<String, EndpointConfig>,
    modelSpecs: ModelSpecs?,
    hasAgentAccess: Boolean,
): List<String> {
    if (endpointConfigs.isEmpty()) return emptyList()
    val reachable = reachableEndpoints(endpointConfigs, modelSpecs, hasAgentAccess)
    return endpointConfigs
        .filter { (name, config) -> name in reachable && config.takesUserProvidedValue() }
        .entries
        .sortedWith(
            compareBy<Map.Entry<String, EndpointConfig>, Int?>(nullsLast()) { it.value.order }
                .thenBy { (it.value.modelDisplayLabel ?: it.key).lowercase() },
        )
        .map { it.key }
}

private fun reachableEndpoints(
    endpointConfigs: Map<String, EndpointConfig>,
    modelSpecs: ModelSpecs?,
    hasAgentAccess: Boolean,
): Set<String> {
    val specs = modelSpecs?.list.orEmpty()
    if (specs.isEmpty()) return endpointConfigs.keys

    val reachable = buildSet {
        specs.mapNotNullTo(this) { it.preset?.endpoint?.takeIf(String::isNotEmpty) }
        addAll(modelSpecs?.addedEndpoints.orEmpty())
    }
    val agents = EModelEndpoint.AGENTS.toSerialName()
    if (!hasAgentAccess || agents !in reachable) return reachable

    val allowedProviders = endpointConfigs[agents]?.allowedProviders.orEmpty()
    if (allowedProviders.isNotEmpty()) return reachable + allowedProviders
    return reachable + endpointConfigs.keys.filter { !isAgentsEndpoint(it) && !isAssistantsEndpoint(it) }
}

/** Upstream `isAgentsEndpoint` (`packages/data-provider/src/schemas.ts`). */
private fun isAgentsEndpoint(name: String): Boolean = name == EModelEndpoint.AGENTS.toSerialName()

/** Upstream `isAssistantsEndpoint`: any name ending in `assistants`, ignoring case. */
private fun isAssistantsEndpoint(name: String): Boolean =
    name.lowercase().endsWith(EModelEndpoint.ASSISTANTS.toSerialName())
