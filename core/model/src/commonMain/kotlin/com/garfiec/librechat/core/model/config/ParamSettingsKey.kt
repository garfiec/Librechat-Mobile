package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.EndpointConfig

/**
 * The key that picks [endpoint]'s parameter set: its `customParams.defaultParamsEndpoint` when that
 * names a parameter set the server has a schema for, else its `type`, else the endpoint itself. A
 * custom endpoint ("OpenRouter", "Groq") is keyed by its own name, which names no parameter set, but
 * is served as `type: custom`, whose set is OpenAI's; the server parses it with that schema, which
 * keeps only `top_p` / `max_tokens`.
 *
 * MIRRORED from web's `Panel.tsx` (`customParams.defaultParamsEndpoint ?? endpointType ?? provider`)
 * and the server's `getFallbackEndpointSchema` (`packages/data-provider/src/parsers.ts`), registered in
 * `scripts/mirrors.json` as `param-settings-key-resolution`. The override wins only when it is an
 * exact `endpointSchemas` key (`isEndpointSchemaLookupKey`, case-sensitive): an admin's `xai` or
 * `Anthropic` is parsed by the server with the `type`'s schema, so the client must send that set too.
 *
 * Only the parameter set follows this key. `dropParams` stays keyed on the endpoint's own name
 * ([EndpointDropParams.resolve]), as web reads it.
 */
fun paramSettingsKey(endpoint: String, endpointConfigs: Map<String, EndpointConfig>): String {
    val config = endpointConfigs[endpoint] ?: endpointConfigs[normalizeEndpointName(endpoint)]
    return config?.customParams?.defaultParamsEndpoint?.takeIf { it in ENDPOINT_SCHEMA_KEYS }
        ?: config?.type?.takeIf { it.isNotBlank() }
        ?: endpoint
}

/** MIRRORED from upstream `endpointSchemas`' keys (`packages/data-provider/src/parsers.ts`). */
private val ENDPOINT_SCHEMA_KEYS = setOf(
    "openAI",
    "azureOpenAI",
    "custom",
    "openrouter",
    "google",
    "anthropic",
    "assistants",
    "azureAssistants",
    "agents",
    "bedrock",
)
