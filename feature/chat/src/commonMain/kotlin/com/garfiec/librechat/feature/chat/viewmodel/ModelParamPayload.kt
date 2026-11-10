package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.ParameterType
import com.garfiec.librechat.core.model.config.EndpointDropParams
import com.garfiec.librechat.core.ui.components.EndpointParameterRegistry
import com.garfiec.librechat.core.ui.components.ModelParameters
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializes the configured model parameters into the provider-keyed map the server expects on a
 * chat send. Mirrors the web client, which spreads the `endpointOption` (the conversation's model
 * params) at the top level of the chat payload (`createPayload.ts: { ...userMessage, ...endpointOption }`).
 *
 * The mobile parameter sheet is driven by [EndpointParameterRegistry] definitions whose `key` is the
 * server's wire key (provider-aware: `top_p` for OpenAI, `topP`/`topK` for Anthropic, `promptCache`/
 * `promptCacheTtl`, etc.). We emit only the params the user actually changed from the composer's
 * default state — so an untouched sheet sends nothing and the server keeps its own defaults, exactly
 * as before.
 *
 * **A value a per-model rule took away is not sent** — deliberately unlike the web, which sends
 * it. The per-model rules narrow an option list after a value was chosen: `minimal` effort saved on
 * one model is not an option on gpt-6-sol, `none` is not one on Grok 4.7. The sheet then shows the
 * control at its first position ("Unset"), and sending the stale value would make that display a
 * lie and put a value on the wire the provider may reject.
 *
 * Only such a value ([EndpointParameterRegistry.modelRemovedOptions]), not every value absent from
 * the options: those also lack `xhigh`/`max` while the server version is undetected, and anything a
 * newer backend added. Omitting those would change the run silently — the server saves the conversation's
 * parameters from the request — where sending one an older server rejects fails visibly.
 */
object ModelParamPayload {

    /**
     * Builds the model-parameter map for the given endpoint selection, carrying only the params the
     * user changed from the default composer state, plus [alwaysSent]. Returns an empty object when
     * there is neither.
     *
     * [settingsEndpoint] / [settingsProvider] pick the parameter set, and so the wire key of every
     * value (`top_p` on a custom endpoint, `topP` on Anthropic): see `paramSettingsKey`. [endpoint] /
     * [provider] are the configured names `dropParams` is keyed on. They differ only for a custom
     * endpoint (or an agent on one).
     *
     * **Only the keys of that set, and its [EndpointParameterRegistry.wireOnlyDefinitions], go out, and
     * the server unsets every stored field a request leaves out** (`getUnsetFields`,
     * `packages/api/src/conversations/save.ts`). A value lost on the way here,
     * a key missing from the set or sent under another provider's spelling, is erased from the
     * conversation on the next send, including one set on web.
     *
     * [alwaysSent] keys go out even at their default, where the server would otherwise apply a
     * default of its own (an admin's `paramDefinitions` default for `web_search`).
     */
    fun build(
        endpoint: String,
        provider: String?,
        model: String?,
        extendedEffortSupported: Boolean,
        params: ModelParameters,
        dropParamsMap: Map<String, JsonElement>? = null,
        settingsEndpoint: String = endpoint,
        settingsProvider: String? = provider,
        alwaysSent: Set<String> = emptySet(),
    ): JsonObject {
        val dropParams = EndpointDropParams.resolve(dropParamsMap, endpoint, provider, model)
        val definitions = EndpointParameterRegistry.getDefinitions(
            endpoint = settingsEndpoint,
            extendedEffortSupported = extendedEffortSupported,
            provider = settingsProvider,
            model = model,
            dropParams = dropParams,
        )
        val modelRemoved = EndpointParameterRegistry.modelRemovedOptions(
            endpoint = settingsEndpoint,
            provider = settingsProvider,
            model = model,
            dropParams = dropParams,
        )
        val wireOnly = EndpointParameterRegistry.wireOnlyDefinitions(settingsEndpoint, settingsProvider, model, dropParams)
        val out = LinkedHashMap<String, JsonElement>()
        for (definition in definitions + wireOnly) {
            val key = definition.key
            val raw = params.getValueForKey(key)
            // Skip any value the server would apply on its own: a blank (unset), the composer baseline
            // (ModelParameters.DEFAULT), or the provider-specific registry default. The registry default
            // must be checked too — for a dynamic-only key like promptCache the composer baseline is ""
            // (empty dynamicValues), so a seeded default-valued entry would otherwise be over-sent. Mere
            // presence in `dynamicValues` is not "changed": the sheet seeds default-valued entries on open.
            val atDefault = raw == ModelParameters.DEFAULT.getValueForKey(key) || raw == definition.default
            if (raw.isBlank() || (atDefault && key !in alwaysSent)) continue
            if (raw in modelRemoved[key].orEmpty()) continue
            out[key] = encode(definition.type, raw)
        }
        return JsonObject(out)
    }

    /** Encodes a string param value to a typed JSON element matching its control type. */
    private fun encode(type: ParameterType, raw: String): JsonElement = when (type) {
        ParameterType.CHECKBOX, ParameterType.SWITCH ->
            JsonPrimitive(raw.toBooleanStrictOrNull() ?: (raw == "true"))
        ParameterType.SLIDER -> {
            val asInt = raw.toIntOrNull()
            when {
                asInt != null -> JsonPrimitive(asInt)
                else -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
            }
        }
        ParameterType.TAGS ->
            JsonArray(
                raw.split('\n', ',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { JsonPrimitive(it) },
            )
        // DROPDOWN, ENUM_SLIDER, TEXT, TEXTAREA -> string (e.g. promptCacheTtl "5m"/"1h", reasoning_effort)
        else -> JsonPrimitive(raw)
    }
}
