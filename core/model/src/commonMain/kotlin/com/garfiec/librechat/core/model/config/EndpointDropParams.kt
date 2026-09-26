package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads `/api/config`'s `endpointsDropParamsMap` (v0.8.8-rc3), the admin's per-endpoint
 * `dropParams` list: parameters the server deletes from the provider request before sending it.
 *
 * A dropped parameter is accepted and discarded in silence, so without this the user sets a
 * control that provably does nothing.
 *
 * The value is `Record<string, string[] | Record<string, string[]>>` — a list for most endpoints,
 * but for `azureOpenAI` a per-model lookup, because Azure deployments differ model by model. Both
 * arms are answered here so no caller has to know which shape it got.
 */
object EndpointDropParams {

    /**
     * The parameter names dropped for this endpoint/model selection, or empty when the server said
     * nothing about it.
     *
     * Keyed on the endpoint name as the admin configured it, including a custom endpoint's own
     * name — and on the agent's [provider] for the `agents` endpoint, because that is the endpoint
     * whose `dropParams` the request will actually be subject to. Mirrors upstream's
     * `dropParamsMap[provider] ?? dropParamsMap[normalizeEndpointName(provider)]`.
     *
     * The result is UI keys only after
     * `EndpointParameterRegistry`'s alias pass — these are backend field names.
     */
    fun resolve(
        map: Map<String, JsonElement>?,
        endpoint: String?,
        provider: String?,
        model: String?,
    ): List<String> {
        if (map.isNullOrEmpty()) return emptyList()
        val endpointName = if (endpoint?.lowercase() == "agents") provider else endpoint
        if (endpointName.isNullOrBlank()) return emptyList()
        val entry = map[endpointName] ?: map[normalizeEndpointName(endpointName)] ?: return emptyList()
        return when (entry) {
            is JsonArray -> entry.toStringList()
            is JsonObject -> (model?.let { entry[it] } as? JsonArray)?.toStringList().orEmpty()
            else -> emptyList()
        }
    }

    /** Mirrors upstream `normalizeEndpointName` (`packages/data-provider/src/utils.ts`). */
    private fun normalizeEndpointName(name: String): String =
        if (name.lowercase() == "ollama") "ollama" else name

    // `isString` rather than `contentOrNull`: the latter renders a bare literal, so a stray `1`
    // would arrive as the parameter name "1".
    private fun JsonArray.toStringList(): List<String> =
        mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
}
